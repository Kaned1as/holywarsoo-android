@file:Suppress("SpellCheckingInspection") // we don't care for dependency names

import com.android.build.VariantOutput.FilterType
import com.palantir.gradle.gitversion.VersionDetails
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.net.URI

plugins {
    id("com.android.application")

    id("com.palantir.git-version").version("0.11.0")
    id("com.github.triplet.play").version("2.6.2")

    id("kotlin-android")
    id("kotlin-android-extensions")
    id("kotlin-kapt")
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
    compileSdkVersion(29)
    ndkVersion = "21.3.6528147"

    defaultConfig {
        applicationId = "com.kanedias.holywarsoo"
        manifestPlaceholders = mapOf("mainHost" to "holywarsoo.net")
        minSdkVersion(21)
        targetSdkVersion(29)
        versionCode = 30
        versionName = "1.5.10"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments = mapOf(
                    "room.schemaLocation" to "$projectDir/src/main/db-schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
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
            setEnable(true)
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

            // don't produce universal apk if publishing to google play
            // as all architectures are covered by existing splits
            val gplayPublishing = project.hasProperty("gplayReleaseType")
            setUniversalApk(!gplayPublishing)
        }
    }

    flavorDimensions("purity")
    productFlavors {
        create("fdroid") {
            setDimension("purity")
        }

        create("googleplay") {
            setDimension("purity")
        }
    }

    applicationVariants.all {
        val imgurApiKey = System.getenv("IMGUR_API_KEY").orEmpty()
        buildConfigField("String", "IMGUR_API_KEY", "\"$imgurApiKey\"")

        outputs.forEach { output ->
            val outputApk = output as ApkVariantOutputImpl

            // user-supplied code
            val versionCode = android.defaultConfig.versionCode!!

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

            buildConfigField("int", "VANILLA_VERSION_CODE", versionCode.toString())
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
    implementation(kotlin("stdlib-jdk8", "1.3.61"))

    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")           // constaint layout view
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.0.0")       // swipe-to-refresh layout view
    implementation("androidx.cardview:cardview:1.0.0")                           // snappy cardview for lists
    implementation("androidx.preference:preference:1.1.0")                       // preference fragment compatibility
    implementation("androidx.lifecycle:lifecycle-extensions:2.1.0")              // view-model providers
    implementation("com.google.android.material:material:1.2.0-alpha03")         // Material design support lib
    implementation("androidx.room:room-runtime:${extra["roomVersion"]}")         // SQLite ORM lib

    implementation("com.jakewharton:butterknife:10.2.0")                         // Annotation processor
    implementation("com.squareup.okhttp3:okhttp:3.14.2")                         // android http client
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

    implementation("org.jsoup:jsoup:1.12.1")                                     // HTML parser

    // kotlin extensions
    implementation("androidx.core:core-ktx:1.1.0")                               // kotlin support for androidx
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.2.0-rc03")        // coroutines in lifecycles
    implementation("androidx.room:room-ktx:${extra["roomVersion"]}")             // coroutines/transactions in orm

    // annotation processors
    kapt("com.jakewharton:butterknife-compiler:10.2.0")                          // view bindings
    kapt("androidx.room:room-compiler:${extra["roomVersion"]}")                  // database schema

    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
}
