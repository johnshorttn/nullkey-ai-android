package com.nullverse.nullkeyai.sync

import java.security.MessageDigest
import java.util.UUID

/**
 * Deterministic conflict handling for two versions of the same [SyncClipRecord.syncId].
 *
 * Sequential edits (different revisions) keep the higher revision. Concurrent
 * live edits at the same revision preserve **both** records: the lineage winner
 * keeps the original syncId and the other is forked under a stable UUID so two
 * devices resolving A vs B independently produce the same pair.
 */
object SyncConflictResolver {

    fun resolve(local: SyncClipRecord, remote: SyncClipRecord): MergeDecision {
        require(local.syncId == remote.syncId) {
            "Cannot resolve records with different syncIds (${local.syncId} vs ${remote.syncId})"
        }
        if (sameVersion(local, remote)) return MergeDecision.Keep(local)

        val localLive = !local.isTombstone
        val remoteLive = !remote.isTombstone
        return when {
            localLive && remoteLive -> resolveLive(local, remote)
            !localLive && !remoteLive -> MergeDecision.Keep(newer(local, remote))
            else -> resolveDeleteVersusEdit(local, remote)
        }
    }

    private fun resolveLive(a: SyncClipRecord, b: SyncClipRecord): MergeDecision {
        if (a.payloadFingerprint() == b.payloadFingerprint()) {
            return MergeDecision.Keep(newer(a, b))
        }
        if (a.revision == b.revision) {
            return preserveBoth(a, b)
        }
        return MergeDecision.Keep(if (a.revision > b.revision) a else b)
    }

    private fun resolveDeleteVersusEdit(a: SyncClipRecord, b: SyncClipRecord): MergeDecision {
        val tombstone = if (a.isTombstone) a else b
        val live = if (a.isTombstone) b else a
        return when {
            tombstone.revision > live.revision -> MergeDecision.Keep(tombstone)
            live.revision > tombstone.revision -> MergeDecision.Keep(live)
            else -> preserveDeleteAndEdit(tombstone, live)
        }
    }

    private fun preserveBoth(a: SyncClipRecord, b: SyncClipRecord): MergeDecision {
        val canonicalIsA = compareLineage(a, b) >= 0
        val canonical = if (canonicalIsA) a else b
        val losing = if (canonicalIsA) b else a
        return MergeDecision.PreserveBoth(
            canonical = canonical,
            fork = forkLosing(canonical.syncId, losing)
        )
    }

    private fun preserveDeleteAndEdit(tombstone: SyncClipRecord, live: SyncClipRecord): MergeDecision {
        return MergeDecision.PreserveBoth(
            canonical = tombstone,
            fork = forkLosing(tombstone.syncId, live)
        )
    }

    private fun forkLosing(originalSyncId: String, losing: SyncClipRecord): SyncClipRecord =
        losing.copy(
            syncId = forkSyncId(originalSyncId, losing),
            revision = 1L,
            state = SyncRecordState.CONFLICT
        )

    internal fun forkSyncId(originalSyncId: String, losing: SyncClipRecord): String =
        NameUuid.v5(
            NAMESPACE,
            listOf(
                originalSyncId,
                losing.modifiedByDeviceId,
                losing.revision.toString(),
                losing.updatedAt.toString(),
                losing.payloadFingerprint()
            ).joinToString("|")
        )

    internal fun newer(a: SyncClipRecord, b: SyncClipRecord): SyncClipRecord =
        if (compareLineage(a, b) >= 0) a else b

    /**
     * Higher revision first, then later [SyncClipRecord.updatedAt], then
     * lexicographically greater device ids. The comparison is independent of
     * which record is "local" vs "remote".
     */
    internal fun compareLineage(a: SyncClipRecord, b: SyncClipRecord): Int {
        a.revision.compareTo(b.revision).let { if (it != 0) return it }
        a.updatedAt.compareTo(b.updatedAt).let { if (it != 0) return it }
        a.modifiedByDeviceId.compareTo(b.modifiedByDeviceId).let { if (it != 0) return it }
        a.originDeviceId.compareTo(b.originDeviceId).let { if (it != 0) return it }
        return a.payloadFingerprint().compareTo(b.payloadFingerprint())
    }

    private fun sameVersion(a: SyncClipRecord, b: SyncClipRecord): Boolean =
        a.revision == b.revision &&
            a.isTombstone == b.isTombstone &&
            a.payloadFingerprint() == b.payloadFingerprint()

    private val NAMESPACE: UUID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
}

sealed class MergeDecision {
    data class Keep(val record: SyncClipRecord) : MergeDecision()
    data class PreserveBoth(
        val canonical: SyncClipRecord,
        val fork: SyncClipRecord
    ) : MergeDecision()
}

/** RFC 4122 version-5 (name-based SHA-1) UUID, used so conflict forks are deterministic. */
internal object NameUuid {
    fun v5(namespace: UUID, name: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(uuidBytes(namespace))
        md.update(name.toByteArray(Charsets.UTF_8))
        val hash = md.digest()
        hash[6] = (hash[6].toInt() and 0x0f or 0x50).toByte()
        hash[8] = (hash[8].toInt() and 0x3f or 0x80).toByte()
        return formatUuid(hash.copyOf(16))
    }

    private fun uuidBytes(uuid: UUID): ByteArray {
        val bytes = ByteArray(16)
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        for (i in 0..7) bytes[i] = (msb shr (8 * (7 - i))).toByte()
        for (i in 8..15) bytes[i] = (lsb shr (8 * (15 - i))).toByte()
        return bytes
    }

    private fun formatUuid(bytes: ByteArray): String {
        fun hex(start: Int, len: Int) =
            bytes.copyOfRange(start, start + len).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "${hex(0, 4)}-${hex(4, 2)}-${hex(6, 2)}-${hex(8, 2)}-${hex(10, 6)}"
    }
}
