package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.ArushiAmber
import com.example.ui.theme.ArushiCyan
import com.example.ui.theme.ArushiDarkBg
import com.example.ui.theme.ArushiDarkSurface
import com.example.ui.theme.ArushiDarkSurfaceVariant
import com.example.ui.theme.ArushiError
import com.example.ui.theme.ArushiGlowBorder
import com.example.ui.theme.ArushiMagenta
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTextPrimary
import com.example.ui.theme.ArushiTextSecondary
import com.example.ui.theme.ArushiTextTertiary
import com.example.ui.theme.ArushiViolet
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArushiScreen(viewModel: ArushiViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogsSheet by remember { mutableStateOf(false) }

    // Permission launcher for Mic & Contacts
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            viewModel.toggleSession()
        } else {
            viewModel.addLog("Permission", "Microphone permission was denied by user")
        }
    }

    val handleMicClick = {
        val hasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMic) {
            viewModel.toggleSession()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ArushiDarkBg),
        containerColor = ArushiDarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Background cosmic aura
            CosmicAtmosphere(state = uiState.state)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Bar
                TopAppBarSection(
                    uiState = uiState,
                    onTestSpeaker = {
                        viewModel.testSpeaker()
                    },
                    onOpenLogs = { showLogsSheet = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Central Voice Stage (Interactive animated AI Orb & waveforms)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ArushiVoiceOrb(
                        state = uiState.state,
                        micAmplitude = uiState.micAmplitude,
                        speakerAmplitude = uiState.speakerAmplitude,
                        onClick = handleMicClick
                    )
                }

                // 3. Conversation & Action Feedback Section
                ConversationFeedbackSection(
                    uiState = uiState,
                    onDismissAction = { viewModel.dismissAction() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Quick Action Suggestions
                QuickActionPills(
                    onSelect = { prompt ->
                        viewModel.sendQuickCommand(prompt)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Main tactile Mic Control button
                MainVoiceButton(
                    uiState = uiState,
                    onClick = handleMicClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Safe area padding for navigation bars
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }

            // Debug Logs Bottom Sheet
            if (showLogsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showLogsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = ArushiDarkSurface
                ) {
                    DebugLogsSheet(
                        logs = uiState.logs,
                        onClose = { showLogsSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopAppBarSection(
    uiState: ArushiUiState,
    onTestSpeaker: () -> Unit,
    onOpenLogs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(ArushiCyan, ArushiViolet, ArushiMagenta)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Arushi Icon",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "ARUSHI",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = ArushiTextPrimary
                    )
                )
                Text(
                    text = "Real-Time Voice AI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ArushiCyan,
                        fontSize = 10.sp
                    )
                )
            }
        }

        // Action controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Speaker Diagnostic Test Button (Requirement 15)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ArushiDarkSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, ArushiGlowBorder),
                modifier = Modifier
                    .clickable { onTestSpeaker() }
                    .testTag("test_speaker_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Test Speaker",
                        tint = ArushiAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tone Test",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ArushiTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Debug Logs Button (Requirement 9)
            IconButton(
                onClick = onOpenLogs,
                modifier = Modifier
                    .size(36.dp)
                    .background(ArushiDarkSurfaceVariant, CircleShape)
                    .border(1.dp, ArushiGlowBorder, CircleShape)
                    .testTag("debug_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "View Logs",
                    tint = ArushiCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ArushiVoiceOrb(
    state: AssistantState,
    micAmplitude: Float,
    speakerAmplitude: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Dynamic amplitude based on current state
    val dynamicAmp = when (state) {
        AssistantState.LISTENING -> micAmplitude
        AssistantState.SPEAKING -> speakerAmplitude
        AssistantState.CONNECTING -> 0.35f
        AssistantState.IDLE -> 0.05f
        AssistantState.ERROR -> 0.1f
    }

    val orbBaseColor = when (state) {
        AssistantState.LISTENING -> ArushiCyan
        AssistantState.SPEAKING -> ArushiMagenta
        AssistantState.CONNECTING -> ArushiAmber
        AssistantState.IDLE -> ArushiViolet
        AssistantState.ERROR -> ArushiError
    }

    Box(
        modifier = Modifier
            .size(280.dp)
            .clickable { onClick() }
            .testTag("voice_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.30f * breathScale

            // 1. Outermost soft glowing pulse
            val outerRadius = baseRadius * (1.25f + dynamicAmp * 0.65f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbBaseColor.copy(alpha = 0.35f),
                        orbBaseColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // 2. Orbital frequency wave rings
            val ringCount = 3
            for (i in 1..ringCount) {
                val ringRadius = baseRadius + (i * 24.dp.toPx()) * (1f + dynamicAmp * 0.4f)
                val alpha = (0.4f / i) * (0.6f + dynamicAmp)
                drawCircle(
                    color = orbBaseColor.copy(alpha = alpha.coerceIn(0.05f, 0.9f)),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            // 3. Dancing soundwave nodes around circumference
            val nodeCount = 24
            val angleStep = (2 * Math.PI / nodeCount).toFloat()
            for (i in 0 until nodeCount) {
                val nodeAngle = i * angleStep + Math.toRadians(rotationAngle.toDouble()).toFloat()
                val waveOffset = (sin((i * 1.5 + rotationAngle * 0.1)) * dynamicAmp * 35.dp.toPx()).toFloat()
                val r = baseRadius + waveOffset

                val x = center.x + r * cos(nodeAngle)
                val y = center.y + r * sin(nodeAngle)

                drawCircle(
                    color = Color.White.copy(alpha = (0.4f + dynamicAmp * 0.6f).coerceIn(0.2f, 1.0f)),
                    radius = (2.dp.toPx() + dynamicAmp * 3.dp.toPx()),
                    center = Offset(x, y)
                )
            }

            // 4. Core glowing orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        orbBaseColor,
                        orbBaseColor.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )
        }

        // Center mic icon or status
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ArushiDarkSurface.copy(alpha = 0.75f))
                .border(1.5.dp, orbBaseColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                AssistantState.CONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = ArushiAmber,
                        strokeWidth = 2.5.dp
                    )
                }
                AssistantState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Arushi Speaking",
                        tint = ArushiMagenta,
                        modifier = Modifier.size(32.dp)
                    )
                }
                AssistantState.LISTENING -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening",
                        tint = ArushiCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
                AssistantState.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Error",
                        tint = ArushiError,
                        modifier = Modifier.size(32.dp)
                    )
                }
                AssistantState.IDLE -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Idle",
                        tint = ArushiTextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationFeedbackSection(
    uiState: ArushiUiState,
    onDismissAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status indicator pill
        StatusPill(state = uiState.state, message = uiState.statusMessage)

        Spacer(modifier = Modifier.height(10.dp))

        // Latest Action Result Banner (Requirement 19-25)
        AnimatedVisibility(
            visible = uiState.latestAction != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            uiState.latestAction?.let { action ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (action.success) ArushiSuccess.copy(alpha = 0.15f) else ArushiError.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (action.success) ArushiSuccess else ArushiError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("action_result_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (action.success) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (action.success) ArushiSuccess else ArushiError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = action.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ArushiTextPrimary,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismissAction, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = ArushiTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Spoken transcript card (Arushi's voice response or user prompt)
        if (uiState.arushiSpeechText.isNotEmpty() || uiState.userSpeechText.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArushiDarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ArushiGlowBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transcript_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (uiState.userSpeechText.isNotEmpty()) {
                        Text(
                            text = "You: \"${uiState.userSpeechText}\"",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArushiCyan,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (uiState.arushiSpeechText.isNotEmpty()) {
                        Text(
                            text = uiState.arushiSpeechText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = ArushiTextPrimary,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 20.sp
                            ),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: AssistantState, message: String) {
    val indicatorColor = when (state) {
        AssistantState.LISTENING -> ArushiCyan
        AssistantState.SPEAKING -> ArushiMagenta
        AssistantState.CONNECTING -> ArushiAmber
        AssistantState.IDLE -> ArushiTextTertiary
        AssistantState.ERROR -> ArushiError
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ArushiDarkSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArushiGlowBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ArushiTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun QuickActionPills(onSelect: (String) -> Unit) {
    val actions = listOf(
        "WhatsApp kholo" to "💬",
        "Call Mom" to "📞",
        "Open YouTube" to "🎬",
        "Open Maps" to "🗺️",
        "Kya haal hai Arushi?" to "🇮🇳",
        "Tell me a witty joke" to "✨"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { (text, emoji) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ArushiDarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, ArushiGlowBorder),
                modifier = Modifier
                    .clickable { onSelect(text) }
                    .testTag("quick_action_$text")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ArushiTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MainVoiceButton(
    uiState: ArushiUiState,
    onClick: () -> Unit
) {
    val isActive = uiState.state == AssistantState.LISTENING || uiState.state == AssistantState.SPEAKING
    val infiniteTransition = rememberInfiniteTransition(label = "btn_waves")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btn_pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        // Outer pulsing ring when active
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                (if (uiState.state == AssistantState.LISTENING) ArushiCyan else ArushiMagenta).copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Primary Button
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) {
                        Brush.linearGradient(listOf(ArushiCyan, ArushiMagenta))
                    } else {
                        Brush.linearGradient(listOf(ArushiDarkSurfaceVariant, ArushiGlowBorder))
                    }
                )
                .border(
                    2.dp,
                    if (isActive) Color.White else ArushiCyan.copy(alpha = 0.5f),
                    CircleShape
                )
                .clickable { onClick() }
                .testTag("main_mic_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isActive) "Stop Voice Session" else "Start Voice Session",
                tint = if (isActive) Color.White else ArushiCyan,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun CosmicAtmosphere(state: AssistantState) {
    val auraColor = when (state) {
        AssistantState.LISTENING -> ArushiCyan.copy(alpha = 0.08f)
        AssistantState.SPEAKING -> ArushiMagenta.copy(alpha = 0.08f)
        AssistantState.CONNECTING -> ArushiAmber.copy(alpha = 0.06f)
        AssistantState.ERROR -> ArushiError.copy(alpha = 0.08f)
        AssistantState.IDLE -> ArushiViolet.copy(alpha = 0.05f)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(auraColor, Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.4f),
                radius = size.width * 0.9f
            )
        )
    }
}

@Composable
private fun DebugLogsSheet(
    logs: List<DebugLogEntry>,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Audio & Gemini Live Pipeline Logs",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = ArushiTextPrimary
                )
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = ArushiTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF07060E),
            modifier = Modifier.fillMaxSize()
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No logs yet. Start speaking with Arushi to see live traces.",
                        color = ArushiTextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { entry ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "[${entry.timestamp}] ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ArushiTextTertiary,
                                    fontSize = 10.sp
                                )
                            )
                            Text(
                                text = "${entry.tag}: ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = when (entry.tag) {
                                        "Error", "LiveError" -> ArushiError
                                        "Audio", "AudioTrack" -> ArushiMagenta
                                        "Microphone", "Recorder" -> ArushiCyan
                                        "Action" -> ArushiSuccess
                                        else -> ArushiAmber
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ArushiTextPrimary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
