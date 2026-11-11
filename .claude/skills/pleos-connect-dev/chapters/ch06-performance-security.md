# Chapter 6: 성능 · 네트워크 · 보안 · 개인정보

## Core Idea
차량은 **자원이 공유되고, 네트워크가 끊기고, 여러 사람이 함께 쓰는** 기기입니다. 숫자로 정해진 성능 한도를 지키고, 오프라인을 전제로 만들고, 토큰과 개인정보는 Keystore와 최소 수집으로 다룹니다.

## Frameworks Introduced
- **성능 한도표** (심사 항목. 아래 Reference Tables)
- **오프라인 우선 3종 세트**: ① 최근 콘텐츠 로컬 캐시 ② 재시도와 타임아웃 ③ 오프라인 안내 UI
- **공유 기기 보안 원칙**
  - 로그아웃과 계정 전환을 쉽게 만듭니다.
  - 토큰은 Keystore에 둡니다. 민감정보는 앱 수준에서 암호화하고 내부 저장소에 두며, 캐시와 백업에서는 제외합니다.
  - 로그는 마스킹합니다. 릴리스 빌드에서는 디버그 로그를 끕니다.
- **동의의 3원칙**: 이유를 밝히고, 최소한만 수집하고, 명시적 버튼으로만 동의를 받습니다(뒤로가기나 빈 곳 터치는 동의가 아님). 철회하면 즉시 중단하고 저장된 데이터를 삭제합니다.

## Key Concepts
- **TLS 1.2 이상**(1.3 권장): SSL과 TLS 1.0/1.1을 차단합니다. 인증서 검증을 우회하지 않습니다.
- **AES-GCM / SHA-256**: 권장 암호입니다. ECB, MD5, SHA-1은 금지입니다. IV를 재사용하지 않고, 안전한 난수를 씁니다.
- **Authorization 헤더**: 토큰과 세션은 URL이나 쿼리에 넣지 않습니다.
- **exported=false**: 외부 호출이 필요 없는 컴포넌트는 노출하지 않습니다. 민감정보를 암시적 브로드캐스트로 보내지 않습니다.
- **텔레메트리 가명화**: 차량 ID와 사용자 ID를 익명화하거나 가명화합니다. 운전 행동 데이터는 마케팅에 쓰지 않습니다.

## Reference Tables

| 항목 | 한도 |
|---|---|
| RAM | 400MB 이하 |
| CPU 주행 중 | 내비 앱 15% 이하 / 그 외 5% 미만 |
| CPU 정차 중 | 25% 이하 |
| 앱 실행, 콘텐츠 로드 | 10초 이내 |
| 버튼 반응 | 2초 이내(넘으면 진행 중 UI 표시) |
| 터치 후 반응 애니메이션 | 0.25초 이내 |

| 릴리스 빌드 체크 | 값 |
|---|---|
| debuggable | false |
| WebView 원격 디버깅 | off |
| 서명 | 정식 배포 키(디버그 키 금지) |
| 디버그 로그 | off 또는 마스킹 |
| 자동 백업 | 끄거나 민감 파일 제외 |
| 서버 주소 | 도메인(IP 하드코딩 금지) |
| 키·시크릿 | 소스·리소스에 하드코딩 금지 |

## Code Examples
```kotlin
// app/build.gradle.kts — 릴리스 빌드 기본값
android {
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true            // 난독화는 문서상 필수 아님, 권장 관행
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```
```xml
<application
    android:allowBackup="false"
    android:usesCleartextTraffic="false">
    <service android:name=".SyncService" android:exported="false"/>
</application>
```
```kotlin
// 토큰 저장: Keystore 기반 암호화 (예: AndroidX Security / 직접 Keystore AES-GCM)
// 네트워크: OkHttp 등에서 TLS 1.2+ 만 허용, 타임아웃·재시도 설정
val client = OkHttpClient.Builder()
    .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
    .connectTimeout(10, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
```

## Worked Example
로그인이 있는 스트리밍 앱의 체크입니다.
1. Cold start에서는 토큰을 확인하고 첫 화면만 그립니다. 추천 목록은 lazy 로드하고, 10초를 넘기지 않습니다.
2. 주행 중에는 CPU 5% 미만이어야 합니다. 앨범아트 애니메이션과 1초 단위 진행바 갱신을 줄입니다. 백그라운드에서는 UI 갱신을 멈춥니다.
3. 터널에서 끊기면 캐시된 최근 플레이리스트를 보여 주고 "오프라인" 배너를 띄웁니다. 재시도는 백오프로 합니다.
4. 운전자가 바뀌면 설정에서 바로 로그아웃하고 계정을 전환할 수 있어야 합니다. 토큰은 Keystore에서 삭제합니다.
5. 분석 이벤트는 사용자 ID를 가명화해서 보냅니다. 위치를 쓴다면 위치정보 약관과 동의 화면을 갖춥니다.

## Anti-patterns
- **타이머나 폴링으로 차량 값 갱신**: CPU 한도를 넘습니다. 리스너를 씁니다.
- **스플래시에서 모든 초기화**: 실행 10초를 넘깁니다.
- **기기 재시작이나 무관한 시스템 설정 변경을 요구**: 반려 사유입니다.
- **SharedPreferences에 평문 토큰, URL 쿼리에 토큰**
- **모호한 동의, 철회 후 데이터 보관**: 정책 위반입니다.
- **악성 행위, 무단 설치, 보안 우회**: 즉시 거부됩니다.

## Key Takeaways
1. RAM 400MB, 주행 중 CPU 5%(내비 15%), 실행 10초, 버튼 2초, 터치 0.25초를 지킵니다.
2. 네트워크는 끊긴다고 가정하고 캐시, 재시도, 오프라인 UI를 갖춥니다.
3. 공유 기기이므로 쉬운 로그아웃, Keystore, 마스킹을 기본으로 합니다.
4. 릴리스 빌드에서 debuggable을 끄고 정식 키로 서명합니다.

## Connects To
- **ch04**: 백그라운드에서 작업 정리
- **ch07**: 보안, 개인정보, 성능 심사 기준
