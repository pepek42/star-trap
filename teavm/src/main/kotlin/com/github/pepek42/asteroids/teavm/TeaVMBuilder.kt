package com.github.pepek42.asteroids.teavm

import com.github.xpenatan.gdx.backends.teavm.config.TeaBuildConfiguration
import com.github.xpenatan.gdx.backends.teavm.config.TeaBuilder
import com.github.xpenatan.gdx.backends.teavm.gen.SkipClass
import java.io.File

/** Builds the TeaVM/HTML application. */
@SkipClass
object TeaVMBuilder {
    @JvmStatic fun main(arguments: Array<String>) {
        val teaBuildConfiguration = TeaBuildConfiguration().apply {
            assetsPath.add(File("../assets"))
            webappPath = File("build/dist").canonicalPath
            // Register any extra classpath assets here:
            // additionalAssetsClasspathFiles += "com/github/pepek42/asteroids/asset.extension"
        }

        // Register any classes or packages that require reflection here:
        // TeaReflectionSupplier.addReflectionClass("com.github.pepek42.asteroids.reflect")

        val tool = TeaBuilder.config(teaBuildConfiguration)
        tool.mainClass = "com.github.pepek42.asteroids.teavm.TeaVMLauncher"
        TeaBuilder.build(tool)
    }
}
