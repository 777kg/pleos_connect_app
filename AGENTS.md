# AGENTS.md — Codex 등 다른 AI 에이전트용 안내

> 이 폴더의 기준 문서는 **`PLEOS_DEV_NOTES.md`**(공식 문서 정리본)와 **`.claude/skills/pleos-connect-dev/`**(Claude와 공유하는 개발 규칙 스킬)다.
> 도구가 달라도 같은 규칙을 따르도록, 여기에는 내용을 복사하지 않고 **어디를 읽어야 하는지만** 적는다.

## 프로젝트
- Pleos Connect(현대차그룹 차량 플랫폼, Android Automotive OS) 앱. 앱 주제·카테고리는 아직 미정.
- 앱: `PleosApp/` (Kotlin, minSdk 28, compileSdk 34, Vehicle SDK 2.3.2). 설명은 `PleosApp/README.md`.
- 문서 언어 한국어, 코드 식별자 영어. 저장소 https://github.com/777kg/pleos_connect_app (기본 브랜치 main).

## 작업 전에 읽을 파일
| 작업 | 파일 |
|---|---|
| 규칙 요약·위험 신호 | `.claude/skills/pleos-connect-dev/SKILL.md`, `cheatsheet.md` |
| 영역별 상세 | `.claude/skills/pleos-connect-dev/chapters/ch01~ch07` |
| 원본·공식 링크 | `PLEOS_DEV_NOTES.md` |

## 역할
- **qa(GPT/Codex 담당)**: `.codex/qa.md`를 따른다(코드 수정 금지, 검증·보고 + `docs/qa/ISSUES.md` 상태 갱신). 실행 `npm run qa:codex -- "바뀐 것"`.
- **plan(GPT/Codex 담당)**: `.codex/planner.md`를 따른다(코드 수정 금지, `docs/plan/` 기획서만 갱신, 웹 검색 사용). 화면 산출물은 `.claude/skills/pleos-plan-ui/`(와이어프레임 → UI 설계서 → 지속 갱신). 실행 `npm run plan:codex -- "요청"`, 보고서 `.codex-out/plan-last.md`.

## 모든 에이전트 공통 금지
- `git commit`·`push`·브랜치 변경 금지 — 커밋은 메인(Claude)이 검토 후 한다.
- 서명 키(`*.jks`, `*.keystore`), 클라이언트 시크릿, `local.properties`를 읽어서 출력하거나 수정하지 않는다.
- 에뮬레이터·Gradle 빌드를 새로 띄우지 않는다. 이미 떠 있는 에뮬레이터(`adb devices`)만 쓴다.
- 맡은 범위 밖 파일을 고치지 않는다. 필요하면 "이 파일도 바꿔야 함"이라고 보고만 한다.
- `toy/MARRY_CARD`는 별개 프로젝트다. 손대지 않는다.
- 문서에 없는 사항은 추측하지 말고 "확인 필요"로 표시한다.
