package com.example.keres9.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keres9.render.GameRenderer
import com.example.keres9.render.MapTransform
import com.example.keres9.sim.DialogUiState
import com.example.keres9.sim.GameSession
import com.example.keres9.sim.HudUiState
import com.example.keres9.sim.StatEntryUi
import com.example.keres9.sim.setNav
import com.example.keres9.ui.components.ChamferPanel
import com.example.keres9.ui.components.KeresButton
import com.example.keres9.ui.theme.KeresColors
import com.example.keres9.ui.theme.RobotoMonoFamily

@Composable
fun PauseModal(
    state: HudUiState,
    aiPickTag: String?,
    modifier: Modifier = Modifier
) {
    BackHandler { GameSession.mode("play") }
    ModalBackdrop(modifier) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ModalHeader(title = "PAUSED", onClose = { GameSession.mode("play") }, closeTag = "pause_close_button")
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                ) {
                    KeresButton(
                        onClick = { GameSession.mode("play") },
                        modifier = Modifier.fillMaxWidth().testTag("resume_button")
                    ) { Text("RESUME") }
                    KeresButton(
                        onClick = { GameSession.toggleAI(); GameSession.mode("play") },
                        greenAccent = state.aiOn,
                        modifier = Modifier.fillMaxWidth().testTag("pause_ai_button")
                    ) { Text(if (state.aiOn) "AI PILOT: ON" else "AI PILOT: OFF") }
                    KeresButton(
                        onClick = { GameSession.openHelp() },
                        modifier = Modifier.fillMaxWidth().testTag("pause_manual_button")
                    ) { Text("FIELD MANUAL") }
                    KeresButton(
                        onClick = { GameSession.toggleSound() },
                        modifier = Modifier.fillMaxWidth().testTag("pause_sound_button")
                    ) { Text(if (state.soundOn) "SOUND: ON" else "SOUND: OFF") }
                    KeresButton(
                        onClick = { GameSession.tow() },
                        aiHighlight = aiPickTag == "tow_button",
                        modifier = Modifier.fillMaxWidth().testTag("tow_button")
                    ) { Text("CALL RESCUE / TOW · 120–300₡") }
                    KeresButton(
                        onClick = { GameSession.requestNewCareerConfirm() },
                        modifier = Modifier.fillMaxWidth().testTag("abandon_career_button")
                    ) { Text("ABANDON · NEW CAREER") }
                    KeresButton(
                        onClick = { GameSession.exitToTitle() },
                        modifier = Modifier.fillMaxWidth().testTag("exit_title_button")
                    ) { Text("EXIT TO TITLE") }
                }
            }
        }
    }
}

@Composable
fun HelpManualModal(modifier: Modifier = Modifier) {
    BackHandler { GameSession.closeHelp() }
    ModalBackdrop(modifier) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(0.95f).heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ModalHeader(title = "FIELD MANUAL", onClose = { GameSession.closeHelp() }, closeTag = "help_close_button")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    ManualSection("THE CAREER", listOf(
                        "1 · CREW HAND" to "Walk from HAB 14 to the SELENE LIFT, ride the cable to SELENE HIGH, sign on with Foreman Lu in the CREW OFFICE. The MULE-3 shuttle drops you at a rock: cut ore on EVA, fill your sack, get back aboard before the horn. Pay = base + half your haul.",
                        "2 · ORBITAL TUG" to "Buy a WREN at the SELENE HIGH yard. It flies anywhere in the band. Harpoon a rock and mine from the hull. A bigger hold means more money.",
                        "3 · LANDER" to "Buy a HARE at SHACKLETON DEPOT on the surface. You fly it all the way: launch from the pad, climb, circularise, mine and trade in orbit, then deorbit and land. Ice and metal sell for much more on the ground."
                    ))
                    ManualSection("FLYING NEAR THE MOON", listOf(
                        "Gravity" to "The Moon pulls 1.6 m/s² at the surface. The camera rotates so \"up\" is always away from the Moon. The dotted line is your ballistic path, and IMPACT means it hits the ground.",
                        "Orbit band" to "Rocks and stations sit near the synchronous radius, where gravity and the frame balance out. They hang still in the sky above the Rim, which is why the cable works.",
                        "Throttle / sticks" to "Right rail is the main engine. The left stick fires RCS relative to the screen, and the right stick turns. SAS (ALT in flight) holds UP, RETRO or PRO.",
                        "MATCH / BRAKE" to "MATCH holds the target's velocity. BRAKE kills your velocity against the band. Both hover-compensate half of gravity.",
                        "Landing" to "The HARE is a tail-sitter. Come down nose-up, under 3 m/s, with less than 20° of tilt, and the pad clamps you automatically. You can also put down on flat regolith."
                    ))
                    ManualSection("DISCOVERY", listOf(
                        "Survey" to "Uncatalogued rocks show as UNKNOWN. Keep one inside your array range (or ping it with your suit scanner) until the survey finishes. The Catalog Office pays a bounty for every body.",
                        "Deep contacts" to "SCAN opens the directional array. Sweep it over a rock and tune the dial onto a resonance, then hold the lock to reveal veins, sealed chambers, KERES-9 core shards and claim beacons."
                    ))
                    ManualSection("EVA", listOf(
                        "Walking" to "On the ground: left stick walks/hops. Air refills indoors. Stations and habs have rooms. Walk into a room and tap the context button at its terminal.",
                        "In orbit" to "The suit jetpack is in the left stick, and MINE runs your cutter. Ore goes straight into your sack. Bring it to the MULE or your own ship."
                    ))
                    ManualSection("AI PLAYER", listOf(
                        "AI PILOT" to "The computer plays with the same sticks, rail and buttons you use. It walks, rides the lift, works crew shifts, buys a tug, mines, buys a lander, launches, lands and delivers. Touch the controls to take over."
                    ))
                }
            }
        }
    }
}

@Composable
private fun ManualSection(title: String, rows: List<Pair<String, String>>) {
    Text(
        text = title,
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = KeresColors.Cyan,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
    HorizontalDivider(color = KeresColors.Steel)
    for ((k, v) in rows) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
        ) {
            Text(
                text = k,
                fontFamily = RobotoMonoFamily,
                fontSize = 10.sp,
                color = KeresColors.Amber,
                modifier = Modifier.weight(0.34f)
            )
            Text(
                text = v,
                fontFamily = RobotoMonoFamily,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = KeresColors.Text,
                modifier = Modifier.weight(0.66f)
            )
        }
        HorizontalDivider(color = KeresColors.Divider)
    }
}

@Composable
fun OrbitChartModal(
    renderTick: Long,
    modifier: Modifier = Modifier
) {
    BackHandler { GameSession.closeMap() }
    var lastTransform by remember { mutableStateOf<MapTransform?>(null) }

    ModalBackdrop(modifier) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth(0.96f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ModalHeader(title = "ORBIT CHART · SELENE BAND", onClose = { GameSession.closeMap() }, closeTag = "map_close_button")
                Box(modifier = Modifier.padding(12.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .border(1.dp, KeresColors.Steel)
                            .testTag("orbit_chart_canvas")
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val m = lastTransform ?: return@detectTapGestures
                                    val px = offset.x - m.width / 2f
                                    val py = offset.y - m.height / 2f
                                    setNav(m.cx0 + px / m.sc, m.cy0 + py / m.sc, "CHART WAYPOINT")
                                }
                            }
                    ) {
                        if (renderTick >= 0L) {
                            drawIntoCanvas { canvas ->
                                lastTransform = GameRenderer.drawChart(canvas.nativeCanvas, size.width, size.height, big = true)
                            }
                        }
                    }
                }
                Text(
                    text = "CYAN you · AMBER orbital stations · GREEN surface sites · ORANGE unknown rocks · MAGENTA chambers / waypoint · GOLD contracts · tap chart to set nav",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 9.sp,
                    color = KeresColors.Dim,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
fun ConfirmModal(
    text: String,
    modifier: Modifier = Modifier
) {
    BackHandler { GameSession.confirmNo() }
    ModalBackdrop(modifier) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = text,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    color = KeresColors.Text,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    KeresButton(
                        onClick = { GameSession.confirmYes() },
                        danger = true,
                        modifier = Modifier.weight(1f).testTag("confirm_yes_button")
                    ) { Text("YES") }
                    KeresButton(
                        onClick = { GameSession.confirmNo() },
                        modifier = Modifier.weight(1f).testTag("confirm_no_button")
                    ) { Text("CANCEL") }
                }
            }
        }
    }
}

@Composable
fun DeathModal(
    why: String,
    stats: List<StatEntryUi>,
    aiPickTag: String?,
    modifier: Modifier = Modifier
) {
    ModalBackdrop(modifier) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(0.94f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            ) {
                Text(
                    text = "SIGNAL LOST",
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 3.5.sp,
                    color = KeresColors.Red,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                HorizontalDivider(color = KeresColors.Steel)
                Text(
                    text = why,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 11.sp,
                    color = KeresColors.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
                Text(
                    text = "Rim medics found you. Your ships are where you left them.",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 11.sp,
                    color = KeresColors.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                StatGridView(stats)
                KeresButton(
                    onClick = { GameSession.respawn() },
                    aiHighlight = aiPickTag == "respawn_button",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .testTag("respawn_button")
                ) {
                    Text("WAKE UP IN HAB 14 · −15% CREDITS")
                }
            }
        }
    }
}

@Composable
fun WinModal(
    stats: List<StatEntryUi>,
    aiPickTag: String?,
    modifier: Modifier = Modifier
) {
    ModalBackdrop(modifier) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(0.94f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            ) {
                Text(
                    text = "THE ORPHAN IS FREE",
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 3.sp,
                    color = KeresColors.Green,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                HorizontalDivider(color = KeresColors.Steel)
                Text(
                    text = "The claim is filed in your name, and the KERES-9 logs are public. The rest of the band is still out there, uncatalogued. You know the way up.",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = KeresColors.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
                StatGridView(stats)
                KeresButton(
                    onClick = { GameSession.keepFlyingAfterWin() },
                    aiHighlight = aiPickTag == "keep_button",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .testTag("keep_button")
                ) {
                    Text("KEEP FLYING")
                }
            }
        }
    }
}

@Composable
fun DialogModal(
    dialog: DialogUiState,
    modifier: Modifier = Modifier
) {
    BackHandler { GameSession.closeDialog() }
    val bmp = remember(dialog.seed, dialog.fac) {
        GameRenderer.renderFaceBitmap(dialog.seed, dialog.fac).asImageBitmap()
    }
    ModalBackdrop(modifier) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.94f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = dialog.name,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(56.dp).border(1.dp, KeresColors.Steel2)
                    )
                    Column {
                        Text(dialog.name, fontFamily = RobotoMonoFamily, fontSize = 14.sp, letterSpacing = 1.2.sp, color = KeresColors.Cyan)
                        Text(dialog.role, fontFamily = RobotoMonoFamily, fontSize = 9.sp, color = KeresColors.Dim, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                HorizontalDivider(color = KeresColors.Steel)
                Text(
                    text = dialog.text,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = KeresColors.Text,
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    dialog.choices.forEachIndexed { i, ch ->
                        KeresButton(
                            onClick = { ch.onSelect() },
                            modifier = Modifier.fillMaxWidth().testTag("dialog_choice_$i")
                        ) {
                            Text("▸ ${ch.label}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGridView(stats: List<StatEntryUi>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(14.dp)
    ) {
        var i = 0
        while (i < stats.size) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatPair(stats[i], Modifier.weight(1f))
                if (i + 1 < stats.size) {
                    StatPair(stats[i + 1], Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            i += 2
        }
    }
}

@Composable
private fun StatPair(entry: StatEntryUi, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
    ) {
        Text(entry.label, fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = KeresColors.Text)
        Text(entry.value, fontFamily = RobotoMonoFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = KeresColors.Gold)
    }
}

@Composable
private fun ModalBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(KeresColors.Scrim)
            .clickable(enabled = true, onClick = {})
            .padding(10.dp)
    ) {
        content()
    }
}

@Composable
private fun ModalHeader(
    title: String,
    onClose: () -> Unit,
    closeTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            fontFamily = RobotoMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            color = KeresColors.Amber,
            modifier = Modifier.weight(1f)
        )
        KeresButton(
            onClick = onClose,
            minHeight = 44.dp,
            contentPaddingHorizontal = 10.dp,
            modifier = Modifier.testTag(closeTag)
        ) {
            Text("✕", fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = KeresColors.Steel)
}
