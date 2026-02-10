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
    dependsOn("generateSdkDex")
    from("src/main/resources") // pick up sdk.dex generated after processResources
}

// --- SDK dex generation task ---

tasks.register("generateSdkDex") {
    description = "Generate SDK dex from lib module (build AAR → d8)"
    dependsOn(":lib:assembleRelease")

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

        val workDir = project.layout.buildDirectory.dir("generate-sdk-dex").get().asFile
        val dexOut = project.file("src/main/resources/sdk.dex")

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

        // 3. Copy classes.dex as sdk.dex to patcher resources
        val generatedDex = File(workDir, "classes.dex")
        require(generatedDex.exists()) { "d8 did not produce classes.dex" }
        generatedDex.copyTo(dexOut, overwrite = true)
        println("Generated sdk.dex (${dexOut.length()} bytes)")
    }
}
