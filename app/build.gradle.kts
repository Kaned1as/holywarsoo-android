@file:Suppress("SpellCheckingInspection") // we don't care for dependency names

import com.android.build.VariantOutput.FilterType
import com.palantir.gradle.gitversion.VersionDetails
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.net.URI

plugins {
    id("com.android.application")

    id("com.palantir.git-version").version("0.11.0")
    id("com.github.triplet.play").version("2.6.2")

    kotlin("android")
    kotlin("kapt")
}

repositories {
    mavenCentral()
    google()
    maven { url = URI.create("https://jitpack.io") }
    maven { url = URI.create("https://oss.sonatype.org/content/repositories/snapshots/") }
}

fun versionDetails() = (extra["versionDetails"] as groovy.lang.Closure<*>)() as VersionDetails
fun gitVersion() = (extra["gitVersion"] as groovy.lang.Closure<*>)() as String

android {
    compileSdk = 31
    ndkVersion = "22.1.7171670"

    namespace = "com.kanedias.holywarsoo"
    defaultConfig {
        applicationId = "com.kanedias.holywarsoo"
        manifestPlaceholders["mainHost"] = "holywarsoo.net"
        minSdk = 24
        targetSdk = 31
        versionCode = 31
        versionName = "1.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/src/main/db-schemas"
                arguments["room.incremental"] = "true"
                arguments["room.expandProjection"] = "true"
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("misc/signing.keystore")
            storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            keyAlias = "release-key"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

            // don't produce universal apk if publishing to google play
            // as all architectures are covered by existing splits
            val gplayPublishing = project.hasProperty("gplayReleaseType")
            isUniversalApk = !gplayPublishing
        }
    }

    flavorDimensions += listOf("purity")
    productFlavors {
        create("fdroid") {
            dimension = "purity"
        }

        create("googleplay") {
            dimension = "purity"
        }
    }

    applicationVariants.all {
        val imgurApiKey = System.getenv("IMGUR_API_KEY").orEmpty()
        val versionCode = android.defaultConfig.versionCode!!

        buildConfigField("String", "IMGUR_API_KEY", "\"$imgurApiKey\"")
        buildConfigField("int", "VANILLA_VERSION_CODE", versionCode.toString())

        outputs.forEach { output ->
            val outputApk = output as ApkVariantOutputImpl

            // user-supplied code

            // code based on ABI
            val versionCodes = mapOf(
                "universal" to 0,
                "armeabi-v7a" to 1,
                "arm64-v8a" to 2,
                "x86" to 3,
                "x86_64" to 4
            )

            val abiVersionCode = versionCodes[outputApk.getFilter(FilterType.ABI) ?: "universal"]!!

            // code based on track
            val gitVersionCode = versionDetails().commitDistance

            // production code should be higher than alpha so it can substitute it on release publish
            // (google play requirement)
            val playVersionCode = when (play.track) {
                "internal" -> 1
                "alpha" -> 2
                "beta" -> 3
                "production" -> 4
                else -> 0
            }

            outputApk.versionCodeOverride = versionCode * 10000 + playVersionCode * 1000 + gitVersionCode * 10 + abiVersionCode
            outputApk.versionNameOverride = gitVersion().replace(".dirty", "")
        }
    }
}


kapt {
    useBuildCache = true
}

play {
    serviceAccountCredentials = file("misc/android-publisher-account.json")
    track = project.findProperty("gplayReleaseType").toString()
}

dependencies {
    implementation(kotlin("stdlib-jdk8", "1.6.21"))

    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")           // constaint layout view
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")       // swipe-to-refresh layout view
    implementation("androidx.cardview:cardview:1.0.0")                           // snappy cardview for lists
    implementation("androidx.preference:preference:1.2.0")                       // preference fragment compatibility
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")              // view-model providers
    implementation("com.google.android.material:material:1.7.0-alpha02")         // Material design support lib
    implementation("androidx.room:room-runtime:${extra["roomVersion"]}")         // SQLite ORM lib

    implementation("com.squareup.okhttp3:okhttp:4.9.3")                         // android http client
    implementation("com.github.franmontiel:PersistentCookieJar:v1.0.1")          // cookie support
    implementation("com.github.stfalcon:stfalcon-imageviewer:0.1.0")             // embedded image viewer
    implementation("com.r0adkll:slidableactivity:2.1.0")                         // fragment swipe right to go back action
    implementation("com.github.marcoscgdev:EasyAbout:1.0.6")                     // easy 'about' dialog

    implementation("ch.acra:acra-mail:${extra["acraVersion"]}")                  // crash handler
    implementation("ch.acra:acra-dialog:${extra["acraVersion"]}")                // crash handler dialog

    implementation("io.noties.markwon:core:${extra["markwonVersion"]}")          // markdown rendering
    implementation("io.noties.markwon:image-glide:${extra["markwonVersion"]}")
    implementation("io.noties.markwon:html:${extra["markwonVersion"]}")
    implementation("io.noties.markwon:ext-tables:${extra["markwonVersion"]}")
    implementation("io.noties.markwon:ext-strikethrough:${extra["markwonVersion"]}")

    implementation("org.jsoup:jsoup:1.13.1")                                     // HTML parser

    // kotlin extensions
    implementation("androidx.core:core-ktx:1.7.0")                               // kotlin support for androidx
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.0-rc01")        // coroutines in lifecycles
    implementation("androidx.room:room-ktx:${extra["roomVersion"]}")             // coroutines/transactions in orm

    // annotation processors
    kapt("androidx.room:room-compiler:${extra["roomVersion"]}")                  // database schema

    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
