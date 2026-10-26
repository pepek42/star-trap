plugins {
    id("kotlin-jvm")
}

dependencies {
    implementation(libs.gdxTools)
}

tasks.register<JavaExec>("packTextures") {
    group = rootProject.name
    description = "Packs raw_assets/*.png into assets/textures/textures.atlas"

    mainClass.set("com.github.pepek42.asteroids.tool.TexturePackerKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
}
