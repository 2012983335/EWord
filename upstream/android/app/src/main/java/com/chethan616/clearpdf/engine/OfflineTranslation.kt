package com.chethan616.clearpdf.engine

import android.content.Context
import android.os.Build
import io.github.yinvoker.foxlet.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** APK-only model source. No model-manager prepare, download or update calls. */
class OfflineTranslation(private val context: Context) {
    private val mutex = Mutex()
    private var client: Foxlet? = null
    private val verified = mutableMapOf<String, ExternalModel>()

    suspend fun translate(text: String, englishToChinese: Boolean): String = mutex.withLock {
        require(text.isNotBlank() && text.length <= 2000) { "请选择 1 到 2000 字进行翻译" }
        check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "离线翻译需要 ARM64 系统" }
        val pair = if (englishToChinese) "en-zh-Hans" else "zh-Hans-en"
        val model = verified[pair] ?: withContext(Dispatchers.IO) {
            val registry = context.assets.open("translation/registry.json").bufferedReader().use { JSONArray(it.readText()) }
            val entry = (0 until registry.length()).map { registry.getJSONObject(it) }.first { "${it.getString("from")}-${it.getString("to")}" == pair }
            val folder = File(context.noBackupFilesDir, "translation/${entry.getString("version")}/$pair").apply { mkdirs() }
            val files = entry.getJSONArray("files")
            val hashes = mutableMapOf<String, String>()
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                val name = item.getString("name")
                val expected = item.getString("sha256")
                hashes[name] = expected
                val file = File(folder, name)
                fun valid(candidate: File): Boolean {
                    if (!candidate.isFile || candidate.length() != item.getLong("size")) return false
                    val digest = MessageDigest.getInstance("SHA-256")
                    candidate.inputStream().use { input -> val buffer = ByteArray(64 * 1024); var n = input.read(buffer); while (n >= 0) { if (n > 0) digest.update(buffer, 0, n); n = input.read(buffer) } }
                    return digest.digest().joinToString("") { "%02x".format(it) } == expected
                }
                if (!valid(file)) {
                    val temp = File(folder, ".$name.part")
                    try {
                        context.assets.open("translation/$pair/$name").use { input -> temp.outputStream().use { input.copyTo(it) } }
                        check(valid(temp)) { "内置模型校验失败，请重新安装完整 APK" }
                        FileVault.copyDurably(temp, file)
                    } finally { temp.delete() }
                }
            }
            ExternalModel(LanguagePair(entry.getString("from"), entry.getString("to")), ModelFiles.fromDirectory(folder).copy(expectedSha256 = hashes))
        }.also { verified[pair] = it }
        val active = client ?: Foxlet.create(context) {
            translation { threading = Threading.Fixed(1); retention = ModelRetention.AfterRequest }
        }.also { client = it }
        active.translator.translate(text, model)
    }
}
