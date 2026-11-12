package com.pleos.consumables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenCodecTest {

    @Test fun everyScreenRoundTrips() {
        val screens = listOf(
            Screen.Home,
            Screen.Detail(Consumable.OIL),
            Screen.History(Consumable.TIRE_FL),
            Screen.History(Consumable.TIRE_FL, from = Consumable.OIL),
            Screen.Edit(Consumable.FILTER, 42L),
            Screen.Edit(Consumable.FILTER, 42L, from = Consumable.TIRE_FL),
            Screen.Confirm(Consumable.OIL),
            Screen.Baseline(Consumable.TIRE_FL, Screen.Detail(Consumable.TIRE_FL)),
            Screen.Baseline(Consumable.OIL, Screen.Confirm(Consumable.OIL)),
        )
        screens.forEach { assertEquals(it, ScreenCodec.decode(ScreenCodec.encode(it))) }
    }

    /** QA-016 이전에 저장된 형식(S02를 연 항목 없음)은 보는 항목을 연 항목으로 읽습니다. */
    @Test fun oldFormatWithoutOriginStillDecodes() {
        assertEquals(Screen.History(Consumable.TIRE_FL, Consumable.TIRE_FL), ScreenCodec.decode("history:tire_fl"))
        assertEquals(Screen.Edit(Consumable.OIL, 3L, Consumable.OIL), ScreenCodec.decode("edit:oil:3"))
    }

    @Test fun unknownTextIsIgnored() {
        assertNull(ScreenCodec.decode("detail:brake"))
        assertNull(ScreenCodec.decode("edit:oil:x"))
        assertNull(ScreenCodec.decode(""))
    }
}
