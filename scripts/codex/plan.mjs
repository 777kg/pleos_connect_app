// 기획(GPT/Codex): 앱 주제·카테고리·기능을 제안하고, 사용자 결정을 받아 docs/plan/ 기획서를 계속 고친다. 코드는 고치지 않는다.
//
// 사용 (pleos-connect/ 에서):
//   npm run plan:codex -- "주제·카테고리 후보를 뽑아줘"
//   npm run plan:codex -- "결정: 후보 2(카테고리 Lifestyle)로 간다. 기능을 구체화해줘"
//   npm run plan:codex -- --dir docs/plan/service-mileage-log "…"   (기획 폴더 지정, 기본 docs/plan)
//   npm run plan:codex -- --no-search "…"   (웹 검색 끄기)
//   npm run plan:codex -- --dry-run "…"
// 기억: docs/plan/PLAN.md·DECISIONS.md (매번 다시 읽는다) / 보고서: .codex-out/plan-last.md
import { parseArgs, runCodex } from "./runner.mjs";

let argv = process.argv.slice(2);
const search = !argv.includes("--no-search");
// --dir docs/plan/<slug>: 기획 폴더(PLAN.md·DECISIONS.md·ui/)를 바꾼다. 기본은 docs/plan
const dirAt = argv.indexOf("--dir");
const dir = (dirAt >= 0 ? argv[dirAt + 1] : "docs/plan").replace(/\\/g, "/").replace(/\/+$/, "");
if (dirAt >= 0) argv = argv.filter((_, i) => i !== dirAt && i !== dirAt + 1);
const opts = parseArgs(argv.filter((a) => a !== "--no-search"));
if (!opts.request) {
  console.error('무엇을 기획할지 적어주세요. 예) npm run plan:codex -- "주제·카테고리 후보를 뽑아줘"');
  process.exit(1);
}

// 기획은 기획 폴더 문서와 보고서만 쓴다(아이디어 목록 상태 칸 갱신 포함)
const escaped = dir.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const ALLOWED = [/^\.codex-out\//, new RegExp(`^${escaped}/`), /^docs\/plan\/ideas\//];

const prompt = [
  "너는 이 폴더(Pleos Connect 차량 앱)의 기획 담당이다.",
  `먼저 \`.codex/planner.md\`를 읽고 그 원칙·절차·보고 형식을 그대로 따라라. 이번 기획 폴더는 \`${dir}/\`다. planner.md와 pleos-plan-ui 스킬에 적힌 \`docs/plan/PLAN.md\`·\`docs/plan/DECISIONS.md\`·\`docs/plan/ui/\`는 각각 \`${dir}/PLAN.md\`·\`${dir}/DECISIONS.md\`·\`${dir}/ui/\`로 바꿔 읽어라. 기억은 이 두 파일이다.`,
  `화면(와이어프레임·UI 설계서·화면 갱신)은 \`.claude/skills/pleos-plan-ui/SKILL.md\` 규칙대로 \`${dir}/ui/\`에 만든다.`,
  ...(dir !== "docs/plan" ? ["`docs/plan/PLAN.md`·`docs/plan/DECISIONS.md`·`docs/plan/ui/`는 다른(이전) 기획이다. 읽지도 고치지도 마라."] : []),
  `\`${dir}/\`와 \`docs/plan/ideas/\` 밖의 파일을 수정하지 말고, git commit/push/브랜치 변경을 하지 마라. 에뮬레이터·Gradle 빌드를 실행하지 마라.`,
  "범위를 좁혀라(사용량 절약): 필요한 문서 부분만 `rg`로 찾아 읽고, 웹 검색은 꼭 필요할 때만 짧게 해라.",
  "문서에 없는 사항은 추측하지 말고 '확인 필요'로 남겨라. 사용자가 정할 것은 질문으로 남겨라.",
  "",
  "## 이번 요청 (메인이 전달)",
  opts.request,
].join("\n");

runCodex({
  role: "plan",
  prompt,
  allowed: ALLOWED,
  opts,
  search,
  nextStep: "보고서의 ❓를 사용자에게 묻기 → 답을 받아 plan:codex 재실행(결정 반영)",
});
