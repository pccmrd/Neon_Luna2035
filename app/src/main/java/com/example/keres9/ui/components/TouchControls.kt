package com.example.keres9.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keres9.core.GS
import com.example.keres9.core.Vec2
import com.example.keres9.core.cl
import com.example.keres9.core.hy
import com.example.keres9.sim.ControlsUiState
import com.example.keres9.sim.IN
import com.example.keres9.sim.s2wv
import com.example.keres9.sim.worldScale
import com.example.keres9.ui.theme.KeresColors
import com.example.keres9.ui.theme.RobotoMonoFamily
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WorldTouchSurface(
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tapSlopPx = with(density) { 10.dp.toPx() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("world_touch_surface")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val pointers = HashMap<Long, Offset>()
                    var downTime = System.currentTimeMillis()
                    var totalMove = 0f
                    var pinchStartDist = 0f
                    var pinchStartZoom = GS.cam.tz
                    var lastTapPos = Offset.Zero

                    val first = awaitFirstDown(requireUnconsumed = false)
                    pointers[first.id.value] = first.position
                    lastTapPos = first.position

                    while (pointers.isNotEmpty()) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                for (c in event.changes) {
                                    if (c.pressed && !pointers.containsKey(c.id.value)) {
                                        pointers[c.id.value] = c.position
                                        if (pointers.size == 2) {
                                            val pts = pointers.values.toList()
                                            pinchStartDist = hy(
                                                (pts[0].x - pts[1].x).toDouble(),
                                                (pts[0].y - pts[1].y).toDouble()
                                            ).toFloat()
                                            pinchStartZoom = GS.cam.tz
                                        }
                                    }
                                }
                            }
                            PointerEventType.Move -> {
                                if (pointers.size == 2) {
                                    for (c in event.changes) {
                                        if (pointers.containsKey(c.id.value)) {
                                            pointers[c.id.value] = c.position
                                        }
                                    }
                                    val pts = pointers.values.toList()
                                    if (pts.size == 2 && pinchStartDist > 1f) {
                                        val d = hy(
                                            (pts[0].x - pts[1].x).toDouble(),
                                            (pts[0].y - pts[1].y).toDouble()
                                        )
                                        GS.cam.tz = cl((pinchStartZoom * d) / pinchStartDist, 0.35, 3.4)
                                        GS.cam.user = 6.0
                                    }
                                    totalMove += 20f
                                } else {
                                    for (c in event.changes) {
                                        val prev = pointers[c.id.value]
                                        if (prev != null) {
                                            val dx = c.position.x - prev.x
                                            val dy = c.position.y - prev.y
                                            pointers[c.id.value] = c.position
                                            lastTapPos = c.position
                                            totalMove += abs(dx) + abs(dy)
                                            val sc = worldScale().coerceAtLeast(0.2)
                                            val wv = s2wv(-dx / sc, -dy / sc)
                                            GS.cam.lx += wv.x
                                            GS.cam.ly += wv.y
                                            GS.cam.lt = 2.2
                                        }
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                for (c in event.changes) {
                                    if (!c.pressed) {
                                        lastTapPos = c.position
                                        pointers.remove(c.id.value)
                                    }
                                }
                            }
                            else -> {
                                for (c in event.changes) {
                                    if (!c.pressed) pointers.remove(c.id.value)
                                }
                            }
                        }
                    }

                    val elapsed = System.currentTimeMillis() - downTime
                    if (elapsed < 260L && totalMove < tapSlopPx) {
                        IN.taps.add(Vec2(lastTapPos.x.toDouble(), lastTapPos.y.toDouble()))
                    }
                }
            }
    )
}

@Composable
fun FlightTouchControls(
    controls: ControlsUiState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxHeight > maxWidth
        val compactLandscape = !isPortrait && maxHeight < 430.dp

        val zoneSize = when {
            compactLandscape -> 120.dp
            isPortrait -> 134.dp
            else -> 146.dp
        }
        val baseRadius = when {
            compactLandscape -> 45.dp
            isPortrait -> 49.dp
            else -> 54.dp
        }
        val thumbSize = if (compactLandscape) 38.dp else 44.dp
        val thrHeight = when {
            compactLandscape -> 118.dp
            isPortrait -> 128.dp
            else -> 156.dp
        }
        val thrBottom = if (isPortrait) 172.dp else 0.dp

        // Left Joystick: MOVE · RCS
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 6.dp)
        ) {
            VirtualStick(
                label = "MOVE · RCS",
                zoneSize = zoneSize,
                baseRadius = baseRadius,
                thumbSize = thumbSize,
                aiGhost = controls.aiOn,
                ghostX = controls.aiLx.toFloat(),
                ghostY = controls.aiLy.toFloat(),
                tag = "stick_left"
            ) { x, y ->
                IN.lx = x.toDouble()
                IN.ly = y.toDouble()
            }
        }

        // Right Joystick: SPIN · TORQUE
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = if (isPortrait) 48.dp else 54.dp, bottom = 6.dp)
        ) {
            VirtualStick(
                label = "SPIN · TORQUE",
                zoneSize = zoneSize,
                baseRadius = baseRadius,
                thumbSize = thumbSize,
                aiGhost = controls.aiOn,
                ghostX = controls.aiRx.toFloat(),
                ghostY = 0f,
                tag = "stick_right"
            ) { x, y ->
                IN.rx = x.toDouble()
                IN.ry = y.toDouble()
            }
        }

        // Throttle Rail
        val thrModifier = if (isPortrait) {
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = thrBottom)
        } else {
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        }
        ThrottleRail(
            value = if (controls.aiOn) controls.aiThr.toFloat() else controls.humanThr.toFloat(),
            aiGhost = controls.aiOn,
            heightDp = thrHeight,
            modifier = thrModifier
        ) { v ->
            IN.thr = v.toDouble()
        }

        // Left Action Cluster: MATCH, BRAKE, TGT, GEAR
        val leftClusterBottom = when {
            compactLandscape -> 128.dp
            isPortrait -> 146.dp
            else -> 162.dp
        }
        val sqWidth = if (compactLandscape) 50.dp else 56.dp
        val sqHeight = if (compactLandscape) 40.dp else 44.dp

        if (compactLandscape) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = leftClusterBottom)
            ) {
                ControlLeftButtons(controls, sqWidth, sqHeight)
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = leftClusterBottom)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeresButton(
                        onClick = { IN.edge.add("match") },
                        active = controls.matchAct,
                        minHeight = 48.dp,
                        contentPaddingHorizontal = 4.dp,
                        modifier = Modifier
                            .width(sqWidth)
                            .height(48.dp)
                            .testTag("match_button")
                    ) {
                        Text("MATCH\nΔV", fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
                    }
                    HoldControlButton(
                        label = "BRAKE",
                        active = controls.brakeAct,
                        modifier = Modifier
                            .width(sqWidth)
                            .height(48.dp)
                            .testTag("brake_button"),
                        onHoldChange = { held -> IN.brakeHold = held }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeresButton(
                        onClick = { IN.edge.add("target") },
                        minHeight = 48.dp,
                        contentPaddingHorizontal = 4.dp,
                        modifier = Modifier
                            .width(sqWidth)
                            .height(48.dp)
                            .testTag("target_button")
                    ) {
                        Text("TGT ▸", fontSize = 9.sp)
                    }
                    KeresButton(
                        onClick = { IN.edge.add("gear") },
                        active = controls.gearAct,
                        minHeight = 48.dp,
                        contentPaddingHorizontal = 4.dp,
                        modifier = Modifier
                            .width(sqWidth)
                            .height(48.dp)
                            .testTag("gear_button")
                    ) {
                        Text(
                            "GEAR",
                            fontSize = 9.sp,
                            color = if (controls.gearEnabled) KeresColors.Amber else KeresColors.Dim
                        )
                    }
                }
            }
        }

        // Right Action Cluster: CTX, ALT, SCAN, MINE
        val rightClusterBottom = when {
            compactLandscape -> 128.dp
            isPortrait -> 146.dp
            else -> 162.dp
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 54.dp, bottom = rightClusterBottom)
        ) {
            if (!compactLandscape) {
                KeresButton(
                    onClick = { IN.edge.add("ctx") },
                    enabled = controls.ctxEnabled,
                    primaryCtx = true,
                    ready = controls.ctxReady,
                    minHeight = 56.dp,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 86.dp, minHeight = 56.dp)
                        .testTag("ctx_button")
                ) {
                    Text(
                        controls.ctxLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                if (controls.altLabel != null) {
                    KeresButton(
                        onClick = { IN.edge.add("alt") },
                        minHeight = 48.dp,
                        contentPaddingVertical = 4.dp,
                        modifier = Modifier
                            .width(86.dp)
                            .height(48.dp)
                            .testTag("alt_button")
                    ) {
                        Text(controls.altLabel, fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (compactLandscape) {
                    if (controls.altLabel != null) {
                        KeresButton(
                            onClick = { IN.edge.add("alt") },
                            minHeight = 48.dp,
                            modifier = Modifier
                                .width(68.dp)
                                .height(48.dp)
                                .testTag("alt_button")
                        ) {
                            Text(controls.altLabel, fontSize = 8.sp)
                        }
                    }
                    KeresButton(
                        onClick = { IN.edge.add("ctx") },
                        enabled = controls.ctxEnabled,
                        primaryCtx = true,
                        ready = controls.ctxReady,
                        minHeight = 48.dp,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 76.dp, minHeight = 48.dp)
                            .testTag("ctx_button")
                    ) {
                        Text(controls.ctxLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                KeresButton(
                    onClick = { IN.edge.add("light") },
                    active = controls.lightOn,
                    minHeight = 48.dp,
                    contentPaddingHorizontal = 4.dp,
                    modifier = Modifier
                        .width(sqWidth.coerceAtLeast(48.dp))
                        .height(48.dp)
                        .testTag("light_button")
                ) {
                    Text("LIGHT", fontSize = 9.sp)
                }
                KeresButton(
                    onClick = { IN.edge.add("scan") },
                    active = controls.scanAct,
                    minHeight = 48.dp,
                    contentPaddingHorizontal = 4.dp,
                    modifier = Modifier
                        .width(sqWidth.coerceAtLeast(48.dp))
                        .height(48.dp)
                        .testTag("scan_button")
                ) {
                    Text("SCAN", fontSize = 9.sp)
                }
                HoldControlButton(
                    label = "MINE",
                    active = controls.aiOn && controls.aiMine,
                    modifier = Modifier
                        .width(sqWidth.coerceAtLeast(48.dp))
                        .height(48.dp)
                        .testTag("mine_button"),
                    onHoldChange = { held -> IN.mineHold = held }
                )
            }
        }
    }
}

@Composable
private fun ControlLeftButtons(
    controls: ControlsUiState,
    sqWidth: androidx.compose.ui.unit.Dp,
    sqHeight: androidx.compose.ui.unit.Dp
) {
    KeresButton(
        onClick = { IN.edge.add("match") },
        active = controls.matchAct,
        minHeight = 48.dp,
        contentPaddingHorizontal = 4.dp,
        modifier = Modifier
            .width(sqWidth)
            .height(48.dp)
            .testTag("match_button")
    ) {
        Text("MATCH\nΔV", fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center)
    }
    HoldControlButton(
        label = "BRAKE",
        active = controls.brakeAct,
        modifier = Modifier
            .width(sqWidth)
            .height(48.dp)
            .testTag("brake_button"),
        onHoldChange = { held -> IN.brakeHold = held }
    )
    KeresButton(
        onClick = { IN.edge.add("target") },
        minHeight = 48.dp,
        contentPaddingHorizontal = 4.dp,
        modifier = Modifier
            .width(sqWidth)
            .height(48.dp)
            .testTag("target_button")
    ) {
        Text("TGT ▸", fontSize = 9.sp)
    }
    KeresButton(
        onClick = { IN.edge.add("gear") },
        active = controls.gearAct,
        minHeight = 48.dp,
        contentPaddingHorizontal = 4.dp,
        modifier = Modifier
            .width(sqWidth)
            .height(48.dp)
            .testTag("gear_button")
    ) {
        Text(
            "GEAR",
            fontSize = 9.sp,
            color = if (controls.gearEnabled) KeresColors.Amber else KeresColors.Dim
        )
    }
}

@Composable
private fun HoldControlButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onHoldChange: (Boolean) -> Unit
) {
    val shape = rememberChamferShape(9.dp)
    var pressed by remember { mutableStateOf(false) }
    val borderColor = when {
        pressed -> KeresColors.Amber
        active -> KeresColors.Green
        else -> KeresColors.Steel2
    }
    val textColor = when {
        pressed -> KeresColors.AmberBright
        active -> KeresColors.Green
        else -> KeresColors.Amber
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(KeresColors.ButtonTop, KeresColors.ButtonBottom)))
            .chamferBorder(cut = 9.dp, borderColor = borderColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onHoldChange(true)
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.none { it.pressed }) break
                    }
                    pressed = false
                    onHoldChange(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = RobotoMonoFamily,
            fontSize = 9.sp,
            letterSpacing = 0.7.sp,
            color = textColor
        )
    }
}

@Composable
private fun VirtualStick(
    label: String,
    zoneSize: androidx.compose.ui.unit.Dp,
    baseRadius: androidx.compose.ui.unit.Dp,
    thumbSize: androidx.compose.ui.unit.Dp,
    aiGhost: Boolean,
    ghostX: Float,
    ghostY: Float,
    tag: String,
    onStick: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val maxTravelPx = with(density) { (baseRadius * 0.8f).toPx() }
    var userDragging by remember { mutableStateOf(false) }
    var baseCenterOffset by remember { mutableStateOf(Offset.Zero) }
    var thumbOffsetPx by remember { mutableStateOf(Offset.Zero) }

    val displayThumbPx = if (aiGhost && !userDragging) {
        Offset(ghostX * maxTravelPx, ghostY * maxTravelPx)
    } else {
        thumbOffsetPx
    }

    Box(
        modifier = Modifier
            .size(zoneSize)
            .testTag(tag)
            .pointerInput(maxTravelPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val ox = down.position.x - cx
                    val oy = down.position.y - cy
                    userDragging = true
                    baseCenterOffset = Offset(ox, oy)
                    thumbOffsetPx = Offset.Zero
                    onStick(0f, 0f)

                    while (true) {
                        val ev = awaitPointerEvent()
                        val change = ev.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        var dx = change.position.x - down.position.x
                        var dy = change.position.y - down.position.y
                        val dist = kotlin.math.hypot(dx, dy)
                        if (dist > maxTravelPx && dist > 0.01f) {
                            dx *= maxTravelPx / dist
                            dy *= maxTravelPx / dist
                        }
                        thumbOffsetPx = Offset(dx, dy)
                        onStick(dx / maxTravelPx, dy / maxTravelPx)
                        change.consume()
                    }
                    userDragging = false
                    baseCenterOffset = Offset.Zero
                    thumbOffsetPx = Offset.Zero
                    onStick(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(baseCenterOffset.x.roundToInt(), baseCenterOffset.y.roundToInt()) }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rPx = baseRadius.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    0.3f to Color(0x8C101823),
                    0.85f to Color(0x33101823),
                    center = center,
                    radius = rPx
                ),
                radius = rPx,
                center = center
            )
            drawCircle(
                color = if (aiGhost) Color(0x666DF08F) else KeresColors.Steel2,
                radius = rPx,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
            )
            val thumbCenter = center + displayThumbPx
            val tRadius = (thumbSize / 2f).toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD9A0), KeresColors.Ember, Color(0xFF8A4A12)),
                    center = Offset(thumbCenter.x - tRadius * 0.24f, thumbCenter.y - tRadius * 0.36f),
                    radius = tRadius * 1.15f
                ),
                radius = tRadius,
                center = thumbCenter
            )
            drawCircle(
                color = if (aiGhost) KeresColors.Green else Color(0xFFFFCF90),
                radius = tRadius,
                center = thumbCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx())
            )
        }
        Text(
            text = label,
            fontFamily = RobotoMonoFamily,
            fontSize = 8.sp,
            letterSpacing = 1.1.sp,
            color = KeresColors.Dim,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun ThrottleRail(
    value: Float,
    aiGhost: Boolean,
    heightDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    var heightPx by remember { mutableFloatStateOf(160f) }
    val shape = rememberChamferShape(8.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = "THR",
            fontFamily = RobotoMonoFamily,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp,
            color = KeresColors.Dim
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(48.dp)
                .height(heightDp)
                .testTag("throttle_rail")
                .clip(shape)
                .background(KeresColors.Panel)
                .chamferBorder(cut = 8.dp, borderColor = if (aiGhost) KeresColors.Green else KeresColors.Steel2)
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(32f) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun update(y: Float) {
                            val pad = 8.dp.toPx()
                            val usable = (heightPx - pad * 2f).coerceAtLeast(10f)
                            val v = (1f - (y - pad) / usable).coerceIn(0f, 1f)
                            onValueChange(v)
                        }
                        update(down.position.y)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null || !ch.pressed) break
                            update(ch.position.y)
                            ch.consume()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = 12.dp.toPx()
                val padY = 8.dp.toPx()
                val usableH = (size.height - padY * 2f).coerceAtLeast(10f)
                // Track ticks
                drawRect(
                    color = KeresColors.TrackBg,
                    topLeft = Offset(padX, padY),
                    size = androidx.compose.ui.geometry.Size(size.width - padX * 2f, usableH)
                )
                val step = 12.dp.toPx()
                var ty = padY
                while (ty < size.height - padY) {
                    drawLine(
                        color = Color(0xFF1A2634),
                        start = Offset(padX, ty),
                        end = Offset(size.width - padX, ty),
                        strokeWidth = 1.dp.toPx()
                    )
                    ty += step
                }
                // Fill bar
                val v = value.coerceIn(0f, 1f)
                val fillH = usableH * v
                if (fillH > 0.5f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(KeresColors.Amber, KeresColors.Rust),
                            startY = size.height - padY - fillH,
                            endY = size.height - padY
                        ),
                        topLeft = Offset(padX, size.height - padY - fillH),
                        size = androidx.compose.ui.geometry.Size(size.width - padX * 2f, fillH)
                    )
                }
                // Thumb
                val thumbH = 8.dp.toPx()
                val thumbY = (size.height - padY - fillH - thumbH / 2f).coerceIn(padY * 0.5f, size.height - padY - thumbH * 0.5f)
                drawRect(
                    color = Color(0xFFFFD9A0),
                    topLeft = Offset(6.dp.toPx(), thumbY),
                    size = androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), thumbH)
                )
                drawRect(
                    color = if (aiGhost) KeresColors.Green else KeresColors.Ember,
                    topLeft = Offset(6.dp.toPx(), thumbY),
                    size = androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), thumbH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                )
            }
        }
    }
}
