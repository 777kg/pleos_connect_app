package com.pleos.consumables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    @Test fun keysAreUniqueAndExistingKeysKept() {
        val keys = Consumable.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        // 기존 기록·기준 거리 저장 키는 바꾸지 않습니다.
        listOf("oil", "tire_fl", "tire_rr", "filter").forEach { assertTrue(it in keys) }
    }

    @Test fun defaultsAreOriginalThreePlusRecommended() {
        assertEquals(
            setOf(Consumable.OIL, Consumable.FILTER, Consumable.BRAKE_PAD, Consumable.WIPER, Consumable.AIR_FILTER) + Consumable.TIRES,
            Consumable.DEFAULT_ENABLED,
        )
    }

    /** 타이어는 위치별 4개이고, 탑뷰에서 왼쪽·오른쪽 열이 정해져 있으며 같은 타이어 사진을 씁니다. */
    @Test fun tiresAreSplitByPosition() {
        assertEquals(4, Consumable.TIRES.size)
        assertEquals(listOf(Consumable.Side.LEFT, Consumable.Side.RIGHT, Consumable.Side.LEFT, Consumable.Side.RIGHT), Consumable.TIRES.map { it.side })
        Consumable.TIRES.forEach { assertEquals("tire", it.imageKey) }
        assertEquals(null, Consumable.fromKey(Consumable.LEGACY_TIRE_KEY))
    }

    @Test fun orderedGoesFrontToRear() {
        val ordered = Consumable.ordered(listOf(Consumable.TIRE_FL, Consumable.FILTER, Consumable.OIL))
        assertEquals(listOf(Consumable.OIL, Consumable.FILTER, Consumable.TIRE_FL), ordered)
    }

    @Test fun fuelTypeMapping() {
        assertEquals(VehicleKind.UNKNOWN, VehicleInfoSource.kindOf(null))
        assertEquals(VehicleKind.UNKNOWN, VehicleInfoSource.kindOf(intArrayOf()))
        assertEquals(VehicleKind.EV, VehicleInfoSource.kindOf(intArrayOf(10)))
        assertEquals(VehicleKind.ICE, VehicleInfoSource.kindOf(intArrayOf(1)))
        assertEquals(VehicleKind.HYBRID, VehicleInfoSource.kindOf(intArrayOf(1, 10)))
    }

    @Test fun notApplicableOnlyForOtherPowertrain() {
        assertTrue(Consumable.OIL.notApplicableTo(VehicleKind.EV))
        assertFalse(Consumable.OIL.notApplicableTo(VehicleKind.HYBRID))
        assertTrue(Consumable.REDUCTION_OIL.notApplicableTo(VehicleKind.ICE))
        assertFalse(Consumable.TIRE_FL.notApplicableTo(VehicleKind.EV))
        assertFalse(Consumable.OIL.notApplicableTo(VehicleKind.UNKNOWN))
    }

    @Test fun visibleItemsFollowVehicleKind() {
        val enabled = setOf(Consumable.OIL, Consumable.TIRE_FL, Consumable.REDUCTION_OIL)
        val ev = AppUiState(enabledItems = enabled, vehicleKind = VehicleKind.EV).visibleItems
        assertEquals(listOf(Consumable.TIRE_FL, Consumable.REDUCTION_OIL), ev)
        val ice = AppUiState(enabledItems = enabled, vehicleKind = VehicleKind.ICE).visibleItems
        assertEquals(listOf(Consumable.OIL, Consumable.TIRE_FL), ice)
        // 모르거나 하이브리드면 켠 항목을 모두 보여 줍니다.
        assertEquals(3, AppUiState(enabledItems = enabled, vehicleKind = VehicleKind.UNKNOWN).visibleItems.size)
        assertEquals(3, AppUiState(enabledItems = enabled, vehicleKind = VehicleKind.HYBRID).visibleItems.size)
    }

    @Test fun applicableItemsIgnoreOnOffButFollowVehicleKind() {
        val ev = AppUiState(enabledItems = emptySet(), vehicleKind = VehicleKind.EV).applicableItems
        assertTrue(Consumable.WIPER in ev)
        assertTrue(Consumable.REDUCTION_OIL in ev)
        assertFalse(Consumable.OIL in ev)
        assertEquals(Consumable.entries.size, AppUiState(vehicleKind = VehicleKind.UNKNOWN).applicableItems.size)
    }
}
