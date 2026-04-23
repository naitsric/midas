package com.midas.ui.copilot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.CopilotEvent
import com.midas.domain.model.CopilotHistoryItem
import com.midas.ui.components.ArrowForwardGlyph
import com.midas.ui.components.MidasMonogram
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private enum class CopilotState { Empty, Thinking, Answered }

private data class CopilotMessage(
    val id: Int,
    val role: Role,
    val text: String = "",
    val thinking: Boolean = false,
    val sources: List<Source> = emptyList(),
) {
    enum class Role { User, Assistant }
}

private data class Source(val type: Type, val label: String) {
    enum class Type { Call, Chat, Application }
}

@Composable
fun CopilotScreen(apiClient: MidasApiClient) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var state by remember { mutableStateOf(CopilotState.Empty) }
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<CopilotMessage>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var nextId by remember { mutableStateOf(1) }

    fun ask(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val userMsg = CopilotMessage(id = nextId++, role = CopilotMessage.Role.User, text = q)
        val assistantId = nextId++
        val thinkingMsg = CopilotMessage(id = assistantId, role = CopilotMessage.Role.Assistant, thinking = true)
        // Construir historial del backend ANTES de agregar el nuevo turno.
        val history = messages
            .filter { !it.thinking && it.text.isNotEmpty() }
            .map {
                CopilotHistoryItem(
                    role = if (it.role == CopilotMessage.Role.User) "user" else "assistant",
                    text = it.text,
                )
            }
        messages = messages + userMsg + thinkingMsg
        input = ""
        state = CopilotState.Thinking

        scope.launch {
            apiClient.streamCopilot(history = history, message = q)
                .catch { e ->
                    messages = messages.map { m ->
                        if (m.id == assistantId) m.copy(
                            thinking = false,
                            text = "Error: ${e.message ?: "no se pudo conectar"}",
                        ) else m
                    }
                    state = CopilotState.Answered
                }
                .collect { event ->
                    messages = messages.map { m ->
                        if (m.id != assistantId) return@map m
                        when (event) {
                            is CopilotEvent.Token -> m.copy(thinking = false, text = m.text + event.text)
                            is CopilotEvent.ToolCall -> m.copy(thinking = true)
                            is CopilotEvent.ToolResult -> {
                                val src = event.toSource(s) ?: return@map m
                                m.copy(sources = m.sources + src)
                            }
                            is CopilotEvent.Done -> m.copy(thinking = false)
                            is CopilotEvent.Error -> m.copy(
                                thinking = false,
                                text = if (m.text.isEmpty()) "Error: ${event.message}" else m.text,
                            )
                        }
                    }
                    if (event is CopilotEvent.Done || event is CopilotEvent.Error) {
                        state = CopilotState.Answered
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()  // header below the iOS notch / status bar
            .imePadding(),        // composer rises with the keyboard
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MidasMonogram(size = 26.dp, cornerRadius = 7.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = s.copilotTitle,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )
        }

        // Body
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                CopilotState.Empty -> EmptyState(
                    title = s.copilotEmptyTitle,
                    body = s.copilotEmptyBody,
                    suggestions = listOf(
                        s.copilotSuggestion1,
                        s.copilotSuggestion2,
                        s.copilotSuggestion3,
                    ),
                    onPick = { ask(it) },
                )
                else -> MessagesList(
                    messages = messages,
                    thinkingLabel = s.copilotThinking,
                )
            }
        }

        Composer(
            value = input,
            onValueChange = { input = it },
            placeholder = s.copilotComposerPlaceholder,
            onSend = { ask(input) },
        )
    }
}

// ─────────────────────────── Empty ───────────────────────────

@Composable
private fun EmptyState(
    title: String,
    body: String,
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    val colors = LocalMidasColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp),
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp,
            letterSpacing = (-0.4).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = colors.muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { prompt ->
                SuggestionRow(text = prompt, onClick = { onPick(prompt) })
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        ArrowForwardGlyph(tint = colors.muted, size = 14.dp)
    }
}

// ─────────────────────────── Messages ───────────────────────────

@Composable
private fun MessagesList(messages: List<CopilotMessage>, thinkingLabel: String) {
    val scroll = rememberScrollState()
    LaunchedEffect(messages.size) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        messages.forEach { msg ->
            MessageBubble(msg, thinkingLabel)
        }
    }
}

@Composable
private fun MessageBubble(msg: CopilotMessage, thinkingLabel: String) {
    when {
        msg.role == CopilotMessage.Role.User -> UserBubble(text = msg.text)
        msg.text.isEmpty() && msg.thinking -> ThinkingRow(label = thinkingLabel)
        else -> AssistantAnswer(text = msg.text, sources = msg.sources)
    }
    Spacer(Modifier.height(if (msg.role == CopilotMessage.Role.User) 14.dp else 18.dp))
}

@Composable
private fun UserBubble(text: String) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = 4.dp,
                        bottomStart = 16.dp,
                    ),
                )
                .background(colors.primaryAccent)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                color = colors.primaryAccentOn,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun ThinkingRow(label: String) {
    val colors = LocalMidasColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ThinkingDots()
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = colors.muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ThinkingDots() {
    val colors = LocalMidasColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            DotPulse(delayMs = i * 150)
        }
    }
}

@Composable
private fun DotPulse(delayMs: Int) {
    val colors = LocalMidasColors.current
    val transition = rememberInfiniteTransition(label = "thinkDot$delayMs")
    val anim by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                delayMillis = delayMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha$delayMs",
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(colors.primaryAccent.copy(alpha = anim)),
    )
}

@Composable
private fun AssistantAnswer(text: String, sources: List<Source>) {
    val colors = LocalMidasColors.current
    Column {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 14.5.sp,
            lineHeight = 22.sp,
        )
        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            // Flow-style row: simple Row with wrap (use simple chunked approach for now)
            sources.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    row.forEach { source -> SourceChip(source = source) }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: Source) {
    val colors = LocalMidasColors.current
    val icon: ImageVector = when (source.type) {
        Source.Type.Call -> Icons.Default.Phone
        Source.Type.Chat -> Icons.Default.ChatBubble
        Source.Type.Application -> Icons.Default.Description
    }
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = source.label,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─────────────────────────── Composer ───────────────────────────

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val canSend = value.isNotBlank()

    val borderColor = when {
        value.isNotEmpty() -> colors.primaryAccent.copy(alpha = 0.4f)
        focused -> colors.primaryAccent.copy(alpha = 0.25f)
        else -> colors.cardBorder
    }
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.05f) else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(bg)
                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = SolidColor(colors.primaryAccent),
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.muted,
                                fontSize = 14.sp,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(6.dp))
            val sendBg = if (canSend) colors.primaryAccent else {
                if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            }
            val sendContent = if (canSend) colors.primaryAccentOn else colors.muted
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(sendBg)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = sendContent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────── helpers ───────────────────────────

/**
 * Mapea un `tool_result` event del backend al chip que renderiza la UI.
 * Usa el label que mandó el backend (ej. "Llamadas · 3") y completa el
 * tipo. Devuelve null si el evento no produce un chip útil.
 */
private fun CopilotEvent.ToolResult.toSource(s: com.midas.ui.i18n.Strings): Source? {
    val st = sourceType ?: return null
    val sl = sourceLabel ?: return null
    val type = when (st.lowercase()) {
        "call" -> Source.Type.Call
        "chat" -> Source.Type.Chat
        "application" -> Source.Type.Application
        else -> return null
    }
    return Source(type = type, label = sl)
}
