package com.chethan616.clearpdf.engine

import java.io.File

/** Route legacy presentations before the text-only reader can discard their objects. */
object OfficeCompatibility {
    fun needsReader(file: File): Boolean {
        val extension = file.extension.lowercase()
        if (extension !in setOf("ppt", "pps", "pot", "odp", "otp", "fodp")) return false
        val head = ByteArray(8)
        val count = file.inputStream().use { it.read(head) }
        if (count < 4) return false
        return when (extension) {
            "ppt", "pps", "pot" -> count == 8 && head.contentEquals(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))
            "odp", "otp" -> head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
            else -> true // the complete engine validates flat XML and reports errors itself
        }
    }
}
