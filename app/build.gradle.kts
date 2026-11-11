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
 * 契约夹具（任务书 §8.1）—— 方案 C：夹具随工程走
 *
 * 夹具真身：app/src/test/resources/contract/
 *   ContractTest 通过 classLoader.getResource("contract/xxx.json") 直接读取，
 *   不需要任何复制/同步任务，clone 下来 ./gradlew test 即可运行。
 *
 * 对外分发副本：工作区 docs/contract/（给后端 pytest 和文档引用）
 *   改动规则：先改工程内真身 → 跑测试绿 → 再复制到 docs/contract/，
 *            并同步修改《接口契约.md》和升版本号。
 *   （历史：方案 A 曾用绝对路径 C:/Users/.../docs/contract 做复制源，
 *     换机器或他人 clone 后测试必挂，已废弃。）
 */

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
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
