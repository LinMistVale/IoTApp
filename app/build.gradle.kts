plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.iotapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.iotapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
}

/*
 * 契约夹具同步（任务书 §8.1）
 * 真身在 docs/contract/，测试前复制到 src/test/resources/contract/，
 * 保证「一份夹具、两端共用」。
 *
 * ⚠️ 临时方案 A：绝对路径。原因是当前 docs 和工程不在同一个仓库里。
 * TODO(交付前必须改)：换成方案 C —— 把夹具放进工程内（或改工程内相对路径），
 *                       否则别人 clone 下来 ./gradlew test 会因为找不到目录直接失败。
 */
val contractDir = "C:/Users/zzl/WorkBuddy/2026-11-04-13-53-22/docs/contract"
val syncContract = tasks.register<Copy>("syncContract") {
    from(contractDir)
    into(layout.projectDirectory.dir("src/test/resources/contract").asFile)
}
// syncContract 会写入 src/test/resources，凡是要读该目录的任务都必须先跑它，
// 否则 Gradle 9 会报 "implicit dependency" 校验错误
tasks.matching { it.name.contains("UnitTest") || it.name.startsWith("test") }
    .configureEach { dependsOn(syncContract) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
