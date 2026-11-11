// Codex CLI 공용 실행기: codex.exe 찾기 → `codex exec` 실행 → 바뀐 파일·범위 밖 변경 점검 → 보고서 경로 출력.
// 역할별 스크립트(qa.mjs, plan.mjs)가 이 함수를 부른다. MARRY_CARD/web/scripts/codex/runner.mjs를 옮겨 왔다.
// 변경 감지는 git status 대신 폴더 전체 파일 해시로 한다(.gitignore에 걸린 파일을 고쳐도 잡히게).
import { spawn, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
export const REPO = path.resolve(here, "../.."); // pleos-connect
const OUT_DIR = path.join(REPO, ".codex-out");

// 1) CODEX_BIN  2) PATH의 codex  3) Codex 데스크톱 앱 번들(업데이트마다 폴더명이 바뀌어 최신 것 선택)
export function findCodex() {
  if (process.env.CODEX_BIN && fs.existsSync(process.env.CODEX_BIN)) return process.env.CODEX_BIN;
  const which = spawnSync(process.platform === "win32" ? "where" : "which", ["codex"], { encoding: "utf8" });
  const onPath = which.status === 0 && which.stdout.split(/\r?\n/).find(Boolean);
  if (onPath) return onPath.trim();
  const appBin = path.join(process.env.LOCALAPPDATA ?? "", "OpenAI/Codex/bin");
  if (fs.existsSync(appBin)) {
    const found = fs
      .readdirSync(appBin)
      .map((d) => path.join(appBin, d, process.platform === "win32" ? "codex.exe" : "codex"))
      .filter((f) => fs.existsSync(f))
      .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
    if (found[0]) return found[0];
  }
  return null;
}

// 기본 모델·추론 강도. Codex 설정(~/.codex/config.toml·데스크톱 앱)에 맡기면 ultra 같은 비싼 값으로 돌 수 있어 여기서 고정한다.
// 바꾸려면: 환경변수 CODEX_MODEL / CODEX_EFFORT, 또는 실행 인자 --model / --effort
export const DEFAULT_MODEL = process.env.CODEX_MODEL || "gpt-6-sol";
export const DEFAULT_EFFORT = process.env.CODEX_EFFORT || "medium";

// 인자: [--dry-run] [--model M] [--effort low|medium|high] [--image F]... 요청문
export function parseArgs(argv) {
  const opts = { dryRun: false, model: DEFAULT_MODEL, effort: DEFAULT_EFFORT, images: [], request: "" };
  const words = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--dry-run") opts.dryRun = true;
    else if (argv[i] === "--model") opts.model = argv[++i];
    else if (argv[i] === "--effort") opts.effort = argv[++i];
    else if (argv[i] === "--image") opts.images.push(path.resolve(argv[++i]));
    else words.push(argv[i]);
  }
  opts.request = words.join(" ").trim();
  return opts;
}

// 빌드 산출물·캐시는 크고 매번 바뀌어 비교에서 뺀다
const SKIP_DIRS = new Set([".gradle", "build", ".idea", ".cxx", ".externalNativeBuild", "node_modules"]);

// 폴더 전체 "상대 경로 → 내용 해시". 이름이 아니라 내용을 비교해서 같은 파일을 또 고친 경우도 잡는다
function changedSnapshot() {
  const snap = new Map();
  const walk = (dir) => {
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      const abs = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        if (!SKIP_DIRS.has(ent.name)) walk(abs);
      } else if (ent.isFile()) {
        const rel = path.relative(REPO, abs).split(path.sep).join("/");
        snap.set(rel, createHash("sha1").update(fs.readFileSync(abs)).digest("hex"));
      }
    }
  };
  walk(REPO);
  return snap;
}

/**
 * @param {{ role: string, prompt: string, allowed: RegExp[], opts: ReturnType<typeof parseArgs>, nextStep: string, search?: boolean }} p
 */
export function runCodex({ role, prompt, allowed, opts, nextStep, search = false }) {
  const codex = findCodex();
  if (!codex) {
    console.error("Codex CLI를 찾지 못했어요. Codex 앱을 설치하거나 CODEX_BIN 환경변수에 codex 경로를 지정해주세요.");
    process.exit(1);
  }
  const outFile = path.join(OUT_DIR, `${role}-last.md`);
  // --search(웹 검색)는 최상위 옵션이라 exec 앞에 둔다. exec 뒤에 두면 "unexpected argument"로 실패한다
  const args = [...(search ? ["--search"] : []), "exec", "-C", REPO, "-s", "workspace-write", "--color", "never", "-o", outFile];
  if (opts.model) args.push("-m", opts.model);
  if (opts.effort) args.push("-c", `model_reasoning_effort=${opts.effort}`); // 따옴표 없이: Windows 인자 전달에서 따옴표가 깨지지 않게 (Codex는 문자열로 해석)
  for (const img of opts.images) args.push("-i", img);
  // "--" 필수: -i는 값을 여러 개 받아서, 없으면 프롬프트까지 이미지 경로로 먹고 "No prompt provided"로 끝난다
  args.push("--", prompt);

  if (opts.dryRun) {
    console.log(`codex: ${codex}\n작업 폴더: ${REPO}\n인자: ${JSON.stringify(args.slice(0, -1))}\n\n--- 프롬프트 ---\n${prompt}`);
    process.exit(0);
  }

  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.rmSync(outFile, { force: true }); // 실패했을 때 지난번 보고서를 이번 결과로 착각하지 않게
  const before = changedSnapshot();
  console.log(`▶ Codex(${role}) 실행 [${opts.model} · 추론 ${opts.effort}]: ${opts.request}\n`);
  // codex exec는 진행 로그를 stderr로 낸다 → 실시간으로 그대로 보여주면서 끝부분(64KB)만 보관해 한도 오류를 확인한다
  // stdin은 넘기지 않는다(ignore): 입력이 안 닫힌 환경에서 "Reading additional input from stdin..." 무한 대기 방지
  const child = spawn(codex, args, { stdio: ["ignore", "inherit", "pipe"] });
  let tail = "";
  child.stderr.setEncoding("utf8");
  child.stderr.on("data", (chunk) => {
    process.stderr.write(chunk);
    tail = (tail + chunk).slice(-64_000);
  });
  child.on("error", (e) => console.error(`Codex 실행 실패: ${e.message}`));
  child.on("close", (status) => finish(status));

  function finish(status) {
    // 실패했을 때만, Codex의 실제 한도 오류 문구로 판정 (stderr엔 Codex가 읽은 파일 내용도 섞일 수 있음)
    const limitHit = status !== 0 && /You[’']ve hit your usage limit|usage_limit_reached/i.test(tail);
    report(status, limitHit);
  }

  function report(status, limitHit) {
    const after = changedSnapshot();
    // 앞뒤 합집합으로 비교: 새로 생긴 파일 + 내용이 바뀐 파일 + 사라진 파일
    const keys = new Set([...before.keys(), ...after.keys()]);
    const changed = [...keys].filter((f) => before.get(f) !== after.get(f));
    const outOfScope = changed.filter((f) => !allowed.some((re) => re.test(f)));

    console.log(`\n─── ${role} 실행 결과 ───`);
    console.log(`종료 코드: ${status}`);
    console.log(`새로 바뀐 파일(${changed.length}): ${changed.join(", ") || "없음"}`);
    if (outOfScope.length) console.log(`⚠ 허용 범위 밖 변경: ${outOfScope.join(", ")} — 메인이 검토 후 되돌릴지 결정`);
    if (limitHit) console.log("⛔ Codex 사용량 한도에 걸렸어요 — 보고서 없음. 메인(Claude)이 직접 리뷰로 대체하세요.");
    if (fs.existsSync(outFile)) console.log(`보고서: ${path.relative(REPO, outFile)}`);
    else console.log("보고서: 없음(이번 실행이 끝까지 가지 못함)");
    console.log(`다음 단계(메인): ${nextStep}`);
    process.exit(status ?? 1);
  }
}
