package com.github.pepek42.asteroids

import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun noMessageValueIsBlank() {
        val props = i18nProperties("messages.properties")
        val blank = props.stringPropertyNames().filter { props.getProperty(it).isBlank() }
        assertEquals(emptyList<String>(), blank, "Blank i18n values in messages.properties")
    }
}
