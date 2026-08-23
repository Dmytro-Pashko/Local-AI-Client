package com.dpashko.localaiclient.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dpashko.localaiclient.domain.models.conversation.MessageRole
import com.dpashko.localaiclient.domain.models.conversation.MessageStatus
import com.dpashko.localaiclient.presentation.R
import com.dpashko.localaiclient.presentation.ui.models.MessageUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connects the chat view model to the stateless chat UI and its user actions.
 */
@Composable
fun ChatRoute(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    ChatScreen(
        state = state,
        onBack = onBack,
        onMessageChanged = viewModel::onMessageChanged,
        onSendClick = viewModel::sendMessage,
        onCancelEditClick = viewModel::cancelEditingMessage,
        onEditClick = { message -> viewModel.startEditingMessage(message.id, message.content) },
        onRetryClick = viewModel::retryGeneration,
        onRegenerateLastAssistantResponse = viewModel::regenerateLastAssistantResponse,
        onBranchFromMessage = viewModel::branchFromMessage,
        onSwitchBranch = viewModel::switchBranch,
        onCompactConversation = viewModel::compactConversation,
        onStopGenerationClick = viewModel::stopGeneration,
        onSearchQueryChanged = viewModel::onChatSearchQueryChanged,
        onPreviousSearchMatch = viewModel::moveToPreviousSearchMatch,
        onNextSearchMatch = viewModel::moveToNextSearchMatch,
        onSaveConversationSettings = viewModel::saveConversationSettings,
    )
}

/**
 * Preview of the regular conversation mode used while composing and reading messages.
 */
@Preview
@Composable
private fun ChatScreenPreview() {
    return ChatScreen(
        state = ChatUiState(
            messages = listOf(
                MessageUi(
                    id = 1,
                    role = MessageRole.USER,
                    content = "Hello, how are you?",
                    status = MessageStatus.SENT,
                    errorMessage = null,
                    createdAtMillis = System.currentTimeMillis() - 60_000,
                    createdAtText = "1 min ago",
                ),
                MessageUi(
                    id = 2,
                    role = MessageRole.ASSISTANT,
                    content = "I'm good, thank you! How can I assist you today?",
                    status = MessageStatus.SENT,
                    errorMessage = null,
                    createdAtMillis = System.currentTimeMillis() - 30_000,
                    createdAtText = "30 sec ago",
                ),
            ),
            messageText = "",
            isSending = false,
            hasGeneratingMessage = false,
            editingMessageId = null,
            chatSearchQuery = "",
            searchMatchMessageIds = emptyList(),
            currentSearchMatchIndex = 0,
            modelName = "gpt-4",
        ),
        onBack = {},
        onMessageChanged = {},
        onSendClick = {},
        onCancelEditClick = {},
        onEditClick = {},
        onRetryClick = {},
        onRegenerateLastAssistantResponse = {},
        onBranchFromMessage = {},
        onSwitchBranch = {},
        onCompactConversation = {},
        onStopGenerationClick = {},
        onSearchQueryChanged = {},
        onPreviousSearchMatch = {},
        onNextSearchMatch = {},
        onSaveConversationSettings = { _, _, _ -> },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onMessageChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onEditClick: (MessageUi) -> Unit,
    onRetryClick: (Long) -> Unit,
    onRegenerateLastAssistantResponse: () -> Unit,
    onBranchFromMessage: (Long) -> Unit,
    onSwitchBranch: (Long) -> Unit,
    onCompactConversation: () -> Unit,
    onStopGenerationClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onPreviousSearchMatch: () -> Unit,
    onNextSearchMatch: () -> Unit,
    onSaveConversationSettings: (String, String, String) -> Unit,
) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val hasGeneratingMessage = state.messages.any { it.status == MessageStatus.GENERATING }
    val hasAssistantResponse = state.messages.any { it.role == MessageRole.ASSISTANT }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var actionMessage by remember { mutableStateOf<MessageUi?>(null) }
    var isSearchMode by remember { mutableStateOf(false) }
    var isToolbarMenuExpanded by remember { mutableStateOf(false) }
    var isStopGenerationDialogVisible by remember { mutableStateOf(false) }
    var isCompactionDialogVisible by remember { mutableStateOf(false) }
    var isSettingsDialogVisible by remember { mutableStateOf(false) }
    var settingsSystemPrompt by remember { mutableStateOf("") }

    ChatDialogs(
        state = state,
        isSettingsDialogVisible = isSettingsDialogVisible,
        settingsSystemPrompt = settingsSystemPrompt,
        isStopGenerationDialogVisible = isStopGenerationDialogVisible,
        isCompactionDialogVisible = isCompactionDialogVisible,
        onSettingsDialogVisibleChanged = { isSettingsDialogVisible = it },
        onSettingsSystemPromptChanged = { settingsSystemPrompt = it },
        onSaveConversationSettings = onSaveConversationSettings,
        onStopGenerationDialogVisibleChanged = { isStopGenerationDialogVisible = it },
        onCompactionDialogVisibleChanged = { isCompactionDialogVisible = it },
        onStopGenerationClick = onStopGenerationClick,
        onCompactConversation = onCompactConversation,
    )

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.currentSearchMatchMessageId) {
        val messageId = state.currentSearchMatchMessageId ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(hasGeneratingMessage) {
        while (hasGeneratingMessage) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
        nowMillis = System.currentTimeMillis()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                state = state,
                isSearchMode = isSearchMode,
                isToolbarMenuExpanded = isToolbarMenuExpanded,
                hasAssistantResponse = hasAssistantResponse,
                onBack = onBack,
                onSearchModeChanged = { isSearchMode = it },
                onSearchQueryChanged = onSearchQueryChanged,
                onToolbarMenuExpandedChanged = { isToolbarMenuExpanded = it },
                onStopGenerationDialogVisibleChanged = { isStopGenerationDialogVisible = it },
                onCompactionDialogVisibleChanged = { isCompactionDialogVisible = it },
                onSettingsDialogVisibleChanged = { isSettingsDialogVisible = it },
                onSettingsSystemPromptChanged = { settingsSystemPrompt = it },
                onRegenerateLastAssistantResponse = onRegenerateLastAssistantResponse,
                onSwitchBranch = onSwitchBranch,
                onPreviousSearchMatch = onPreviousSearchMatch,
                onNextSearchMatch = onNextSearchMatch,
            )
        },
        bottomBar = {
            MessageInputBar(
                messageText = state.messageText,
                isSending = state.isSending,
                hasGeneratingMessage = state.hasGeneratingMessage,
                isEditing = state.editingMessageId != null,
                onMessageChanged = onMessageChanged,
                onSendClick = onSendClick,
                onCancelEditClick = onCancelEditClick,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.messages.isEmpty()) {
                Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    text = "Start the conversation",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.contextEstimate?.let { estimate ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ContextEstimateChip(
                                tokenCount = estimate.estimatedTokens,
                                messageCount = estimate.messageCount,
                                characterCount = estimate.characterCount,
                            )
                            if (state.hasCompactionSummary) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Compacted") },
                                )
                            }
                            if (state.isCompactingConversation) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = if (state.contextEstimate == null) 16.dp else 0.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = state.messages,
                            key = { it.id },
                        ) { message ->
                            MessageBubble(
                                message = message,
                                isCurrentSearchMatch = message.id == state.currentSearchMatchMessageId,
                                isEditEnabled = !state.isSending,
                                isRetryEnabled = !state.isSending && !state.hasGeneratingMessage,
                                nowMillis = nowMillis,
                                isActionsMenuExpanded = actionMessage?.id == message.id,
                                onMessageLongPress = { actionMessage = message },
                                onDismissActionsMenu = { actionMessage = null },
                                onCopyClick = {
                                    clipboardManager.setText(AnnotatedString(message.displayText(nowMillis)))
                                    actionMessage = null
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Message copied")
                                    }
                                },
                            onEditClick = {
                                actionMessage = null
                                onEditClick(message)
                            },
                            onBranchClick = {
                                actionMessage = null
                                onBranchFromMessage(message.id)
                            },
                            onRetryClick = onRetryClick,
                        )
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}


/**
 * Renders the modal actions owned by the chat screen.
 *
 * Keeping dialog creation in one place makes the screen content easier to read and ensures
 * each dialog closes itself before invoking its corresponding action.
 */
@Composable
private fun ChatDialogs(
    state: ChatUiState,
    isSettingsDialogVisible: Boolean,
    settingsSystemPrompt: String,
    isStopGenerationDialogVisible: Boolean,
    isCompactionDialogVisible: Boolean,
    onSettingsDialogVisibleChanged: (Boolean) -> Unit,
    onSettingsSystemPromptChanged: (String) -> Unit,
    onSaveConversationSettings: (String, String, String) -> Unit,
    onStopGenerationDialogVisibleChanged: (Boolean) -> Unit,
    onCompactionDialogVisibleChanged: (Boolean) -> Unit,
    onStopGenerationClick: () -> Unit,
    onCompactConversation: () -> Unit,
) {
    if (isSettingsDialogVisible) {
        ConversationSettingsAlertDialog(
            systemPrompt = settingsSystemPrompt,
            onDismiss = { onSettingsDialogVisibleChanged(false) },
            onSystemPromptChanged = onSettingsSystemPromptChanged,
            onSave = {
                onSaveConversationSettings(
                    state.modelName,
                    state.generationTimeoutMinutes.toString(),
                    settingsSystemPrompt,
                )
                onSettingsDialogVisibleChanged(false)
            },
        )
    }

    if (isStopGenerationDialogVisible) {
        StopGenerationAlertDialog(
            isSending = state.isSending,
            onDismiss = { onStopGenerationDialogVisibleChanged(false) },
            onStop = {
                onStopGenerationDialogVisibleChanged(false)
                onStopGenerationClick()
            },
        )
    }

    if (isCompactionDialogVisible) {
        CompactConversationAlertDialog(
            isCompacting = state.isCompactingConversation,
            onDismiss = { onCompactionDialogVisibleChanged(false) },
            onCompact = {
                onCompactionDialogVisibleChanged(false)
                onCompactConversation()
            },
        )
    }
}

/**
 * Edits the system prompt and saves the conversation generation settings.
 */
@Composable
private fun ConversationSettingsAlertDialog(
    systemPrompt: String,
    onDismiss: () -> Unit,
    onSystemPromptChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation settings") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = systemPrompt,
                onValueChange = { onSystemPromptChanged(it.take(4_000)) },
                label = { Text("System prompt") },
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun StopGenerationAlertDialog(
    isSending: Boolean,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop generation?") },
        text = { Text("The current assistant response will be stopped.") },
        confirmButton = {
            TextButton(
                onClick = onStop,
                enabled = !isSending,
            ) {
                Text("Stop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CompactConversationAlertDialog(
    isCompacting: Boolean,
    onDismiss: () -> Unit,
    onCompact: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compact conversation?") },
        text = {
            Text(
                "Older messages stay in the chat. The local model creates a summary that is used only for future context.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onCompact,
                enabled = !isCompacting,
            ) {
                Text("Compact")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Preview
@Composable
private fun ConversationSettingsAlertDialogPreview() {
    ConversationSettingsAlertDialog(
        systemPrompt = "You are a helpful assistant.",
        onDismiss = {},
        onSystemPromptChanged = {},
        onSave = {},
    )
}

@Preview
@Composable
private fun StopGenerationAlertDialogPreview() {
    StopGenerationAlertDialog(
        isSending = false,
        onDismiss = {},
        onStop = {},
    )
}

@Preview
@Composable
private fun CompactConversationAlertDialogPreview() {
    CompactConversationAlertDialog(
        isCompacting = false,
        onDismiss = {},
        onCompact = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    state: ChatUiState,
    isSearchMode: Boolean,
    isToolbarMenuExpanded: Boolean,
    hasAssistantResponse: Boolean,
    onBack: () -> Unit,
    onSearchModeChanged: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onToolbarMenuExpandedChanged: (Boolean) -> Unit,
    onStopGenerationDialogVisibleChanged: (Boolean) -> Unit,
    onCompactionDialogVisibleChanged: (Boolean) -> Unit,
    onSettingsDialogVisibleChanged: (Boolean) -> Unit,
    onSettingsSystemPromptChanged: (String) -> Unit,
    onRegenerateLastAssistantResponse: () -> Unit,
    onSwitchBranch: (Long) -> Unit,
    onPreviousSearchMatch: () -> Unit,
    onNextSearchMatch: () -> Unit,
) {
    TopAppBar(
        title = {
            ChatTopBarTitle(
                state = state,
                isSearchMode = isSearchMode,
                onSearchQueryChanged = onSearchQueryChanged,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isSearchMode) {
                        onSearchModeChanged(false)
                        onSearchQueryChanged("")
                    } else {
                        onBack()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            ChatTopBarActions(
                state = state,
                isSearchMode = isSearchMode,
                isToolbarMenuExpanded = isToolbarMenuExpanded,
                hasAssistantResponse = hasAssistantResponse,
                onSearchModeChanged = onSearchModeChanged,
                onToolbarMenuExpandedChanged = onToolbarMenuExpandedChanged,
                onStopGenerationDialogVisibleChanged = onStopGenerationDialogVisibleChanged,
                onCompactionDialogVisibleChanged = onCompactionDialogVisibleChanged,
                onSettingsDialogVisibleChanged = onSettingsDialogVisibleChanged,
                onSettingsSystemPromptChanged = onSettingsSystemPromptChanged,
                onRegenerateLastAssistantResponse = onRegenerateLastAssistantResponse,
                onSwitchBranch = onSwitchBranch,
                onPreviousSearchMatch = onPreviousSearchMatch,
                onNextSearchMatch = onNextSearchMatch,
            )
        },
    )
}

/**
 * Displays the title for either search mode or the regular conversation mode.
 */
@Composable
private fun ChatTopBarTitle(
    state: ChatUiState,
    isSearchMode: Boolean,
    onSearchQueryChanged: (String) -> Unit,
) {
    if (isSearchMode) {
        SearchModeTitle(
            query = state.chatSearchQuery,
            matchCount = state.searchMatchMessageIds.size,
            currentMatchIndex = state.currentSearchMatchIndex,
            onQueryChanged = onSearchQueryChanged,
        )
    } else {
        ConversationModeTitle(
            modelName = state.modelName,
            branchTitle = state.activeBranchTitle,
        )
    }
}

@Composable
private fun SearchModeTitle(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    onQueryChanged: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Search") },
                singleLine = true,
            )
            IconButton(onClick = { onQueryChanged("") }) {
                Icon(
                    painter = painterResource(R.drawable.ic_backspace),
                    contentDescription = "Clear search",
                )
            }
        }
        Text(
            text = if (query.isBlank() || matchCount == 0) {
                "0/0"
            } else {
                "${currentMatchIndex + 1}/$matchCount"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ConversationModeTitle(
    modelName: String,
    branchTitle: String?,
) {
    Column {
        Text("Conversation")
        Text(
            text = listOfNotNull(modelName, branchTitle).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ChatTopBarActions(
    state: ChatUiState,
    isSearchMode: Boolean,
    isToolbarMenuExpanded: Boolean,
    hasAssistantResponse: Boolean,
    onSearchModeChanged: (Boolean) -> Unit,
    onToolbarMenuExpandedChanged: (Boolean) -> Unit,
    onStopGenerationDialogVisibleChanged: (Boolean) -> Unit,
    onCompactionDialogVisibleChanged: (Boolean) -> Unit,
    onSettingsDialogVisibleChanged: (Boolean) -> Unit,
    onSettingsSystemPromptChanged: (String) -> Unit,
    onRegenerateLastAssistantResponse: () -> Unit,
    onSwitchBranch: (Long) -> Unit,
    onPreviousSearchMatch: () -> Unit,
    onNextSearchMatch: () -> Unit,
) {
    if (isSearchMode) {
        TextButton(
            onClick = onPreviousSearchMatch,
            enabled = state.searchMatchMessageIds.isNotEmpty(),
        ) {
            Text("Prev")
        }
        TextButton(
            onClick = onNextSearchMatch,
            enabled = state.searchMatchMessageIds.isNotEmpty(),
        ) {
            Text("Next")
        }
    } else {
        ConversationModeActions(
            state = state,
            isToolbarMenuExpanded = isToolbarMenuExpanded,
            hasAssistantResponse = hasAssistantResponse,
            onSearchModeChanged = onSearchModeChanged,
            onToolbarMenuExpandedChanged = onToolbarMenuExpandedChanged,
            onCompactionDialogVisibleChanged = onCompactionDialogVisibleChanged,
            onSettingsDialogVisibleChanged = onSettingsDialogVisibleChanged,
            onSettingsSystemPromptChanged = onSettingsSystemPromptChanged,
            onRegenerateLastAssistantResponse = onRegenerateLastAssistantResponse,
            onSwitchBranch = onSwitchBranch,
        )
    }
    if (state.hasGeneratingMessage) {
        TextButton(
            onClick = { onStopGenerationDialogVisibleChanged(true) },
            enabled = !state.isSending,
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun ConversationModeActions(
    state: ChatUiState,
    isToolbarMenuExpanded: Boolean,
    hasAssistantResponse: Boolean,
    onSearchModeChanged: (Boolean) -> Unit,
    onToolbarMenuExpandedChanged: (Boolean) -> Unit,
    onCompactionDialogVisibleChanged: (Boolean) -> Unit,
    onSettingsDialogVisibleChanged: (Boolean) -> Unit,
    onSettingsSystemPromptChanged: (String) -> Unit,
    onRegenerateLastAssistantResponse: () -> Unit,
    onSwitchBranch: (Long) -> Unit,
) {
    IconButton(onClick = { onToolbarMenuExpandedChanged(true) }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "More options",
        )
    }
    DropdownMenu(
        expanded = isToolbarMenuExpanded,
        onDismissRequest = { onToolbarMenuExpandedChanged(false) },
    ) {
        DropdownMenuItem(
            text = { Text("Search") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                )
            },
            onClick = {
                onToolbarMenuExpandedChanged(false)
                onSearchModeChanged(true)
            },
        )
        DropdownMenuItem(
            text = { Text("Regenerate response") },
            onClick = {
                onToolbarMenuExpandedChanged(false)
                onRegenerateLastAssistantResponse()
            },
            enabled = hasAssistantResponse && !state.isSending && !state.hasGeneratingMessage,
        )
        DropdownMenuItem(
            text = { Text("Compact conversation") },
            onClick = {
                onToolbarMenuExpandedChanged(false)
                onCompactionDialogVisibleChanged(true)
            },
            enabled = !state.isSending &&
                !state.hasGeneratingMessage &&
                !state.isCompactingConversation &&
                state.messages.size > 8,
        )
        state.branches.forEach { branch ->
            DropdownMenuItem(
                text = {
                    Text(
                        if (branch.isActive) {
                            "${branch.title} selected"
                        } else {
                            "Switch to ${branch.title}"
                        },
                    )
                },
                onClick = {
                    onToolbarMenuExpandedChanged(false)
                    onSwitchBranch(branch.id)
                },
                enabled = !branch.isActive && !state.isSending && !state.hasGeneratingMessage,
            )
        }
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                )
            },
            onClick = {
                onToolbarMenuExpandedChanged(false)
                onSettingsSystemPromptChanged(state.systemPrompt)
                onSettingsDialogVisibleChanged(true)
            },
        )
    }
}

private val previewChatState = ChatUiState(
    modelName = "gpt-4",
    chatSearchQuery = "assistant",
    searchMatchMessageIds = listOf(1L, 2L),
    currentSearchMatchIndex = 0,
)

@Preview
@Composable
private fun SearchModeTitlePreview() {
    SearchModeTitle(
        query = previewChatState.chatSearchQuery,
        matchCount = previewChatState.searchMatchMessageIds.size,
        currentMatchIndex = previewChatState.currentSearchMatchIndex,
        onQueryChanged = {},
    )
}

@Preview
@Composable
private fun ConversationModeTitlePreview() {
    ConversationModeTitle(
        modelName = previewChatState.modelName,
        branchTitle = "Main",
    )
}

@Composable
private fun ContextEstimateChip(
    modifier: Modifier = Modifier,
    tokenCount: Int,
    messageCount: Int,
    characterCount: Int,
) {
    AssistChip(
        modifier = modifier,
        onClick = {},
        label = {
            Text(
                text = "~${tokenCount.toCompactCount()} tokens • $messageCount messages • " +
                    "${characterCount.toCompactCount()} chars",
            )
        },
    )
}

private fun Int.toCompactCount(): String =
    if (this < 1_000) {
        toString()
    } else {
        "%.1fk".format(this / 1_000.0)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageUi,
    isCurrentSearchMatch: Boolean,
    isEditEnabled: Boolean,
    isRetryEnabled: Boolean,
    nowMillis: Long,
    isActionsMenuExpanded: Boolean,
    onMessageLongPress: () -> Unit,
    onDismissActionsMenu: () -> Unit,
    onCopyClick: () -> Unit,
    onEditClick: () -> Unit,
    onBranchClick: () -> Unit,
    onRetryClick: (Long) -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    val bubbleText = message.displayText(nowMillis)
    val bubbleColor = when {
        isCurrentSearchMatch -> MaterialTheme.colorScheme.tertiaryContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.status == MessageStatus.FAILED -> MaterialTheme.colorScheme.error
        isCurrentSearchMatch -> MaterialTheme.colorScheme.onTertiaryContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onMessageLongPress,
                )
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.status == MessageStatus.GENERATING) {
                    CircularProgressIndicator()
                }
                Text(
                    text = bubbleText,
                    color = textColor,
                )
            }
            Text(
                modifier = Modifier.align(Alignment.End),
                text = message.createdAtText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!isUser && message.status == MessageStatus.FAILED) {
                Button(
                    modifier = Modifier.align(Alignment.End),
                    onClick = { onRetryClick(message.id) },
                    enabled = isRetryEnabled,
                ) {
                    Text("Retry")
                }
            }
            DropdownMenu(
                expanded = isActionsMenuExpanded,
                onDismissRequest = onDismissActionsMenu,
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = onCopyClick,
                )
                if (isUser && isEditEnabled) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = onEditClick,
                    )
                    DropdownMenuItem(
                        text = { Text("Branch from here") },
                        onClick = onBranchClick,
                    )
                }
            }
        }
    }
}

private fun MessageUi.displayText(nowMillis: Long): String =
    when (status) {
        MessageStatus.GENERATING -> content.ifBlank {
            "Generating... ${formatElapsedTime(nowMillis - createdAtMillis)}"
        }
        MessageStatus.FAILED -> content.ifBlank { errorMessage ?: "Generation failed." }
        MessageStatus.CANCELED -> errorMessage ?: "Generation stopped."
        MessageStatus.SENT -> content
    }

private fun formatElapsedTime(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis.coerceAtLeast(0L) / 1_000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun MessageInputBar(
    messageText: String,
    isSending: Boolean,
    hasGeneratingMessage: Boolean,
    isEditing: Boolean,
    onMessageChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelEditClick: () -> Unit,
) {
    val isSubmitEnabled = if (isEditing) {
        !isSending && messageText.isNotBlank()
    } else {
        !isSending && !hasGeneratingMessage && messageText.isNotBlank()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = messageText,
            onValueChange = onMessageChanged,
            enabled = !isSending,
            minLines = 1,
            maxLines = 4,
            placeholder = { Text(if (isEditing) "Edit message" else "Message") },
        )

        if (isEditing) {
            TextButton(
                onClick = onCancelEditClick,
                enabled = !isSending,
            ) {
                Text("Cancel")
            }
        }

        Button(
            onClick = onSendClick,
            enabled = isSubmitEnabled,
        ) {
            if (isSending) {
                CircularProgressIndicator()
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = null,
                    )
                    Text(if (isEditing) "Save" else "Send")
                }
            }
        }
    }
}
