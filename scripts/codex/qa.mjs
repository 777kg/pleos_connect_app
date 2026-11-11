// QA(GPT/Codex): 메인(Claude)이 만든 변경을 Codex가 독립적으로 검증한다. 코드는 고치지 않는다.
//
// 사용 (pleos-connect/ 에서):
//   npm run qa:codex -- "주행 규제 리스너 추가 (PleosApp/app/src/main/java/.../DrivingRestrictionMonitor.kt)"
//   npm run qa:codex -- --dry-run "…"
// 보고서: .codex-out/qa-last.md
import { parseArgs, runCodex } from "./runner.mjs";

const opts = parseArgs(process.argv.slice(2));
if (!opts.request) {
  console.error('무엇이 바뀌었는지 적어주세요. 예) npm run qa:codex -- "주행 규제 리스너 추가 (바뀐 파일 경로)"');
  process.exit(1);
}

// QA는 소스를 바꾸지 않는다. 보고서와 이슈 기록(ISSUES.md·증거)만 허용
const ALLOWED = [
  /^\.codex-out\//,
  /^docs\/qa\/ISSUES\.md$/,
  /^docs\/qa\/evidence\//,
];

const prompt = [
  "너는 이 폴더(Pleos Connect 차량 앱)의 QA 담당이다.",
  "먼저 `.codex/qa.md`를 읽고 그 절차와 보고 형식을 그대로 따라라. 기준 문서는 거기서 지시하는 스킬 파일들이다.",
  "소스·문서·설정·Gradle 파일을 수정하지 말고, git commit/push/브랜치 변경을 하지 마라. 에뮬레이터·Gradle 빌드를 새로 띄우거나 APK를 설치하지 마라.",
  "예외: 검증이 끝나면 `.codex/qa.md` 2.2대로 `docs/qa/ISSUES.md`의 이슈 상태(해결·재검증 대기·미해결)와 새 이슈를 직접 갱신해라.",
  "범위를 좁혀라(사용량 절약): 빌드·lint는 메인 몫이니 돌리지 말고, 바뀐 파일과 메인이 지정한 재검증 기준만 확인해라. 관련 없는 파일·문서 전체를 읽지 마라.",
  "문서에 없는 사항은 추측하지 말고 💬 '확인 필요'로 남겨라.",
  "",
  "## 이번에 바뀐 것 (메인이 전달)",
  opts.request,
].join("\n");

runCodex({
  role: "qa",
  prompt,
  allowed: ALLOWED,
  opts,
  nextStep: "보고서의 🔴부터 수정 → 필요하면 qa:codex 재실행(최대 2회)",
});
