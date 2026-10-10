extension { name = "extensions/naolib-microg-maps.mpe" }

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains", module = "annotations")
}

android {
    namespace = "io.github.yannsoliman.naolib.microgmaps"
    defaultConfig { minSdk = 26 }
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

tasks.withType<Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    systemProperty("user.home", testHome.absolutePath)
    doFirst { testHome.mkdirs() }
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
        "javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}
