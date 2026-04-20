package com.midas.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.ConversationSummary
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasBlue
import com.midas.ui.theme.MidasOrange
import com.midas.ui.theme.MidasPurple
import kotlinx.coroutines.launch

private enum class ChatFilter { All, Hot, Unread, App }

@Composable
fun ConversationListScreen(
    apiClient: MidasApiClient,
    onConversationClick: (String) -> Unit = {},
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var applicationsCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ChatFilter.All) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                conversations = apiClient.listConversations()
                applicationsCount = runCatching { apiClient.listApplications().size }.getOrDefault(0)
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    val totalConversations = conversations.size
    val totalMessages = conversations.sumOf { it.messageCount }

    val filtered = remember(conversations, query, filter) {
        conversations.filter { c ->
            (query.isBlank() || c.clientName.contains(query, ignoreCase = true))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            // Sync pill + title row + overflow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SyncPill(label = s.chatsSyncPill)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s.conversationsTitle,
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (colors.isDark) Color.White.copy(alpha = 0.04f)
                            else Color.Black.copy(alpha = 0.04f),
                        )
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Intelligence chips trio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IntelChip(
                    n = totalConversations,
                    label = s.chatsStatConversations,
                    accent = colors.primaryAccent,
                    modifier = Modifier.weight(1f),
                )
                IntelChip(
                    n = totalMessages,
                    label = s.chatsStatMessages,
                    accent = MidasOrange,
                    modifier = Modifier.weight(1f),
                )
                IntelChip(
                    n = applicationsCount,
                    label = s.chatsStatApplications,
                    accent = MidasPurple,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Search
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = s.chatsSearchPlaceholder,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Filter pills (horizontal scroll)
            FilterPills(
                filter = filter,
                onFilterChange = { filter = it },
                allLabel = s.chatsFilterAll, allCount = totalConversations,
                hotLabel = s.chatsFilterHot,
                unreadLabel = s.chatsFilterUnread,
                appLabel = s.chatsFilterApp, appCount = applicationsCount,
            )

            Spacer(Modifier.height(8.dp))

            // List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAccent)
                    }

                    conversations.isEmpty() -> Text(
                        text = s.conversationsEmpty,
                        color = colors.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    )

                    filtered.isEmpty() -> Text(
                        text = s.chatsEmptyFilter,
                        color = colors.muted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )

                    else -> {
                        filtered.forEachIndexed { index, conv ->
                            ChatRow(
                                conv = conv,
                                accent = avatarColorFor(index),
                                messagesLabel = s.conversationsMessages,
                                onClick = { onConversationClick(conv.id) },
                            )
                        }
                        Spacer(Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Sync pill ───────────────────────────

@Composable
private fun SyncPill(label: String) {
    val colors = LocalMidasColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.statusPositive),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ─────────────────────────── Intel chips ───────────────────────────

@Composable
private fun IntelChip(
    n: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = n.toString(),
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 22.sp,
            letterSpacing = (-0.4).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            color = colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

// ─────────────────────────── Search ───────────────────────────

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) colors.primaryAccent else colors.cardBorder
    val bg = if (colors.isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFFFAFAF8)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
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
                        )
                    }
                    inner()
                },
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.08f)
                    else Color.Black.copy(alpha = 0.05f),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "⌘ K",
                color = colors.muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ─────────────────────────── Filter pills ───────────────────────────

@Composable
private fun FilterPills(
    filter: ChatFilter,
    onFilterChange: (ChatFilter) -> Unit,
    allLabel: String, allCount: Int,
    hotLabel: String,
    unreadLabel: String,
    appLabel: String, appCount: Int,
) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterPill(
            label = allLabel,
            count = allCount,
            selected = filter == ChatFilter.All,
            accent = colors.primaryAccent,
            onClick = { onFilterChange(ChatFilter.All) },
        )
        FilterPill(
            label = hotLabel,
            count = null,
            selected = filter == ChatFilter.Hot,
            accent = colors.primaryAccent,
            onClick = { onFilterChange(ChatFilter.Hot) },
        )
        FilterPill(
            label = unreadLabel,
            count = null,
            selected = filter == ChatFilter.Unread,
            accent = MidasOrange,
            onClick = { onFilterChange(ChatFilter.Unread) },
        )
        FilterPill(
            label = appLabel,
            count = appCount,
            selected = filter == ChatFilter.App,
            accent = MidasPurple,
            onClick = { onFilterChange(ChatFilter.App) },
        )
    }
}

@Composable
private fun FilterPill(
    label: String,
    count: Int?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val bg = if (selected) accent.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (selected) accent.copy(alpha = 0.45f) else colors.cardBorder
    val contentColor = if (selected) accent else colors.muted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                color = contentColor.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ─────────────────────────── Chat row ───────────────────────────

@Composable
private fun ChatRow(
    conv: ConversationSummary,
    accent: Color,
    messagesLabel: String,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Avatar with gradient
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        0f to accent,
                        1f to accent.copy(alpha = 0.65f),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(conv.clientName),
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conv.clientName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTime(conv.createdAt),
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${conv.messageCount} $messagesLabel",
                color = colors.muted,
                fontSize = 12.5.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ─────────────────────────── helpers ───────────────────────────

private val avatarPalette = listOf(
    Color(0xFF00E676),
    Color(0xFFB388FF),
    Color(0xFFFF9800),
    Color(0xFF42A5F5),
)

@Suppress("FunctionName")
private fun avatarColorFor(index: Int): Color = avatarPalette[index % avatarPalette.size]

@Suppress("unused")
private val _midasBlueAnchor = MidasBlue

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

private fun relativeTime(createdAt: String): String {
    // Display time portion if available; otherwise the date.
    val time = createdAt.substringAfter('T', "").take(5)
    return time.ifEmpty { createdAt.substringBefore('T').takeIf { it.isNotEmpty() }.orEmpty() }
}
