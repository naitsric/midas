package com.midas.ui.conversations

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.Conversation
import com.midas.domain.model.Message
import com.midas.ui.components.ArrowBackGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import kotlinx.coroutines.launch

@Composable
fun ConversationDetailScreen(
    apiClient: MidasApiClient,
    conversationId: String,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiOn by remember { mutableStateOf(true) }
    var composer by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(conversationId) {
        scope.launch {
            try {
                conversation = apiClient.getConversation(conversationId)
            } catch (e: Exception) {
                error = e.message ?: "Error"
            } finally {
                loading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                conversation = conversation,
                aiOn = aiOn,
                onToggleAi = { aiOn = !aiOn },
                onBack = onBack,
                aiLabel = s.chatDetailAiToggle,
            )

            // Scrollable messages
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAccent)
                    }

                    error != null || conversation == null -> Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = error ?: s.conversationsEmpty,
                            color = colors.statusNegative,
                        )
                    }

                    else -> MessagesList(
                        conversation = conversation!!,
                        firstSeenLabel = s.chatDetailFirstSeen,
                    )
                }
            }

            Composer(
                value = composer,
                onValueChange = { composer = it },
                placeholder = s.chatComposerPlaceholder,
                footer = s.chatComposerFooter,
            )
        }
    }
}

// ─────────────────────────── Header ───────────────────────────

@Composable
private fun Header(
    conversation: Conversation?,
    aiOn: Boolean,
    onToggleAi: () -> Unit,
    onBack: () -> Unit,
    aiLabel: String,
) {
    val colors = LocalMidasColors.current
    val name = conversation?.clientName ?: "…"
    val subtitle = conversation?.let { "${it.messageCount} msg" } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.04f)
                    else Color.Black.copy(alpha = 0.04f),
                )
                .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            ArrowBackGlyph(tint = colors.textPrimary, size = 14.dp)
        }
        Spacer(Modifier.width(10.dp))
        // Avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        0f to colors.primaryAccent,
                        1f to colors.primaryAccent.copy(alpha = 0.65f),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsFor(name),
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = colors.muted,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        AiToggle(on = aiOn, onClick = onToggleAi, label = aiLabel)
    }

    // Bottom divider line under header
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalMidasColors.current.cardBorder),
    )
}

@Composable
private fun AiToggle(on: Boolean, onClick: () -> Unit, label: String) {
    val colors = LocalMidasColors.current
    val bg = if (on) colors.primaryAccent.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (on) colors.primaryAccent.copy(alpha = 0.45f) else colors.cardBorder
    val contentColor = if (on) colors.primaryAccent else colors.muted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
        )
    }
}

// ─────────────────────────── Messages ───────────────────────────

@Composable
private fun MessagesList(conversation: Conversation, firstSeenLabel: String) {
    val colors = LocalMidasColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 16.dp),
    ) {
        // Date separator
        Text(
            text = "— $firstSeenLabel · ${formatDate(conversation.createdAt)} —".uppercase(),
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )

        conversation.messages.forEach { message ->
            MessageBubble(message = message)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val colors = LocalMidasColors.current
    val isMe = message.isAdvisor
    val bubbleBg = if (isMe) colors.primaryAccent else {
        if (colors.isDark) Color.White.copy(alpha = 0.06f) else Color.White
    }
    val textColor = if (isMe) Color.Black else colors.textPrimary
    val borderColor = if (isMe) Color.Transparent else colors.cardBorder
    val timestampColor = if (isMe) Color.Black.copy(alpha = 0.55f) else colors.muted
    val timeText = formatTime(message.timestamp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isMe) 14.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 14.dp,
                    ),
                )
                .background(bubbleBg)
                .border(
                    1.dp,
                    borderColor,
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isMe) 14.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 14.dp,
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isMe) "$timeText ✓✓" else timeText,
                color = timestampColor,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// ─────────────────────────── Composer ───────────────────────────

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    footer: String,
) {
    val colors = LocalMidasColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) colors.primaryAccent.copy(alpha = 0.4f) else colors.cardBorder
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.04f) else Color.White

    // Bar background matching design
    val barBg = if (colors.isDark) colors.bg else Color(0xFFF6F6F4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(bg)
                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                    ),
                    cursorBrush = SolidColor(colors.primaryAccent),
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = colors.muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.primaryAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    tint = colors.primaryAccentOn,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = footer,
            color = colors.muted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────── helpers ───────────────────────────

private fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

private fun formatDate(iso: String): String =
    iso.substringBefore('T').takeIf { it.isNotEmpty() } ?: iso

private fun formatTime(iso: String): String {
    val time = iso.substringAfter('T', "").take(5)
    return time.ifEmpty { iso }
}
