# 내 차량 소모품

> 소모품을 바꿀 때 차의 누적 주행거리를 기록해, 교체 후 달린 거리와 남은 정도를 한눈에 보여 주는 Pleos Connect 차량용 앱

- 팀명: 777kg
- 출품: 현대자동차그룹 차량용 앱 개발 공모전
- 플랫폼: Pleos Connect(Android Automotive OS), 카테고리 Mobility Services, 정차 중 전용

## 앱 소개
엔진오일·타이어·브레이크 패드처럼 주행거리에 따라 바꾸는 소모품을 언제 교체했는지 기억하기 어렵습니다. 이 앱은 교체할 때 버튼 한 번으로 **차량이 알려 주는 누적 주행거리**를 기록하고, 그 뒤로 얼마나 달렸는지와 사용자가 정한 교체 거리 대비 남은 비율을 보여 줍니다.

| 기능 | 설명 |
|---|---|
| 차량 탑뷰 홈 | 차 그림 주변에 소모품을 놓고(타이어는 앞·뒤·왼쪽·오른쪽 위치별 4개), 항목마다 남은 비율과 단계 이미지를 표시. 목록 보기·임박 순 정렬 |
| 교체 기록 | Pleos Vehicle SDK로 읽은 누적 주행거리를 그대로 저장(직접 입력 없음) |
| 교체 거리 | 항목별로 사용자가 정함(빠른 선택, 내 평균으로 채우기). 앱이 교체 주기를 권장하지 않음 |
| 이력·통계 | 최신순 이력, 수정·삭제, 평균 교체 간격 |
| 차량 신호 나란히 | 상세 화면에 차량이 직접 알려 주는 엔진오일·브레이크액·변속기 오일 양을 내 교체 기록과 별개로 표시(Safety, `CAR_SAFETY`). 지원하지 않는 차량에서는 숨김 |
| Gleo 음성 질의 | "타이어 얼마나 탔어?"처럼 물으면 교체 후 달린 거리와 남은 비율을 답하고, 항목 화면을 열어 줌(`assets/schema.json`, 3개 기능, [기능 정리](docs/plan/service-mileage-log/GLEO_ACTIONS.md)). 주행 중에는 정차 후 확인하라고만 답함 |
| 소모품 21종 | 차량 동력 방식(전기차·내연기관·하이브리드)을 자동 판정해 맞는 항목만 표시, 설정에서 직접 선택 가능 |
| 기준 다시 잡기 | 계기판 교체 등으로 주행거리가 마지막 기록보다 작아지면 새 기준으로 다시 시작 |
| 주행 안전 | 주행 규제(CarUxRestrictions) 중에는 값과 조작을 모두 숨기고 "정차 중에만 사용할 수 있습니다"만 표시 |

데이터는 차량 안에만 저장되며 서버로 보내지 않습니다([개인정보처리방침](docs/store/privacy-policy.html)).

## APK 위치
- [`apk/777kg_내차량소모품_release.apk`](apk/777kg_내차량소모품_release.apk) — release 빌드, 정식 키로 서명(APK Signature Scheme v2), 약 11 MB
- 패키지: `com.pleos.consumables`, 실행 Activity: `com.pleos.consumables.MainActivity`

## 테스트 환경
| 항목 | 값 |
|---|---|
| 에뮬레이터 | Android Studio AVD — Automotive, Android 14(API 34), **Pleos Connect system image v2.1.0** |
| AVD 설정 | Cold boot, RAM 4GB 권장 |
| 화면 | 2560×1440(160dpi)에서 확인. portrait 1칸·landscape 2칸 모두 지원 |
| 앱 빌드 | minSdk 28 / compileSdk 34 / targetSdk 34, AGP 8.5.2, Kotlin 1.9.25, Gradle 8.7, JDK 17 |
| 사용 SDK | Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`(Pleos Nexus 저장소) |
| 퍼미션 | `pleos.car.permission.CAR_DRIVE`(누적 주행거리), `pleos.car.permission.CAR_SAFETY`(액량 신호), `android.car.permission.CAR_INFO`(동력 방식) |

### 필요한 설정과 참고
- 별도 계정·로그인·서버 설정이 필요 없습니다. 설치 후 바로 실행됩니다.
- 차량 상태는 **Pleos Connect Virtual Console**로 바꿉니다(에뮬레이터 v2.1.0 이상). 아래를 실행한 뒤 PC 브라우저에서 `http://127.0.0.1:8088/`을 엽니다.
  ```bash
  adb forward tcp:8088 tcp:8088
  ```
- 누적 주행거리: Virtual Console → Emulation Controls → Motion → **Odometer**(처음 0 km). 값을 올리면 앱의 '교체 후 달린 거리'와 남은 비율이 바뀝니다. 에뮬레이터는 주행거리를 줄이는 값은 받지 않으므로, 주행거리가 줄어드는 '기준 다시 잡기' 상황은 단위 테스트로 검증했습니다.
- 에뮬레이터는 동력 방식을 전기차로 알려 주므로 엔진오일 등 내연기관 항목이 숨겨집니다. 설정 → 차량 동력에서 '내연기관'을 고르면 보입니다.
- 주행 규제(정차 중 전용) 확인: Virtual Console 화면 오른쪽 위 기어(또는 Emulation Controls → Motion → Gear Selection)에서 **D**를 고르면 앱이 가려지고, **P**로 돌리면 다시 보입니다. 에뮬레이터 v2.1.0에는 이전 안내의 `hmg_car_service` 명령이 없습니다.
- 차량 신호 카드(엔진오일·브레이크액·변속기 오일 양)는 에뮬레이터 v2.1.0이 해당 값을 지원하지 않아 나타나지 않습니다. 지원 차량에서만 보입니다.
- Gleo 음성 기능은 Gleo 승인 전에는 음성으로 부를 수 없습니다. adb로 같은 호출을 보내 확인할 수 있습니다([GLEO_ACTIONS.md](docs/plan/service-mileage-log/GLEO_ACTIONS.md) '검증').

## 실행 방법
### 1) APK로 실행
에뮬레이터를 켠 뒤 설치하고 실행합니다.
```bash
adb install -r apk/777kg_내차량소모품_release.apk
```
```bash
adb shell am start -n com.pleos.consumables/com.pleos.consumables.MainActivity
```
앱 목록의 **내 차량 소모품**(타이어 아이콘)을 눌러도 됩니다.

같은 패키지의 디버그 빌드가 이미 설치되어 있으면 서명이 달라 설치가 거부됩니다. 이때는 먼저 지운 뒤 설치합니다(앱 기록도 함께 지워짐).
```bash
adb uninstall com.pleos.consumables
```

### 2) 소스로 빌드
1. Android Studio에서 `PleosApp` 폴더를 엽니다. SDK Manager에서 Android 14(API 34)와 Pleos Connect system image v2.1.0을 설치합니다.
2. Pleos 저장소(`https://nexus-playground.pleos.ai/repository/maven-releases/`)는 `PleosApp/settings.gradle.kts`에 등록되어 있습니다.
3. 빌드·테스트(JDK 17):
   ```bash
   cd PleosApp && ./gradlew assembleDebug testDebugUnitTest
   ```
4. 설치: `PleosApp/app/build/outputs/apk/debug/app-debug.apk`를 위 `adb install`로 설치합니다.

## 저장소 구성
| 경로 | 내용 |
|---|---|
| `PleosApp/` | 앱 소스(Kotlin). 상세는 [PleosApp/README.md](PleosApp/README.md) |
| `docs/plan/service-mileage-log/` | 기획서(PLAN), 결정 기록(DECISIONS), UI 설계서·와이어프레임 |
| `docs/qa/ISSUES.md` | QA 이슈와 처리 상태 |
| `docs/store/`, `docs/assets/store/` | 개인정보처리방침, 앱 아이콘 원본 |
| `PLEOS_DEV_NOTES.md` | Pleos 공식 문서 정리(개발 규칙) |
