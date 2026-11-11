# Chapter 4: 화면 구조 · 생명주기 · CCP/Back

## Core Idea
앱은 **Driving view 옆의 1~2칸 window**에 뜹니다. 사용자가 크기를 바꾸고, 앱은 죽지 않고 뒤로 숨습니다. 그래서 **반응형 레이아웃, 상태 복원, Back으로 종료하지 않기**가 기본입니다.

## Frameworks Introduced
- **Window 모델**
  - Driving view는 고정 영역입니다. 앱이 가리거나 종료할 수 없습니다.
  - `screenOrientation`을 지정하지 않으면 portrait(1칸), `landscape`로 지정하면 2칸을 씁니다.
  - 새 앱은 현재 화면의 방향을 따라 뜹니다. portrait 두 개가 떠 있으면 새 앱은 primary window에 뜹니다.
  - How: portrait에는 핵심 기능, landscape에는 부가 정보와 확장 기능을 둡니다. **두 방향 모두 같은 기능을 노출**합니다(기능 패리티).
- **포그라운드/백그라운드 계약**
  - 포그라운드 진입: 상태를 복원하고, 현재 크기와 방향에 맞게 UI를 재구성합니다.
  - 백그라운드 전환: 화면 갱신, 애니메이션, 센서 수집, 타이머, 폴링, 불필요한 네트워크를 멈춥니다.
  - 유지할 작업(오디오, 통신, 다운로드)은 실행 조건과 중단·재개 정책을 정의합니다.
  - 복귀하면 **마지막 상태를 복원**합니다. 수 분 이내면 최대한 그대로, 수 시간 이상이면 초기 상태도 허용됩니다. 프로세스가 재생성돼도 복원되게 합니다.
- **CCP Back 규칙**: 최상위 화면에서 Back을 받으면 `moveTaskToBack(true)`로 숨깁니다. 하위 화면이나 팝업이 있으면 일반 Back 동작을 합니다.

## Key Concepts
- **기능 패리티**: 화면 상태나 전환과 관계없이 같은 버튼과 기능이 보여야 합니다. UI는 비율에 맞게 이동만 합니다.
- **레터박스 금지**: 화면을 채웁니다. 한 방향 미세 보정만 허용됩니다. 호환성 모드로 실행되면 안 됩니다.
- **안전 영역**: 컷아웃, 인셋, 시스템 바, 소프트 키보드를 피해 배치합니다.
- **CCP**: 로터리 컨트롤러입니다(회전, 클릭, 방향 이동). 사용자가 설치하는 앱은 지원이 선택이고, 기본 탑재 앱은 필수입니다.
- **ROTARY_SUPPORTED**: CCP를 지원하지 않는다고 선언하는 meta-data입니다.

## Code Examples
```kotlin
// CCP·일반 Back: 최상위에서는 숨기기, 종료 금지
onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        when {
            dialogShowing -> dismissDialog()
            navController.previousBackStackEntry != null -> navController.popBackStack()
            else -> moveTaskToBack(true)   // finish()/finishAffinity() 금지. 미디어는 계속 재생
        }
    }
})
```
```xml
<!-- CCP 미지원 선언 (필수 조작은 터치만으로 끝낼 수 있어야 함) -->
<application>
    <meta-data android:name="com.hmg.rotary.ROTARY_SUPPORTED" android:value="false"/>
</application>
```
```kotlin
// EditText가 없는 Dialog: 키보드가 떠도 밀려 올라가지 않게 IME insets 제외
dialog.window?.let { win ->
    val p = win.attributes
    p.setFitInsetsTypes(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    win.attributes = p
}
```

## Reference Tables

| CCP 지원 요건 | 사용자 설치 앱 | 기본 탑재 앱 |
|---|---|---|
| 지원 여부 | 선택 | 필수(계약 조건) |
| 미지원 시 | 터치만으로 필수 조작 완결 | 승인 불가(정적 콘텐츠, WebView 제약, 게임은 면제 가능) |
| Back 숨김 | **필수** | **필수** |

## Worked Example
landscape 음악 앱 옆에 portrait 내비가 붙어 앱이 1칸으로 줄어드는 경우입니다.
1. `onConfigurationChanged`나 리사이즈 이벤트가 오면 레이아웃을 1칸 기준으로 재배치합니다. 재생 컨트롤과 현재 곡이 남아 있어야 합니다.
2. 확장 영역(가사, 추천)은 접고, 같은 기능은 메뉴로 접근할 수 있게 둡니다. 기능 패리티를 유지합니다.
3. 사용자가 내비를 닫으면 다시 2칸이 됩니다. 스크롤 위치와 선택 탭을 복원합니다.
4. 사용자가 CCP Back을 눌러 최상위에 도달하면 `moveTaskToBack`을 호출합니다. 음악은 계속 재생됩니다.
5. 몇 시간 뒤 시스템이 프로세스를 죽였다가 다시 띄우면 `SavedStateHandle`이나 영속 저장소에서 마지막 곡과 위치를 복원합니다.

## Anti-patterns
- **고정 크기 레이아웃 또는 레터박스**: 디스플레이 호환성 심사에서 반려됩니다.
- **방향에 따라 기능이 사라짐**: 기능 패리티 위반입니다.
- **Back에서 `finish()`**: 반려 사유입니다.
- **백그라운드에서 타이머나 폴링을 유지**: CPU와 배터리 한도를 넘습니다(ch06).
- **EditText가 없는 Dialog가 키보드에 밀려 올라감**: 앱에서 고쳐야 하는 부분입니다.

## Key Takeaways
1. portrait와 landscape를 모두 설계하고, 어떤 크기에서도 핵심 기능이 동작하게 합니다.
2. 앱은 숨겨질 뿐 종료되지 않습니다. 다시 포그라운드로 올 때와 프로세스 재생성 때 모두 복원합니다.
3. Back을 최상위에서 받으면 숨기기입니다.
4. CCP를 지원하지 않으면 meta-data로 선언하고, 터치만으로 완결되게 합니다.
5. 화면 해상도 수치는 문서에 없습니다. 폼팩터 테스트(ch07)로 검증합니다.

## Connects To
- **ch03**: 주행 시작 시 미디어 UI 숨김
- **ch05**: Back으로 숨긴 뒤 오디오 유지
- **ch07**: 폼팩터 테스트, 디스플레이 호환성 심사
