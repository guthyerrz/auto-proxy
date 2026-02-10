import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("com.guthyerrz.autoproxy.patcher.MainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.clikt)
    implementation(libs.apktool.lib)
    implementation(libs.smali.baksmali)
    implementation(libs.apksig)

    testImplementation(libs.junit5)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("auto-proxy-patcher")
    archiveClassifier.set("")
    archiveVersion.set("")
}

// --- Smali generation task ---

val generateSmaliCp = sourceSets["main"].runtimeClasspath

tasks.register("generateSmali") {
    description = "Generate SDK smali from lib module (build AAR → d8 → baksmali)"
    dependsOn(":lib:assembleRelease", "classes")

    doLast {
        // Locate d8 from Android SDK
        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
        val sdkDir = localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
            ?: error("Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties")
        val buildToolsDir = File(sdkDir, "build-tools").listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?: error("No build-tools found in Android SDK at $sdkDir")
        val d8 = File(buildToolsDir, "d8").also {
            require(it.exists()) { "d8 not found at ${it.absolutePath}" }
        }

        val aar = rootProject.file("lib/build/outputs/aar/lib-release.aar")
        require(aar.exists()) { "AAR not found: ${aar.absolutePath}" }

        val workDir = project.layout.buildDirectory.dir("generate-smali").get().asFile
        val smaliOut = project.file("src/main/resources/smali/com/guthyerrz/autoproxy")

        workDir.deleteRecursively()
        workDir.mkdirs()

        // 1. Extract classes.jar from AAR
        project.copy {
            from(project.zipTree(aar))
            include("classes.jar")
            into(workDir)
        }

        // 2. Convert JAR to DEX
        ProcessBuilder(d8.absolutePath, "${workDir}/classes.jar", "--output", workDir.absolutePath)
            .inheritIO()
            .start()
            .also { require(it.waitFor() == 0) { "d8 failed with exit code ${it.exitValue()}" } }

        // 3. Baksmali: DEX → smali (uses google/smali via apktool-lib transitive dep)
        val cp = generateSmaliCp.asPath
        ProcessBuilder(
            "java", "-cp", cp,
            "com.guthyerrz.autoproxy.patcher.tools.SmaliGeneratorKt",
            "${workDir}/classes.dex", "${workDir}/smali"
        )
            .inheritIO()
            .start()
            .also { require(it.waitFor() == 0) { "Baksmali failed with exit code ${it.exitValue()}" } }

        // 4. Copy SDK smali to patcher resources
        smaliOut.deleteRecursively()
        smaliOut.mkdirs()

        val sourceDir = File(workDir, "smali/com/guthyerrz/autoproxy")
        require(sourceDir.exists() && sourceDir.isDirectory) {
            "Expected baksmali output at ${sourceDir.absolutePath}"
        }

        project.copy {
            from(sourceDir)
            include("*.smali")
            into(smaliOut)
        }

        val generated = smaliOut.listFiles()?.filter { it.extension == "smali" }?.sorted() ?: emptyList()
        println("Generated ${generated.size} smali files:")
        generated.forEach { println("  ${it.name}") }
    }
}
