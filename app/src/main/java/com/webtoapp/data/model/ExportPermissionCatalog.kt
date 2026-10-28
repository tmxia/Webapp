package com.webtoapp.data.model































enum class ExportPermKind {
    
    DANGEROUS,
    
    SPECIAL,
    
    NORMAL
}

enum class ExportPermGroup {
    CALENDAR,
    CAMERA,
    CONTACTS,
    LOCATION,
    MICROPHONE,
    PHONE,
    CALL_LOG,
    ACTIVITY,
    SENSORS,
    SMS,
    STORAGE,
    NEARBY,
    NOTIFICATIONS,
    SPECIAL,
    FOREGROUND,
    SYSTEM
}

data class ExportPerm(
    val key: String,
    val manifest: String,
    val group: ExportPermGroup,
    val kind: ExportPermKind,
    val addedApi: Int,
    val deprecatedApi: Int? = null,
    
    val requestFromApi: Int? = null,
    
    val requestUntilApi: Int? = null,
    val playRestricted: Boolean = false,
    
    val requestSeparately: Boolean = false,
    val legacy: (ApkRuntimePermissions) -> Boolean = { false },
    val feature: (ApkRuntimePermissions, PermissionFeatureDefaults) -> Boolean = { _, _ -> false },
    val reasonKeys: Set<String> = emptySet()
)






data class PermissionFeatureDefaults(
    val network: Boolean = false,
    
    val mediaPlayback: Boolean = false,
    
    val specialUse: Boolean = false,
    val scheduledExactAlarm: Boolean = false,
    val activationBiometric: Boolean = false
)

object ExportPermissionCatalog {

    private fun dangerous(
        key: String,
        group: ExportPermGroup,
        addedApi: Int,
        deprecatedApi: Int? = null,
        requestFromApi: Int? = null,
        requestUntilApi: Int? = null,
        playRestricted: Boolean = false,
        requestSeparately: Boolean = false,
        legacy: (ApkRuntimePermissions) -> Boolean = { false },
        feature: (ApkRuntimePermissions, PermissionFeatureDefaults) -> Boolean = { _, _ -> false },
        manifest: String = "android.permission.$key",
        reasonKeys: Set<String> = emptySet()
    ) = ExportPerm(
        key = key,
        manifest = manifest,
        group = group,
        kind = ExportPermKind.DANGEROUS,
        addedApi = addedApi,
        deprecatedApi = deprecatedApi,
        requestFromApi = requestFromApi,
        requestUntilApi = requestUntilApi,
        playRestricted = playRestricted,
        requestSeparately = requestSeparately,
        legacy = legacy,
        feature = feature,
        reasonKeys = reasonKeys
    )

    private fun special(
        key: String,
        group: ExportPermGroup,
        addedApi: Int,
        playRestricted: Boolean = false,
        legacy: (ApkRuntimePermissions) -> Boolean = { false },
        feature: (ApkRuntimePermissions, PermissionFeatureDefaults) -> Boolean = { _, _ -> false },
        reasonKeys: Set<String> = emptySet()
    ) = ExportPerm(
        key = key,
        manifest = "android.permission.$key",
        group = group,
        kind = ExportPermKind.SPECIAL,
        addedApi = addedApi,
        playRestricted = playRestricted,
        legacy = legacy,
        feature = feature,
        reasonKeys = reasonKeys
    )

    private fun normal(
        key: String,
        group: ExportPermGroup,
        addedApi: Int,
        deprecatedApi: Int? = null,
        playRestricted: Boolean = false,
        legacy: (ApkRuntimePermissions) -> Boolean = { false },
        feature: (ApkRuntimePermissions, PermissionFeatureDefaults) -> Boolean = { _, _ -> false },
        reasonKeys: Set<String> = emptySet()
    ) = ExportPerm(
        key = key,
        manifest = "android.permission.$key",
        group = group,
        kind = ExportPermKind.NORMAL,
        addedApi = addedApi,
        deprecatedApi = deprecatedApi,
        playRestricted = playRestricted,
        legacy = legacy,
        feature = feature,
        reasonKeys = reasonKeys
    )

    val entries: List<ExportPerm> = listOf(
        
        dangerous("READ_CALENDAR", ExportPermGroup.CALENDAR, 1, legacy = { it.readCalendar }),
        dangerous("WRITE_CALENDAR", ExportPermGroup.CALENDAR, 1, legacy = { it.writeCalendar }),

        
        dangerous("CAMERA", ExportPermGroup.CAMERA, 1, legacy = { it.camera }, reasonKeys = setOf("camera")),

        
        dangerous("READ_CONTACTS", ExportPermGroup.CONTACTS, 1, legacy = { it.readContacts }),
        dangerous("WRITE_CONTACTS", ExportPermGroup.CONTACTS, 1, legacy = { it.writeContacts }),
        dangerous("GET_ACCOUNTS", ExportPermGroup.CONTACTS, 1),

        
        dangerous("ACCESS_COARSE_LOCATION", ExportPermGroup.LOCATION, 1,
            legacy = { it.location || it.backgroundLocation }, reasonKeys = setOf("location")),
        dangerous("ACCESS_FINE_LOCATION", ExportPermGroup.LOCATION, 1,
            legacy = { it.location || it.backgroundLocation }, reasonKeys = setOf("location")),
        dangerous("ACCESS_BACKGROUND_LOCATION", ExportPermGroup.LOCATION, 29,
            requestFromApi = 29, requestSeparately = true,
            legacy = { it.backgroundLocation }, reasonKeys = setOf("location")),
        dangerous("ACCESS_MEDIA_LOCATION", ExportPermGroup.LOCATION, 29, requestFromApi = 29),
        
        normal("USE_LOCATION_BUTTON", ExportPermGroup.LOCATION, 37),

        
        dangerous("RECORD_AUDIO", ExportPermGroup.MICROPHONE, 1, legacy = { it.microphone }),
        normal("MODIFY_AUDIO_SETTINGS", ExportPermGroup.MICROPHONE, 1, legacy = { it.microphone }),

        
        dangerous("READ_PHONE_STATE", ExportPermGroup.PHONE, 1, legacy = { it.readPhoneState }),
        normal("READ_BASIC_PHONE_STATE", ExportPermGroup.PHONE, 33),
        dangerous("READ_PHONE_NUMBERS", ExportPermGroup.PHONE, 26, requestFromApi = 26),
        dangerous("CALL_PHONE", ExportPermGroup.PHONE, 1, legacy = { it.callPhone }),
        dangerous("ANSWER_PHONE_CALLS", ExportPermGroup.PHONE, 26, requestFromApi = 26),
        dangerous(
            "ADD_VOICEMAIL", ExportPermGroup.PHONE, 14,
            manifest = "com.android.voicemail.permission.ADD_VOICEMAIL"
        ),
        dangerous("USE_SIP", ExportPermGroup.PHONE, 9),
        dangerous("ACCEPT_HANDOVER", ExportPermGroup.PHONE, 28, requestFromApi = 28),

        
        dangerous("READ_CALL_LOG", ExportPermGroup.CALL_LOG, 16, playRestricted = true, legacy = { it.readCallLog }),
        dangerous("WRITE_CALL_LOG", ExportPermGroup.CALL_LOG, 16, playRestricted = true, legacy = { it.writeCallLog }),
        dangerous("PROCESS_OUTGOING_CALLS", ExportPermGroup.CALL_LOG, 1,
            deprecatedApi = 29, requestUntilApi = 29, playRestricted = true,
            legacy = { it.processOutgoingCalls }),

        
        dangerous("ACTIVITY_RECOGNITION", ExportPermGroup.ACTIVITY, 29,
            requestFromApi = 29, legacy = { it.activityRecognition }),

        
        dangerous("BODY_SENSORS", ExportPermGroup.SENSORS, 20, legacy = { it.bodySensors }),
        dangerous("BODY_SENSORS_BACKGROUND", ExportPermGroup.SENSORS, 33,
            requestFromApi = 33, requestSeparately = true, legacy = { it.bodySensors }),
        normal("HIGH_SAMPLING_RATE_SENSORS", ExportPermGroup.SENSORS, 31, legacy = { it.bodySensors }),

        
        dangerous("SEND_SMS", ExportPermGroup.SMS, 1, playRestricted = true, legacy = { it.sendSms }),
        dangerous("RECEIVE_SMS", ExportPermGroup.SMS, 1, playRestricted = true, legacy = { it.receiveSms }),
        dangerous("READ_SMS", ExportPermGroup.SMS, 1, playRestricted = true, legacy = { it.readSms }),
        dangerous("RECEIVE_WAP_PUSH", ExportPermGroup.SMS, 1, playRestricted = true),
        dangerous("RECEIVE_MMS", ExportPermGroup.SMS, 1, playRestricted = true),

        
        
        dangerous("READ_EXTERNAL_STORAGE", ExportPermGroup.STORAGE, 16,
            requestUntilApi = 33, legacy = { it.readExternalStorage }),
        dangerous("WRITE_EXTERNAL_STORAGE", ExportPermGroup.STORAGE, 4,
            requestUntilApi = 33, legacy = { it.writeExternalStorage },
            reasonKeys = setOf("writeExternalStorage")),
        dangerous("READ_MEDIA_IMAGES", ExportPermGroup.STORAGE, 33,
            requestFromApi = 33, legacy = { it.readMediaImages }),
        dangerous("READ_MEDIA_VIDEO", ExportPermGroup.STORAGE, 33,
            requestFromApi = 33, legacy = { it.readMediaVideo }),
        dangerous("READ_MEDIA_AUDIO", ExportPermGroup.STORAGE, 33,
            requestFromApi = 33, legacy = { it.readMediaAudio }),
        dangerous("READ_MEDIA_VISUAL_USER_SELECTED", ExportPermGroup.STORAGE, 34, requestFromApi = 34),
        special("MANAGE_EXTERNAL_STORAGE", ExportPermGroup.STORAGE, 30, playRestricted = true),

        
        
        normal("BLUETOOTH", ExportPermGroup.NEARBY, 1, legacy = { it.bluetooth }),
        normal("BLUETOOTH_ADMIN", ExportPermGroup.NEARBY, 1, legacy = { it.bluetooth }),
        dangerous("BLUETOOTH_SCAN", ExportPermGroup.NEARBY, 31, requestFromApi = 31, legacy = { it.bluetooth }),
        dangerous("BLUETOOTH_CONNECT", ExportPermGroup.NEARBY, 31, requestFromApi = 31, legacy = { it.bluetooth }),
        dangerous("BLUETOOTH_ADVERTISE", ExportPermGroup.NEARBY, 31, requestFromApi = 31, legacy = { it.bluetooth }),
        dangerous("UWB_RANGING", ExportPermGroup.NEARBY, 31, requestFromApi = 31),
        dangerous("NEARBY_WIFI_DEVICES", ExportPermGroup.NEARBY, 33,
            requestFromApi = 33, legacy = { it.nearbyWifiDevices }),
        dangerous("RANGING", ExportPermGroup.NEARBY, 36, requestFromApi = 36),
        normal("NFC", ExportPermGroup.NEARBY, 9, legacy = { it.nfc }),
        dangerous(
            "ACCESS_LOCAL_NETWORK", ExportPermGroup.NEARBY, 36, requestFromApi = 36,
            feature = { _, defaults -> defaults.network }
        ),

        
        dangerous("POST_NOTIFICATIONS", ExportPermGroup.NOTIFICATIONS, 33,
            requestFromApi = 33, legacy = { it.notifications }, reasonKeys = setOf("notifications")),
        
        normal("POST_PROMOTED_NOTIFICATIONS", ExportPermGroup.NOTIFICATIONS, 36),
        special("USE_FULL_SCREEN_INTENT", ExportPermGroup.NOTIFICATIONS, 29),
        normal("ACCESS_NOTIFICATION_POLICY", ExportPermGroup.NOTIFICATIONS, 23),

        
        special("SYSTEM_ALERT_WINDOW", ExportPermGroup.SPECIAL, 1,
            legacy = { it.systemAlertWindow }, reasonKeys = setOf("systemAlertWindow")),
        special("WRITE_SETTINGS", ExportPermGroup.SPECIAL, 1),
        special("REQUEST_INSTALL_PACKAGES", ExportPermGroup.SPECIAL, 23,
            playRestricted = true, legacy = { it.installPackages }),
        special("PACKAGE_USAGE_STATS", ExportPermGroup.SPECIAL, 23, playRestricted = true),
        special("SCHEDULE_EXACT_ALARM", ExportPermGroup.SPECIAL, 31,
            feature = { _, defaults -> defaults.scheduledExactAlarm }),
        normal("USE_EXACT_ALARM", ExportPermGroup.SPECIAL, 33,
            playRestricted = true,
            feature = { _, defaults -> defaults.scheduledExactAlarm }),
        normal("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", ExportPermGroup.SPECIAL, 23,
            legacy = { it.requestIgnoreBatteryOptimizations },
            reasonKeys = setOf("requestIgnoreBatteryOptimizations")),
        normal("QUERY_ALL_PACKAGES", ExportPermGroup.SPECIAL, 30,
            playRestricted = true, legacy = { it.queryAllPackages }),

        
        
        
        
        normal("FOREGROUND_SERVICE", ExportPermGroup.FOREGROUND, 28,
            legacy = { it.foregroundService },
            feature = { _, defaults -> defaults.specialUse || defaults.mediaPlayback },
            reasonKeys = setOf("foregroundService")),
        normal("FOREGROUND_SERVICE_SPECIAL_USE", ExportPermGroup.FOREGROUND, 34,
            feature = { _, defaults -> defaults.specialUse },
            reasonKeys = setOf("foregroundService")),
        normal("FOREGROUND_SERVICE_DATA_SYNC", ExportPermGroup.FOREGROUND, 34),
        normal("FOREGROUND_SERVICE_MEDIA_PLAYBACK", ExportPermGroup.FOREGROUND, 34,
            feature = { _, defaults -> defaults.mediaPlayback },
            reasonKeys = setOf("foregroundService")),
        normal("FOREGROUND_SERVICE_LOCATION", ExportPermGroup.FOREGROUND, 34,
            legacy = { it.location }, reasonKeys = setOf("location")),
        normal("FOREGROUND_SERVICE_CAMERA", ExportPermGroup.FOREGROUND, 34,
            legacy = { it.camera }, reasonKeys = setOf("camera")),
        normal("FOREGROUND_SERVICE_MICROPHONE", ExportPermGroup.FOREGROUND, 34,
            legacy = { it.microphone }),
        normal("FOREGROUND_SERVICE_CONNECTED_DEVICE", ExportPermGroup.FOREGROUND, 34),
        normal("FOREGROUND_SERVICE_MEDIA_PROJECTION", ExportPermGroup.FOREGROUND, 34),
        normal("FOREGROUND_SERVICE_PHONE_CALL", ExportPermGroup.FOREGROUND, 34),
        normal("FOREGROUND_SERVICE_HEALTH", ExportPermGroup.FOREGROUND, 34),
        normal("FOREGROUND_SERVICE_REMOTE_MESSAGING", ExportPermGroup.FOREGROUND, 34),
        normal("FOREGROUND_SERVICE_SYSTEM_EXEMPTED", ExportPermGroup.FOREGROUND, 34),
        normal("FOREGROUND_SERVICE_MEDIA_PROCESSING", ExportPermGroup.FOREGROUND, 35),

        
        normal("WAKE_LOCK", ExportPermGroup.SYSTEM, 1,
            legacy = { it.wakeLock }, reasonKeys = setOf("wakeLock")),
        normal("RECEIVE_BOOT_COMPLETED", ExportPermGroup.SYSTEM, 1,
            legacy = { it.bootCompleted }, reasonKeys = setOf("bootCompleted")),
        normal("VIBRATE", ExportPermGroup.SYSTEM, 1, legacy = { it.vibration }),
        normal("REQUEST_DELETE_PACKAGES", ExportPermGroup.SYSTEM, 26, legacy = { it.requestDeletePackages }),
        normal("USE_BIOMETRIC", ExportPermGroup.SYSTEM, 28,
            feature = { _, defaults -> defaults.activationBiometric }),
        normal("USE_FINGERPRINT", ExportPermGroup.SYSTEM, 23, deprecatedApi = 28,
            feature = { _, defaults -> defaults.activationBiometric }),
        normal("DETECT_SCREEN_CAPTURE", ExportPermGroup.SYSTEM, 34),
        normal("DETECT_SCREEN_RECORDING", ExportPermGroup.SYSTEM, 35),
        normal("ACCESS_WIFI_STATE", ExportPermGroup.SYSTEM, 1, legacy = { it.wifiState }),
        normal("CHANGE_WIFI_STATE", ExportPermGroup.SYSTEM, 1, legacy = { it.wifiState }),
        normal("CHANGE_NETWORK_STATE", ExportPermGroup.SYSTEM, 1, legacy = { it.wifiState })
    )

    val byKey: Map<String, ExportPerm> = entries.associateBy { it.key }

    init {
        check(byKey.size == entries.size) { "Duplicate export permission keys" }
    }

    




    private val emitOrder = listOf(
        "CAMERA",
        "RECORD_AUDIO",
        "MODIFY_AUDIO_SETTINGS",
        "ACCESS_COARSE_LOCATION",
        "ACCESS_FINE_LOCATION",
        "ACCESS_BACKGROUND_LOCATION",
        "POST_NOTIFICATIONS",
        "READ_EXTERNAL_STORAGE",
        "WRITE_EXTERNAL_STORAGE",
        "READ_MEDIA_IMAGES",
        "READ_MEDIA_VIDEO",
        "READ_MEDIA_AUDIO",
        "BLUETOOTH",
        "BLUETOOTH_ADMIN",
        "BLUETOOTH_SCAN",
        "BLUETOOTH_CONNECT",
        "BLUETOOTH_ADVERTISE",
        "NFC",
        "ACCESS_WIFI_STATE",
        "CHANGE_WIFI_STATE",
        "CHANGE_NETWORK_STATE",
        "NEARBY_WIFI_DEVICES",
        "QUERY_ALL_PACKAGES",
        "BODY_SENSORS",
        "BODY_SENSORS_BACKGROUND",
        "HIGH_SAMPLING_RATE_SENSORS",
        "ACTIVITY_RECOGNITION",
        "READ_PHONE_STATE",
        "CALL_PHONE",
        "READ_CONTACTS",
        "WRITE_CONTACTS",
        "READ_CALENDAR",
        "WRITE_CALENDAR",
        "READ_SMS",
        "SEND_SMS",
        "RECEIVE_SMS",
        "READ_CALL_LOG",
        "WRITE_CALL_LOG",
        "PROCESS_OUTGOING_CALLS",
        "FOREGROUND_SERVICE",
        "FOREGROUND_SERVICE_SPECIAL_USE",
        "FOREGROUND_SERVICE_DATA_SYNC",
        "FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        "WAKE_LOCK",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "RECEIVE_BOOT_COMPLETED",
        "VIBRATE",
        "REQUEST_INSTALL_PACKAGES",
        "REQUEST_DELETE_PACKAGES",
        "SYSTEM_ALERT_WINDOW",
        "FOREGROUND_SERVICE_LOCATION",
        "FOREGROUND_SERVICE_CAMERA",
        "FOREGROUND_SERVICE_MICROPHONE",
        "SCHEDULE_EXACT_ALARM",
        "USE_EXACT_ALARM",
        "USE_BIOMETRIC",
        "USE_FINGERPRINT"
    )

    fun manifestPermissions(
        packageName: String,
        permissions: ApkRuntimePermissions,
        defaults: PermissionFeatureDefaults,
        downloadWithoutNotification: Boolean
    ): List<String> {
        val granted = entries.filter { permissions.exportGranted(it, defaults) }.map { it.key }.toSet()
        val out = linkedSetOf<String>()
        out += "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        if (defaults.network) {
            out += "android.permission.INTERNET"
            out += "android.permission.ACCESS_NETWORK_STATE"
        }
        if ("ACCESS_LOCAL_NETWORK" in granted) {
            out += "android.permission.ACCESS_LOCAL_NETWORK"
        }
        for (key in emitOrder) {
            if (key !in granted || key == "ACCESS_LOCAL_NETWORK") continue
            out += byKey.getValue(key).manifest
        }
        for (perm in entries) {
            if (perm.key in granted && perm.manifest !in out) out += perm.manifest
        }
        if (downloadWithoutNotification) {
            
            
            
            out += "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION"
        }
        return out.toList()
    }
}

private data class PermBundle(
    val primary: String?,
    val members: Set<String>,
    val isOn: (ApkRuntimePermissions) -> Boolean,
    val set: (ApkRuntimePermissions, Boolean) -> ApkRuntimePermissions
)

private val permBundles = listOf(
    PermBundle("CAMERA", setOf("CAMERA", "FOREGROUND_SERVICE_CAMERA"),
        { it.camera }, { p, v -> p.copy(camera = v) }),
    PermBundle("RECORD_AUDIO", setOf("RECORD_AUDIO", "MODIFY_AUDIO_SETTINGS", "FOREGROUND_SERVICE_MICROPHONE"),
        { it.microphone }, { p, v -> p.copy(microphone = v) }),
    PermBundle(null, setOf("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION", "FOREGROUND_SERVICE_LOCATION"),
        { it.location }, { p, v -> p.copy(location = v) }),
    PermBundle(null, setOf("BLUETOOTH", "BLUETOOTH_ADMIN", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE"),
        { it.bluetooth }, { p, v -> p.copy(bluetooth = v) }),
    PermBundle(null, setOf("ACCESS_WIFI_STATE", "CHANGE_WIFI_STATE", "CHANGE_NETWORK_STATE"),
        { it.wifiState }, { p, v -> p.copy(wifiState = v) }),
    PermBundle("BODY_SENSORS", setOf("BODY_SENSORS", "BODY_SENSORS_BACKGROUND", "HIGH_SAMPLING_RATE_SENSORS"),
        { it.bodySensors }, { p, v -> p.copy(bodySensors = v) }),
    PermBundle("FOREGROUND_SERVICE", setOf("FOREGROUND_SERVICE"),
        { it.foregroundService }, { p, v -> p.copy(foregroundService = v) })
)

private val singleBooleans: Map<String, (ApkRuntimePermissions, Boolean) -> ApkRuntimePermissions> = mapOf(
    "POST_NOTIFICATIONS" to { p, v -> p.copy(notifications = v) },
    "READ_EXTERNAL_STORAGE" to { p, v -> p.copy(readExternalStorage = v) },
    "WRITE_EXTERNAL_STORAGE" to { p, v -> p.copy(writeExternalStorage = v) },
    "READ_MEDIA_IMAGES" to { p, v -> p.copy(readMediaImages = v) },
    "READ_MEDIA_VIDEO" to { p, v -> p.copy(readMediaVideo = v) },
    "READ_MEDIA_AUDIO" to { p, v -> p.copy(readMediaAudio = v) },
    "NFC" to { p, v -> p.copy(nfc = v) },
    "NEARBY_WIFI_DEVICES" to { p, v -> p.copy(nearbyWifiDevices = v) },
    "ACTIVITY_RECOGNITION" to { p, v -> p.copy(activityRecognition = v) },
    "READ_PHONE_STATE" to { p, v -> p.copy(readPhoneState = v) },
    "CALL_PHONE" to { p, v -> p.copy(callPhone = v) },
    "READ_CONTACTS" to { p, v -> p.copy(readContacts = v) },
    "WRITE_CONTACTS" to { p, v -> p.copy(writeContacts = v) },
    "READ_CALENDAR" to { p, v -> p.copy(readCalendar = v) },
    "WRITE_CALENDAR" to { p, v -> p.copy(writeCalendar = v) },
    "READ_SMS" to { p, v -> p.copy(readSms = v) },
    "SEND_SMS" to { p, v -> p.copy(sendSms = v) },
    "RECEIVE_SMS" to { p, v -> p.copy(receiveSms = v) },
    "READ_CALL_LOG" to { p, v -> p.copy(readCallLog = v) },
    "WRITE_CALL_LOG" to { p, v -> p.copy(writeCallLog = v) },
    "PROCESS_OUTGOING_CALLS" to { p, v -> p.copy(processOutgoingCalls = v) },
    "WAKE_LOCK" to { p, v -> p.copy(wakeLock = v) },
    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to { p, v -> p.copy(requestIgnoreBatteryOptimizations = v) },
    "RECEIVE_BOOT_COMPLETED" to { p, v -> p.copy(bootCompleted = v) },
    "VIBRATE" to { p, v -> p.copy(vibration = v) },
    "REQUEST_INSTALL_PACKAGES" to { p, v -> p.copy(installPackages = v) },
    "REQUEST_DELETE_PACKAGES" to { p, v -> p.copy(requestDeletePackages = v) },
    "SYSTEM_ALERT_WINDOW" to { p, v -> p.copy(systemAlertWindow = v) },
    "ACCESS_BACKGROUND_LOCATION" to { p, v -> p.copy(backgroundLocation = v) },
    "QUERY_ALL_PACKAGES" to { p, v -> p.copy(queryAllPackages = v) }
)

fun ApkRuntimePermissions.grantMap(): Map<String, Boolean> = grants ?: emptyMap()

fun ApkRuntimePermissions.exportGranted(
    perm: ExportPerm,
    defaults: PermissionFeatureDefaults = PermissionFeatureDefaults()
): Boolean {
    grantMap()[perm.key]?.let { return it }
    if (perm.legacy(this)) return true
    if (perm.feature(this, defaults)) return true
    return false
}

fun ApkRuntimePermissions.withExportGrant(
    key: String,
    enabled: Boolean,
    defaults: PermissionFeatureDefaults = PermissionFeatureDefaults()
): ApkRuntimePermissions {
    val perm = ExportPermissionCatalog.byKey[key] ?: return this
    if (exportGranted(perm, defaults) == enabled) return this

    if (enabled && key == "ACCESS_BACKGROUND_LOCATION") {
        val cleared = grantMap().toMutableMap().apply {
            remove("ACCESS_BACKGROUND_LOCATION")
            remove("ACCESS_FINE_LOCATION")
            remove("ACCESS_COARSE_LOCATION")
        }
        return copy(
            backgroundLocation = true,
            location = true,
            grants = cleared.ifEmpty { null }
        )
    }

    val single = singleBooleans[key]
    if (single != null) {
        val next = single(this, enabled)
        val base = perm.legacy(next) || perm.feature(next, defaults)
        val updated = next.grantMap().toMutableMap()
        if (enabled == base) updated.remove(key) else updated[key] = enabled
        return next.copy(grants = updated.ifEmpty { null })
    }

    if (!enabled) {
        val owning = permBundles.filter { it.isOn(this) && key in it.members }
        if (owning.any { it.primary == key }) {
            var next = this
            owning.filter { it.primary == key }.forEach { next = it.set(next, false) }
            val updated = next.grantMap().toMutableMap()
            owning.filter { it.primary == key }.forEach { bundle ->
                bundle.members.forEach { member -> updated.remove(member) }
            }
            val base = perm.legacy(next) || perm.feature(next, defaults)
            if (enabled != base) updated[key] = false
            return next.copy(grants = updated.ifEmpty { null })
        }
        if (owning.isNotEmpty()) {
            var next = this
            val siblingOn = linkedSetOf<String>()
            owning.forEach { bundle ->
                bundle.members.filter { it != key }.forEach { sibling ->
                    if (next.exportGranted(ExportPermissionCatalog.byKey.getValue(sibling), defaults)) {
                        siblingOn += sibling
                    }
                }
                next = bundle.set(next, false)
            }
            val updated = next.grantMap().toMutableMap()
            siblingOn.forEach { sibling ->
                val siblingPerm = ExportPermissionCatalog.byKey.getValue(sibling)
                val still = siblingPerm.legacy(next) || siblingPerm.feature(next, defaults)
                if (!still) updated[sibling] = true
            }
            val base = perm.legacy(next) || perm.feature(next, defaults)
            if (!base) updated.remove(key) else updated[key] = false
            return next.copy(grants = updated.ifEmpty { null })
        }
    }

    val base = perm.legacy(this) || perm.feature(this, defaults)
    val updated = grantMap().toMutableMap()
    if (enabled == base) updated.remove(key) else updated[key] = enabled
    return copy(grants = updated.ifEmpty { null })
}


fun ExportPerm.requestsAtRuntime(sdk: Int, targetSdk: Int): Boolean {
    if (kind != ExportPermKind.DANGEROUS) return false
    if (requestFromApi != null && sdk < requestFromApi) return false
    if (requestUntilApi != null && sdk >= requestUntilApi) return false
    if (key == "POST_NOTIFICATIONS" && targetSdk < 33) return false
    return true
}
