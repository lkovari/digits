package com.lkovari.mobile.apps.digits.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lkovari.mobile.apps.digits.data.PuzzleDataCodec
import com.lkovari.mobile.apps.digits.data.firestore.PuzzleFirestoreRepository
import com.lkovari.mobile.apps.digits.domain.DailyProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.digitsDataStore by preferencesDataStore(name = "numbers_progress")

class ProgressRepository(private val context: Context) {
    private val key = stringPreferencesKey("daily_progress")

    suspend fun loadToday(): DailyProgress? {
        val raw = context.digitsDataStore.data.map { prefs -> prefs[key] }.first() ?: return null
        val progress = PuzzleDataCodec.parseProgress(raw) ?: return null
        if (!PuzzleFirestoreRepository.isSameCalendarDay(progress.dayEpochMillis, System.currentTimeMillis())) {
            return null
        }
        return progress
    }

    suspend fun save(progress: DailyProgress) {
        context.digitsDataStore.edit { prefs ->
            prefs[key] = PuzzleDataCodec.serializeProgress(progress)
        }
    }

    suspend fun clear() {
        context.digitsDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}
