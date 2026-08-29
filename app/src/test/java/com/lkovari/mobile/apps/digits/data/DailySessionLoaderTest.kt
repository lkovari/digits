package com.lkovari.mobile.apps.digits.data

import com.lkovari.mobile.apps.digits.data.firestore.LocalePuzzleLookup
import com.lkovari.mobile.apps.digits.domain.DailyProgress
import com.lkovari.mobile.apps.digits.domain.Operand
import com.lkovari.mobile.apps.digits.domain.PuzzleDay
import com.lkovari.mobile.apps.digits.domain.StageLevel
import com.lkovari.mobile.apps.digits.domain.StagePuzzle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySessionLoaderTest {
    @Test
    fun sameDayProgressIsRestoredWithoutGeneratingOrFetching() = runTest {
        val saved = progressWithFirstStageCompleted()
        val fakes = SessionFakes(local = saved)

        val session = fakes.loader().load("en-US")

        val restored = session as DailySession.Restored
        assertEquals(saved, restored.progress)
        assertEquals(SyncIssue.NONE, restored.syncIssue)
        assertEquals(0, fakes.generateCount)
        assertEquals(0, fakes.lookupCount)
        assertEquals(0, fakes.upsertCount)
    }

    @Test
    fun restoredProgressKeepsCompletedStageWhenOffline() = runTest {
        val saved = progressWithFirstStageCompleted()
        val fakes = SessionFakes(local = saved, online = false)

        val session = fakes.loader().load("hu-HU")

        val restored = session as DailySession.Restored
        assertTrue(restored.progress.stageLevels[0].completed)
        assertEquals(1, restored.progress.stageIndex)
        assertEquals(SyncIssue.NO_INTERNET, restored.syncIssue)
        assertEquals(0, fakes.generateCount)
        assertEquals(0, fakes.upsertCount)
    }

    @Test
    fun missingLocalProgressUsesTodaysRemotePuzzle() = runTest {
        val remoteStages = stages(target = 77)
        val fakes = SessionFakes(
            local = null,
            lookup = LocalePuzzleLookup(
                documentId = "doc-1",
                todaysPuzzle = PuzzleDay(
                    dayEpochMillis = 1_700_000_000_000L,
                    locale = "en-US",
                    stages = remoteStages
                )
            )
        )

        val session = fakes.loader().load("en-US")

        val fresh = session as DailySession.Fresh
        assertEquals(remoteStages, fresh.stages)
        assertEquals("doc-1", fresh.documentId)
        assertEquals(SyncIssue.NONE, fresh.syncIssue)
        assertEquals(0, fakes.generateCount)
        assertEquals(0, fakes.upsertCount)
    }

    @Test
    fun missingLocalProgressGeneratesOfflineWithoutUpsert() = runTest {
        val generated = stages(target = 42)
        val fakes = SessionFakes(
            local = null,
            online = false,
            generated = generated,
            endOfToday = 123L
        )

        val session = fakes.loader().load("en-US")

        val fresh = session as DailySession.Fresh
        assertEquals(generated, fresh.stages)
        assertEquals(123L, fresh.dayEpochMillis)
        assertEquals("en-US", fresh.locale)
        assertEquals(SyncIssue.NO_INTERNET, fresh.syncIssue)
        assertEquals(1, fakes.generateCount)
        assertEquals(0, fakes.lookupCount)
        assertEquals(0, fakes.upsertCount)
    }

    @Test
    fun missingRemotePuzzleGeneratesAndUpserts() = runTest {
        val generated = stages(target = 55)
        val fakes = SessionFakes(
            local = null,
            generated = generated,
            lookup = LocalePuzzleLookup(documentId = "doc-2", todaysPuzzle = null),
            endOfToday = 456L
        )

        val session = fakes.loader().load("en-US")

        val fresh = session as DailySession.Fresh
        assertEquals(generated, fresh.stages)
        assertEquals("doc-2", fresh.documentId)
        assertEquals(SyncIssue.NONE, fresh.syncIssue)
        assertEquals(1, fakes.generateCount)
        assertEquals(1, fakes.upsertCount)
    }

    private fun progressWithFirstStageCompleted(): DailyProgress {
        return DailyProgress(
            dayEpochMillis = 1_700_000_000_000L,
            stageIndex = 1,
            completed = false,
            stageLevels = listOf(
                StageLevel(index = 0, target = 39, completed = true, summary = "+×"),
                StageLevel(index = 1, target = 112, selected = true)
            ),
            stages = stages(target = 39) + stages(target = 112)
        )
    }

    private fun stages(target: Int): List<StagePuzzle> {
        return listOf(
            StagePuzzle(
                stageIndex = 0,
                target = target,
                operands = listOf(1, 2, 3, 4, 5, 10).mapIndexed { id, value -> Operand(id, value) }
            )
        )
    }

    private class SessionFakes(
        private val local: DailyProgress?,
        private val online: Boolean = true,
        private val lookup: LocalePuzzleLookup = LocalePuzzleLookup(null, null),
        private val generated: List<StagePuzzle> = emptyList(),
        private val endOfToday: Long = 0L
    ) {
        var generateCount = 0
        var lookupCount = 0
        var upsertCount = 0

        fun loader(): DailySessionLoader {
            return DailySessionLoader(
                loadToday = { local },
                lookupLocalePuzzle = {
                    lookupCount += 1
                    lookup
                },
                upsertPuzzle = { _, existingId ->
                    upsertCount += 1
                    existingId ?: "new-id"
                },
                isOnline = { online },
                generateStages = {
                    generateCount += 1
                    generated
                },
                endOfTodayMillis = { endOfToday }
            )
        }
    }
}
