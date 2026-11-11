//! PowerPoint presentations (.pptx) through storytold/deckcraft's reader and slide renderer: each
//! slide is a page of the viewer (see [`crate::office`]). Viewing needs no deckcraft engine.

use std::sync::{Arc, Mutex, PoisonError};

use deckcraft_model::Presentation;
use deckcraft_model::text::Action;
use deckcraft_render::RenderOpts;
use pdfcraft_render::PageText;

use crate::office::{OfficeDoc, is_web_link, text_glyph};
use crate::{DocumentInfo, DocumentKind, LinkInfo, MobileError, PageInfo, RenderedImage, TileRect};

/// One open presentation and its slides' text layers.
pub(crate) struct SlideDeck {
    state: Mutex<SlideState>,
    name: String,
    path: String,
    file_size: u64,
    texts: Mutex<Vec<Option<(u64, Arc<PageText>)>>>,
}

struct SlideState {
    pres: Arc<Presentation>,
    bytes: Option<Vec<u8>>,
    paths: Vec<String>,
    dirty: bool,
    generation: u64,
}

/// Worker threads a slide renders on (effects that blur need some).
const THREADS: u16 = 2;

impl SlideDeck {
    pub(crate) fn open(path: &str, bytes: &[u8]) -> Result<SlideDeck, MobileError> {
        if deckcraft_pptx::is_encrypted(bytes) {
            return Err(MobileError::Open("password-protected presentations can't be opened yet".into()));
        }
        let pres = deckcraft_pptx::import(bytes).map_err(|e| MobileError::Open(e.to_string()))?;
        Self::of(path, pres, bytes.len(), (bytes.len() <= 64 * 1024 * 1024).then(|| bytes.to_vec()))
    }

    /// A PowerPoint 97-2003 presentation (.ppt): its slides' text in a deck of its size.
    pub(crate) fn open_ppt(path: &str, bytes: &[u8]) -> Result<SlideDeck, MobileError> {
        let pres = crate::ppt::import(bytes).map_err(MobileError::Open)?;
        Self::of(path, pres, bytes.len(), None)
    }

    fn of(path: &str, pres: Presentation, size: usize, bytes: Option<Vec<u8>>) -> Result<SlideDeck, MobileError> {
        let n = pres.slides.len();
        // Unsupported package relationships must not disable the existing reader.
        let paths = bytes.as_deref().and_then(|b| crate::slide_text::paths(b).ok()).unwrap_or_default();
        Ok(SlideDeck {
            state: Mutex::new(SlideState { pres: Arc::new(pres), bytes, paths, dirty: false, generation: 0 }),
            name: crate::office::file_name(path),
            path: path.to_string(),
            file_size: size as u64,
            texts: Mutex::new(vec![None; n]),
        })
    }

    fn update_text(&self, page: u32, index: u32, text: String, paragraph: bool) -> Result<(), MobileError> {
        let i = self.slide(page)?;
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let bytes = state.bytes.as_ref().ok_or_else(|| MobileError::Command("旧版 PPT 仅支持阅读".into()))?;
        let path = state.paths.get(i).ok_or_else(|| MobileError::Command("找不到幻灯片".into()))?;
        let updated = if paragraph { crate::slide_text::replace_paragraph(bytes, path, index, &text)? } else { crate::slide_text::replace(bytes, path, index, &text)? };
        let pres = deckcraft_pptx::import(&updated).map_err(|e| MobileError::Command(e.to_string()))?;
        state.pres = Arc::new(pres);
        state.bytes = Some(updated);
        state.dirty = true;
        state.generation += 1;
        Ok(())
    }

    fn slide(&self, page: u32) -> Result<usize, MobileError> {
        let n = self.state.lock().unwrap_or_else(PoisonError::into_inner).pres.slides.len();
        let i = page as usize;
        if i < n {
            Ok(i)
        } else {
            Err(MobileError::Render(format!("slide {} is out of range: the presentation has {n} slides", u64::from(page) + 1)))
        }
    }
}

impl OfficeDoc for SlideDeck {
    fn info(&self, id: u64) -> DocumentInfo {
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let p = &state.pres;
        let (w, h) = (p.slide_size.width as f32, p.slide_size.height as f32);
        let some = |v: &str| (!v.trim().is_empty()).then(|| v.to_string());
        DocumentInfo {
            id,
            kind: DocumentKind::Slides,
            name: self.name.clone(),
            path: Some(self.path.clone()),
            pages: (0..p.slides.len()).map(|i| PageInfo { width: w, height: h, label: (i + 1).to_string(), rotation: 0 }).collect(),
            title: some(&p.props.title),
            author: some(&p.props.author),
            subject: some(&p.props.subject),
            keywords: some(&p.props.keywords),
            creator: None,
            producer: None,
            pdf_version: String::new(),
            file_size: self.file_size,
            encrypted: false,
            tagged: false,
            read_only_reason: (state.bytes.is_none() || state.paths.len() != p.slides.len()).then(|| "此文件暂不支持文字编辑；请使用 64 MiB 以内的标准 PPTX 文件，旧版 PPT 请先转换".into()),
            allows_printing: true,
            allows_copying: true,
            allows_annotation: false,
            allows_form_filling: false,
            allows_assembly: false,
            dirty: state.dirty,
            undo: None,
            redo: None,
            fields: 0,
            has_outline: false,
            attachments: 0,
            signed: false,
            comments_hidden: false,
            generation: state.generation,
        }
    }

    fn render_page(&self, page: u32, scale: f32, tile: Option<TileRect>) -> Result<RenderedImage, MobileError> {
        let i = self.slide(page)?;
        let mut opts = RenderOpts { scale: f64::from(scale), threads: THREADS, ..RenderOpts::default() };
        if let Some(t) = tile {
            opts.offset = (-f64::from(t.x), -f64::from(t.y));
            opts.size = Some((t.width.max(1), t.height.max(1)));
        }
        let pres = self.state.lock().unwrap_or_else(PoisonError::into_inner).pres.clone();
        let img = deckcraft_render::render_slide(&pres, i, &opts);
        if img.width == 0 || img.height == 0 {
            return Err(MobileError::Render(format!("slide {} could not be drawn", i + 1)));
        }
        Ok(RenderedImage { width: img.width, height: img.height, rgba: img.pixels })
    }

    fn page_text(&self, page: u32) -> Result<Arc<PageText>, MobileError> {
        let i = self.slide(page)?;
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let mut texts = self.texts.lock().unwrap_or_else(PoisonError::into_inner);
        if let Some(Some((generation, t))) = texts.get(i) {
            if *generation == state.generation { return Ok(t.clone()); }
        }
        let mut glyphs = Vec::new();
        for pt in deckcraft_render::place_slide(&state.pres, i).texts {
            for run in &pt.layout.runs {
                if run.size <= 0.0 {
                    continue;
                }
                for (gi, (gid, x, y)) in run.glyphs.iter().enumerate() {
                    // bullets draw no paragraph character
                    let Some((_, ch)) = run.cells.get(gi) else { continue };
                    if ch.is_whitespace() {
                        continue;
                    }
                    let next = run.glyphs.get(gi + 1).map(|g| g.1).filter(|n| n > x);
                    let x1 = next.unwrap_or(x + run.face.advance(*gid) * run.size / run.face.upem.max(1.0));
                    glyphs.push(text_glyph(ch.to_string(), *x, x1, *y, run.size, pt.transform));
                }
            }
        }
        let text = Arc::new(pdfcraft_render::text::layout(glyphs));
        if let Some(slot) = texts.get_mut(i) {
            *slot = Some((state.generation, text.clone()));
        }
        Ok(text)
    }

    fn links(&self, page: u32) -> Result<Vec<LinkInfo>, MobileError> {
        let i = self.slide(page)?;
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        Ok(deckcraft_render::place_slide(&state.pres, i)
            .links
            .into_iter()
            .filter_map(|l| match &l.link.action {
                Action::Url { url } if is_web_link(url) => {
                    let r = l.rect;
                    Some(LinkInfo { rect: vec![r.x0 as f32, r.y0 as f32, r.x1 as f32, r.y1 as f32], uri: Some(url.clone()), page: None, top: None })
                }
                _ => None,
            })
            .collect())
    }

    fn export(&self, format: &str) -> Result<Vec<u8>, MobileError> {
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        match format {
            "pdf" => deckcraft_pdf::export(&state.pres, &deckcraft_pdf::PdfOptions::default()).map_err(|e| MobileError::Command(e.to_string())),
            "pptx" => match &state.bytes {
                Some(bytes) => Ok(bytes.clone()),
                None => deckcraft_pptx::export(&state.pres).map_err(|e| MobileError::Command(e.to_string())),
            },
            _ => Err(crate::office::unknown_format(DocumentKind::Slides, format)),
        }
    }

    fn save(&self, path: &str) -> Result<(), MobileError> {
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let bytes = state.bytes.as_ref().ok_or_else(|| MobileError::Command("旧版 PPT 仅支持阅读".into()))?;
        let pending = format!("{path}.pending");
        std::fs::write(&pending, bytes).map_err(|e| MobileError::Command(e.to_string()))?;
        std::fs::rename(&pending, path).map_err(|e| MobileError::Command(e.to_string()))?;
        state.dirty = false;
        Ok(())
    }

    fn slide_texts(&self, page: u32) -> Result<String, MobileError> {
        let i = self.slide(page)?;
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let bytes = state.bytes.as_ref().ok_or_else(|| MobileError::Command("旧版 PPT 仅支持阅读".into()))?;
        let path = state.paths.get(i).ok_or_else(|| MobileError::Command("找不到幻灯片".into()))?;
        serde_json::to_string(&crate::slide_text::texts(bytes, path)?).map_err(|e| MobileError::Command(e.to_string()))
    }

    fn slide_text_boxes(&self, page: u32) -> Result<String, MobileError> {
        let i = self.slide(page)?;
        let state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        let bytes = state.bytes.as_ref().ok_or_else(|| MobileError::Command("此文件仅支持阅读".into()))?;
        let path = state.paths.get(i).ok_or_else(|| MobileError::Command("找不到幻灯片".into()))?;
        crate::slide_text::boxes(bytes, path, &state.pres, i)
    }

    fn slide_set_text(&self, page: u32, index: u32, text: String) -> Result<(), MobileError> {
        self.update_text(page, index, text, false)
    }

    fn slide_set_paragraph(&self, page: u32, index: u32, text: String) -> Result<(), MobileError> {
        self.update_text(page, index, text, true)
    }
    fn release_memory(&self) {
        let mut texts = self.texts.lock().unwrap_or_else(PoisonError::into_inner);
        texts.iter_mut().for_each(|t| *t = None);
    }
}
