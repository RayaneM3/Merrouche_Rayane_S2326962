plugins {
    alias(libs.plugins.android.application)
    // Reads private keys from local.properties (not shared on GitHub) into the manifest
    alias(libs.plugins.secrets.gradle.plugin)
}

android {
    namespace = "org.me.gcu.merrouche_rayane_s2326962"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.me.gcu.merrouche_rayane_s2326962"
        minSdk = 26          // Android 8.0 Oreo, as required by the brief
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true // android.util.Log calls do nothing in local tests
        unitTests.isIncludeAndroidResources = true // needed by the Robolectric UI tests
    }
}

// Keys (MAPS_API_KEY, TS_CLIENT_ID, TS_CLIENT_KEY) live in local.properties.
// local.defaults.properties only holds placeholders so the project always builds.
secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.play.services.maps)
    testImplementation(libs.junit)
    // Test-only XmlPullParser implementation, so the parser can be unit tested on the JVM
    testImplementation(libs.kxml2)
    // Test-only: runs the real Activity/Fragments on the JVM for UI tests and screenshots
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.ext.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
