package com.nullverse.nullkeyai.sync

data class MergeResult(
    val records: List<SyncClipRecord>,
    val conflictsPreserved: Int,
    val applied: Int
)

/**
 * Merges an incoming envelope into a local record set. Identity is [SyncClipRecord.syncId];
 * unknown remote ids are inserted, known ids go through [SyncConflictResolver].
 */
class SyncMerger(
    private val resolver: SyncConflictResolver = SyncConflictResolver
) {
    fun merge(local: List<SyncClipRecord>, incoming: List<SyncClipRecord>): MergeResult {
        val byId = LinkedHashMap<String, SyncClipRecord>(local.size + incoming.size)
        local.forEach { byId[it.syncId] = it }
        var conflicts = 0
        var applied = 0
        for (remote in incoming) {
            val existing = byId[remote.syncId]
            if (existing == null) {
                byId[remote.syncId] = remote
                applied++
                continue
            }
            when (val decision = resolver.resolve(existing, remote)) {
                is MergeDecision.Keep -> {
                    if (decision.record != existing) {
                        byId[decision.record.syncId] = decision.record
                        applied++
                    }
                }
                is MergeDecision.PreserveBoth -> {
                    byId[decision.canonical.syncId] = decision.canonical
                    byId[decision.fork.syncId] = decision.fork
                    conflicts++
                    applied++
                }
            }
        }
        return MergeResult(
            records = byId.values.toList(),
            conflictsPreserved = conflicts,
            applied = applied
        )
    }
}
