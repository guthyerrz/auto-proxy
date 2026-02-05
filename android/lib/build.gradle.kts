plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.guthyerrz.autoproxy"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral()

    if (project.findProperty("sign") != "false") {
        signAllPublications()
    }

    coordinates("com.guthyerrz", "autoproxy", project.findProperty("VERSION_NAME")?.toString() ?: "0.1.0-SNAPSHOT")

    pom {
        name.set("Auto Proxy")
        description.set("Zero-code HTTP/HTTPS proxy injection for Android debugging and traffic inspection.")
        url.set("https://github.com/guthyerrz/auto-proxy")
        inceptionYear.set("2025")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("guthyerrz")
                name.set("Guthyerrz Silva")
                url.set("https://github.com/guthyerrz")
            }
        }
        scm {
            url.set("https://github.com/guthyerrz/auto-proxy")
            connection.set("scm:git:git://github.com/guthyerrz/auto-proxy.git")
            developerConnection.set("scm:git:ssh://git@github.com/guthyerrz/auto-proxy.git")
        }
    }
}