package com.lkovari.mobile.apps.digits.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.lkovari.mobile.apps.digits.data.PuzzleDataCodec
import com.lkovari.mobile.apps.digits.domain.PuzzleDay
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

data class LocalePuzzleLookup(
    val documentId: String?,
    val todaysPuzzle: PuzzleDay?
)

class PuzzleFirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val collection = firestore.collection(COLLECTION)

    suspend fun lookupLocalePuzzle(localeTag: String): LocalePuzzleLookup {
        val docs = collection.get().await()
        val normalized = normalize(localeTag)
        val primary = primaryLanguage(normalized)
        val match = docs.documents.firstOrNull { doc ->
            val locale = normalize(doc.getString(FIELD_LOCALE).orEmpty())
            locale == normalized || locale == primary || primaryLanguage(locale) == primary
        } ?: return LocalePuzzleLookup(documentId = null, todaysPuzzle = null)

        val dataJson = match.getString(FIELD_DATA) ?: return LocalePuzzleLookup(match.id, null)
        val puzzle = PuzzleDataCodec.parsePuzzleDay(dataJson) ?: return LocalePuzzleLookup(match.id, null)
        val locale = match.getString(FIELD_LOCALE) ?: localeTag
        val withLocale = PuzzleDay(
            dayEpochMillis = puzzle.dayEpochMillis,
            locale = locale,
            stages = puzzle.stages
        )
        val todays = if (isSameCalendarDay(withLocale.dayEpochMillis, System.currentTimeMillis())) {
            withLocale
        } else {
            null
        }
        return LocalePuzzleLookup(documentId = match.id, todaysPuzzle = todays)
    }

    suspend fun upsertPuzzle(puzzle: PuzzleDay, existingDocumentId: String?): String {
        val payload = hashMapOf(
            FIELD_LOCALE to puzzle.locale,
            FIELD_DATA to PuzzleDataCodec.serializePuzzleDay(puzzle)
        )
        return if (existingDocumentId.isNullOrBlank()) {
            collection.add(payload).await().id
        } else {
            collection.document(existingDocumentId).set(payload).await()
            existingDocumentId
        }
    }

    companion object {
        const val COLLECTION = "puzzledata"
        const val FIELD_LOCALE = "locale"
        const val FIELD_DATA = "data"

        fun normalize(locale: String): String {
            return locale.trim().lowercase(Locale.ROOT)
        }

        fun primaryLanguage(locale: String): String {
            return normalize(locale).substringBefore('-').substringBefore('_')
        }

        fun endOfTodayMillis(now: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return calendar.timeInMillis
        }

        fun isSameCalendarDay(a: Long, b: Long): Boolean {
            val calA = Calendar.getInstance().apply { timeInMillis = a }
            val calB = Calendar.getInstance().apply { timeInMillis = b }
            return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
                calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
        }
    }
}
