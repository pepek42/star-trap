plugins {
    id("kotlin-jvm")
}

sourceSets {
    main {
        // assets folder contains the resources of the game
        resources.srcDir(rootProject.files("assets"))
    }
}

dependencies {
    api(libs.gdx) // requires API to correctly expose Disposable for launcher classes (TeaVM)
    api(libs.bundles.ktxBaseBundle) // requires API to correctly expose ApplicationListener for launcher classes
    api(libs.bundles.ashleyBundle)
    implementation(libs.bundles.box2dBundle)
    implementation(libs.bundles.freetypeBundle)
    implementation(libs.bundles.aiBundle)
    implementation(libs.ktxPreferences)
    implementation(libs.ktxI18n)
    implementation(libs.gdxControllersCore)

    // Tests touch real Box2D/libGDX classes, so they need the desktop backend and natives.
    testImplementation(libs.gdxBackendLwjgl3)
    listOf(libs.gdxPlatform, libs.gdxBox2dPlatform).forEach {
        testRuntimeOnly(it) {
            artifact { classifier = "natives-desktop" }
        }
    }
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterParams)
    testImplementation(libs.mockitoKotlin)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
