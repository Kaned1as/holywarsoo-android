buildscript {
    val kotlinVersion: String by extra("1.6.21")

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
    }
}

allprojects {

    dependencies {
        val kotlinVersion: String by extra("1.6.21")
        val acraVersion: String by extra("5.9.3")
        val markwonVersion: String by extra("4.6.2")
        val roomVersion: String by extra("2.2.3")
    }

    repositories {
        google()
        jcenter()
    }
}
