package com.github.pepek42.asteroids.component

import com.badlogic.ashley.core.Component
import java.io.File
import java.lang.reflect.Modifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Web guard: on TeaVM, Ashley's `PooledEngine` builds components through gdx-teavm's `ClassReflection.getConstructor(c)`,
 * which returns `c.getConstructors()[0]` instead of the no-arg one. A Kotlin class with defaulted constructor parameters
 * has several public constructors, so it crashes in the browser ("Illegal argument(s) supplied to constructor").
 * Components must therefore have exactly one public constructor, with no parameters; set fields in the body instead.
 */
class ComponentConstructorTest {
    @Test
    fun everyComponentHasOnlyAPublicNoArgConstructor() {
        val pkg = BodyComponent::class.java.`package`.name
        val dir = File(BodyComponent::class.java.protectionDomain.codeSource.location.toURI()).resolve(pkg.replace('.', '/'))
        val components = dir.listFiles { f -> f.name.endsWith(".class") }!!
            .map { Class.forName("$pkg.${it.name.removeSuffix(".class")}") }
            .filter { Component::class.java.isAssignableFrom(it) && !it.isInterface && !Modifier.isAbstract(it.modifiers) }
        assertTrue(components.size > 10, "found only ${components.size} components in $dir")
        val bad = components.filter { c -> c.constructors.size != 1 || c.constructors[0].parameterCount != 0 }
            .map { c -> "${c.simpleName}: ${c.constructors.map { it.parameterCount }}" }
        assertEquals(emptyList<String>(), bad, "components need exactly one public no-arg constructor (TeaVM)")
    }
}
