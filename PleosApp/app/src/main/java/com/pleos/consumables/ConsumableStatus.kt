package com.pleos.consumables

import kotlin.math.ceil
import kotlin.math.roundToInt

/** 항목 하나의 표시 판정(UI_SPEC §0 데이터·판정, 공통 상태 표). S01·S05가 같은 결과를 씁니다. */
sealed interface ConsumableStatus {
    /** 차량 값을 기다리는 중. */
    data object Loading : ConsumableStatus

    /** 미지원·권한 없음·값 미수신. 새 기록 차단. */
    data class Unavailable(val reading: OdometerReading) : ConsumableStatus

    data object NoRecord : ConsumableStatus

    /** 현재 값 < 마지막 기록 값. 경과·잔여 숨김, 새 기록 차단, 기록 보존. */
    data class ComparisonBlocked(val last: ReplacementRecord) : ConsumableStatus

    /** 경과 거리는 계산되지만 기준 거리가 없어 잔여%는 숨깁니다. */
    data class BaselineUnset(val last: ReplacementRecord, val elapsedKm: Float) : ConsumableStatus

    data class Normal(
        val last: ReplacementRecord,
        val elapsedKm: Float,
        val baselineKm: Int,
        val remaining: Remaining,
    ) : ConsumableStatus

    /** 새 교체 기록을 받을 수 있는지(S01-07, S04-05). 기준 거리 미설정만으로는 막지 않습니다. */
    val canAddRecord: Boolean
        get() = this is NoRecord || this is BaselineUnset || this is Normal
}

/**
 * 잔여 비율.
 * - [exactPercent]: `100 × max(0, 1 − 경과/기준)`
 * - [displayPercent]: 정수 반올림. 정확히 0·100일 때만 0%·100%, 나머지는 1~99%
 * - [stepPercent]: 단계 이미지. `ceil(exact/10)×10`, 실제 0%만 0
 */
data class Remaining(val exactPercent: Double, val displayPercent: Int, val stepPercent: Int) {
    val reached: Boolean get() = exactPercent == 0.0
}

object ConsumableCalculator {

    const val BASELINE_MIN_KM = 100
    const val BASELINE_MAX_KM = 100_000
    private const val STEP_EPSILON = 1e-6

    /** 교체 시각이 가장 늦은 기록, 같은 시각이면 나중에 저장한 기록. */
    fun lastRecord(records: List<ReplacementRecord>): ReplacementRecord? =
        records.maxWithOrNull(compareBy<ReplacementRecord> { it.replacedAtMillis }.thenBy { it.saveOrder })

    fun status(reading: OdometerReading, records: List<ReplacementRecord>, baselineKm: Int?): ConsumableStatus {
        val current = when (reading) {
            OdometerReading.Checking -> return ConsumableStatus.Loading
            is OdometerReading.Received -> reading.km
            else -> return ConsumableStatus.Unavailable(reading)
        }
        val last = lastRecord(records) ?: return ConsumableStatus.NoRecord
        // 차량 변경·계기판 교체·초기화를 구분할 수 없으므로 자동 연결하지 않습니다(DECISIONS 2026-10-28).
        if (current < last.recordKm) return ConsumableStatus.ComparisonBlocked(last)
        val elapsed = current - last.recordKm
        if (baselineKm == null) return ConsumableStatus.BaselineUnset(last, elapsed)
        return ConsumableStatus.Normal(last, elapsed, baselineKm, remaining(elapsed, baselineKm))
    }

    fun remaining(elapsedKm: Float, baselineKm: Int): Remaining {
        val exact = 100.0 * maxOf(0.0, 1.0 - elapsedKm.toDouble() / baselineKm)
        val display = when (exact) {
            0.0 -> 0
            100.0 -> 100
            else -> exact.roundToInt().coerceIn(1, 99)
        }
        // 부동소수 오차(예: 90.00000000000001)로 한 단계 올라가지 않게 작은 여유를 뺍니다.
        val step = if (exact == 0.0) 0 else (ceil((exact - STEP_EPSILON) / 10.0) * 10).toInt().coerceIn(10, 100)
        return Remaining(exact, display, step)
    }

    fun isValidBaseline(km: Int) = km in BASELINE_MIN_KM..BASELINE_MAX_KM

    /** S06 빠른 선택(S06-09). 권장값이 아닌 사용자 선택지입니다(PLAN.md §8 질문 14). */
    val BASELINE_PRESETS_KM = listOf(5_000, 10_000, 15_000, 20_000)

    /** 내 평균 간격으로 채울 때(S06-10): 100 km 단위로 반올림하고 저장 가능한 범위로 맞춥니다. */
    fun baselineFromAverage(averageKm: Int): Int =
        ((averageKm + 50) / 100 * 100).coerceIn(BASELINE_MIN_KM, BASELINE_MAX_KM)

    // ── S02 이력·S03 기록 수정 ──

    const val RECORD_STEP_KM = 100f
    /** 차량 값을 모를 때 기록 거리의 상한(Virtual Console 범위 0~1,000,000 km). */
    const val RECORD_MAX_KM = 1_000_000f

    /** 교체 시각은 최대 10년 전까지 고칠 수 있습니다(DECISIONS 2026-11-10 결정 8). */
    const val RECORD_MAX_PAST_MS = 10L * 365 * 24 * 60 * 60 * 1000

    /** 이력 목록 순서: 교체 시각 최신 먼저, 같은 시각이면 나중에 저장한 기록 먼저(마지막 기록이 맨 위). */
    fun historyOrder(records: List<ReplacementRecord>): List<ReplacementRecord> =
        records.sortedWith(compareByDescending<ReplacementRecord> { it.replacedAtMillis }.thenByDescending { it.saveOrder })

    /**
     * 수정할 수 있는 기록 거리의 상한: 현재 누적 주행거리(결정 8). 차량 값을 모르면 [RECORD_MAX_KM].
     * 원래 기록이 상한보다 큰 경우(거리 비교 불가, 1,000,000 km 초과 포함)에도 시각만 고칠 수 있게 원래 값까지는 허용합니다(QA-022).
     */
    fun editMaxKm(currentKm: Float?, originalKm: Float): Float =
        maxOf(currentKm?.coerceAtMost(RECORD_MAX_KM) ?: RECORD_MAX_KM, originalKm)

    fun isValidRecordKm(km: Float, maxKm: Float = RECORD_MAX_KM) = km in 0f..maxKm

    /** 교체 시각은 미래로 고칠 수 없고, 최대 10년 전까지만 고칩니다. */
    fun isValidReplacedAt(millis: Long, nowMillis: Long) = millis in (nowMillis - RECORD_MAX_PAST_MS)..nowMillis

    // ── S05 홈 요약·임박 순 보기(PLAN.md §3 기능 6) ──

    /** 목록 정렬 묶음: 정상 → 기준 거리 미설정 → 기록 없음 → 거리 비교 불가 → 조회 중·조회 불가. */
    private fun urgencyGroup(status: ConsumableStatus): Int = when (status) {
        is ConsumableStatus.Normal -> 0
        is ConsumableStatus.BaselineUnset -> 1
        ConsumableStatus.NoRecord -> 2
        is ConsumableStatus.ComparisonBlocked -> 3
        ConsumableStatus.Loading, is ConsumableStatus.Unavailable -> 4
    }

    /**
     * 임박 순 목록. 정상끼리는 실제 잔여%(반올림 전)가 낮은 순, 나머지는 묶음 순서이며
     * 같은 묶음 안에서는 [items]의 순서(카탈로그 순)를 지킵니다. 권고가 아니라 사용자 기준 거리의 진행 순서입니다.
     */
    fun urgencyOrder(items: List<Consumable>, statusOf: (Consumable) -> ConsumableStatus): List<Consumable> {
        val statuses = items.associateWith(statusOf)
        return items.sortedWith(
            compareBy<Consumable> { urgencyGroup(statuses.getValue(it)) }
                .thenBy { (statuses.getValue(it) as? ConsumableStatus.Normal)?.remaining?.exactPercent ?: 0.0 },
        )
    }

    /** 홈 `가장 임박` 요약: 정상 상태 중 실제 잔여%가 가장 낮은 항목. 정상 항목이 없으면 null. */
    fun mostUrgent(items: List<Consumable>, statusOf: (Consumable) -> ConsumableStatus): Pair<Consumable, ConsumableStatus.Normal>? =
        urgencyOrder(items, statusOf).firstNotNullOfOrNull { item ->
            (statusOf(item) as? ConsumableStatus.Normal)?.let { item to it }
        }

    // ── S02 내 교체 주기 통계(PLAN.md §3 기능 7) ──

    /** 1개월 근사(평균 그레고리력 30.44일). */
    private const val MONTH_MILLIS = 30.44 * 24 * 60 * 60 * 1000

    /**
     * 교체 횟수와 평균 교체 간격.
     * - [averageKm]·[averageMonths]: 교체 시각 순으로 이웃한 기록 사이 간격의 평균. 기록이 2건 미만이거나 쓸 간격이 없으면 null.
     *   [averageMonths]는 반올림하지 않은 값입니다. `1개월 미만` 판단과 표시 반올림은 화면에서 합니다(QA-019).
     * - [sameKmIntervals]: 기록 거리가 같은 간격. [decreasedKmIntervals]: 기록 거리가 줄어든 간격(주행거리 초기화·차량 변경 가능성).
     *   둘 다 평균에서 빼고, 화면에는 나눠서 알립니다(QA-018).
     */
    data class IntervalStats(
        val count: Int,
        val averageKm: Int?,
        val averageMonths: Double?,
        val sameKmIntervals: Int = 0,
        val decreasedKmIntervals: Int = 0,
    ) {
        val excludedIntervals: Int get() = sameKmIntervals + decreasedKmIntervals
    }

    fun intervalStats(records: List<ReplacementRecord>): IntervalStats {
        val sorted = records.sortedWith(compareBy<ReplacementRecord> { it.replacedAtMillis }.thenBy { it.saveOrder })
        val pairs = sorted.zipWithNext()
        val usable = pairs.filter { (a, b) -> b.recordKm > a.recordKm }
        val averageKm = usable.takeIf { it.isNotEmpty() }
            ?.map { (a, b) -> (b.recordKm - a.recordKm).toDouble() }?.average()?.roundToInt()
        val averageMonths = usable.takeIf { it.isNotEmpty() }
            ?.map { (a, b) -> (b.replacedAtMillis - a.replacedAtMillis) / MONTH_MILLIS }?.average()
        return IntervalStats(
            count = records.size,
            averageKm = averageKm,
            averageMonths = averageMonths,
            sameKmIntervals = pairs.count { (a, b) -> b.recordKm == a.recordKm },
            decreasedKmIntervals = pairs.count { (a, b) -> b.recordKm < a.recordKm },
        )
    }
}
