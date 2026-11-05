plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(libs.gdxTools)

    // BalanceSim: headless gameplay runner (JVM only, never shipped). Needs the game code, Box2D and the natives.
    implementation(project(":core"))
    implementation(libs.bundles.box2dBundle)
    implementation(libs.gdxBackendHeadless)
    listOf(libs.gdxPlatform, libs.gdxBox2dPlatform).forEach {
        runtimeOnly(it) {
            artifact { classifier = "natives-desktop" }
        }
    }

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.mockitoKotlin)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.register<JavaExec>("packTextures") {
    group = rootProject.name
    description = "Packs raw_assets/*.png into assets/textures/textures.atlas"

    mainClass.set("com.github.pepek42.asteroids.tool.TexturePackerKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("balanceSim") {
    group = rootProject.name
    description = "Headless balance sweep (ST-101). Options: -PbalanceArgs=\"--seeds=8 --levels=1,2 --bots=cautious\", see BalanceSim.kt"

    mainClass.set("com.github.pepek42.asteroids.tool.BalanceSimKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args = (findProperty("balanceArgs") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    // Never up to date: the sweep is the point of running it.
    outputs.upToDateWhen { false }
}
