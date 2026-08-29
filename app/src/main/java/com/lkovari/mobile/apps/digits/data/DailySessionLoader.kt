package com.lkovari.mobile.apps.digits.data

import com.lkovari.mobile.apps.digits.data.firestore.LocalePuzzleLookup
import com.lkovari.mobile.apps.digits.domain.DailyProgress
import com.lkovari.mobile.apps.digits.domain.PuzzleDay
import com.lkovari.mobile.apps.digits.domain.StagePuzzle

sealed class DailySession {
    data class Restored(
        val progress: DailyProgress,
        val syncIssue: SyncIssue
    ) : DailySession()

    data class Fresh(
        val stages: List<StagePuzzle>,
        val dayEpochMillis: Long,
        val locale: String,
        val documentId: String?,
        val syncIssue: SyncIssue
    ) : DailySession()
}

class DailySessionLoader(
    private val loadToday: suspend () -> DailyProgress?,
    private val lookupLocalePuzzle: suspend (String) -> LocalePuzzleLookup,
    private val upsertPuzzle: suspend (PuzzleDay, String?) -> String,
    private val isOnline: () -> Boolean,
    private val generateStages: () -> List<StagePuzzle>,
    private val endOfTodayMillis: () -> Long
) {
    suspend fun load(localeTag: String): DailySession {
        val local = loadToday()
        if (local != null) {
            return DailySession.Restored(
                progress = local,
                syncIssue = if (isOnline()) SyncIssue.NONE else SyncIssue.NO_INTERNET
            )
        }
        return loadFresh(localeTag)
    }

    private suspend fun loadFresh(localeTag: String): DailySession {
        val online = isOnline()
        if (!online) {
            return generatedSession(localeTag, SyncIssue.NO_INTERNET, documentId = null)
        }
        return try {
            val lookup = lookupLocalePuzzle(localeTag)
            val remote = lookup.todaysPuzzle
            if (remote != null && remote.stages.isNotEmpty()) {
                DailySession.Fresh(
                    stages = remote.stages,
                    dayEpochMillis = remote.dayEpochMillis,
                    locale = remote.locale,
                    documentId = lookup.documentId,
                    syncIssue = SyncIssue.NONE
                )
            } else {
                val generated = generateStages()
                val puzzle = PuzzleDay(
                    dayEpochMillis = endOfTodayMillis(),
                    locale = localeTag,
                    stages = generated
                )
                val documentId = try {
                    upsertPuzzle(puzzle, lookup.documentId)
                } catch (error: Exception) {
                    return DailySession.Fresh(
                        stages = generated,
                        dayEpochMillis = puzzle.dayEpochMillis,
                        locale = localeTag,
                        documentId = lookup.documentId,
                        syncIssue = SyncIssueMessages.classify(true, error)
                    )
                }
                DailySession.Fresh(
                    stages = generated,
                    dayEpochMillis = puzzle.dayEpochMillis,
                    locale = localeTag,
                    documentId = documentId,
                    syncIssue = SyncIssue.NONE
                )
            }
        } catch (error: Exception) {
            generatedSession(
                localeTag,
                SyncIssueMessages.classify(online, error),
                documentId = null
            )
        }
    }

    private fun generatedSession(
        localeTag: String,
        syncIssue: SyncIssue,
        documentId: String?
    ): DailySession.Fresh {
        return DailySession.Fresh(
            stages = generateStages(),
            dayEpochMillis = endOfTodayMillis(),
            locale = localeTag,
            documentId = documentId,
            syncIssue = syncIssue
        )
    }
}
