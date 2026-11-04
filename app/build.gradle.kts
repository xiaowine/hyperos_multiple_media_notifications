plugins {
    alias(libs.plugins.agp.app)
}

// 默认关闭界面；需要设置页时使用：gradlew ... -PenableUi=true
val enableUi = providers.gradleProperty("enableUi")
    .map { it.toBoolean() }
    .getOrElse(false)

android {
    namespace = "cc.xiaowine.nmm"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "cc.xiaowine.nmm"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }

    buildFeatures {
        viewBinding = true
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(
                if (enableUi) "src/ui/AndroidManifest.xml" else "src/main/AndroidManifest.xml"
            )
            if (enableUi) {
                java.srcDir("src/ui/java")
                kotlin.srcDir("src/ui/java")
                res.srcDir("src/ui/res")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
