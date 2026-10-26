plugins {
    id("kotlin-jvm")
    alias(libs.plugins.gdxTeaVmPlugin)
}

dependencies {
    implementation(libs.gdxBox2dGwt)
    implementation(libs.gdxTeaVmFreetype)
    implementation(libs.gdxTeaVmControllers)
    implementation(project(":core"))
}

gdxTeaVM {
    assets(rootProject.file("assets"))
    reflection("com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator")

    webDefaults {
        mainClass.set("com.github.pepek42.asteroids.teavm.TeaVMLauncher")
        htmlTitle.set("Star Trap")
        htmlWidth.set(1280)
        htmlHeight.set(720)
        serverPort.set(8080)
    }

    // use JS during development since it is easier to test and debug.
    js {
        obfuscated.set(false)

        devServer {
            enabled.set(true)
            autoBuild.set(true) // detect changed sources/assets and automatically rebuild teavm stuff
            autoReload.set(true) // automatically refresh the browser after a successful build
        }
    }

    // use wasm for your final release as it has better performance usually
    wasm {
        obfuscated.set(true)

        // wasm generation sometimes needs more memory than Gradle's default 512 MiB daemon heap
        outOfProcess = true
        processMemory = 1024

        devServer {
            enabled.set(false)
        }
    }
}
