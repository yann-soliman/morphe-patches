group = "io.github.yannsoliman.patches"

java { sourceCompatibility = JavaVersion.VERSION_11 }

val personalBundle = providers.gradleProperty("personalBundle")
    .map(String::toBoolean)
    .orElse(false)

val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("app.morphe:morphe-patcher:${libs.versions.morphe.patcher.get()}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}
configurations.testImplementation { extendsFrom(configurations.compileOnly.get()) }

tasks.test { testLogging { showStandardStreams = true } }

patches {
    about {
        name = if (personalBundle.get()) "Yann's patches (personal)" else "Yann's patches"
        description = if (personalBundle.get()) {
            "Patchs personnels pour Keep Cool à appliquer avec Morphe."
        } else {
            "Correctifs pour applications Android à appliquer avec Morphe."
        }
        source = "https://github.com/yann-soliman/morphe-patches"
        author = "Yann Soliman; développement avec Tiklaw"
        contact = "https://github.com/yann-soliman/morphe-patches/issues"
        website = "https://github.com/yann-soliman/morphe-patches"
        license = "GPLv3"
    }
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }
    publish { dependsOn("generatePatchesList") }
}
