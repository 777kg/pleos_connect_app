// 이미지(GPT/Codex 이미지 생성): 소모품 원본 이미지를 만들어 docs/assets/consumables/<이름>.png(커밋 전 .webp로 줄여 보관)로 저장한다.
// 만든 원본은 scripts/images/make_step_images.py로 앱용 11단계 이미지(res/drawable-nodpi)로 바꾼다.
//
// 사용 (pleos-connect/ 에서):
//   npm run image:codex -- --name oil "엔진오일 통"
//   npm run image:codex -- --name tire --name filter "…"   (이름 여러 개면 각각 한 장씩, 설명은 이름별로 Codex가 정함)
//   npm run image:codex -- --dry-run --name oil "…"
// 보고서: .codex-out/image-last.md
import { parseArgs, runCodex } from "./runner.mjs";

let argv = process.argv.slice(2);
const names = [];
for (let i = argv.indexOf("--name"); i >= 0; i = argv.indexOf("--name")) {
  names.push(argv[i + 1]);
  argv = argv.filter((_, j) => j !== i && j !== i + 1);
}
const opts = parseArgs(argv);
if (!names.length || names.some((n) => !/^[a-z][a-z0-9_]*$/.test(n ?? ""))) {
  console.error('--name <영문 소문자 키>가 필요합니다. 예) npm run image:codex -- --name oil "엔진오일 통"');
  process.exit(1);
}

const OUT = "docs/assets/consumables";
const ALLOWED = [/^\.codex-out\//, new RegExp(`^${OUT}/`)];

const prompt = [
  "너는 이 폴더(Pleos Connect 차량 앱 '소모품 교체 거리 기록')의 이미지 담당이다. 코드·문서는 고치지 않는다.",
  `이미지 생성 도구로 아래 소모품마다 한 장씩 만들어 \`${OUT}/<이름>.png\`로 저장해라: ${names.join(", ")}.`,
  "공통 스타일(모든 장이 같은 세트처럼 보이게):",
  "- 실사와 애니메이션의 중간: 반실사 3D 일러스트(부드러운 셰이딩, 깔끔한 윤곽, 사진처럼 지저분한 디테일 없음).",
  "- 정사각형 1024×1024, 대상 하나를 가운데에 크게(가로세로 약 70%), 약간 위에서 본 3/4 시점, 부드러운 스튜디오 조명과 바닥 그림자.",
  "- 배경은 어두운 차콜 단색(#1C1C1E 근처)으로 단순하게. 어두운 차량 화면에서 잘 보이도록 대상은 밝고 채도 있게.",
  "- 글자·숫자·로고·상표·특정 브랜드 형태 금지. 사람·손 없음.",
  "- 앱에서 위쪽부터 회색으로 덮어 '사용한 만큼'을 표시하므로, 대상이 화면 위아래로 고르게 차지하도록 구도를 잡아라.",
  "저장 후 각 파일의 경로·크기를 확인하고, 이미지 생성 도구를 쓸 수 없으면 만들지 말고 그 이유만 보고해라.",
  "",
  "## 요청 (메인이 전달)",
  opts.request || "(추가 설명 없음)",
].join("\n");

runCodex({
  role: "image",
  prompt,
  allowed: ALLOWED,
  opts,
  nextStep: `python scripts/images/make_step_images.py --${names[0]} ${OUT}/${names[0]}.png … 로 11단계 이미지 생성`,
});
