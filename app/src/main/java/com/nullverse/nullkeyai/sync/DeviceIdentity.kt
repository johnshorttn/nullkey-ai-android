package com.nullverse.nullkeyai.sync

import android.content.Context
import java.util.UUID

/**
 * Durable per-install device identity used as [com.nullverse.nullkeyai.db.Clip.originDeviceId]
 * and [com.nullverse.nullkeyai.db.Clip.modifiedByDeviceId].
 *
 * The identifier is generated once and persisted locally. It is not an Android
 * hardware serial, ANDROID_ID, or advertising id — those can change across
 * factory resets, sign-in, or OS upgrades.
 */
class DeviceIdentity(
    private val store: DeviceIdStore,
    private val generateId: () -> String = { UUID.randomUUID().toString() }
) {
    private val lock = Any()

    fun current(): String {
        store.read()?.takeIf { it.isNotBlank() }?.let { return it }
        synchronized(lock) {
            store.read()?.takeIf { it.isNotBlank() }?.let { return it }
            val created = generateId().also { require(it.isNotBlank()) { "Device id generator returned blank" } }
            store.write(created)
            return created
        }
    }

    companion object {
        fun from(context: Context): DeviceIdentity =
            DeviceIdentity(SharedPreferencesDeviceIdStore(context.applicationContext))
    }
}

interface DeviceIdStore {
    fun read(): String?
    fun write(id: String)
}

/** In-memory store for tests and for callers that do not have Android prefs. */
class MemoryDeviceIdStore(initial: String? = null) : DeviceIdStore {
    @Volatile private var value: String? = initial
    override fun read(): String? = value
    override fun write(id: String) {
        value = id
    }
}

class SharedPreferencesDeviceIdStore(context: Context) : DeviceIdStore {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY_DEVICE_ID, null)

    override fun write(id: String) {
        prefs.edit().putString(KEY_DEVICE_ID, id).commit()
    }

    companion object {
        const val PREFS_NAME = "nullkey_sync_identity"
        const val KEY_DEVICE_ID = "device_id"
    }
}
