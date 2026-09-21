package com.nullverse.nullkeyai.clipboard

import com.nullverse.nullkeyai.db.Clip

/** How the Vault list should present a configured swipe action. */
enum class ClipSwipePresentation {
    /** Restore the row, then apply immediately. */
    APPLY,
    /** Restore the row, then confirm. Cancel leaves the clip unchanged. */
    CONFIRM,
    /** Restore the row, then collect a tag. Empty/cancel leaves the clip unchanged. */
    PROMPT_TAG,
}

sealed class ClipSwipeApplyResult {
    data object Success : ClipSwipeApplyResult()
    data object Cancelled : ClipSwipeApplyResult()
    data class Failure(val cause: Throwable? = null) : ClipSwipeApplyResult()
}

/**
 * Testable Vault swipe policy and apply path.
 *
 * RecyclerView [androidx.recyclerview.widget.ItemTouchHelper] dismisses the row
 * before the action runs. Callers must restore the row first so cancel and
 * failure never leave a hole in the list.
 */
object ClipSwipeCoordinator {
    fun presentation(action: ClipSwipeAction): ClipSwipePresentation = when (action) {
        ClipSwipeAction.PIN, ClipSwipeAction.PROTECT -> ClipSwipePresentation.APPLY
        ClipSwipeAction.DELETE -> ClipSwipePresentation.CONFIRM
        ClipSwipeAction.TAG -> ClipSwipePresentation.PROMPT_TAG
    }

    fun parseTagInput(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun apply(
        repository: ClipRepository,
        action: ClipSwipeAction,
        clip: Clip,
        tagName: String? = null,
    ): ClipSwipeApplyResult {
        if (action == ClipSwipeAction.TAG) {
            val tag = parseTagInput(tagName) ?: return ClipSwipeApplyResult.Cancelled
            return runCatching { repository.addTag(clip.id, tag) }
                .fold(
                    onSuccess = { ClipSwipeApplyResult.Success },
                    onFailure = { ClipSwipeApplyResult.Failure(it) },
                )
        }
        return runCatching {
            when (action) {
                ClipSwipeAction.PIN -> repository.setPinned(clip.id, !clip.pinned)
                ClipSwipeAction.PROTECT -> repository.setProtected(clip.id, !clip.protected)
                ClipSwipeAction.DELETE -> repository.moveToTrash(clip.id)
                ClipSwipeAction.TAG -> error("unreachable")
            }
        }.fold(
            onSuccess = { ClipSwipeApplyResult.Success },
            onFailure = { ClipSwipeApplyResult.Failure(it) },
        )
    }
}
