package com.lkovari.mobile.apps.digits.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lkovari.mobile.apps.digits.data.AndroidNetworkStatusChecker
import com.lkovari.mobile.apps.digits.data.NetworkStatusChecker
import com.lkovari.mobile.apps.digits.data.SyncIssue
import com.lkovari.mobile.apps.digits.data.SyncIssueMessages
import com.lkovari.mobile.apps.digits.data.firestore.PuzzleFirestoreRepository
import com.lkovari.mobile.apps.digits.data.prefs.ProgressRepository
import com.lkovari.mobile.apps.digits.domain.DailyProgress
import com.lkovari.mobile.apps.digits.domain.EngineEvent
import com.lkovari.mobile.apps.digits.domain.GameEngine
import com.lkovari.mobile.apps.digits.domain.GameOperation
import com.lkovari.mobile.apps.digits.domain.Operand
import com.lkovari.mobile.apps.digits.domain.Operator
import com.lkovari.mobile.apps.digits.domain.PuzzleDay
import com.lkovari.mobile.apps.digits.domain.PuzzleGenerator
import com.lkovari.mobile.apps.digits.domain.StageLevel
import com.lkovari.mobile.apps.digits.domain.StagePuzzle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GameUiState(
    val loading: Boolean = false,
    val dateLabel: String = "",
    val stageLevels: List<StageLevel> = emptyList(),
    val stageIndex: Int = 0,
    val target: Int = 0,
    val operands: List<Operand> = emptyList(),
    val selectedOperator: Operator? = null,
    val welcomeVisible: Boolean = false,
    val stageCompleteVisible: Boolean = false,
    val stageCompleteMessages: List<String> = emptyList(),
    val allCompleteVisible: Boolean = false,
    val allCompleteMessages: List<String> = emptyList(),
    val shareText: String? = null,
    val syncIssue: SyncIssue = SyncIssue.NONE,
    val errorMessage: String? = null,
    val retryVisible: Boolean = false
)

sealed class GameUserEvent {
    data class ShowMessage(val message: String) : GameUserEvent()
}

class DigitsViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val progressRepository = ProgressRepository(application)
    private val firestoreRepository = PuzzleFirestoreRepository()
    private val networkStatusChecker: NetworkStatusChecker = AndroidNetworkStatusChecker(application)
    private val generator = PuzzleGenerator()
    private val engine = GameEngine()

    private val stages = mutableListOf<StagePuzzle>()
    private var dayEpochMillis = PuzzleFirestoreRepository.endOfTodayMillis()
    private var firestoreDocumentId: String? = null
    private var localeTag = Locale.getDefault().toLanguageTag()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameUserEvent>()
    val events: SharedFlow<GameUserEvent> = _events.asSharedFlow()

    init {
        val generated = generator.generateStages()
        applyFreshPuzzle(
            generated,
            PuzzleFirestoreRepository.endOfTodayMillis(),
            localeTag
        )
        bootstrap()
    }

    fun onOperandClick(operandId: Int) {
        val state = _uiState.value
        if (state.loading || state.allCompleteVisible || state.stageCompleteVisible) {
            return
        }
        val result = engine.selectOperand(operandId, state.operands)
        handleEngineResult(result.operands, result.selectedOperator, result.event, result.operationSteps)
    }

    fun onOperatorClick(operator: Operator) {
        val state = _uiState.value
        if (state.loading || state.allCompleteVisible || state.stageCompleteVisible) {
            return
        }
        val result = engine.selectOperator(operator, state.operands)
        handleEngineResult(result.operands, result.selectedOperator, result.event, result.operationSteps)
    }

    fun dismissWelcome() {
        _uiState.update { it.copy(welcomeVisible = false) }
    }

    fun dismissSyncBanner() {
        _uiState.update {
            it.copy(
                syncIssue = SyncIssue.NONE,
                errorMessage = null,
                retryVisible = false
            )
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            _uiState.update { it.copy(retryVisible = false) }
            loadRemoteOrOffline(preferExistingBoard = true)
        }
    }

    fun dismissStageComplete() {
        val state = _uiState.value
        if (state.allCompleteVisible || state.stageLevels.all { it.completed }) {
            _uiState.update { it.copy(stageCompleteVisible = false) }
            return
        }
        _uiState.update {
            it.copy(
                stageCompleteVisible = false,
                shareText = null
            )
        }
        persist(completed = false)
    }

    fun consumeShareText() {
        _uiState.update { it.copy(shareText = null) }
    }

    private fun handleEngineResult(
        operands: List<Operand>,
        selectedOperator: Operator?,
        event: EngineEvent,
        operationSteps: List<GameOperation>
    ) {
        when (event) {
            EngineEvent.InvalidOperation -> {
                viewModelScope.launch {
                    _events.emit(GameUserEvent.ShowMessage("Invalid operation"))
                }
                _uiState.update {
                    it.copy(operands = operands, selectedOperator = null)
                }
            }
            is EngineEvent.OperationApplied -> {
                val target = _uiState.value.target
                stages[_uiState.value.stageIndex] =
                    stages[_uiState.value.stageIndex].copy(operands = operands)
                if (event.result == target) {
                    onStageSolved(operationSteps)
                } else {
                    _uiState.update {
                        it.copy(
                            operands = operands,
                            selectedOperator = selectedOperator
                        )
                    }
                    persist(completed = false)
                }
            }
            EngineEvent.Undone, EngineEvent.None -> {
                if (stages.isNotEmpty()) {
                    stages[_uiState.value.stageIndex] =
                        stages[_uiState.value.stageIndex].copy(operands = operands)
                }
                _uiState.update {
                    it.copy(
                        operands = operands,
                        selectedOperator = selectedOperator
                    )
                }
                persist(completed = false)
            }
        }
    }

    private fun onStageSolved(operationSteps: List<GameOperation>) {
        val state = _uiState.value
        val equations = operationSteps.map { it.formatEquation() }
        val compact = operationSteps.joinToString("") { it.operator.symbol }
        val levels = state.stageLevels.mapIndexed { index, level ->
            if (index == state.stageIndex) {
                level.copy(completed = true, summary = compact, selected = false)
            } else {
                level
            }
        }
        engine.clearAllHistory()
        val nextIndex = state.stageIndex + 1
        if (nextIndex >= stages.size) {
            val messages = levels.map { level ->
                if (level.summary.isNotBlank()) {
                    "${level.target} -> ${level.summary}"
                } else {
                    level.target.toString()
                }
            }
            val share = buildString {
                appendLine("Genius!")
                messages.forEach { appendLine(it) }
            }
            _uiState.update {
                it.copy(
                    stageLevels = levels,
                    stageCompleteVisible = false,
                    allCompleteVisible = true,
                    allCompleteMessages = messages,
                    shareText = share.trim(),
                    selectedOperator = null,
                    operands = stages[state.stageIndex].operands
                )
            }
            persist(completed = true, stageIndexOverride = state.stageIndex)
            return
        }

        val nextStage = stages[nextIndex]
        val resetOperands = nextStage.operands.map { it.copy(selected = false, disabled = false) }
        stages[nextIndex] = nextStage.copy(operands = resetOperands)
        val levelsForNext = levels.mapIndexed { index, level ->
            level.copy(selected = index == nextIndex)
        }
        _uiState.update {
            it.copy(
                stageLevels = levelsForNext,
                stageIndex = nextIndex,
                target = nextStage.target,
                operands = resetOperands,
                stageCompleteVisible = true,
                stageCompleteMessages = equations,
                selectedOperator = null,
                shareText = null
            )
        }
        persist(completed = false, stageIndexOverride = nextIndex)
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _uiState.update { it.copy(dateLabel = todayLabel()) }
            val local = progressRepository.loadToday()
            if (local != null) {
                applyProgress(local, showWelcome = !local.completed)
                if (local.completed) {
                    val messages = local.stageLevels.map { level ->
                        if (level.summary.isNotBlank()) {
                            "${level.target} -> ${level.summary}"
                        } else {
                            level.target.toString()
                        }
                    }
                    _uiState.update {
                        it.copy(
                            allCompleteVisible = true,
                            allCompleteMessages = messages,
                            shareText = null
                        )
                    }
                }
                if (!networkStatusChecker.isOnline()) {
                    applySyncIssue(SyncIssue.NO_INTERNET)
                }
                return@launch
            }
            loadRemoteOrOffline(preferExistingBoard = true)
        }
    }

    private suspend fun loadRemoteOrOffline(preferExistingBoard: Boolean) {
        val online = networkStatusChecker.isOnline()
        if (!online) {
            if (!preferExistingBoard || stages.isEmpty()) {
                startOfflinePuzzle(SyncIssue.NO_INTERNET)
            } else {
                applySyncIssue(SyncIssue.NO_INTERNET)
                _uiState.update { it.copy(loading = false) }
            }
            return
        }

        try {
            val lookup = firestoreRepository.lookupLocalePuzzle(localeTag)
            firestoreDocumentId = lookup.documentId
            val remote = lookup.todaysPuzzle
            if (remote != null && remote.stages.isNotEmpty()) {
                if (stages.isEmpty() || _uiState.value.welcomeVisible) {
                    applyFreshPuzzle(remote.stages, remote.dayEpochMillis, remote.locale)
                }
                clearSyncIssue()
                _uiState.update { it.copy(loading = false) }
            } else {
                val generated = if (stages.isEmpty()) {
                    generator.generateStages()
                } else {
                    stages.toList()
                }
                val puzzle = PuzzleDay(
                    dayEpochMillis = PuzzleFirestoreRepository.endOfTodayMillis(),
                    locale = localeTag,
                    stages = generated
                )
                try {
                    firestoreDocumentId = firestoreRepository.upsertPuzzle(puzzle, firestoreDocumentId)
                    clearSyncIssue()
                } catch (error: Exception) {
                    applySyncIssue(SyncIssueMessages.classify(true, error))
                }
                if (stages.isEmpty()) {
                    applyFreshPuzzle(generated, puzzle.dayEpochMillis, localeTag)
                }
                _uiState.update { it.copy(loading = false) }
            }
        } catch (error: Exception) {
            val issue = SyncIssueMessages.classify(online, error)
            if (!preferExistingBoard || stages.isEmpty()) {
                startOfflinePuzzle(issue)
            } else {
                applySyncIssue(issue)
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    private fun startOfflinePuzzle(issue: SyncIssue) {
        val generated = generator.generateStages()
        applyFreshPuzzle(
            generated,
            PuzzleFirestoreRepository.endOfTodayMillis(),
            localeTag
        )
        applySyncIssue(issue)
    }

    private fun applySyncIssue(issue: SyncIssue) {
        _uiState.update {
            it.copy(
                syncIssue = issue,
                errorMessage = null,
                retryVisible = issue != SyncIssue.NONE
            )
        }
    }

    private fun clearSyncIssue() {
        _uiState.update {
            it.copy(
                syncIssue = SyncIssue.NONE,
                errorMessage = null,
                retryVisible = false
            )
        }
    }

    private fun applyFreshPuzzle(stageList: List<StagePuzzle>, day: Long, locale: String) {
        localeTag = locale
        dayEpochMillis = day
        stages.clear()
        stages.addAll(
            stageList.map { stage ->
                stage.copy(operands = stage.operands.map { it.copy(selected = false, disabled = false) })
            }
        )
        val levels = stages.mapIndexed { index, stage ->
            StageLevel(
                index = index,
                target = stage.target,
                selected = index == 0
            )
        }
        engine.clearAllHistory()
        val previousIssue = _uiState.value.syncIssue
        val previousError = _uiState.value.errorMessage
        val previousRetry = _uiState.value.retryVisible
        _uiState.value = GameUiState(
            loading = false,
            dateLabel = todayLabel(),
            stageLevels = levels,
            stageIndex = 0,
            target = stages.first().target,
            operands = stages.first().operands,
            welcomeVisible = true,
            syncIssue = previousIssue,
            errorMessage = previousError,
            retryVisible = previousRetry
        )
        persist(completed = false)
    }

    private fun applyProgress(progress: DailyProgress, showWelcome: Boolean) {
        dayEpochMillis = progress.dayEpochMillis
        stages.clear()
        stages.addAll(
            progress.stages.map { stage ->
                stage.copy(operands = stage.operands.map { it.copy(selected = false) })
            }
        )
        engine.clearAllHistory()
        val firstIncomplete = progress.stageLevels.indexOfFirst { !it.completed }
        val index = if (progress.completed || firstIncomplete < 0) {
            progress.stageIndex.coerceIn(0, stages.lastIndex.coerceAtLeast(0))
        } else {
            firstIncomplete.coerceIn(0, stages.lastIndex.coerceAtLeast(0))
        }
        val levels = progress.stageLevels.mapIndexed { i, level ->
            level.copy(selected = i == index && !progress.completed)
        }
        val resumeOperands = if (!progress.completed && !levels[index].completed) {
            stages[index].operands.map { it.copy(selected = false) }
        } else {
            stages[index].operands
        }
        if (!progress.completed) {
            stages[index] = stages[index].copy(operands = resumeOperands)
        }
        _uiState.value = GameUiState(
            loading = false,
            dateLabel = todayLabel(),
            stageLevels = levels,
            stageIndex = index,
            target = stages[index].target,
            operands = resumeOperands,
            welcomeVisible = showWelcome && !progress.completed
        )
    }

    private fun persist(completed: Boolean, stageIndexOverride: Int? = null) {
        val state = _uiState.value
        if (state.loading || stages.isEmpty()) {
            return
        }
        viewModelScope.launch {
            progressRepository.save(
                DailyProgress(
                    dayEpochMillis = dayEpochMillis,
                    stageIndex = stageIndexOverride ?: state.stageIndex,
                    completed = completed,
                    stageLevels = state.stageLevels,
                    stages = stages.toList()
                )
            )
        }
    }

    private fun todayLabel(): String {
        return SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
    }
}
