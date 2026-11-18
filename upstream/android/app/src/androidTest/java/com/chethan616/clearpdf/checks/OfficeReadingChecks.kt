package com.chethan616.clearpdf.checks

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModelProvider
import com.chethan616.clearpdf.MainActivity
import com.chethan616.clearpdf.engine.LocalFiles
import com.chethan616.clearpdf.engine.OfficeReadCache
import com.chethan616.clearpdf.ui.AppViewModel
import java.io.File

/** Uses an independent debug app and synthetic documents; never touches the release app's data. */
class OfficeReadingChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            val tree = DocumentsContract.buildTreeDocumentUri("cn.jiandoc.app.debug.test.courses", "root")
            val root = LocalFiles.children(targetContext, tree, "root")
            check(root.map { it.name } == listOf("Chapter 1", "Notes.pdf")) { "Folder ordering or file filtering failed" }
            for (depth in 1..9) check(LocalFiles.children(targetContext, tree, "$depth").single().directory)
            check(LocalFiles.children(targetContext, tree, "10").single().name == "Slides.ppt")
            report.putInt("folderChecks", 11)

            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            lateinit var vm: AppViewModel
            runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
            val uris = listOf("pdf", "ppt").map { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
            runOnMainSync { vm.importDocuments(uris + uris.first()) }
            await(10_000) { !vm.opening && vm.recentList.count { it.uri in uris.map(Uri::toString) } == 2 }
            report.putInt("batchImport", 2)

            val source = File(targetContext.cacheDir, "cold-${System.nanoTime()}.fodp")
            context.assets.open("lecture-preview.fodp").use { input -> source.outputStream().use { input.copyTo(it) } }
            // Unique content forces a cold cache even when the test has run before.
            source.appendText("\n<!-- ${System.nanoTime()} -->\n")
            val before = source.readBytes()
            val prepared = OfficeReadCache(File(targetContext.cacheDir, "share/office-preview")).prepare(source)
            check(!prepared.pdf.exists())
            runOnMainSync { vm.open(Uri.fromFile(source)) }
            await(120_000) { vm.document != null }
            runOnMainSync {
                val doc = requireNotNull(vm.document)
                check(doc.info.pages.size == 1) { "Wrong slide count" }
                check(doc.source == null) { "Preview can overwrite original presentation" }
                check(doc.readingSource == Uri.fromFile(source))
                check(doc.file != prepared.pdf) { "Immutable cache used as editable working file" }
            }
            check(OfficeReadCache.valid(prepared.pdf))
            check(source.readBytes().contentEquals(before))
            report.putString("coldOfficeOpen", "native PDF viewer opened without a second tap")
            val modified = prepared.pdf.lastModified()
            runOnMainSync { vm.closeDocument() }
            await(15_000) { vm.document == null }
            runOnMainSync { vm.open(Uri.fromFile(source)) }
            await(20_000) { vm.document != null }
            check(prepared.pdf.lastModified() == modified) { "Warm cache was regenerated" }
            report.putString("warmOfficeOpen", "cache reused")
            finish(Activity.RESULT_OK, report)
        } catch (failure: Throwable) {
            report.putString("failure", android.util.Log.getStackTraceString(failure))
            finish(Activity.RESULT_CANCELED, report)
        }
    }

    private fun await(timeout: Long, predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            runOnMainSync { ready = predicate() }
            if (ready) return
            Thread.sleep(100)
        }
        error("Timed out waiting for document state")
    }
}
