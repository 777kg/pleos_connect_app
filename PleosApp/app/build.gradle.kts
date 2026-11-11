plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pleos.consumables"
    compileSdk = 34

    defaultConfig {
        // 출시용 패키지명(DECISIONS 2026-11-10 결정 5). 사용 가능 확인(2026-11-10).
        // 소스 패키지(namespace)도 같은 이름을 씁니다.
        applicationId = "com.pleos.consumables"
        minSdk = 28
        targetSdk = 34 // Pleos 문서에 targetSdk 요건 없음 [확인 필요]
        versionCode = 1 // 심사 재제출마다 반드시 올립니다(ch07).
        versionName = "0.1.0"
    }

    // release 서명: 키 파일·비밀번호는 저장소 밖 ~/.gradle/gradle.properties에서 읽습니다(저장소에 넣지 않음).
    // 네 값이 모두 있어야 서명하고, 없으면 지금처럼 서명 없는 release APK가 만들어집니다.
    val releaseStoreFile = findProperty("PLEOS_RELEASE_STORE_FILE") as String?
    val releaseStorePassword = findProperty("PLEOS_RELEASE_STORE_PASSWORD") as String?
    val releaseKeyAlias = findProperty("PLEOS_RELEASE_KEY_ALIAS") as String?
    val releaseKeyPassword = findProperty("PLEOS_RELEASE_KEY_PASSWORD") as String?
    val hasReleaseKey = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 디버그 키 제출은 반려 사유입니다. 정식 키가 없으면 서명하지 않습니다.
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }

    // CarUxRestrictionsManager(AAOS 표준)용. Pleos 문서가 아니라 AAOS 표준 설정입니다.
    useLibrary("android.car")
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")

    testImplementation("junit:junit:4.13.2")
    // 로컬 단위 테스트에서 org.json(안드로이드 스텁은 동작하지 않음)을 쓰기 위함: GleoActions 응답·파라미터 검사
    testImplementation("org.json:json:20231013")
}
