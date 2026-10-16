package com.byronhung.firstlight.ui.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneMakerTest {
    @Test fun brandsMapRegardlessOfCase() {
        assertEquals(PhoneMaker.HONOR, PhoneMaker.from("HONOR"))
        assertEquals(PhoneMaker.HUAWEI, PhoneMaker.from("Huawei"))
        assertEquals(PhoneMaker.XIAOMI, PhoneMaker.from("Redmi"))
        assertEquals(PhoneMaker.OPPO, PhoneMaker.from("OnePlus"))
        assertEquals(PhoneMaker.OPPO, PhoneMaker.from("realme"))
        assertEquals(PhoneMaker.VIVO, PhoneMaker.from(" vivo "))
    }

    @Test fun stockAndSamsungKeepTheAppInfoRoute() {
        assertNull(PhoneMaker.from("samsung"))
        assertNull(PhoneMaker.from("Google"))
        assertNull(PhoneMaker.from(""))
    }

    @Test fun honorCopyNamesTheExactButton() {
        // Honor's battery dialog calls the bad option "Allow" (tester, 16 Oct).
        assertTrue("Don't allow" in PhoneMaker.HONOR.batteryHow)
    }

    @Test fun everyBrandHasAScreenToTry() {
        PhoneMaker.entries.forEach { assertTrue(it.name, it.launchScreens.isNotEmpty()) }
    }
}
