# Chapter 1: 프로젝트 준비 · 개발 환경

## Core Idea
코드를 쓰기 전에 **Playground 프로젝트(카테고리 포함)와 SDK 사용 상태**를 먼저 확정합니다. 개발 환경은 **minSdk 28, API 34 Pleos Connect Emulator v2.1.0, Pleos Nexus 저장소** 세 가지만 맞으면 됩니다.

## Frameworks Introduced
- **SDK 신청 상태 6단계**: 준비 중 → 신청 필요 → 검토 중 → 사용 가능 / 반려됨 / 제휴 계약 필요
  - When to use: 기능을 기획할 때, 그 기능이 기대는 SDK를 지금 쓸 수 있는지 판단합니다.
  - How: Workspace > 프로젝트 정보 > SDK > 해당 SDK 상세에서 상태를 봅니다. "준비 중"이면 신청 버튼이 없으므로 그 기능을 일정에서 빼거나 대체 수단(AAOS 표준 API)을 찾습니다. "제휴 계약 필요"는 partnership@pleos.ai로 문의합니다.
- **3종 키**: 프로젝트 ID(Key) / 클라이언트 ID / 클라이언트 시크릿
  - 프로젝트 ID는 **심사 제출 시** App Market Console에 입력합니다.
  - 클라이언트 ID와 시크릿은 **서버 연동 SDK(TTS, STT)** 에 씁니다. 시크릿은 소스에 하드코딩하지 않습니다.

## Key Concepts
- **Pleos Playground**: 계정, 프로젝트, SDK 신청을 관리하는 개발자 포털(https://pleos.ai/playground)
- **카테고리**: 프로젝트를 만들 때 정합니다. 주행 중 허용 범위와 심사 적합성을 좌우하므로 미리 정합니다(ch03).
- **CRN**: 예전 테스트용 식별자입니다. Emulator 2.1.0부터 필요 없습니다(제공 종료).
- **Pleos Connect Emulator**: API 34 기반 Automotive system image입니다. 최신 버전은 v2.1.0(2026-10-19)입니다.
- **Virtual Console**: 에뮬레이터 2.1.0 이상에서 차량 상태와 이벤트(기어 등)를 주입하는 도구입니다.

## Reference Tables

| SDK | 신청 상태(2026-10 문서 기준) |
|---|---|
| Vehicle State, NaviHelper | 신청 없이 바로 사용 |
| Vehicle Control, FusedLocation, TextToSpeech, SpeechToText | 준비 중(신청 불가) |

| SDK | 최신 버전 | 주의할 Breaking change |
|---|---|---|
| Vehicle | 2.3.2 | `CruiseControl` 상수 제거 · `checkHvacFanSpeedCapabilities`가 `AreaBounds<Int>` 반환 · `FanDirectionType` 순서 변경(ordinal 저장 금지) · Area 타입 equals/hashCode 추가 |
| NaviHelper | 3.0.5 | 좌표 인자 **(lat, lng)** · `remainDistance/remainTime` · 콜백 타입 `NavigationStatus` |
| TTS / STT | 3.3.1 | 오류 콜백 인자 `ErrorType` |
| FusedLocation | 1.1.3 | `onChanged(LocationData?)` nullable · `onError` 구현 필수 |

| 항목 | 값 |
|---|---|
| minSdk | 28 이상 |
| compile/에뮬레이터 API | 34 |
| Gradle | 8.0 이상 |
| AVD | Automotive, Pleos Connect v2.1.0, **Cold boot**, **RAM 4GB** |
| system image URL | Windows/Linux `crp-sys-img.xml`, Mac `crp-arm64-sys-img.xml` |

## Code Examples
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
```
```kotlin
// app/build.gradle.kts
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")  // Getting Started 예제는 2.3.1
    // 쓰는 SDK만 추가 (Importing 페이지 기준)
    // implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    // implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // implementation("ai.pleos.playground:FusedLocation:1.1.3")
}
```
- "준비 중" SDK(TTS·STT·FusedLocation)는 좌표가 문서에 있어도 실제로 받을 수 있는지 확인이 필요합니다.
- **What it demonstrates**: Pleos Maven 저장소를 프로젝트 수준이 아닌 `settings`에만 등록합니다(`FAIL_ON_PROJECT_REPOS`).

## Worked Example
새 앱을 시작할 때의 순서입니다.
1. Playground에서 로그인한 뒤 Workspace > My Project > 프로젝트 생성을 합니다. 카테고리는 예를 들어 Music/Audio로 정합니다.
2. 프로젝트 정보 > SDK에서 Vehicle State가 "사용 가능"인지 확인합니다. TTS를 쓰려 했는데 "준비 중"이면 안드로이드 기본 TTS로 대체할지 결정합니다.
3. Android Studio에서 New Project > Automotive > Empty Activity를 만듭니다(Kotlin, minSdk 28). 먼저 `app` 모듈이 빌드되는지 확인합니다.
4. SDK Update Sites에 system image URL(Windows는 `crp-sys-img.xml`)을 추가하고 API 34 Pleos image를 설치합니다. 이전 버전 이미지는 먼저 지웁니다. 그다음 Android Studio를 재시작합니다.
5. AVD를 만듭니다(Cold boot, 4GB). 부팅이 끝난 뒤 Run 합니다.
6. 위 Gradle 설정을 넣고 Sync 합니다. 의존성을 찾지 못하면 저장소 접근 권한과 버전(2.3.2 → 2.3.1)을 확인합니다.

## Anti-patterns
- **문서 예제 버전을 그대로 고정**: Getting Started는 2.3.1이지만 최신은 2.3.2입니다. 릴리스 노트를 기준으로 삼습니다.
- **enum ordinal을 저장**: `FanDirectionType`처럼 순서가 바뀌면 저장된 값이 깨집니다. 저장할 때는 이름이나 명시적인 코드를 씁니다.
- **NaviHelper 좌표 순서를 가정**: 3.0.5부터 (lat, lng)입니다. 이름 있는 인자로 호출합니다.

## Key Takeaways
1. 카테고리와 SDK 가용성을 먼저 정합니다. 기능 기획이 여기에 따라 달라집니다.
2. "준비 중" SDK에 기대는 기능은 대체 경로를 함께 설계합니다.
3. 에뮬레이터는 Cold boot와 4GB로 설정하고, Mac과 Windows의 image URL이 다릅니다.
4. 시크릿은 코드에 넣지 않습니다(ch06).

## Connects To
- **ch02**: 받은 SDK를 실제로 호출하는 규칙
- **ch03**: 카테고리가 결정하는 주행 중 허용 범위
- **ch07**: 프로젝트 ID는 심사 제출 때 필요
