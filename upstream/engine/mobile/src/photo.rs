//! Pictures through storytold/lightcraft: a photo decoded into linear light, developed with
//! LightCraft's own settings (exposure, contrast, white balance, crop, orientation, … as its
//! `DevelopSettings` JSON, every field optional) by its CPU pipeline, and saved as a JPEG. The app
//! uses it to edit the photos in notes.

use std::sync::{Arc, Mutex, PoisonError};

use lightcraft_codecs::{ChromaSubsampling, DecodeOptions, EncodeImage, EncodeMeta};
use lightcraft_develop::DevelopSettings;
use lightcraft_pipeline::{RenderRequest, SourceInfo};
use lightcraft_raster::Rgb32f;

use crate::{MobileError, RenderedImage};

/// Reuse the engine's paper/shadow separation, keeping only black ink on alpha.
#[uniffi::export]
pub fn scan_signature_photo(bytes: Vec<u8>) -> Result<Vec<u8>, MobileError> {
    let fail = |e: pdfcraft_engine::ScanError| MobileError::Command(e.to_string());
    let image = pdfcraft_engine::SignatureImage::from_bytes(&bytes).map_err(|e| MobileError::Command(e.to_string()))?;
    let image = if image.has_transparency() {
        image
    } else {
        let photo = pdfcraft_engine::SignaturePhoto::from_bytes(&bytes).map_err(fail)?;
        let [width, height] = photo.size();
        photo.scan(pdfcraft_engine::PhotoRect { x: 0, y: 0, width, height }, 50, 1600).map_err(fail)?
    };
    if !image.rgba().chunks_exact(4).any(|p| p[3] > 32) {
        return Err(MobileError::Command("No signature ink was found".into()));
    }
    let black = image.in_color([0, 0, 0]).map_err(|e| MobileError::Command(e.to_string()))?;
    Ok(black.bytes().as_ref().clone())
}

/// Preview the same perspective warp and cleanup used when saving a scanned PDF.
#[uniffi::export]
pub fn preview_scan_photo(image: RenderedImage, corners: Vec<f32>, mode: String, level: u8) -> Result<RenderedImage, MobileError> {
    use pdfcraft_engine::{camera, camera_clean};
    if corners.len() != 8 || corners.iter().any(|v| !v.is_finite() || !(0.0..=1.0).contains(v)) {
        return Err(MobileError::Command("Invalid scan corners".into()));
    }
    let look = camera_clean::Look::from_name(&mode).ok_or_else(|| MobileError::Command("Invalid scan filter".into()))?;
    let quad = std::array::from_fn(|i| [f64::from(corners[i * 2]) * f64::from(image.width), f64::from(corners[i * 2 + 1]) * f64::from(image.height)]);
    let warped = camera::warp(&image.rgba, image.width, image.height, quad).map_err(MobileError::Command)?;
    let pixels = camera_clean::apply(&warped.rgb, warped.width, warped.height, look, level)
        .ok_or_else(|| MobileError::Command("Invalid scan image".into()))?;
    let rgba = match pixels {
        camera_clean::Pixels::Rgb(rgb) => rgb.chunks_exact(3).flat_map(|p| [p[0], p[1], p[2], 255]).collect(),
        camera_clean::Pixels::Grey(grey) => grey.into_iter().flat_map(|g| [g, g, g, 255]).collect(),
    };
    Ok(RenderedImage { width: warped.width, height: warped.height, rgba })
}

/// The largest side a preview source is decoded at: the editor's sliders re-develop it on every
/// change, so it is kept small (exports decode the full picture again).
const PREVIEW_SOURCE: u32 = 2048;

/// The decoded picture last asked for (path, preview or full size), kept for the next render.
type Source = Option<(String, bool, Arc<Rgb32f>)>;

/// LightCraft's develop pipeline, with the last decoded picture kept so slider moves only re-develop.
#[derive(uniffi::Object)]
pub struct PhotoEditor {
    source: Mutex<Source>,
}

fn settings(json: &str) -> Result<DevelopSettings, MobileError> {
    if json.trim().is_empty() {
        return Ok(DevelopSettings::default());
    }
    serde_json::from_str(json).map_err(|e| MobileError::Command(format!("the develop settings are not valid: {e}")))
}

impl PhotoEditor {
    fn source(&self, path: &str, preview: bool) -> Result<Arc<Rgb32f>, MobileError> {
        let mut s = self.source.lock().unwrap_or_else(PoisonError::into_inner);
        if let Some((p, pv, img)) = &*s
            && p == path
            && *pv == preview
        {
            return Ok(img.clone());
        }
        let bytes = crate::office::read(path)?;
        let opts = DecodeOptions { max_size: preview.then_some((PREVIEW_SOURCE, PREVIEW_SOURCE)), ..DecodeOptions::default() };
        let decoded = lightcraft_codecs::decode(&bytes, opts).map_err(|e| MobileError::Open(format!("{path}: {e}")))?;
        let img = Arc::new(decoded.image);
        *s = Some((path.to_string(), preview, img.clone()));
        Ok(img)
    }
}

#[uniffi::export]
impl PhotoEditor {
    #[uniffi::constructor]
    #[allow(clippy::new_without_default)]
    pub fn new() -> Arc<Self> {
        Arc::new(PhotoEditor { source: Mutex::new(None) })
    }

    /// The picture at `path` developed with `settings_json` (LightCraft's develop settings; empty:
    /// as shot), fitted within `max_side` pixels: what the editor shows while sliders move.
    pub fn render(&self, path: String, settings_json: String, max_side: u32) -> Result<RenderedImage, MobileError> {
        let s = settings(&settings_json)?;
        let src = self.source(&path, true)?;
        let side = max_side.clamp(16, PREVIEW_SOURCE) as usize;
        let out = lightcraft_pipeline::render(&src, &SourceInfo::default(), &s, &RenderRequest::fit(side, side));
        let img = out.image;
        Ok(RenderedImage { width: img.width as u32, height: img.height as u32, rgba: img.data.as_flattened().to_vec() })
    }

    /// Develop the full picture at `path` with `settings_json` and write it to `out_path` as a JPEG
    /// of `quality` (1–100).
    pub fn export_jpeg(&self, path: String, settings_json: String, out_path: String, quality: u8) -> Result<(), MobileError> {
        let s = settings(&settings_json)?;
        let src = self.source(&path, false)?;
        let (w, h) = (src.width, src.height);
        let out = lightcraft_pipeline::render(&src, &SourceInfo::default(), &s, &RenderRequest::fit(w.max(h), w.max(h)));
        let jpeg = lightcraft_codecs::encode_jpeg(
            &EncodeImage::rgba8(&out.image),
            quality.clamp(1, 100),
            ChromaSubsampling::default(),
            &EncodeMeta::default(),
        )
        .map_err(|e| MobileError::Command(format!("the picture could not be saved: {e}")))?;
        std::fs::write(&out_path, jpeg).map_err(|e| MobileError::Command(format!("{out_path}: {e}")))
    }

    /// Let go of the decoded picture (the editor closed, or memory is short).
    pub fn release(&self) {
        *self.source.lock().unwrap_or_else(PoisonError::into_inner) = None;
    }
}
