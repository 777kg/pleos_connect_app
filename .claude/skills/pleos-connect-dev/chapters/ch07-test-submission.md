# Chapter 7: 테스트 · 등록 · 심사

## Core Idea
심사는 **완성도, 주의 분산, 성능, 디스플레이 호환성, 퍼미션, 개인정보**를 봅니다. 제출 전에 에뮬레이터의 Virtual Console, 폼팩터 테스트, adb 주행 규제 토글로 모두 확인하고, **버전을 올려** APK로 제출합니다.

## Frameworks Introduced
- **제출 전 검증 루프**
  1. 주행 규제를 on/off 해서 기능이 차단되는지 확인합니다(ch03).
  2. Virtual Console로 기어와 도메인 상태를 바꿔 앱 반응을 확인합니다.
  3. 폼팩터 테스트로 Portrait/Landscape, 해상도, 크기 변경 시 잘림, 겹침, 정렬, 터치를 확인합니다.
  4. 오디오 필수 시나리오 5개를 확인합니다(ch05).
  5. Back으로 숨기고 복귀했을 때 상태가 복원되는지 확인합니다(ch04).
  6. 성능 한도를 측정합니다(ch06).
- **심사 5영역**: ① 완성도(설치·실행·크래시·더미 데이터 없음) ② 기능·성능(카테고리 적합, 응답 시간) ③ HW/SW 손상 금지 ④ SDK·API(승인된 퍼미션만) ⑤ 디스플레이 호환성(레터박스와 호환성 모드 금지, 기능 패리티)

## Key Concepts
- **Pleos App Market Console**: 심사를 제출하는 곳입니다.
- **버전 업 의무**: 수정이 하나라도 있으면 버전을 올립니다. 버전이 같으면 심사가 진행되지 않거나 반려됩니다.
- **영문 태그**: 앱 이름이 외국어라면 원문 영문명을 태그로 반드시 넣습니다(축약 금지).
- **테스트 계정**: 모든 기능을 확인할 수 있는 활성 계정을 제공하고, 외부 서버는 미리 켜 둡니다.
- **재심사**: 등록 후에도 모니터링하며, 품질이 미달하면 마켓에서 내려갑니다.

## Reference Tables

| 제출 자료 | 조건 |
|---|---|
| 앱 패키지 | **APK**, 최대 600MB |
| 프로젝트 ID | 권한 신청이 필요한 SDK를 쓸 때 |
| 아이콘 | PNG/JPG 512×512, 1MB 이하 |
| IVI 스크린샷 | 최대 8장, 장당 8MB 이하. 권장 2560×1380, 1700×1212 |
| 연령 등급 | IARC 증빙 |
| 개인정보처리방침 | 국가·언어별 URL |
| 위치정보 약관 | 위치를 쓰면 필수 |
| 고객지원 | 웹, 이메일, 전화 중 1개 이상 |
| 심사 노트 | 선택, 첨부 최대 60MB |

| 폼팩터 테스트 준비 | 값 |
|---|---|
| 필요 도구 | Platform-Tools, Go 1.22 이상, 포트 8095·8096 |
| 에뮬레이터 실행 | **별도 창**(Running Devices 내장이나 Headless 모드는 안 됨) |
| 종료 순서 | 가상 디스플레이 제거 → Bridge 종료(Ctrl+C) → `adb forward --remove tcp:8095` |

## Code Examples
- 에뮬레이터 v2.1.0에는 `hmg_car_service`가 없습니다(2026-11-11). Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`)에서 기어를 D/P로 바꿔 규제를 켜고 끕니다. 아래 명령은 이전 이미지용입니다.
```bash
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
```
```bash
adb shell cmd hmg_car_service --service drivingstate driving_regulation off
```
```bash
adb install -d -r app-release.apk
```
```bash
adb shell monkey -p com.example.app -c android.intent.category.LAUNCHER 1
```
```bash
adb shell am force-stop com.example.app
```
```bash
adb exec-out screencap -p > screen.png
```
- 기어 변경은 adb로 할 수 없고 Virtual Console에서 합니다. 가상 디스플레이를 제거하면 그 위의 앱이 강제 종료되므로 테스트 데이터를 먼저 저장합니다.

## Worked Example
첫 제출 직전 체크리스트입니다.
- [ ] `versionCode`와 `versionName`을 올렸다
- [ ] 릴리스 서명, `debuggable=false`, 로그 마스킹(ch06)
- [ ] Manifest 퍼미션이 승인 목록과 일치한다(쓰지 않는 도메인 퍼미션은 제거)
- [ ] 더미 데이터, 임시 콘텐츠, 테스트 서버 주소가 없다
- [ ] 주행 규제 on 상태에서 동영상, 설정, QR, 긴 텍스트가 차단된다
- [ ] portrait, landscape, 크기 변경에서 레터박스와 잘림이 없고 기능이 같다
- [ ] 최상위 Back이 숨기기로 동작한다
- [ ] 실행 10초, 버튼 2초 이내다(넘으면 로딩 UI)
- [ ] 테스트 계정과 심사 노트, 영문 태그, 스크린샷, IARC, 처리방침 URL을 준비했다

## Anti-patterns
- **같은 버전으로 재제출**: 심사가 진행되지 않습니다.
- **에뮬레이터 결과만으로 확신**: 코덱과 `Build.DEVICE`는 실차와 다릅니다.
- **카테고리와 맞지 않는 기능**: 거절될 수 있습니다.
- **게임 등 심의가 필요한 콘텐츠를 심의 없이 제출**: 한국은 게임물관리위원회 심의가 필요합니다.

## Key Takeaways
1. 제출 전 검증 루프 6단계를 돌립니다.
2. 버전을 올리고, APK로 제출하고, 승인된 퍼미션만 둡니다.
3. 테스트 계정과 자료를 빠짐없이 준비합니다.
4. 문서에 없는 항목(targetSdk, 서명 v2/v3, 심사 기간, 화면 해상도)은 제출 전에 문의로 확인합니다.

## Connects To
- **ch03 · ch04 · ch05 · ch06**: 검증 루프의 각 항목
- **ch01**: 프로젝트 ID
