package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.keres9.core.GS
import com.example.keres9.render.GameRenderer
import com.example.keres9.sim.GameSession
import com.example.keres9.sim.IN
import com.example.keres9.sim.VIEW
import com.example.keres9.ui.GameViewModel
import com.example.keres9.ui.components.CrtAtmosphereOverlay
import com.example.keres9.ui.components.FlightTouchControls
import com.example.keres9.ui.components.HudOverlays
import com.example.keres9.ui.components.WorldTouchSurface
import com.example.keres9.ui.screens.ConfirmModal
import com.example.keres9.ui.screens.DeathModal
import com.example.keres9.ui.screens.DialogModal
import com.example.keres9.ui.screens.HelpManualModal
import com.example.keres9.ui.screens.OrbitChartModal
import com.example.keres9.ui.screens.PauseModal
import com.example.keres9.ui.screens.StationSheet
import com.example.keres9.ui.screens.TitleScreen
import com.example.keres9.ui.screens.WinModal
import com.example.keres9.ui.theme.Keres9Theme
import com.example.keres9.ui.theme.KeresColors

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            Keres9Theme {
                KeresRootScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun KeresRootScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val controlsState by viewModel.controlsState.collectAsStateWithLifecycle()
    val renderTick by viewModel.renderTick.collectAsStateWithLifecycle()
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onBackground()
                Lifecycle.Event.ON_RESUME -> viewModel.onForeground()
                Lifecycle.Event.ON_STOP -> GameSession.save()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNs == 0L) 0.016 else (now - lastNs) / 1_000_000_000.0
                lastNs = now
                viewModel.stepFrame(dt)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KeresColors.Void)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                handleHardwareKey(keyEvent.key, keyEvent.type == KeyEventType.KeyDown)
            }
    ) {
        // World Rendering Surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    val firstInit = VIEW.widthPx <= 0f
                    VIEW.widthPx = sz.width.toFloat().coerceAtLeast(1f)
                    VIEW.heightPx = sz.height.toFloat().coerceAtLeast(1f)
                    VIEW.density = density
                    if (GS.G?.mode == "title" || firstInit) {
                        if (GS.G?.mode == "title") {
                            GameSession.titleCam()
                            GS.cam.z = GS.cam.tz
                        }
                    }
                }
        ) {
            if (renderTick >= 0L) {
                drawIntoCanvas { canvas ->
                    GameRenderer.renderWorld(
                        canvas = canvas.nativeCanvas,
                        widthPx = size.width,
                        heightPx = size.height,
                        density = density,
                        dt = viewModel.currentDt()
                    )
                }
            }
        }

        // World tap / drag / pinch surface shares exact Canvas bounds when playing
        if (uiState.mode != "title") {
            WorldTouchSurface()
        }

        // CRT scanlines & vignette pass-through overlay
        CrtAtmosphereOverlay()

        // Safe-drawing inset container for UI & Touch controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (uiState.mode == "title") {
                TitleScreen(state = uiState)
            } else {
                // Virtual sticks, throttle rail, flight/EVA action buttons
                FlightTouchControls(controls = controlsState)

                // Top status bar, objective card, minimap, toasts, banners, scanner, docking scope, AI narration
                HudOverlays(state = uiState, renderTick = renderTick)
            }

            // Modals (highest layer)
            if (uiState.station != null) {
                StationSheet(
                    station = uiState.station!!,
                    aiPickTag = controlsState.aiPickTag
                )
            }
            if (uiState.mapOpen) {
                OrbitChartModal(renderTick = renderTick)
            }
            if (uiState.helpOpen) {
                HelpManualModal()
            }
            if (uiState.mode == "pause") {
                PauseModal(state = uiState, aiPickTag = controlsState.aiPickTag)
            }
            if (uiState.dialog != null) {
                DialogModal(dialog = uiState.dialog!!)
            }
            if (uiState.mode == "dead") {
                DeathModal(
                    why = uiState.deathWhy,
                    stats = uiState.endStats,
                    aiPickTag = controlsState.aiPickTag
                )
            }
            if (uiState.mode == "win") {
                WinModal(
                    stats = uiState.endStats,
                    aiPickTag = controlsState.aiPickTag
                )
            }
            if (uiState.confirmText != null) {
                ConfirmModal(text = uiState.confirmText!!)
            }
        }
    }
}

private fun handleHardwareKey(key: Key, down: Boolean): Boolean {
    val code = when (key) {
        Key.W -> "KeyW"
        Key.A -> "KeyA"
        Key.S -> "KeyS"
        Key.D -> "KeyD"
        Key.Q -> "KeyQ"
        Key.E -> "KeyE"
        Key.ShiftLeft -> "ShiftLeft"
        Key.ShiftRight -> "ShiftRight"
        Key.CtrlLeft -> "ControlLeft"
        Key.CtrlRight -> "ControlRight"
        Key.Spacebar -> "Space"
        Key.M -> "KeyM"
        Key.Z -> "KeyZ"
        Key.F -> "KeyF"
        Key.V -> "KeyV"
        Key.X -> "KeyX"
        Key.T -> "KeyT"
        Key.Tab -> "Tab"
        Key.G -> "KeyG"
        Key.C -> "KeyC"
        Key.L -> "KeyL"
        Key.P -> "KeyP"
        Key.H -> "KeyH"
        Key.Escape -> "Escape"
        else -> return false
    }

    if (down) {
        val isNew = IN.keys.add(code)
        if (isNew) {
            if (code == "Space" || code == "KeyM") IN.mineHold = true
            if (code == "KeyZ") IN.brakeHold = true
            when (code) {
                "KeyF" -> IN.edge.add("ctx")
                "KeyV" -> IN.edge.add("alt")
                "KeyX" -> IN.edge.add("match")
                "KeyT", "Tab" -> IN.edge.add("target")
                "KeyG" -> IN.edge.add("gear")
                "KeyC" -> IN.edge.add("scan")
                "KeyL" -> IN.edge.add("light")
                "KeyP" -> IN.edge.add("ai")
                "KeyH" -> IN.edge.add("help")
                "Escape" -> IN.edge.add("pause")
            }
        }
    } else {
        IN.keys.remove(code)
        if ((code == "Space" && !IN.keys.contains("KeyM")) || (code == "KeyM" && !IN.keys.contains("Space"))) {
            IN.mineHold = false
        }
        if (code == "KeyZ") IN.brakeHold = false
    }
    return true
}
