package com.byronhung.firstlight.ui.list

/**
 * Phone makers that stop background apps harder than stock Android, and where their own screens live.
 * Stock and Samsung keep the App info route; these get pointed straight at the screen that matters,
 * in that brand's own words (Honor's battery dialog says "Allow" for the bad option, so the copy names
 * the exact button to tap).
 */
enum class PhoneMaker(
    /** How to use the standard battery optimization list on this brand. */
    val batteryHow: String,
    /** How to use this brand's own "let it start / keep running" screen. */
    val launchHow: String,
    /** That screen as (package, activity), newest firmware first. Not public API: each is tried until one opens. */
    val launchScreens: List<Pair<String, String>>,
) {
    HONOR(
        batteryHow = "Pick All apps, tap First Light, then Don't allow and OK.",
        launchHow = "Turn First Light's switch off, then turn on all three and tap OK.",
        launchScreens = listOf(
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        ),
    ),
    HUAWEI(
        batteryHow = "Pick All apps, tap First Light, then Don't allow and OK.",
        launchHow = "Turn First Light's switch off, then turn on all three and tap OK.",
        launchScreens = listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        ),
    ),
    XIAOMI(
        batteryHow = "Find First Light and turn battery optimization off.",
        launchHow = "Turn on Autostart for First Light.",
        launchScreens = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
    ),
    OPPO(
        batteryHow = "Find First Light and turn battery optimization off.",
        launchHow = "Turn on Auto launch for First Light.",
        launchScreens = listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        ),
    ),
    VIVO(
        batteryHow = "Find First Light and turn battery optimization off.",
        launchHow = "Let First Light run in the background.",
        launchScreens = listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        ),
    ),
    ;

    companion object {
        /** Null for stock Android, Samsung and anything else not listed: they keep the App info route. */
        fun from(manufacturer: String): PhoneMaker? = when (manufacturer.trim().lowercase()) {
            "honor" -> HONOR
            "huawei" -> HUAWEI
            "xiaomi", "redmi", "poco" -> XIAOMI
            "oppo", "realme", "oneplus" -> OPPO
            "vivo", "iqoo" -> VIVO
            else -> null
        }
    }
}
