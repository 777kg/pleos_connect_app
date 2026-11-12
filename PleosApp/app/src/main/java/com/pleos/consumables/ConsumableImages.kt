package com.pleos.consumables

import android.content.Context

/**
 * 항목 상태에 맞는 사진(PLAN.md 이미지 목록, UI_SPEC §0 판정 4·5).
 * - 계산 가능(Normal): 단계 사진 `<key>_<단계 3자리>`(예: oil_060, res/drawable-nodpi)
 * - 계산 불가: 공통 중립 이미지 `consumable_unknown`
 * - 홈(S05) 타일과 상세(S01)는 계산 불가여도 항목 사진(100% 단계)을 흐리게 보여 줍니다([homeDrawableFor], DECISIONS.md 2026-11-09).
 *
 * 단계 사진은 scripts/images/make_step_images.py로 만들어 넣습니다. 사진이 아직 없는 항목은 중립 이미지로 대신합니다.
 */
object ConsumableImages {

    fun drawableFor(context: Context, item: Consumable, status: ConsumableStatus): Int {
        val normal = status as? ConsumableStatus.Normal ?: return R.drawable.consumable_unknown
        return step(context, item, normal.remaining.stepPercent)
    }

    /** 홈 타일·상세: 계산 가능하면 단계 사진, 아니면 대표 사진. 계산 불가는 [imageAlpha]로 흐리게 그립니다. */
    fun homeDrawableFor(context: Context, item: Consumable, status: ConsumableStatus): Int =
        if (status is ConsumableStatus.Normal) drawableFor(context, item, status) else thumbnail(context, item)

    /** 잔여%를 계산할 수 없을 때 대표 사진을 40% 불투명도로 흐리게(ImageView.imageAlpha, 0~255). */
    fun imageAlpha(status: ConsumableStatus): Int = if (status is ConsumableStatus.Normal) 255 else 102

    /** 설정(S07) 줄에 쓰는 대표 사진: 100% 단계. */
    fun thumbnail(context: Context, item: Consumable): Int = step(context, item, 100)

    private fun step(context: Context, item: Consumable, percent: Int): Int {
        val name = "%s_%03d".format(item.imageKey, percent)
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (id != 0) id else R.drawable.consumable_unknown
    }
}
