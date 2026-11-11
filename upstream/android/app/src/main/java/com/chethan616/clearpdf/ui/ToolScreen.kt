package com.chethan616.clearpdf.ui

/** A full-screen tool shown over the app. */
sealed class ToolScreen {
    class Compress(val doc: OpenDocument) : ToolScreen()
    class Combine(val doc: OpenDocument?) : ToolScreen()
    class Protect(val doc: OpenDocument) : ToolScreen()
    data object Create : ToolScreen()
    class Crop(val doc: OpenDocument) : ToolScreen()
    class Export(val doc: OpenDocument) : ToolScreen()
    data object Scan : ToolScreen()
    class Utility(val doc: OpenDocument, val kind: UtilityKind) : ToolScreen()
    class Vault(val kind: com.chethan616.clearpdf.engine.VaultKind, val doc: OpenDocument? = null) : ToolScreen()
    class Result(val result: DocumentResult) : ToolScreen()
    class PdfToolbox(val doc: OpenDocument) : ToolScreen()
    class Extract(val doc: OpenDocument) : ToolScreen()
    class Redact(val doc: OpenDocument) : ToolScreen()
    class Translate(val text: String) : ToolScreen()
}

enum class UtilityKind { Watermark, PageNumbers }
