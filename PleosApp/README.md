# PleosApp — 소모품 교체 거리 기록

Android Automotive OS(Pleos Connect)용 Kotlin 앱입니다. 소모품(엔진오일·타이어·에어컨 필터)을 교체할 때 차량 누적 주행거리를 기록하고, 교체 후 경과 거리와 잔여 비율을 보여 줍니다.
카테고리 Mobility Services, DO 미선언(정차 중 전용). 기획: `../docs/plan/service-mileage-log/PLAN.md`, 설계서: `../docs/plan/service-mileage-log/ui/UI_SPEC.md`

**구현 단계**: 3단계까지(이전 '출발 전 점검' 코드는 교체, 공통 기반만 재사용).
| 단계 | 내용 | 상태 |
|---|---|---|
| 1 | 차량 누적 주행거리 조회(`OdometerSource`) + 정차 중 전용 처리 | 에뮬레이터 확인 |
| 2 | 교체 기록·기준 거리 로컬 저장(`RecordStore`), 판정 로직(`ConsumableCalculator`), 화면 S05 홈·S01 상세·S04 새 기록 확인·S06 기준 거리 | 에뮬레이터 확인(1칸 배치 제외) |
| 3 | S02 이력(최신순·마지막 기록 배지·개별 삭제·전체 삭제), S03 기록 수정(±100 km, ±1일·1시간·10분, 미래 시각 금지, 수정 표기) | 에뮬레이터 확인. 단위 테스트 12개 |
| 4 | 소모품 18종 카탈로그(2026-11-12부터 타이어는 위치별 4개 항목, 종류 수는 1종으로 셈)(`Consumable`), 설정 S07(홈에 보일 항목 ON/OFF, `RecordStore.enabledItems`), 동력 방식 읽기(`VehicleInfoSource`, INFO_FUEL_TYPE·CAR_INFO)로 맞지 않는 항목 숨김, 반실사 소모품 이미지 18종×11단계, QA-009~012 반영 | 에뮬레이터 확인(EV로 읽힘) |

소모품 이미지: `npm run image:codex -- --name <키> "설명"`으로 원본(`docs/assets/consumables/<키>.png`, Codex 이미지 생성 → 보관은 .webp)을 만들고 `python scripts/images/make_step_images.py --from-dir docs/assets/consumables`로 `res/drawable-nodpi/`에 11단계씩 넣습니다. `ConsumableImages`가 이름으로 찾아 씁니다. 새 소모품은 `Consumable`에 한 줄 + 문자열 + 이미지로 추가합니다.
규칙 출처: `../.claude/skills/pleos-connect-dev`, `../PLEOS_DEV_NOTES.md`

## 환경
| 항목 | 값 |
|---|---|
| minSdk / compileSdk / targetSdk | 28 / 34 / 34 (targetSdk 요건은 문서에 없음 [확인 필요]) |
| AGP / Kotlin / Gradle | 8.5.2 / 1.9.25 (Vehicle SDK의 kotlin-stdlib와 맞춤) / 8.7 |
| JDK | 17 (Android Studio 내장 JBR) |
| 저장소 | `https://nexus-playground.pleos.ai/repository/maven-releases/` (settings에만 등록) |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |

## 명령줄 빌드·테스트
- Gradle 8.7(`~/.gradle/wrapper/dists/gradle-8.7-bin/.../bin/gradle`)과 JDK 17로 `assembleDebug`, `testDebugUnitTest`.
- 이 PC의 시스템 PATH에 따옴표(`"`)가 섞여 있어 단위 테스트 JVM이 `Could not find or load main class VS`로 실패합니다. 테스트할 때는 PATH를 정리하고(`/usr/bin:/bin:/c/WINDOWS/system32`) `CLASSPATH`를 비운 뒤 실행합니다.

## 여는 방법
1. Android Studio로 이 폴더(`PleosApp`)를 엽니다. `gradlew` 스크립트와 wrapper jar는 아직 없습니다. Studio가 `gradle-wrapper.properties`로 Gradle 8.7을 받습니다. 필요하면 `gradle wrapper`로 생성합니다.
2. SDK Manager에서 Android 14(API 34) 플랫폼과 Pleos Connect system image v2.1.0을 설치합니다(ch01).
3. AVD: Automotive, Cold boot, RAM 4GB.
4. 주행 규제 확인: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off`

## 이미 반영한 규칙
| 규칙 | 위치 |
|---|---|
| 최상위 Back(CCP 포함) → `moveTaskToBack(true)`, `finish()` 없음 | `MainActivity` |
| 주행 규제: 리스너 + 초기값 반영. 정차 중 전용이라 규제 중에는 값·버튼을 숨기고 안내만 표시 | `DrivingRestrictionMonitor`, `MainActivity` |
| 누적 주행거리: capability(READ 계열) 확인 → get 1회(진입·복귀·다시 확인), 세대 번호로 늦은 응답 버림, 상시 리스너 없음 | `OdometerSource` |
| 조회는 화면이 보이고 규제 초기값을 받은 뒤에만, 숨김·규제 시 진행 중 응답 무효화 | `MainViewModel` |
| 백그라운드에서 규제 구독 해제(onStop), 복귀 시 재등록·초기값 재반영 | `DrivingRestrictionMonitor` |
| portrait(1칸)·landscape(2칸) 레이아웃, 같은 ID·같은 기능 | `layout/`, `layout-land/` |
| `screenOrientation` 미지정, 고정 크기 없음 | `AndroidManifest.xml` |
| 차량 값은 저장·복원하지 않음(복귀 시 다시 조회해 오래된 값을 현재 값처럼 보이지 않게) | `MainViewModel` |
| CCP 미지원 선언 `ROTARY_SUPPORTED=false` | `AndroidManifest.xml` |
| 백업 제외, release `debuggable=false` | Manifest, `data_extraction_rules.xml`, `build.gradle.kts` |
| 퍼미션은 조회용 `CAR_DRIVE` 하나만 | `AndroidManifest.xml` |
| Vehicle은 처음 필요할 때 생성, release는 ViewModel이 끝날 때 | `VehicleGateway`, `MainViewModel` |

## SDK 2.3.2에서 확인한 것
- Odometer `getCurrentDistanceDriven(onComplete: (Float?) -> Unit, onFailed)`: KDoc "Current odometer value of the vehicle in kilometers (km)", 값이 없으면 null. capability는 `checkCurrentDistanceDrivenCapabilities`.
- 퍼미션: 인터페이스 `@RequiresPermission`과 구현 `@RequiresCustomPermission` 모두 `CAR_DRIVE`(protection level normal).
- Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)의 Odometer 항목은 표준 `PERF_ODOMETER`(0x11600204)에 연결되고 0~1,000,000 km를 넣을 수 있습니다.
- `VehicleException`은 AAR에서 난독화되어(`callback.exception.a`) 앱에서 타입과 errorCode를 쓸 수 없습니다. message의 ErrorCode 이름과 `SecurityException`으로 구분합니다 [확인 필요].
- 앱 패키지명(applicationId)과 소스 패키지(namespace) 모두 `com.pleos.consumables`입니다.

## 출시 전 승인·확인 (보류 중, 나중에 다시 확인)
| 항목 | 상태 | 비고 |
|---|---|---|
| App Market `pleos.car.permission.CAR_DRIVE` 승인 | 확인 | Playground 프로젝트에서 Vehicle State SDK 사용 가능 확인(2026-11-10 사용자 확인). Odometer 조회 가능 |
| Vehicle SDK가 병합하는 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 심사 대상 여부 | 확인 | 퍼미션 승인 확인(2026-11-10 사용자 확인) |
| `android.car.permission.CAR_INFO`(동력 방식) 승인 | 확인 | 퍼미션 승인 확인(2026-11-10 사용자 확인). S07 직접 선택은 자동 판정을 바꾸고 싶을 때 쓰도록 유지 |
| Codex 생성 소모품·차량 탑뷰 이미지의 사용 조건 | 확인 | OpenAI 약관상 출력물 소유권은 사용자, 사용 정책을 지키면 상업 이용 가능(2026-11-10 사용자 확인). 다른 사람도 비슷한 이미지를 만들 수 있어 독점권은 기대하지 않음 |
| 실차 주행거리 초기화·계기판 교체 동작, 차량 변경 구분 수단 | 미확인 | S01 '기준 다시 잡기'로 사용자가 풀 수 있게 함(구현) |
| 실제 1칸·2칸 해상도 | 확인 | display guide 해상도 표를 PLEOS_DEV_NOTES §6에 옮김(가장 좁은 앱 영역 628dp 폭) |
| UI Design Guide 버튼 높이 | 확인 | Basic button Sizes 그림: Large 100 · Medium 80 · Small 64. 모든 버튼 64dp 이상, 주요 동작 80dp |
| 버튼 사이 간격, 대비 수치 | 미확인 | 웹 가이드에 수치 없음 — Figma에서 직접 확인 |
| 음성·물리 버튼 연계(주행 안전 가이드) | 미확인 | CCP·Gleo 미지원. 정차 전용이라 위험 낮음, 필수 여부 확인 필요 |
| 정식 서명 키 | 확인 | 2026-11-10 생성(공모전 제출용). `build.gradle.kts`가 `~/.gradle/gradle.properties`의 `PLEOS_RELEASE_*` 네 값으로 서명, 값이 없으면 서명 없이 빌드. 키·비밀번호는 저장소에 넣지 않음 |
| targetSdk·서명(v2/v3) 요건 | 미확인 | 지금은 v2 서명(minSdk 28) |
| 패키지명 `com.pleos.consumables` | 확인 | 사용 가능 확인(2026-11-10 사용자 확인) |

## 확인 필요
- 에뮬레이터 v2.1.0에서 Odometer 조회(값 미발행 상태에서는 0 km)·INFO_FUEL_TYPE(EV=[10]) 확인됨. 퍼미션 거부 응답은 미확인
- `initialize()` 직후 호출이 Service 연결 전이라 실패할 수 있음 → "값을 읽지 못했습니다" 후 "다시 확인"으로 재시도
- DO meta-data 정확한 형식(이 앱은 DO 미선언)
- AAR에는 문서의 19개 도메인 외에 `getGear()`도 있습니다. 기어는 표준 값이므로 `CarPropertyManager`를 우선합니다.
- UI 수치·서명 키 등 출시 전 항목은 위 '출시 전 승인·확인' 표에 모았습니다.
