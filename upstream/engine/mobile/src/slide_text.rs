//! Targeted edits to DrawingML text using the existing ZIP/XML libraries. Other parts are copied.
use std::io::{Cursor, Read, Write};

use crate::MobileError;

fn error(e: impl std::fmt::Display) -> MobileError { MobileError::Command(e.to_string()) }

fn part(bytes: &[u8], name: &str) -> Result<String, MobileError> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).map_err(error)?;
    let mut file = zip.by_name(name).map_err(error)?;
    if file.size() > 16 * 1024 * 1024 { return Err(error("幻灯片 XML 超出编辑大小限制")); }
    let mut xml = String::new();
    file.read_to_string(&mut xml).map_err(error)?;
    Ok(xml)
}

pub(crate) fn paths(bytes: &[u8]) -> Result<Vec<String>, MobileError> {
    let xml = part(bytes, "ppt/presentation.xml")?;
    let rels = part(bytes, "ppt/_rels/presentation.xml.rels")?;
    let doc = roxmltree::Document::parse(&xml).map_err(error)?;
    let relationships = roxmltree::Document::parse(&rels).map_err(error)?;
    doc.descendants().filter(|n| n.has_tag_name(("http://schemas.openxmlformats.org/presentationml/2006/main", "sldId"))).map(|n| {
        let id = n.attribute(("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")).ok_or_else(|| error("缺少幻灯片关系"))?;
        let rel = relationships.descendants().find(|r| r.attribute("Id") == Some(id)).ok_or_else(|| error("找不到幻灯片关系"))?;
        if rel.attribute("TargetMode") == Some("External") { return Err(error("不能编辑外部幻灯片")); }
        let target = rel.attribute("Target").ok_or_else(|| error("缺少幻灯片路径"))?;
        let joined = if target.starts_with('/') { target.trim_start_matches('/').to_string() } else { format!("ppt/{target}") };
        let mut segments = Vec::new();
        for segment in joined.split('/') {
            match segment { ".." => { segments.pop(); }, "." | "" => {}, _ => segments.push(segment) }
        }
        Ok(segments.join("/"))
    }).collect()
}

const DRAWING: &str = "http://schemas.openxmlformats.org/drawingml/2006/main";

pub(crate) fn texts(bytes: &[u8], path: &str) -> Result<Vec<String>, MobileError> {
    let xml = part(bytes, path)?;
    let doc = roxmltree::Document::parse(&xml).map_err(error)?;
    Ok(doc.descendants().filter(|n| n.has_tag_name((DRAWING, "t"))).map(|n| n.text().unwrap_or("").to_string()).collect())
}

pub(crate) fn replace(bytes: &[u8], path: &str, index: u32, text: &str) -> Result<Vec<u8>, MobileError> {
    validate_edit(bytes, text)?;
    let xml = part(bytes, path)?;
    let doc = roxmltree::Document::parse(&xml).map_err(error)?;
    let node = doc.descendants().filter(|n| n.has_tag_name((DRAWING, "t"))).nth(index as usize).ok_or_else(|| error("文字片段已不存在，请重新打开编辑器"))?;
    let range = node.range();
    let replacement = replace_node(&xml[range.clone()], text)?;
    let updated = format!("{}{}{}", &xml[..range.start], replacement, &xml[range.end..]);
    write_part(bytes, path, &updated)
}

fn replace_node(original: &str, text: &str) -> Result<String, MobileError> {
    let escaped = text.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;").replace('\r', "&#13;");
    let replacement = if let Some(end) = original.rfind("</") {
        let start = original.find('>').ok_or_else(|| error("无效文字节点"))? + 1;
        format!("{}{}{}", &original[..start], escaped, &original[end..])
    } else {
        let tag = original.trim_end().strip_suffix("/>").ok_or_else(|| error("无效空文字节点"))?;
        let name = tag.trim_start_matches('<').split_whitespace().next().ok_or_else(|| error("缺少文字节点名称"))?;
        format!("{tag}>{escaped}</{name}>")
    };
    Ok(replacement)
}

fn write_part(bytes: &[u8], path: &str, updated: &str) -> Result<Vec<u8>, MobileError> {
    let mut source = zip::ZipArchive::new(Cursor::new(bytes)).map_err(error)?;
    let mut output = zip::ZipWriter::new(Cursor::new(Vec::new()));
    for i in 0..source.len() {
        let file = source.by_index(i).map_err(error)?;
        if file.name() == path {
            output.start_file(path, zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated)).map_err(error)?;
            output.write_all(updated.as_bytes()).map_err(error)?;
        } else { output.raw_copy_file(file).map_err(error)?; }
    }
    Ok(output.finish().map_err(error)?.into_inner())
}

/// Preserve the unchanged prefix/suffix in their original styled runs. Newly inserted text
/// inherits the run at the edit boundary; paragraph properties, links and other shapes stay put.
pub(crate) fn replace_paragraph(bytes: &[u8], path: &str, index: u32, text: &str) -> Result<Vec<u8>, MobileError> {
    validate_edit(bytes, text)?;
    let xml = part(bytes, path)?;
    let doc = roxmltree::Document::parse(&xml).map_err(error)?;
    let node = doc.descendants().filter(|n| n.has_tag_name((DRAWING, "t"))).nth(index as usize).ok_or_else(|| error("找不到文字段落"))?;
    let para = node.ancestors().find(|n| n.has_tag_name((DRAWING, "p"))).ok_or_else(|| error("找不到文字段落"))?;
    if para.descendants().any(|n| n.has_tag_name((DRAWING, "fld"))) { return Err(error("含动态字段的段落暂不支持直接编辑")); }
    let slots: Vec<_> = para.descendants().filter(|n| n.has_tag_name((DRAWING, "t")) || n.has_tag_name((DRAWING, "br"))).collect();
    let values: Vec<String> = slots.iter().map(|n| if n.has_tag_name((DRAWING, "br")) { "\n".into() } else { n.text().unwrap_or("").into() }).collect();
    let old: Vec<char> = values.concat().chars().collect();
    let new: Vec<char> = text.chars().collect();
    let prefix = old.iter().zip(&new).take_while(|(a, b)| a == b).count();
    let suffix = old[prefix..].iter().rev().zip(new[prefix..].iter().rev()).take_while(|(a, b)| a == b).count();
    let insertion: String = new[prefix..new.len() - suffix].iter().collect();
    let mut inserted = false;
    let mut offset = 0;
    let mut patches = Vec::new();
    for (slot, value) in slots.iter().zip(values) {
        let chars: Vec<_> = value.chars().collect();
        let end = offset + chars.len();
        let mut output: String = chars[..prefix.saturating_sub(offset).min(chars.len())].iter().collect();
        if !inserted && prefix >= offset && prefix <= end { output.push_str(&insertion); inserted = true; }
        let tail = (old.len() - suffix).saturating_sub(offset).min(chars.len());
        output.extend(chars[tail..].iter());
        if output != value {
            let original = &xml[slot.range()];
            let replacement = if slot.has_tag_name((DRAWING, "t")) { replace_node(original, &output)? }
            else if output.is_empty() { String::new() }
            else {
                let name = original.trim_start_matches('<').split(|c: char| c.is_whitespace() || c == '/' || c == '>').next().unwrap_or("a:br");
                let prefix = name.rsplit_once(':').map(|(p, _)| format!("{p}:")).unwrap_or_default();
                let props = slot.children().find(|n| n.has_tag_name((DRAWING, "rPr"))).map(|n| &xml[n.range()]).unwrap_or("");
                let t = replace_node(&format!("<{prefix}t xml:space=\"preserve\"></{prefix}t>"), &output)?;
                format!("<{prefix}r>{props}{t}</{prefix}r>")
            };
            patches.push((slot.range(), replacement));
        }
        offset = end;
    }
    let mut updated = xml;
    for (range, value) in patches.into_iter().rev() { updated.replace_range(range, &value); }
    write_part(bytes, path, &updated)
}

/// Text-run geometry from DeckCraft's own layout, with original XML run indexes.
/// Isolating the owning shape retains inherited placeholders and group transforms while
/// avoiding ambiguous text matching between different shapes with identical words.
pub(crate) fn boxes(bytes: &[u8], path: &str, pres: &deckcraft_model::Presentation, page: usize) -> Result<String, MobileError> {
    use deckcraft_model::{Shape, ShapeKind};
    use std::sync::Arc;
    fn keep(shape: &Shape, id: u32) -> Option<Shape> {
        if shape.id.0 == id { return Some(shape.clone()); }
        if let ShapeKind::Group { children, .. } = &shape.kind {
            let kept: Vec<_> = children.iter().filter_map(|s| keep(s, id)).collect();
            if !kept.is_empty() {
                let mut out = shape.clone();
                if let ShapeKind::Group { children, .. } = &mut out.kind { *children = kept; }
                return Some(out);
            }
        }
        None
    }
    let xml = part(bytes, path)?;
    let doc = roxmltree::Document::parse(&xml).map_err(error)?;
    let nodes: Vec<_> = doc.descendants().filter(|n| n.has_tag_name((DRAWING, "t"))).collect();
    let bodies: Vec<_> = doc.descendants().filter(|n| n.is_element() && n.tag_name().name() == "txBody").collect();
    let mut reduced = pres.clone();
    Arc::make_mut(&mut reduced.slides[page]).shapes.clear();
    let inherited = deckcraft_render::place_slide(&reduced, page).texts.len();
    let mut out = Vec::new();
    let mut owners: Vec<(u32, Vec<roxmltree::Node<'_, '_>>)> = Vec::new();
    for body in bodies {
        let owner = body.ancestors().find(|n| n.is_element() && matches!(n.tag_name().name(), "sp" | "graphicFrame"));
        let id = owner.and_then(|n| n.descendants().find(|d| d.is_element() && d.tag_name().name() == "cNvPr"))
            .and_then(|n| n.attribute("id")).and_then(|n| n.parse::<u32>().ok());
        if let Some(id) = id {
            if let Some((_, list)) = owners.iter_mut().find(|(owner, _)| *owner == id) { list.push(body); }
            else { owners.push((id, vec![body])); }
        }
    }
    for (id, bodies) in owners {
        Arc::make_mut(&mut reduced.slides[page]).shapes = pres.slides[page].shapes.iter().filter_map(|s| keep(s, id)).collect();
        let placed = deckcraft_render::place_slide(&reduced, page);
        let mut available = placed.texts.into_iter().skip(inherited).peekable();
        for body in bodies {
            let paragraphs: Vec<_> = body.children().filter(|n| n.has_tag_name((DRAWING, "p"))).collect();
            let expected: Vec<String> = paragraphs.iter().map(|p| p.descendants().filter(|n| n.is_element()).filter_map(|n| {
                if n.has_tag_name((DRAWING, "t")) { Some(n.text().unwrap_or("").to_string()) }
                else if n.has_tag_name((DRAWING, "br")) { Some("\n".into()) } else { None }
            }).collect()).collect();
            // Empty table cells are omitted by the upstream renderer.
            if expected.iter().all(String::is_empty) && available.peek().is_none() {
                if body.parent().is_some_and(|n| n.tag_name().name() == "sp") {
                    if let Some(shape) = placed.shapes.iter().find(|s| s.id.0 == id) {
                        if let Some(index) = nodes.iter().position(|n| n.ancestors().any(|p| p == body)) {
                            let r = shape.rect;
                            out.push(serde_json::json!({"index":index,"text":"","x0":r.x0,"y0":r.y0,"x1":r.x1,"y1":r.y1,"size":18.0}));
                        }
                    }
                }
                continue;
            }
            let Some(pt) = available.peek() else { continue };
            let actual: Vec<String> = pt.body.paragraphs.iter().map(|p| p.runs.iter().map(|r| {
                if matches!(r.kind, deckcraft_model::text::RunKind::Break) { "\n".to_string() } else { r.text.clone() }
            }).collect()).collect();
            if expected != actual { continue; }
            let Some(pt) = available.next() else { continue };
            // A rotated frame keeps its original rotation in the file. Use its page-space
            // bounding box for input rather than applying an unrelated screen transform.
            for (pi, paragraph) in paragraphs.iter().enumerate() {
                let mut offset = 0usize;
                for n in paragraph.descendants().filter(|n| n.is_element()) {
                    if n.has_tag_name((DRAWING, "br")) { offset += 1; }
                    if !n.has_tag_name((DRAWING, "t")) { continue; }
                    let text = n.text().unwrap_or("");
                    let end = offset + text.chars().count();
                    if n.ancestors().any(|p| p.has_tag_name((DRAWING, "fld"))) { offset = end; continue; }
                    let mut rect: Option<kurbo::Rect> = None;
                    for line in pt.layout.lines.iter().filter(|line| line.para == pi) {
                        let span = line.span(offset, end).or_else(|| {
                            if offset == end && offset >= line.start && offset <= line.end {
                                line.caret_x.get(offset - line.start).map(|x| (*x, *x + 12.0))
                            } else { None }
                        });
                        if let Some((x0, x1)) = span {
                            let r = pt.transform.transform_rect_bbox(kurbo::Rect::new(x0, line.top, x1.max(x0 + 6.0), line.bottom));
                            rect = Some(rect.map_or(r, |old| old.union(r)));
                        }
                    }
                    if rect.is_none() && text.is_empty() {
                        let r = pt.layout.inner;
                        rect = Some(pt.transform.transform_rect_bbox(kurbo::Rect::new(r.x0, r.y0, r.x1.max(r.x0 + 12.0), r.y1.max(r.y0 + 18.0))));
                    }
                    if let Some(rect) = rect {
                        let style = pt.layout.runs.iter().find(|r| r.para == pi && r.chars.0 <= offset && r.chars.1 >= end)
                            .or_else(|| pt.layout.runs.iter().find(|r| r.para == pi));
                        if let Some(index) = nodes.iter().position(|candidate| *candidate == n) {
                            out.push(serde_json::json!({"index":index,"text":text,"x0":rect.x0,"y0":rect.y0,"x1":rect.x1,"y1":rect.y1,
                                "size":style.map_or(18.0, |s| s.size), "bold":style.is_some_and(|s|s.fake_bold), "italic":style.is_some_and(|s|s.fake_italic)}));
                        }
                    }
                    offset = end;
                }
            }
        }
    }
    let mut paragraphs: Vec<(usize, serde_json::Value)> = Vec::new();
    for item in out {
        let Some(index) = item["index"].as_u64().and_then(|i| usize::try_from(i).ok()) else { continue };
        let Some(node) = nodes.get(index) else { continue };
        let Some(p) = node.ancestors().find(|n| n.has_tag_name((DRAWING, "p"))) else { continue };
        if p.descendants().any(|n| n.has_tag_name((DRAWING, "fld"))) { continue; }
        let key = p.range().start;
        if let Some((_, group)) = paragraphs.iter_mut().find(|(id, _)| *id == key) {
            for name in ["x0", "y0"] {
                group[name] = serde_json::json!(group[name].as_f64().unwrap_or(0.0).min(item[name].as_f64().unwrap_or(0.0)));
            }
            for name in ["x1", "y1"] {
                group[name] = serde_json::json!(group[name].as_f64().unwrap_or(0.0).max(item[name].as_f64().unwrap_or(0.0)));
            }
        } else {
            let mut group = item;
            group["text"] = serde_json::json!(p.descendants().filter_map(|n| {
                if n.has_tag_name((DRAWING, "t")) { Some(n.text().unwrap_or("")) }
                else if n.has_tag_name((DRAWING, "br")) { Some("\n") } else { None }
            }).collect::<String>());
            paragraphs.push((key, group));
        }
    }
    serde_json::to_string(&paragraphs.into_iter().map(|(_, value)| value).collect::<Vec<_>>()).map_err(error)
}

fn validate_edit(bytes: &[u8], text: &str) -> Result<(), MobileError> {
    let archive = zip::ZipArchive::new(Cursor::new(bytes)).map_err(error)?;
    if archive.file_names().any(|name| name.starts_with("_xmlsignatures/")) {
        return Err(error("文件含有数字签名，修改会使签名失效，请使用未签名副本"));
    }
    drop(archive);
    if text.chars().any(|c| !matches!(c, '\t' | '\n' | '\r' | '\u{20}'..='\u{d7ff}' | '\u{e000}'..='\u{fffd}' | '\u{10000}'..='\u{10ffff}')) {
        return Err(error("文字包含无效控制字符"));
    }
    if text.len() > 128 * 1024 { return Err(error("单个文字片段过长")); }
    Ok(())
}
