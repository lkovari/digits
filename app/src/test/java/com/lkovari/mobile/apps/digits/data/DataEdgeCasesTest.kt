package com.lkovari.mobile.apps.digits.data

import com.lkovari.mobile.apps.digits.data.firestore.PuzzleFirestoreRepository
import com.lkovari.mobile.apps.digits.domain.DailyProgress
import com.lkovari.mobile.apps.digits.domain.Operand
import com.lkovari.mobile.apps.digits.domain.PuzzleDay
import com.lkovari.mobile.apps.digits.domain.StageLevel
import com.lkovari.mobile.apps.digits.domain.StagePuzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.Calendar

class SyncIssueMessagesTest {
    @Test
    fun noErrorMeansNone() {
        assertEquals(SyncIssue.NONE, SyncIssueMessages.classify(true, null))
        assertNull(SyncIssueMessages.message(SyncIssue.NONE))
    }

    @Test
    fun offlineFlagMapsToNoInternet() {
        assertEquals(
            SyncIssue.NO_INTERNET,
            SyncIssueMessages.classify(false, IOException("boom"))
        )
    }

    @Test
    fun networkishExceptionsMapToNoInternetWhenOnlineFlagTrue() {
        assertEquals(
            SyncIssue.NO_INTERNET,
            SyncIssueMessages.classify(true, UnknownHostException("Unable to resolve host"))
        )
        assertEquals(
            SyncIssue.NO_INTERNET,
            SyncIssueMessages.classify(true, IOException("connection timeout"))
        )
    }

    @Test
    fun otherExceptionsMapToDatabaseUnavailable() {
        assertEquals(
            SyncIssue.DATABASE_UNAVAILABLE,
            SyncIssueMessages.classify(true, IllegalStateException("permission-denied"))
        )
    }

    @Test
    fun messagesAreUserFacing() {
        assertTrue(SyncIssueMessages.message(SyncIssue.NO_INTERNET)!!.contains("internet", ignoreCase = true))
        assertTrue(
            SyncIssueMessages.message(SyncIssue.DATABASE_UNAVAILABLE)!!.contains("database", ignoreCase = true)
        )
    }
}

class PuzzleDataCodecTest {
    @Test
    fun puzzleDayRoundTrip() {
        val puzzle = PuzzleDay(
            dayEpochMillis = 1_700_000_000_000L,
            locale = "en-US",
            stages = listOf(
                StagePuzzle(
                    stageIndex = 0,
                    target = 39,
                    operands = listOf(1, 2, 3, 4, 5, 10).mapIndexed { i, v -> Operand(i, v) }
                )
            )
        )
        val encoded = PuzzleDataCodec.serializePuzzleDay(puzzle)
        val decoded = PuzzleDataCodec.parsePuzzleDay(encoded)!!
        assertEquals(puzzle.dayEpochMillis, decoded.dayEpochMillis)
        assertEquals(puzzle.locale, decoded.locale)
        assertEquals(39, decoded.stages[0].target)
        assertEquals(listOf(1, 2, 3, 4, 5, 10), decoded.stages[0].operands.map { it.value })
    }

    @Test
    fun progressRoundTripPreservesDisabledFlags() {
        val progress = DailyProgress(
            dayEpochMillis = 1_700_000_000_000L,
            stageIndex = 2,
            completed = false,
            stageLevels = listOf(
                StageLevel(0, 39, completed = true, summary = "+×"),
                StageLevel(1, 112, selected = true)
            ),
            stages = listOf(
                StagePuzzle(
                    0,
                    39,
                    listOf(Operand(0, 10, disabled = true), Operand(1, 29))
                )
            )
        )
        val decoded = PuzzleDataCodec.parseProgress(PuzzleDataCodec.serializeProgress(progress))!!
        assertEquals(2, decoded.stageIndex)
        assertTrue(decoded.stageLevels[0].completed)
        assertEquals("+×", decoded.stageLevels[0].summary)
        assertTrue(decoded.stages[0].operands[0].disabled)
        assertEquals(29, decoded.stages[0].operands[1].value)
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(PuzzleDataCodec.parsePuzzleDay("{"))
        assertNull(PuzzleDataCodec.parseProgress("not-json"))
    }
}

class PuzzleFirestoreHelpersTest {
    @Test
    fun localeNormalizationAndPrimaryLanguage() {
        assertEquals("en-us", PuzzleFirestoreRepository.normalize(" En-US "))
        assertEquals("en", PuzzleFirestoreRepository.primaryLanguage("en-US"))
        assertEquals("hu", PuzzleFirestoreRepository.primaryLanguage("hu_HU"))
    }

    @Test
    fun sameCalendarDayIgnoresTimeOfDay() {
        val morning = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 27, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val evening = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 27, 22, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val nextDay = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 28, 1, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertTrue(PuzzleFirestoreRepository.isSameCalendarDay(morning, evening))
        assertFalse(PuzzleFirestoreRepository.isSameCalendarDay(morning, nextDay))
    }

    @Test
    fun endOfTodayIsLateEvening() {
        val end = PuzzleFirestoreRepository.endOfTodayMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
    }
}
