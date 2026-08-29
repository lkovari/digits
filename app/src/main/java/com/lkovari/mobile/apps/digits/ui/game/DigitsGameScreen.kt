package com.lkovari.mobile.apps.digits.ui.game

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lkovari.mobile.apps.digits.R
import com.lkovari.mobile.apps.digits.data.SyncIssue
import com.lkovari.mobile.apps.digits.domain.Operand
import com.lkovari.mobile.apps.digits.domain.Operator
import com.lkovari.mobile.apps.digits.domain.StageLevel
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBlue
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBlueLight
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBluePastel
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBlueWash
import com.lkovari.mobile.apps.digits.ui.theme.NumbersCompletedPastel
import com.lkovari.mobile.apps.digits.ui.theme.NumbersIncompletePastel
import com.lkovari.mobile.apps.digits.ui.theme.NumbersMagenta
import com.lkovari.mobile.apps.digits.ui.theme.NumbersSelected
import kotlinx.coroutines.flow.collectLatest

private val OperandButtonDiameter = 90.dp
private val OperatorButtonDiameter = OperandButtonDiameter * 0.75f

private enum class InfoDialog {
    None,
    About,
    Help
}

@Composable
fun DigitsGameScreen(
    modifier: Modifier = Modifier,
    viewModel: DigitsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(InfoDialog.None) }
    val invalidOperationText = stringResource(R.string.invalid_operation)
    val shareChooserTitle = stringResource(R.string.share_result)
    val geniusHeader = stringResource(R.string.genius_share_header)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GameUserEvent.ShowMessage -> {
                    val message = if (event.message == "Invalid operation") {
                        invalidOperationText
                    } else {
                        event.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(state.shareText) {
        val text = state.shareText ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, shareChooserTitle))
        viewModel.consumeShareText()
    }

    BackHandler(
        enabled = infoDialog != InfoDialog.None ||
            state.welcomeVisible ||
            state.stageCompleteVisible ||
            state.allCompleteVisible ||
            menuExpanded
    ) {
        when {
            menuExpanded -> menuExpanded = false
            infoDialog != InfoDialog.None -> infoDialog = InfoDialog.None
            state.welcomeVisible -> viewModel.dismissWelcome()
            state.stageCompleteVisible -> viewModel.dismissStageComplete()
            state.allCompleteVisible -> viewModel.dismissAllComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NumbersBlueWash)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (state.loading && state.operands.isEmpty()) {
            CircularProgressIndicator(
                color = NumbersBlue,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.title_numbers_digits_game),
                            color = NumbersMagenta,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.menu_more)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_about)) },
                                    onClick = {
                                        menuExpanded = false
                                        infoDialog = InfoDialog.About
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_help)) },
                                    onClick = {
                                        menuExpanded = false
                                        infoDialog = InfoDialog.Help
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.intro_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.title_numbers),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.dateLabel,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    StageLevelsRow(levels = state.stageLevels)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.instruction_reach_target),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.target.toString(),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = NumbersBlue
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OperandGrid(
                        operands = state.operands,
                        onClick = viewModel::onOperandClick
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                OperatorRow(
                    selected = state.selectedOperator,
                    onClick = viewModel::onOperatorClick
                )
                if (state.syncIssue != SyncIssue.NONE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SyncErrorBanner(
                            message = syncIssueMessage(state.syncIssue),
                            retryVisible = state.retryVisible,
                            onRetry = viewModel::retrySync,
                            onDismiss = viewModel::dismissSyncBanner
                        )
                    }
                }
            }
        }

        if (state.welcomeVisible) {
            InfoPanelDialog(
                onDismiss = viewModel::dismissWelcome,
                confirmLabel = stringResource(R.string.play)
            ) {
                Text(
                    text = stringResource(R.string.title_numbers_digits_game),
                    color = NumbersMagenta,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.welcome_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.welcome_body))
            }
        }

        if (state.stageCompleteVisible) {
            AlertDialog(
                onDismissRequest = viewModel::dismissStageComplete,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                title = { Text(stringResource(R.string.stage_completed_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.stage_completed_nice))
                        state.stageCompleteMessages.forEach { line ->
                            Text(line)
                        }
                        Text(stringResource(R.string.stage_completed_next_hint))
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissStageComplete) {
                        Text(stringResource(R.string.next))
                    }
                }
            )
        }

        if (state.allCompleteVisible) {
            AlertDialog(
                onDismissRequest = viewModel::dismissAllComplete,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                title = { Text(stringResource(R.string.all_completed_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.all_completed_body))
                        state.allCompleteMessages.forEach { line ->
                            Text(line)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val text = buildString {
                                appendLine(geniusHeader)
                                state.allCompleteMessages.forEach { appendLine(it) }
                            }.trim()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                        }
                    ) {
                        Text(stringResource(R.string.share))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissAllComplete) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        when (infoDialog) {
            InfoDialog.About -> AboutDialog(onDismiss = { infoDialog = InfoDialog.None })
            InfoDialog.Help -> HelpDialog(onDismiss = { infoDialog = InfoDialog.None })
            InfoDialog.None -> Unit
        }
    }
}

@Composable
private fun syncIssueMessage(issue: SyncIssue): String {
    return when (issue) {
        SyncIssue.NONE -> ""
        SyncIssue.NO_INTERNET -> stringResource(R.string.sync_no_internet)
        SyncIssue.DATABASE_UNAVAILABLE -> stringResource(R.string.sync_database_unavailable)
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repoUrl = stringResource(R.string.about_repo_url)
    InfoPanelDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.about_title),
            color = NumbersMagenta,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.about_origin))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.about_repo_label))
        Text(
            text = repoUrl,
            color = NumbersBlue,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.about_author_years))
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    InfoPanelDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.title_numbers_digits_game),
            color = NumbersMagenta,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.help_title),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = stringResource(R.string.help_body))
        }
    }
}

@Composable
private fun InfoPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val panelShape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(panelShape)
            .background(NumbersBluePastel)
            .border(2.dp, NumbersBlue, panelShape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun InfoPanelDialog(
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.close),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        InfoPanel(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            content()
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@Composable
private fun SyncErrorBanner(
    message: String,
    retryVisible: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFFFFEBEE))
            .border(1.dp, Color(0xFFE57373), MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Text(
            text = message,
            color = Color(0xFFB71C1C),
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
            if (retryVisible) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun StageLevelsRow(levels: List<StageLevel>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        levels.forEach { level ->
            val bg = when {
                level.completed -> NumbersCompletedPastel
                level.selected -> NumbersSelected
                else -> NumbersIncompletePastel
            }
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(bg)
                    .border(1.dp, NumbersBlue.copy(alpha = 0.35f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.target.toString(),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OperandGrid(
    operands: List<Operand>,
    onClick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        operands.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { operand ->
                    OperandButton(operand = operand, onClick = { onClick(operand.id) })
                }
            }
        }
    }
}

@Composable
private fun OperandButton(
    operand: Operand,
    onClick: () -> Unit
) {
    val borderColor = when {
        operand.disabled -> Color.LightGray
        operand.selected -> NumbersBlue
        else -> NumbersBlue.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .size(OperandButtonDiameter)
            .clip(CircleShape)
            .border(
                width = if (operand.selected) 3.dp else 2.dp,
                color = borderColor,
                shape = CircleShape
            )
            .background(
                if (operand.selected) NumbersBlueLight.copy(alpha = 0.45f) else Color.Transparent
            )
            .clickable(enabled = !operand.disabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = operand.value.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = if (operand.disabled) Color.Gray else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun OperatorRow(
    selected: Operator?,
    onClick: (Operator) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OperatorButton(
            operator = Operator.UNDO,
            selected = selected == Operator.UNDO,
            icon = Icons.AutoMirrored.Filled.Undo,
            onClick = onClick
        )
        OperatorButton(
            operator = Operator.ADD,
            selected = selected == Operator.ADD,
            icon = Icons.Filled.Add,
            onClick = onClick
        )
        OperatorButton(
            operator = Operator.SUB,
            selected = selected == Operator.SUB,
            icon = Icons.Filled.Remove,
            onClick = onClick
        )
        OperatorButton(
            operator = Operator.MUL,
            selected = selected == Operator.MUL,
            icon = Icons.Filled.Close,
            onClick = onClick
        )
        OperatorButton(
            operator = Operator.DIV,
            selected = selected == Operator.DIV,
            label = "÷",
            onClick = onClick
        )
    }
}

@Composable
private fun OperatorButton(
    operator: Operator,
    selected: Boolean,
    onClick: (Operator) -> Unit,
    icon: ImageVector? = null,
    label: String? = null
) {
    val bg = if (selected) NumbersSelected else NumbersBlueLight.copy(alpha = 0.55f)
    val glyphSize = OperatorButtonDiameter * 0.43f
    Box(
        modifier = Modifier
            .size(OperatorButtonDiameter)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick(operator) },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = operator.symbol,
                tint = NumbersBlue,
                modifier = Modifier.size(glyphSize)
            )
        } else {
            Text(
                text = label ?: operator.symbol,
                fontSize = glyphSize.value.sp,
                color = NumbersBlue
            )
        }
    }
}
