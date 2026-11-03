package com.github.pepek42.asteroids

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * Loads `i18n/$file` from the test classpath (`assets/` is on it) as UTF-8.
 */
fun i18nKeys(file: String): Set<String> = i18nProperties(file).stringPropertyNames()

private fun i18nProperties(file: String): Properties {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("i18n/$file")
        ?: object {}.javaClass.classLoader.getResourceAsStream("i18n/$file")
        ?: throw IllegalStateException("Missing i18n resource i18n/$file on test classpath")
    val properties = Properties()
    stream.use { input ->
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader -> properties.load(reader) }
    }
    return properties
}

class I18nKeysTest {

    private val files = listOf("messages.properties", "messages_pl.properties")

    @Test
    fun bothMessageFilesHaveSameKeys() {
        val en = i18nKeys("messages.properties")
        val pl = i18nKeys("messages_pl.properties")
        val missingInPl = en - pl
        val missingInEn = pl - en
        assertTrue(
            missingInPl.isEmpty() && missingInEn.isEmpty(),
            "i18n keys out of sync. Missing in messages_pl.properties: $missingInPl. " +
                "Missing in messages.properties: $missingInEn"
        )
    }

    @Test
    fun noMessageValueIsBlank() {
        files.forEach { file ->
            val props = i18nProperties(file)
            val blank = props.stringPropertyNames().filter { props.getProperty(it).isBlank() }
            assertEquals(emptyList<String>(), blank, "Blank i18n values in $file")
        }
    }
}
