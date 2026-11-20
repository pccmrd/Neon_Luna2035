package com.example.keres9.ui

import android.app.Application
import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.AndroidViewModel
import com.example.R
import com.example.keres9.render.GameRenderer
import com.example.keres9.sim.ControlsUiState
import com.example.keres9.sim.GameSession
import com.example.keres9.sim.HudUiState
import kotlinx.coroutines.flow.StateFlow

class GameViewModel(application: Application) : AndroidViewModel(application) {
    val uiState: StateFlow<HudUiState> = GameSession.uiState
    val controlsState: StateFlow<ControlsUiState> = GameSession.controlsState
    val renderTick: StateFlow<Long> = GameSession.renderTick

    private var lastFrameDt: Double = 0.016

    init {
        val ctx: Context = application.applicationContext
        val tf = runCatching { ResourcesCompat.getFont(ctx, R.font.roboto_mono) }.getOrNull()
        GameRenderer.setTypeface(tf)
        GameSession.boot(ctx)
    }

    fun stepFrame(dtSec: Double) {
        lastFrameDt = dtSec
        GameSession.tick(dtSec)
    }

    fun currentDt(): Double = lastFrameDt

    fun onBackground() {
        GameSession.onAppBackground()
    }

    fun onForeground() {
        GameSession.onAppForeground()
    }
}
