package com.chethan616.clearpdf.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SettingsTranslationsTest {
    @Test fun welcomeAndGlassSettingsHaveChineseForEveryDisplayedResource() {
        fun names(path: String): Set<String> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
        }
        val translated = names("src/main/res/values-zh-rCN/strings.xml") + "app_name"
        val files = listOf("ui/desk/GlassMotionSettings.kt", "ui/onboarding/Onboarding.kt", "ui/desk/SettingsScreen.kt")
        val referenced = files.flatMap { file ->
            Regex("R\\.string\\.(\\w+)").findAll(File("src/main/java/com/chethan616/clearpdf/$file").readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("Missing Chinese: ${referenced - translated}", translated.containsAll(referenced))
        val chinese = File("src/main/res/values-zh-rCN/strings.xml").readText()
        assertTrue(chinese.contains("欢迎使用"))
        assertTrue(chinese.contains("磨砂模糊"))
    }
}
