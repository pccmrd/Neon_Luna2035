package com.example.keres9.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keres9.render.GameRenderer
import com.example.keres9.sim.DockUiState
import com.example.keres9.sim.GameSession
import com.example.keres9.sim.HudUiState
import com.example.keres9.sim.ToastUiItem
import com.example.keres9.ui.theme.KeresColors
import com.example.keres9.ui.theme.RobotoMonoFamily

@Composable
fun HudOverlays(
    state: HudUiState,
    renderTick: Long,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxHeight > maxWidth
        val compactLandscape = !isPortrait && maxHeight < 430.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Top Status Bar
            TopStatusBar(state = state, isPortrait = isPortrait)

            Spacer(modifier = Modifier.height(6.dp))

            // Second Row: Objective Card + Center Readout/Toasts + Minimap
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Objective tracker (top-left)
                ObjectiveCard(
                    objText = state.objText,
                    objReward = state.objReward,
                    maxWidthDp = if (isPortrait) 168.dp else 218.dp,
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // Minimap (top-right)
                MinimapCard(
                    renderTick = renderTick,
                    isPortrait = isPortrait,
                    onOpenMap = { GameSession.openMap() },
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                // Target readout (top-center or below bar in portrait)
                if (state.target != null) {
                    TargetReadoutCard(
                        tgt = state.target,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (isPortrait) 78.dp else 0.dp)
                    )
                }

                // Toasts
                if (state.toasts.isNotEmpty()) {
                    ToastStack(
                        toasts = state.toasts,
                        isPortrait = isPortrait,
                        modifier = if (isPortrait) {
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 128.dp)
                                .widthIn(max = 230.dp)
                        } else {
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (state.target != null) 46.dp else 4.dp)
                                .widthIn(max = 380.dp)
                        }
                    )
                }

                // Event Banner
                if (!state.evBannerText.isNullOrBlank()) {
                    EventBanner(
                        text = state.evBannerText,
                        isInfo = state.evBannerInfo,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (isPortrait) 106.dp else 44.dp)
                    )
                }

                // Docking Envelope OR Directional Array Drawer
                val drawerBottom = when {
                    compactLandscape -> 8.dp
                    isPortrait -> 312.dp
                    else -> 148.dp
                }
                if (state.scanOpen) {
                    ScannerDrawer(
                        freq = state.scanFreq,
                        lock = state.scanLock,
                        label = state.scanLabel,
                        renderTick = renderTick,
                        onFreqChange = { GameSession.setScannerFreq(it.toDouble()) },
                        onClose = { GameSession.closeScanner() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = drawerBottom)
                    )
                } else if (state.dock != null) {
                    DockEnvelopeCard(
                        dock = state.dock,
                        renderTick = renderTick,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = drawerBottom)
                    )
                }

                // AI Pilot Narration Box
                if (state.aiOn) {
                    AiPilotBanner(
                        act = state.apAct,
                        cap = state.apCap,
                        sub = state.apSub,
                        time = state.apTime,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = when {
                                    isPortrait -> 286.dp
                                    else -> 6.dp
                                }
                            )
                            .widthIn(
                                min = 220.dp,
                                max = if (isPortrait) 380.dp else 440.dp
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopStatusBar(state: HudUiState, isPortrait: Boolean) {
    ChamferPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = KeresColors.Steel
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 5.dp else 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = state.creditsText,
                fontFamily = RobotoMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = KeresColors.Gold,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .border(1.dp, if (state.badgeIsBounty) Color(0x80FFD166) else Color(0x66FF4A3D))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = state.badgeText,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    color = if (state.badgeIsBounty) KeresColors.Gold else KeresColors.Red,
                    maxLines = 1
                )
            }

            val barW = if (isPortrait) 38.dp else 56.dp
            ResourceMeter(
                label = state.fuelLabel,
                ratio = state.fuelRatio,
                low = state.fuelRatio < 0.2f,
                startColor = KeresColors.Rust,
                endColor = KeresColors.Amber,
                width = barW
            )
            ResourceMeter(
                label = state.hullLabel,
                ratio = state.hullRatio,
                low = state.hullRatio < 0.3f,
                startColor = KeresColors.GreenDark,
                endColor = KeresColors.Green,
                width = barW
            )
            ResourceMeter(
                label = state.cargoLabel,
                ratio = state.cargoRatio,
                low = state.cargoRatio > 0.95f,
                startColor = Color(0xFF1F6F8F),
                endColor = KeresColors.Cyan,
                width = barW
            )

            Box(
                modifier = Modifier
                    .border(1.dp, Color(0x5953D9FF))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = state.modeBadge,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = KeresColors.Cyan,
                    maxLines = 1
                )
            }

            if (state.aiWarp > 1) {
                Text(
                    text = "TIME ×${state.aiWarp}",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = KeresColors.Gold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isPortrait) {
                Text(
                    text = state.clockText,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 10.sp,
                    color = KeresColors.Dim,
                    maxLines = 1
                )
            }

            KeresButton(
                onClick = { GameSession.toggleAI() },
                greenAccent = state.aiOn,
                minHeight = 40.dp,
                contentPaddingHorizontal = 6.dp,
                contentPaddingVertical = 4.dp,
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
                    .testTag("ai_toggle_button")
            ) {
                Text(
                    text = "AI",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.aiOn) KeresColors.Green else KeresColors.Dim
                )
            }

            KeresButton(
                onClick = { GameSession.toggleSound() },
                minHeight = 40.dp,
                contentPaddingHorizontal = 6.dp,
                contentPaddingVertical = 4.dp,
                modifier = Modifier
                    .defaultMinSize(minWidth = 42.dp, minHeight = 40.dp)
                    .testTag("sound_toggle_button")
            ) {
                Text(if (state.soundOn) "🔊" else "🔇", fontSize = 12.sp)
            }

            KeresButton(
                onClick = { GameSession.togglePause() },
                minHeight = 40.dp,
                contentPaddingHorizontal = 6.dp,
                contentPaddingVertical = 4.dp,
                modifier = Modifier
                    .defaultMinSize(minWidth = 42.dp, minHeight = 40.dp)
                    .testTag("pause_button")
            ) {
                Text("▐▐", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ResourceMeter(
    label: String,
    ratio: Float,
    low: Boolean,
    startColor: Color,
    endColor: Color,
    width: androidx.compose.ui.unit.Dp
) {
    val inf = rememberInfiniteTransition(label = "lowMeter")
    val pulse by inf.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "lowAlpha"
    )
    val fillAlpha = if (low) pulse else 1f
    val sCol = if (low) KeresColors.RedDark else startColor
    val eCol = if (low) KeresColors.Red else endColor

    Box(
        modifier = Modifier
            .width(width)
            .height(11.dp)
            .background(KeresColors.TrackBg)
            .border(1.dp, KeresColors.Steel)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .padding(1.dp)
                .alpha(fillAlpha)
                .background(Brush.horizontalGradient(listOf(sCol, eCol)))
        )
        Text(
            text = label,
            fontFamily = RobotoMonoFamily,
            fontSize = 7.sp,
            color = KeresColors.Dim,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun ObjectiveCard(
    objText: String,
    objReward: String,
    maxWidthDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = maxWidthDp)
            .background(KeresColors.Panel)
            .drawBehind {
                drawRect(
                    color = KeresColors.Amber,
                    topLeft = Offset.Zero,
                    size = Size(2.dp.toPx(), size.height)
                )
            }
            .padding(start = 8.dp, top = 5.dp, end = 8.dp, bottom = 5.dp)
    ) {
        Text(
            text = "OBJECTIVE",
            fontFamily = RobotoMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = KeresColors.Amber
        )
        Text(
            text = objText,
            fontFamily = RobotoMonoFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = KeresColors.Text,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
        if (objReward.isNotEmpty()) {
            Text(
                text = objReward,
                fontFamily = RobotoMonoFamily,
                fontSize = 9.sp,
                color = KeresColors.Gold
            )
        }
    }
}

@Composable
private fun MinimapCard(
    renderTick: Long,
    isPortrait: Boolean,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wDp = if (isPortrait) 96.dp else 126.dp
    val hDp = if (isPortrait) 72.dp else 94.dp
    Box(
        modifier = modifier
            .border(1.dp, KeresColors.Steel)
            .background(Color(0xD9060A11))
            .padding(2.dp)
            .testTag("minimap_button")
            .clickable(onClick = onOpenMap)
    ) {
        Canvas(modifier = Modifier.size(wDp, hDp)) {
            val tickUnused = renderTick
            if (tickUnused >= 0L) {
                drawIntoCanvas { canvas ->
                    GameRenderer.drawChart(canvas.nativeCanvas, size.width, size.height, big = false)
                }
            }
        }
    }
}

@Composable
private fun TargetReadoutCard(
    tgt: com.example.keres9.sim.TargetReadoutUi,
    modifier: Modifier = Modifier
) {
    ChamferPanel(
        modifier = modifier.widthIn(max = 300.dp),
        borderColor = KeresColors.Steel
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tgt.name,
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = KeresColors.Cyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tgt.kind,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    color = KeresColors.Dim
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadoutStat("D ", "${tgt.dist}m")
                ReadoutStat("ΔV ", tgt.relSpeed)
                ReadoutStat("CLG ", tgt.closeRate)
            }
        }
    }
}

@Composable
private fun ReadoutStat(prefix: String, value: String) {
    Row {
        Text(prefix, fontFamily = RobotoMonoFamily, fontSize = 10.sp, color = KeresColors.Text)
        Text(value, fontFamily = RobotoMonoFamily, fontSize = 10.sp, color = KeresColors.Amber)
    }
}

@Composable
private fun ToastStack(
    toasts: List<ToastUiItem>,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = if (isPortrait) Alignment.End else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        for (t in toasts) {
            val accent = when (t.kind) {
                "warn" -> KeresColors.Amber
                "bad" -> KeresColors.Red
                "good" -> KeresColors.Green
                else -> KeresColors.Cyan
            }
            Box(
                modifier = Modifier
                    .alpha(if (t.fading) 0.25f else 1f)
                    .background(KeresColors.Panel)
                    .drawBehind {
                        drawRect(color = accent, topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height))
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val txt = if (t.count > 1) "${t.msg} ×${t.count}" else t.msg
                Text(
                    text = txt,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    textAlign = if (isPortrait) TextAlign.End else TextAlign.Center,
                    color = KeresColors.Text
                )
            }
        }
    }
}

@Composable
private fun EventBanner(
    text: String,
    isInfo: Boolean,
    modifier: Modifier = Modifier
) {
    ChamferPanel(
        modifier = modifier,
        backgroundColor = if (isInfo) KeresColors.BannerCyanBg else KeresColors.BannerRedBg,
        borderColor = if (isInfo) KeresColors.Cyan else KeresColors.Red
    ) {
        Text(
            text = text,
            fontFamily = RobotoMonoFamily,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = if (isInfo) KeresColors.Ice else KeresColors.RedTint,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun DockEnvelopeCard(
    dock: DockUiState,
    renderTick: Long,
    modifier: Modifier = Modifier
) {
    ChamferPanel(
        modifier = modifier,
        borderColor = KeresColors.Steel2
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Canvas(modifier = Modifier.size(86.dp)) {
                if (renderTick >= 0L) {
                    drawIntoCanvas { canvas ->
                        GameRenderer.drawScope(canvas.nativeCanvas, size.width, size.height)
                    }
                }
            }
            Column(modifier = Modifier.widthIn(max = 140.dp)) {
                Text(
                    text = dock.title,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = KeresColors.Amber
                )
                GateStatus("SPD", dock.spdOk)
                GateStatus("ANG", dock.angOk)
                GateStatus("LAT", dock.latOk)
                GateStatus("ROT", dock.rotOk)
                Text(
                    text = dock.hint,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    color = KeresColors.Dim,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun GateStatus(label: String, ok: Boolean) {
    Text(
        text = "${if (ok) "✓" else "✕"} $label",
        fontFamily = RobotoMonoFamily,
        fontSize = 9.sp,
        color = if (ok) KeresColors.Green else KeresColors.Red
    )
}

@Composable
private fun ScannerDrawer(
    freq: Float,
    lock: Float,
    label: String,
    renderTick: Long,
    onFreqChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChamferPanel(
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth(0.92f),
        borderColor = Color(0x6653D9FF)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DIRECTIONAL ARRAY · TUNE & TRACK",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = KeresColors.Cyan
                )
                KeresButton(
                    onClick = onClose,
                    minHeight = 40.dp,
                    contentPaddingHorizontal = 8.dp,
                    contentPaddingVertical = 4.dp,
                    modifier = Modifier.testTag("scan_close_button")
                ) {
                    Text("✕", fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, KeresColors.Steel)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (renderTick >= 0L) {
                        drawIntoCanvas { canvas ->
                            GameRenderer.drawSpectrum(canvas.nativeCanvas, size.width, size.height)
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = freq,
                    onValueChange = onFreqChange,
                    valueRange = 20f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = KeresColors.Cyan,
                        activeTrackColor = Color(0xFF134A5E),
                        inactiveTrackColor = Color(0xFF0E2A38)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scanner_freq_slider")
                )
                Text(
                    text = "${"%.1f".format(freq)} kHz",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 11.sp,
                    color = KeresColors.Cyan,
                    modifier = Modifier.width(74.dp),
                    textAlign = TextAlign.End
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF0E1826))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(lock.coerceIn(0f, 1f))
                        .background(Brush.horizontalGradient(listOf(KeresColors.Cyan, KeresColors.Mag)))
                )
            }
            Text(
                text = label,
                fontFamily = RobotoMonoFamily,
                fontSize = 9.sp,
                color = KeresColors.Dim,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AiPilotBanner(
    act: String,
    cap: String,
    sub: String,
    time: String,
    modifier: Modifier = Modifier
) {
    ChamferPanel(
        modifier = modifier,
        borderColor = Color(0x736DF08F)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(KeresColors.Green)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "AI PILOT",
                        fontFamily = RobotoMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        color = Color(0xFF06110A)
                    )
                }
                Text(
                    text = act,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    color = KeresColors.Green,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = time,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    color = KeresColors.Dim
                )
            }
            Text(
                text = cap.ifBlank { "—" },
                fontFamily = RobotoMonoFamily,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = KeresColors.Text,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    color = KeresColors.Cyan,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
