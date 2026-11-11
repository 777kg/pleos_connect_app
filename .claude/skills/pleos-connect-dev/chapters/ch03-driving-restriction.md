# Chapter 3: 주행 안전 · 주행 규제 · 앱 카테고리

## Core Idea
주행 중 무엇을 막을지는 **앱이 판단하지 않습니다.** 플랫폼이 내려주는 `CarUxRestrictions`의 `ActiveRestrictions` 비트를 그대로 따릅니다. 그 위에 **카테고리별 허용 범위**와 **"미디어는 정차 중에만"** 원칙을 얹습니다.

## Frameworks Introduced
- **3상태 판정 (Parking / Idling / Driving)**: 플랫폼이 속도, 기어, 브레이크와 지역 규정으로 판정합니다. 속도 임계값은 공개되지 않으며, 앱이 계산하면 안 됩니다.
- **Distraction Optimization(DO) 이분법**
  - DO 미선언 앱: Parking에서만 사용 가능하고 Drive에서는 사용할 수 없습니다(플랫폼이 차단).
  - DO 선언 앱: 주행 중에도 화면이 뜹니다. 대신 **앱이 직접** `ActiveRestrictions`를 보고 기능별로 차단해야 합니다.
  - When to use: Music/Audio, Maps/Navigation, Mobility Services처럼 주행 중 사용 가치가 있는 앱만 DO를 선언합니다. 나머지는 선언하지 않는 편이 안전합니다.
- **미디어 앱 정차 전용 규칙** (동영상·게임·브라우저): 주행이 시작되면 ① 재생을 일시정지하고 ② UI를 숨기며 ③ 주행 중에는 일시정지를 해제할 수 없게 합니다.

## Key Concepts
- **CarUxRestrictionsManager**: AAOS 서비스입니다. `registerListener()`로 등록하면 `onUxRestrictionsChanged()`로 변경을 받습니다.
- **isRequiresDistractionOptimization()**: 현재 제한이 걸려 있는지 나타냅니다.
- **ActiveRestrictions 비트**: NO_DIALPAD(1), NO_FILTERING(2), LIMIT_STRING_LENGTH(4), NO_KEYBOARD(8), NO_VIDEO(16), LIMIT_CONTENT(32), NO_SETUP(64)
- **카테고리 8종**: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication

## Reference Tables

**지역별 Driving 상태 허용 여부** (Parking에서는 전부 허용)

| 기능 (플래그) | 북미 | 유럽 | 그 외(한국 등) |
|---|---|---|---|
| 키보드 (NO_KEYBOARD) | ⛔ | ✅ | ✅ |
| 다이얼패드 (NO_DIALPAD) | ⛔ | ✅ | ✅ |
| 통합 검색 (NO_KEYBOARD, NO_FILTERING) | 음성만 | ✅ | ✅ |
| 미디어 앱 로그인·설정·검색 (NO_SETUP 외) | ⛔ | ⛔ | ✅ |
| 내비 검색 | ⛔ | ✅ | ✅ |
| 디지털키·도어·시트·차량 설정 (NO_SETUP) | ⛔ | ⛔ | ⛔ |
| 동영상 (NO_VIDEO) | ⛔ | ⛔ | ⛔ |
| QR 로그인 (NO_SETUP) | ⛔ | ⛔ | ⛔ |
| 매뉴얼·대용량 텍스트 (LIMIT_CONTENT, LIMIT_STRING_LENGTH) | ⛔ | ⛔ | ⛔ |

**주행 중 허용 예시가 있는 카테고리**

| 카테고리 | 주행 중 허용 예 |
|---|---|
| Music/Audio | 앨범아트, 재생·일시정지·곡 넘김, 스트리밍, 라디오 채널, 오디오북 |
| Maps/Navigation | 경로·TBT 음성, 간결한 지도, 실시간 교통 |
| Mobility Services | EV 충전소·주유소·주차장 찾기, 위치 기반 즉시 정보 |
| 그 외 5종 | 문서에 기준 없음. 정차 중 사용을 전제로 설계 |

## Code Examples
```kotlin
// AAOS 표준 API 스케치. build.gradle에 android { useLibrary("android.car") }가 필요합니다(AAOS 표준, Pleos 문서에는 없음).
val car = Car.createCar(context)
val uxManager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager

val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> apply(r) }
uxManager.registerListener(listener)
apply(uxManager.currentCarUxRestrictions)          // 초기값도 반드시 반영

fun apply(r: CarUxRestrictions) {
    val active = if (r.isRequiresDistractionOptimization) r.activeRestrictions else 0
    if (active and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO != 0) stopPlayingVideo()
    searchField.isEnabled = active and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD == 0
    settingsEntry.isEnabled = active and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP == 0
}
// onDestroy: uxManager.unregisterListener(); car.disconnect()
```
```xml
<!-- DO 선언: AAOS 표준 형식. Pleos 문서에 명시되지 않아 [확인 필요] -->
<activity android:name=".MainActivity">
    <meta-data android:name="distractionOptimized" android:value="true"/>
</activity>
```
- 에뮬레이터 v2.1.0에는 `hmg_car_service`가 없습니다(2026-11-11). Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)에서 기어를 D/P로 바꿔 규제를 켜고 끕니다. 아래 명령은 이전 이미지용입니다.
```bash
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
```
- **What it demonstrates**: 지역을 직접 분기하지 않습니다. 플랫폼이 지역에 맞춰 내려준 비트만 해석합니다.

## Worked Example
DO를 선언한 **음악 앱**이 한국과 북미에서 주행을 시작하는 경우입니다.
- 한국: NO_VIDEO 등은 켜지지만 NO_KEYBOARD와 NO_SETUP은 꺼져 있습니다. 검색과 로그인이 계속 동작합니다.
- 북미: NO_KEYBOARD와 NO_SETUP이 켜집니다. 검색창을 비활성화하고 음성 검색 버튼만 남깁니다. 로그인과 설정 진입 버튼도 막습니다.
- 공통: 첫 화면에 프리셋 플레이리스트, 최근 재생, 즐겨찾기를 둡니다. 재생, 일시정지, 다음 곡 버튼은 크게 만듭니다. 앨범아트 애니메이션은 넣지 않습니다.
- 검증: adb로 `driving_regulation on/off`를 토글하며 버튼 상태가 바뀌는지 확인합니다. 기어 상태는 Virtual Console에서 바꿉니다.

## Anti-patterns
- **속도나 기어로 직접 판정**: 지역 규정이 빠집니다. 판정은 플랫폼 비트로만 합니다.
- **지역 코드로 하드코딩 분기**: 규제 사양이 바뀌면 따라가지 못합니다. `ActiveRestrictions`를 해석합니다.
- **리스너만 등록하고 초기값을 반영하지 않음**: 주행 중에 실행하면 제한이 빠집니다.
- **주행 중 영상 재개, 게임·웹서핑·피트니스 유도**: 반려 사유입니다.
- **미디어 앱의 헤드업 알림이나 운전과 무관한 알림**: 금지입니다.
- **가이드만 지키면 통과한다고 가정**: 심사에서 추가 위험이 있다고 보면 거절됩니다.

## Key Takeaways
1. 판정은 플랫폼이 하고, 앱은 비트를 반영합니다. 초기값과 변경을 모두 처리합니다.
2. 동영상, 차량 설정, QR 로그인, 긴 텍스트는 모든 지역에서 주행 중 금지입니다.
3. DO는 주행 중 쓸 이유가 있는 카테고리만 선언합니다. 선언했다면 모든 기능을 비트별로 막아야 합니다.
4. 텍스트 입력 대신 프리셋, 최근 항목, 음성을 쓰고, 큰 터치 영역과 높은 대비, 적은 텍스트로 만듭니다.
5. 버튼 높이는 UI Design Guide Basic button Sizes(Large 100 · Medium 80 · Small 64)를 따릅니다. 간격 수치는 미정리. 터치 조작을 줄이고 음성·물리 버튼 연계를 검토합니다.

## Connects To
- **ch04**: 주행 시작 시 UI 숨김, 상태 복원
- **ch05**: 주행 중 오디오는 포커스 정책을 따름
- **ch07**: 운전자 주의 분산 심사 기준
