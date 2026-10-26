package com.nullverse.nullkeyai.sync

/**
 * Provider-independent transport. Implementations may talk to a hosted API,
 * user-controlled storage, or another device — none of those endpoints or
 * credentials belong in this foundation.
 *
 * [providerId] is a short local name (for example `memory` or `webdav`), never
 * a production URL.
 */
interface SyncTransport {
    val providerId: String

    suspend fun push(envelope: SyncEnvelope)

    suspend fun pull(): SyncEnvelope
}

/**
 * Last-snapshot memory transport for tests and local experiments. Callers must
 * pull-merge-push; concurrent pushes without a pull can overwrite.
 */
class InMemorySyncTransport(
    override val providerId: String = PROVIDER_ID
) : SyncTransport {
    private val lock = Any()
    private var snapshot: SyncEnvelope = SyncEnvelope(
        producerDeviceId = PROVIDER_ID,
        producedAt = 0L,
        records = emptyList()
    )

    override suspend fun push(envelope: SyncEnvelope) {
        synchronized(lock) { snapshot = envelope }
    }

    override suspend fun pull(): SyncEnvelope = synchronized(lock) { snapshot }

    companion object {
        const val PROVIDER_ID = "memory"
    }
}
