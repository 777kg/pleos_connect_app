package com.pleos.consumables

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoActionsTest {

    private val names = mapOf(Consumable.TIRE to "타이어", Consumable.FILTER to "에어컨 필터", Consumable.WIPER to "와이퍼")
    private val nameOf: (Consumable) -> String = { names[it] ?: it.key }

    private fun record(km: Float) = ReplacementRecord(1, Consumable.TIRE, 0L, km, 0L, km, false, 1)

    @Test
    fun `param reads plain and double-quoted ACTION_PARAM`() {
        assertEquals("tire", GleoActions.param("""{"item":"tire"}""", "item"))
        // 문서 예제처럼 바깥 따옴표로 한 번 더 감싼 형태
        assertEquals("tire", GleoActions.param("\"{\"item\":\"tire\"}\"", "item"))
        assertNull(GleoActions.param("not json", "item"))
        assertNull(GleoActions.param(null, "item"))
        assertNull(GleoActions.param("{}", "item"))
    }

    @Test
    fun `itemOf maps voice keys, storage keys and Korean names`() {
        assertEquals(Consumable.FILTER, GleoActions.itemOf("cabin_air_filter", nameOf))
        assertEquals(Consumable.AIR_FILTER, GleoActions.itemOf("engine_air_filter", nameOf))
        assertEquals(Consumable.TIRE, GleoActions.itemOf("TIRE", nameOf))
        assertEquals(Consumable.FILTER, GleoActions.itemOf("에어컨필터", nameOf))
        assertNull(GleoActions.itemOf("", nameOf))
        assertNull(GleoActions.itemOf("rocket", nameOf))
    }

    @Test
    fun `every consumable has exactly one voice key`() {
        assertEquals(Consumable.entries.toSet(), GleoActions.voiceKeys.values.toSet())
        assertEquals(Consumable.entries.size, GleoActions.voiceKeys.size)
    }

    @Test
    fun `status answer for normal and reached`() {
        val normal = ConsumableCalculator.status(OdometerReading.Received(13_000f, 0L), listOf(record(10_000f)), 10_000)
        val a = GleoActions.statusAnswer("타이어", normal)
        assertEquals("success", a.status)
        assertEquals(
            "타이어 has been driven 3,000 km since the last replacement, with 70% of your 10,000 km distance left.",
            a.message,
        )
        val reached = ConsumableCalculator.status(OdometerReading.Received(25_000f, 0L), listOf(record(10_000f)), 10_000)
        assertTrue(GleoActions.statusAnswer("타이어", reached).message.contains("reaching your 10,000 km distance"))
    }

    @Test
    fun `status answer for no record, blocked, unset, unavailable`() {
        val received = OdometerReading.Received(5_000f, 0L)
        assertEquals(
            "There is no replacement record for 타이어.",
            GleoActions.statusAnswer("타이어", ConsumableCalculator.status(received, emptyList(), null)).message,
        )
        assertTrue(
            GleoActions.statusAnswer("타이어", ConsumableCalculator.status(received, listOf(record(9_000f)), null))
                .message.contains("cannot be compared"),
        )
        assertTrue(
            GleoActions.statusAnswer("타이어", ConsumableCalculator.status(received, listOf(record(1_000f)), null))
                .message.contains("no replacement distance is set"),
        )
        assertEquals(
            "fail",
            GleoActions.statusAnswer("타이어", ConsumableCalculator.status(OdometerReading.Failed, emptyList(), null)).status,
        )
    }

    @Test
    fun `list answer keeps order and caps at three`() {
        fun normal(left: Float) = ConsumableCalculator.status(
            OdometerReading.Received(10_000f - left, 0L), listOf(record(0f)), 10_000,
        ) as ConsumableStatus.Normal
        val ranked = listOf("A" to normal(1_000f), "B" to normal(2_000f), "C" to normal(5_000f), "D" to normal(9_000f))
        val a = GleoActions.listAnswer(ranked, odometerRead = true)
        assertEquals("By the distances you set: A 10% left, B 20% left, C 50% left.", a.message)
        assertEquals(3, a.data.size)
        assertEquals("fail", GleoActions.listAnswer(ranked, odometerRead = false).status)
        assertEquals("success", GleoActions.listAnswer(emptyList(), odometerRead = true).status)
    }

    @Test
    fun `callback json carries data as a JSON array string`() {
        val json = JSONObject(GleoActions.openAnswer("타이어", parked = true).toJson())
        assertEquals("OPEN_CONSUMABLE_RECORD", json.getString("functionName"))
        assertEquals("success", json.getString("status"))
        val data = JSONArray(json.getString("data"))
        assertEquals("타이어", data.getJSONObject(0).getString("item"))
        assertTrue(GleoActions.openAnswer("타이어", parked = false).message.contains("once the vehicle is parked"))
        // data가 없으면 필드를 넣지 않습니다.
        assertTrue(!JSONObject(GleoActions.drivingAnswer("X").toJson()).has("data"))
    }
}
