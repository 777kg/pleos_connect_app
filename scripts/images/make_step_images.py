"""소모품 단계 사진 만들기 (PLAN.md 이미지 목록, UI_SPEC §0 판정 4).

원본 사진 1장(항목마다)에서 잔여 100·90·…·10·0% 단계 이미지 11장을 만듭니다.
쓴 만큼 위에서 아래로 회색이 내려옵니다: 단계 N%면 위쪽 (100−N)%가 회색, 0%는 전체 회색.
결과는 `PleosApp/app/src/main/res/drawable-nodpi/<key>_<NNN>.webp` → 앱의 ConsumableImages가 이름으로 찾아 씁니다.

원본 사진은 사용 라이선스가 확인되고 브랜드 로고가 없는 것만 씁니다(README '출시 전 승인·확인').

사용 (저장소 루트에서, Pillow 필요):
  python scripts/images/make_step_images.py --oil 사진.jpg --tire 사진.jpg --filter 사진.jpg
  python scripts/images/make_step_images.py --oil 사진.jpg --size 512 --quality 85
  python scripts/images/make_step_images.py --unknown      (공통 중립 이미지 consumable_unknown.webp도 생성)
  python scripts/images/make_step_images.py --from-dir docs/assets/consumables   (폴더의 <키>.png·.webp 전부, 키는 파일 이름)

중립 이미지를 webp로 만들면 같은 이름의 벡터 자리표시(res/drawable/consumable_unknown.xml)는 지워야 빌드가 됩니다.
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageOps

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "PleosApp/app/src/main/res/drawable-nodpi"
STEPS = range(0, 101, 10)


def square(photo: Image.Image, size: int) -> Image.Image:
    """가운데를 정사각형으로 잘라 size×size로 맞춥니다."""
    return ImageOps.fit(photo.convert("RGB"), (size, size), Image.LANCZOS)


# 쓴 부분 밝기 배율. 흑백만으로는 원래 무채색인 소모품(타이어·와이퍼·디스크 등)에서 단계가 안 보여 어둡게도 덮습니다.
USED_BRIGHTNESS = 0.4


def step_image(color: Image.Image, step: int) -> Image.Image:
    """단계 N%: 위쪽 (100−N)% 높이를 흑백으로 바꾸고 어둡게 덮습니다. 경계에는 얇은 밝은 선을 그어 남은 높이를 알립니다."""
    used_part = ImageEnhance.Brightness(ImageOps.grayscale(color).convert("RGB")).enhance(USED_BRIGHTNESS)
    out = color.copy()
    used = round(color.height * (100 - step) / 100)
    if used > 0:
        out.paste(used_part.crop((0, 0, color.width, used)), (0, 0))
    if 0 < used < color.height:
        line = max(2, color.height // 192)
        ImageDraw.Draw(out).rectangle([0, used - line // 2, color.width, used + line // 2], fill=(200, 200, 200))
    return out


def neutral_image(size: int) -> Image.Image:
    """계산 불가 공통 중립 이미지: 어두운 바탕에 사선 무늬(사진 아님)."""
    img = Image.new("RGB", (size, size), (46, 46, 46))
    draw = ImageDraw.Draw(img)
    gap = size // 6
    for offset in range(-size, size * 2, gap):
        draw.line([(offset, size), (offset + size, 0)], fill=(110, 110, 110), width=max(2, size // 16))
    return img


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--oil", type=Path, help="엔진오일 원본 사진")
    parser.add_argument("--tire", type=Path, help="타이어 원본 사진")
    parser.add_argument("--filter", type=Path, help="에어컨 필터 원본 사진")
    parser.add_argument("--from-dir", type=Path, help="이 폴더의 <키>.png·.webp를 모두 변환(키 = 파일 이름, 예: brake_pad.webp)")
    parser.add_argument("--unknown", action="store_true", help="공통 중립 이미지도 만들기")
    parser.add_argument("--size", type=int, default=512, help="한 변 픽셀(기본 512, 최종 해상도는 확인 필요)")
    parser.add_argument("--quality", type=int, default=85, help="WebP 품질(기본 85)")
    args = parser.parse_args()

    sources = {"oil": args.oil, "tire": args.tire, "filter": args.filter}
    if args.from_dir:
        for src in sorted([*args.from_dir.glob("*.png"), *args.from_dir.glob("*.webp")]):
            sources[src.stem] = sources.get(src.stem) or src
    if not any(sources.values()) and not args.unknown:
        parser.error("원본 사진(--oil/--tire/--filter) 또는 --unknown 중 하나는 필요합니다.")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    written = []
    for key, path in sources.items():
        if path is None:
            continue
        if not path.is_file():
            print(f"원본 사진이 없습니다: {path}", file=sys.stderr)
            return 1
        color = square(Image.open(path), args.size)
        for step in STEPS:
            target = OUT_DIR / f"{key}_{step:03d}.webp"
            step_image(color, step).save(target, "WEBP", quality=args.quality)
            written.append(target)

    if args.unknown:
        target = OUT_DIR / "consumable_unknown.webp"
        neutral_image(args.size).save(target, "WEBP", quality=args.quality)
        written.append(target)
        vector = ROOT / "PleosApp/app/src/main/res/drawable/consumable_unknown.xml"
        if vector.exists():
            print(f"주의: {vector.relative_to(ROOT)}를 지워야 같은 이름 리소스 충돌 없이 빌드됩니다.")

    for path in written:
        print(path.relative_to(ROOT))
    print(f"{len(written)}개 생성")
    return 0


if __name__ == "__main__":
    sys.exit(main())
