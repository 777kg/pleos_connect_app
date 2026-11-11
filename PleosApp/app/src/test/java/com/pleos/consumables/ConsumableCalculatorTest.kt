package com.pleos.consumables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumableCalculatorTest {

    private fun record(id: Long, km: Float, at: Long, order: Long = id) = ReplacementRecord(
        id = id, item = Consumable.OIL, replacedAtMillis = at, vehicleKm = km,
        receivedAtMillis = at, recordKm = km, edited = false, saveOrder = order,
    )

    private fun received(km: Float) = OdometerReading.Received(km, 0L)

    @Test fun lastRecordIsLatestReplacementTimeThenLaterSave() {
        val a = record(1, 1000f, at = 200, order = 1)
        val b = record(2, 900f, at = 100, order = 2)
        val c = record(3, 1100f, at = 200, order = 3)
        assertEquals(c, ConsumableCalculator.lastRecord(listOf(a, b, c)))
    }

    @Test fun readingStatesComeFirst() {
        assertEquals(ConsumableStatus.Loading, ConsumableCalculator.status(OdometerReading.Checking, emptyList(), 5000))
        val s = ConsumableCalculator.status(OdometerReading.PermissionDenied, listOf(record(1, 0f, 0)), 5000)
        assertEquals(ConsumableStatus.Unavailable(OdometerReading.PermissionDenied), s)
        assertFalse(s.canAddRecord)
    }

    @Test fun noRecordAllowsFirstRecord() {
        val s = ConsumableCalculator.status(received(10f), emptyList(), null)
        assertEquals(ConsumableStatus.NoRecord, s)
        assertTrue(s.canAddRecord)
    }

    @Test fun currentBelowLastBlocksComparisonAndNewRecord() {
        val last = record(1, 5000f, 0)
        val s = ConsumableCalculator.status(received(4999f), listOf(last), 5000)
        assertEquals(ConsumableStatus.ComparisonBlocked(last), s)
        assertFalse(s.canAddRecord)
    }

    @Test fun missingBaselineStillShowsElapsedAndAllowsRecord() {
        val last = record(1, 1000f, 0)
        val s = ConsumableCalculator.status(received(1500f), listOf(last), null)
        assertEquals(ConsumableStatus.BaselineUnset(last, 500f), s)
        assertTrue(s.canAddRecord)
    }

    @Test fun normalComputesRemaining() {
        val s = ConsumableCalculator.status(received(4000f), listOf(record(1, 1000f, 0)), 10_000)
        s as ConsumableStatus.Normal
        assertEquals(3000f, s.elapsedKm)
        assertEquals(70, s.remaining.displayPercent)
        assertEquals(70, s.remaining.stepPercent)
    }

    @Test fun displayPercentKeepsZeroAndHundredExact() {
        assertEquals(100, ConsumableCalculator.remaining(0f, 10_000).displayPercent)
        assertEquals(99, ConsumableCalculator.remaining(1f, 10_000).displayPercent) // 99.99%
        assertEquals(1, ConsumableCalculator.remaining(9_999f, 10_000).displayPercent) // 0.01%
        assertEquals(0, ConsumableCalculator.remaining(10_000f, 10_000).displayPercent)
        assertEquals(0, ConsumableCalculator.remaining(12_000f, 10_000).displayPercent)
    }

    @Test fun stepImageRoundsUpAndZeroOnlyWhenReached() {
        val r = ConsumableCalculator::remaining
        assertEquals(100, r(0f, 10_000).stepPercent)
        assertEquals(100, r(500f, 10_000).stepPercent) // 95%
        assertEquals(90, r(1_000f, 10_000).stepPercent) // 90%
        assertEquals(10, r(9_000f, 10_000).stepPercent) // 10%
        assertEquals(10, r(9_999f, 10_000).stepPercent) // 0.01%
        assertEquals(0, r(10_000f, 10_000).stepPercent)
        assertTrue(r(10_000f, 10_000).reached)
        assertFalse(r(9_999f, 10_000).reached)
    }

    @Test fun historyIsNewestFirstWithLastRecordOnTop() {
        val a = record(1, 1000f, at = 100, order = 1)
        val b = record(2, 2000f, at = 300, order = 2)
        val c = record(3, 1500f, at = 300, order = 3)
        val ordered = ConsumableCalculator.historyOrder(listOf(a, b, c))
        assertEquals(listOf(c, b, a), ordered)
        assertEquals(ConsumableCalculator.lastRecord(listOf(a, b, c)), ordered.first())
    }

    @Test fun editedTimeChangesLastRecord() {
        val a = record(1, 1000f, at = 100, order = 1)
        val b = record(2, 2000f, at = 200, order = 2)
        // a의 교체 시각을 b보다 늦게 고치면 a가 마지막 기록이 됩니다.
        val editedA = a.copy(replacedAtMillis = 300, edited = true)
        assertEquals(editedA, ConsumableCalculator.lastRecord(listOf(editedA, b)))
    }

    @Test fun editLimits() {
        assertTrue(ConsumableCalculator.isValidRecordKm(0f))
        assertTrue(ConsumableCalculator.isValidRecordKm(1_000_000f))
        assertFalse(ConsumableCalculator.isValidRecordKm(-100f))
        assertFalse(ConsumableCalculator.isValidRecordKm(1_000_100f))
        assertTrue(ConsumableCalculator.isValidReplacedAt(1_000, nowMillis = 1_000))
        assertFalse(ConsumableCalculator.isValidReplacedAt(1_001, nowMillis = 1_000))
        val now = 2_000_000_000_000L
        assertTrue(ConsumableCalculator.isValidReplacedAt(now - ConsumableCalculator.RECORD_MAX_PAST_MS, now))
        assertFalse(ConsumableCalculator.isValidReplacedAt(now - ConsumableCalculator.RECORD_MAX_PAST_MS - 1, now))
        // 거리 상한은 현재 누적(모르면 1,000,000), 원래 기록이 더 크면 원래 값까지
        assertEquals(20_000f, ConsumableCalculator.editMaxKm(20_000f, 15_000f))
        assertEquals(25_000f, ConsumableCalculator.editMaxKm(20_000f, 25_000f))
        assertEquals(ConsumableCalculator.RECORD_MAX_KM, ConsumableCalculator.editMaxKm(null, 15_000f))
        assertFalse(ConsumableCalculator.isValidRecordKm(20_100f, maxKm = 20_000f))
        // QA-022: 원래 기록이 1,000,000 km를 넘어도 원래 값까지는 허용(시각만 수정 가능)
        assertEquals(1_200_000f, ConsumableCalculator.editMaxKm(null, 1_200_000f))
        assertEquals(1_200_000f, ConsumableCalculator.editMaxKm(1_500_000f, 1_200_000f))
        assertEquals(ConsumableCalculator.RECORD_MAX_KM, ConsumableCalculator.editMaxKm(1_500_000f, 15_000f))
    }

    @Test fun baselineRange() {
        assertTrue(ConsumableCalculator.isValidBaseline(100))
        assertTrue(ConsumableCalculator.isValidBaseline(100_000))
        assertFalse(ConsumableCalculator.isValidBaseline(0))
        assertFalse(ConsumableCalculator.isValidBaseline(100_100))
    }

    // ── S06 내 평균으로 채우기 ──

    @Test fun baselineFromAverageRoundsTo100AndClamps() {
        assertEquals(5_000, ConsumableCalculator.baselineFromAverage(4_951))
        assertEquals(4_900, ConsumableCalculator.baselineFromAverage(4_949))
        assertEquals(100, ConsumableCalculator.baselineFromAverage(20))
        assertEquals(100_000, ConsumableCalculator.baselineFromAverage(250_000))
    }

    // ── 기능 6: 임박 순 ──

    private fun normal(exact: Double) = ConsumableStatus.Normal(
        record(1, 0f, 0), elapsedKm = 0f, baselineKm = 10_000,
        remaining = Remaining(exact, exact.toInt(), 0),
    )

    @Test fun urgencyOrderPutsLowestRemainingFirstThenGroupsInCatalogOrder() {
        val items = listOf(Consumable.OIL, Consumable.TIRE, Consumable.FILTER, Consumable.WIPER, Consumable.BRAKE_PAD)
        val statuses = mapOf(
            Consumable.OIL to ConsumableStatus.NoRecord,
            Consumable.TIRE to normal(60.0),
            Consumable.FILTER to ConsumableStatus.Unavailable(OdometerReading.Failed),
            Consumable.WIPER to normal(12.5),
            Consumable.BRAKE_PAD to ConsumableStatus.BaselineUnset(record(1, 0f, 0), 10f),
        )
        assertEquals(
            listOf(Consumable.WIPER, Consumable.TIRE, Consumable.BRAKE_PAD, Consumable.OIL, Consumable.FILTER),
            ConsumableCalculator.urgencyOrder(items, statuses::getValue),
        )
        assertEquals(Consumable.WIPER, ConsumableCalculator.mostUrgent(items, statuses::getValue)?.first)
    }

    @Test fun mostUrgentIsNullWithoutNormalItems() {
        val items = listOf(Consumable.OIL, Consumable.TIRE)
        assertEquals(null, ConsumableCalculator.mostUrgent(items) { ConsumableStatus.NoRecord })
    }

    // ── 기능 7: 교체 주기 통계 ──

    private val day = 24L * 60 * 60 * 1000

    @Test fun statsNeedTwoRecordsForAverage() {
        val s = ConsumableCalculator.intervalStats(listOf(record(1, 1000f, day)))
        assertEquals(ConsumableCalculator.IntervalStats(1, null, null), s)
        assertEquals(0, ConsumableCalculator.intervalStats(emptyList()).count)
    }

    @Test fun statsAverageIntervalsInReplacementTimeOrder() {
        // 저장 순서와 무관하게 교체 시각 순으로 이웃을 잡습니다: 1000 → 6000 → 13000 km, 30.44일 → 91.32일
        val records = listOf(
            record(3, 13_000f, at = (3 * 30.44 * day).toLong()),
            record(1, 1_000f, at = 0L),
            record(2, 6_000f, at = (30.44 * day).toLong()),
        )
        val s = ConsumableCalculator.intervalStats(records)
        assertEquals(3, s.count)
        assertEquals(6_000, s.averageKm)
        assertEquals(1.5, s.averageMonths!!, 0.001)
        assertEquals(0, s.excludedIntervals)
    }

    /** QA-019: 평균 29일은 반올림하면 1.0개월이지만 실제로는 1개월 미만입니다. 반올림하지 않은 값을 넘깁니다. */
    @Test fun statsKeepUnroundedMonths() {
        val s = ConsumableCalculator.intervalStats(listOf(record(1, 1_000f, 0L), record(2, 2_000f, 29 * day)))
        assertTrue(s.averageMonths!! < 1.0)
        assertEquals(29.0 / 30.44, s.averageMonths!!, 1e-9)
    }

    @Test fun statsSkipIntervalsWhereDistanceDidNotIncrease() {
        val records = listOf(record(1, 50_000f, 0L), record(2, 1_000f, day), record(3, 4_000f, 2 * day))
        val s = ConsumableCalculator.intervalStats(records)
        assertEquals(3_000, s.averageKm)
        assertEquals(1, s.decreasedKmIntervals)
        assertEquals(0, s.sameKmIntervals)
    }

    /** QA-018: 같은 거리 간격과 줄어든 거리 간격을 나눠 셉니다. */
    @Test fun statsSeparateSameAndDecreasedIntervals() {
        val records = listOf(
            record(1, 5_000f, 0L), record(2, 5_000f, day), record(3, 2_000f, 2 * day), record(4, 6_000f, 3 * day),
        )
        val s = ConsumableCalculator.intervalStats(records)
        assertEquals(1, s.sameKmIntervals)
        assertEquals(1, s.decreasedKmIntervals)
        assertEquals(2, s.excludedIntervals)
        assertEquals(4_000, s.averageKm)
    }

    @Test fun statsWithOnlyDecreasingIntervalsHaveNoAverage() {
        val s = ConsumableCalculator.intervalStats(listOf(record(1, 9_000f, 0L), record(2, 9_000f, day)))
        assertEquals(ConsumableCalculator.IntervalStats(2, null, null, sameKmIntervals = 1), s)
    }

    @Test fun rebaselineRecordBecomesLastAndUnblocks() {
        val old = record(1, 20_000f, at = 100)
        assertTrue(ConsumableCalculator.status(received(500f), listOf(old), 10_000) is ConsumableStatus.ComparisonBlocked)
        val rebased = record(2, 500f, at = 200).copy(rebased = true)
        val s = ConsumableCalculator.status(received(1_500f), listOf(old, rebased), 10_000)
        s as ConsumableStatus.Normal
        assertEquals(rebased, s.last)
        assertEquals(1_000f, s.elapsedKm)
    }
}
