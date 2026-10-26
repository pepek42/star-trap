import io.github.fourlastor.construo.Target

plugins {
    id("kotlin-jvm")
    application
    alias(libs.plugins.construoGradlePlugin)
}

dependencies {
    implementation(libs.gdxBackendLwjgl3)
    implementation(project(":core"))
    implementation(libs.gdxControllersDesktop)
    listOf(
        libs.gdxPlatform,
        libs.gdxBox2dPlatform,
        libs.gdxFreetypePlatform,
    ).forEach {
        runtimeOnly(it) {
            artifact { classifier = "natives-desktop" }
        }
    }

    // constraints can be removed with the next LibGDX version which defaults to 3.4.3+
    constraints {
        listOf(
            "org.lwjgl:lwjgl",
            "org.lwjgl:lwjgl-glfw",
            "org.lwjgl:lwjgl-jemalloc",
            "org.lwjgl:lwjgl-openal",
            "org.lwjgl:lwjgl-opengl",
            "org.lwjgl:lwjgl-stb",
        ).forEach {
            implementation(it) {
                version { strictly("3.4.3") }
            }
        }
    }
}

application {
    applicationName = "star-trap"
    mainClass = "com.github.pepek42.asteroids.lwjgl3.Lwjgl3Launcher"
    // Silence JDK 24+ warnings:
    //  - libGDX' SharedLibraryLoader calls System.load -> needs --enable-native-access
    //  - enable LWJGL's jdk.internal.misc.Unsafe backend instead of the deprecated sun.misc.Unsafe
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
    )
}

tasks {
    named<JavaExec>("run") {
        workingDir = rootProject.file("assets")
        isIgnoreExitValue = true
    }

    jar {
        archiveFileName.set("${application.applicationName}-${project.version}.jar")
        // using 'lib' instead of the default 'libs' appears to be needed by jpackageimage.
        destinationDirectory.set(layout.buildDirectory.dir("lib"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(files(sourceSets.main.get().output.classesDirs))
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        // these "exclude" lines remove some unnecessary duplicate files in the output JAR.
        exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**")

        manifest {
            attributes(
                // Enabling native access is good for JARs run by Java 24 or later.
                // Setting Multi-Release to true allows LWJGL3 to use different classes on recent Java versions.
                "Main-Class" to application.mainClass,
                "Enable-Native-Access" to "ALL-UNNAMED",
                "Multi-Release" to "true",
                "Add-Exports" to "java.base/jdk.internal.misc",
            )
        }
    }

    // Equivalent to the jar task; here for compatibility with gdx-setup.
    register("dist") {
        dependsOn(jar)
    }
}

construo {
    name.set(application.applicationName)
    // human-readable name, used for example in the `.app` name for macOS
    humanName.set(application.applicationName)

    jlink {
        // jdeps reports LWJGL's Unsafe backend usage as "java.base/jdk.internal.misc",
        // which is a package, not a module, and makes jlink fail with
        // "Module java.base/jdk.internal.misc not found". List the real modules instead
        // (java.base is always included in an image).
        guessModulesFromJar.set(false)
        modules.set(listOf("java.desktop", "jdk.unsupported"))
    }

    targets {
        create<Target.Linux>("linuxX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://cdn.azul.com/zulu/bin/zulu25.36.205-ca-jdk25.0.4.1-linux_x64.tar.gz")
        }
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://cdn.azul.com/zulu/bin/zulu25.36.205-ca-jdk25.0.4.1-win_x64.zip")
        }
    }
}
