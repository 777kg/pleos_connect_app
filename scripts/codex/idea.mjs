// 아이디어(GPT/Codex 기획 에이전트의 정기 실행): 실행할 때마다 새 앱 아이디어 1건을 docs/plan/ideas/backlog/에 쓴다.
// 현재 기획(PLAN.md·DECISIONS.md·ui/)은 건드리지 않는다. 작업 스케줄러가 한 시간마다 부른다(scripts/codex/idea-schedule.ps1).
//
// 사용 (pleos-connect/ 에서):
//   npm run idea:codex                       (새 아이디어 1건)
//   npm run idea:codex -- "주행 중 음성 위주로"   (방향 힌트를 덧붙임)
//   npm run idea:codex -- --dry-run
// 목록: docs/plan/ideas/INDEX.md / 보고서: .codex-out/idea-last.md
import { parseArgs, runCodex } from "./runner.mjs";

const argv = process.argv.slice(2);
const search = !argv.includes("--no-search");
const opts = parseArgs(argv.filter((a) => a !== "--no-search"));
const hint = opts.request;
opts.request = hint ? `새 아이디어 1건 (힌트: ${hint})` : "새 아이디어 1건";

// 아이디어는 ideas/ 아래와 보고서만 쓴다
const ALLOWED = [/^\.codex-out\//, /^docs\/plan\/ideas\//];

const now = new Date();
const pad = (n) => String(n).padStart(2, "0");
const stamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`;

const prompt = [
  "너는 이 폴더(Pleos Connect 차량 앱)의 기획 담당이다.",
  "먼저 `.codex/planner.md`를 읽고, 그중 **6장 아이디어 모드**를 그대로 따라라.",
  `이번 파일 이름: \`docs/plan/ideas/backlog/${stamp}-<영문-슬러그>.md\``,
  "`docs/plan/ideas/` 밖의 파일을 수정하지 말고, git commit/push/브랜치 변경을 하지 마라. 에뮬레이터·Gradle 빌드를 실행하지 마라.",
  "범위를 좁혀라(사용량 절약): `docs/plan/ideas/INDEX.md`로 중복을 먼저 확인하고, 필요한 문서 부분만 `rg`로 찾아 읽어라. 웹 검색은 꼭 필요할 때만 짧게.",
  "문서에 없는 사항은 추측하지 말고 '확인 필요'로 남겨라.",
  ...(hint ? ["", "## 방향 힌트 (메인이 전달)", hint] : []),
].join("\n");

runCodex({
  role: "idea",
  prompt,
  allowed: ALLOWED,
  opts,
  search,
  nextStep: "쌓인 아이디어를 사용자에게 보여 주고, 고른 것을 plan:codex로 넘겨 구체화",
});
