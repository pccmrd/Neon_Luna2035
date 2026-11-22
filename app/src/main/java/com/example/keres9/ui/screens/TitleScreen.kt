package com.example.keres9.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keres9.sim.GameSession
import com.example.keres9.sim.HudUiState
import com.example.keres9.ui.components.KeresButton
import com.example.keres9.ui.theme.KeresColors
import com.example.keres9.ui.theme.RobotoMonoFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleScreen(
    state: HudUiState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.46f to Color(0x5903060A),
                    1.0f to Color(0xE603060A)
                )
            )
    ) {
        val compactLandscape = maxHeight < 430.dp && maxWidth > maxHeight
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f, fill = true))

            Column(
                modifier = Modifier.padding(bottom = if (compactLandscape) 8.dp else 14.dp)
            ) {
                Text(
                    text = "> EASTERN CABLE CONSORTIUM · SELENE LIFT 1 · NOMINAL",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = KeresColors.Cyan
                )
                Text(
                    text = buildAnnotatedString {
                        append("> KERES-9 ORBITAL CATALOG · ")
                        withStyle(SpanStyle(color = KeresColors.Red)) {
                            append("NO RESPONSE (DAY 212 · COMPUTE PARTITION)")
                        }
                    },
                    fontFamily = RobotoMonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = KeresColors.Cyan
                )
                Text(
                    text = "> AUTONOMOUS HAUL ENGINES: 41 ACTIVE · SYNDICATE CLAIM BEACONS DETECTED",
                    fontFamily = RobotoMonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = KeresColors.Cyan
                )
            }

            Box {
                val titleSize = if (compactLandscape) 42.sp else 54.sp
                Text(
                    text = "KERES-9",
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize,
                    letterSpacing = 6.sp,
                    color = Color(0x5953D9FF),
                    modifier = Modifier.offset(x = 2.dp)
                )
                Text(
                    text = "KERES-9",
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize,
                    letterSpacing = 6.sp,
                    color = Color(0x4DFF4A3D),
                    modifier = Modifier.offset(x = (-2).dp)
                )
                Text(
                    text = "KERES-9",
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize,
                    letterSpacing = 6.sp,
                    color = KeresColors.Gold
                )
            }

            Text(
                text = "CATALOG ZERO · LUNAR ORBIT · 2035",
                fontFamily = RobotoMonoFamily,
                fontSize = 12.sp,
                letterSpacing = 3.5.sp,
                color = KeresColors.Dim,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )

            if (!compactLandscape) {
                Text(
                    text = buildAnnotatedString {
                        append("After the 2029 Compute Accords split the lunar rim between the Eastern Cable Consortium and the Western Syndicate, autonomous haul engines kept dragging rocks into orbit—even after the KERES-9 catalog AI died. ")
                        withStyle(SpanStyle(color = KeresColors.Amber)) {
                            append("Disembark from PSV MERIDIAN, clear Intake at SELENE HIGH, claim your shared bunk on Shackleton Rim, then commute up to cut ore, buy a tug, and fly a lander home.")
                        }
                    },
                    fontFamily = RobotoMonoFamily,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = KeresColors.Text,
                    modifier = Modifier
                        .widthIn(max = 530.dp)
                        .padding(bottom = 18.dp)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.hasSave) {
                    KeresButton(
                        onClick = { GameSession.load() },
                        primaryCtx = true,
                        minHeight = 48.dp,
                        contentPaddingHorizontal = 20.dp,
                        contentPaddingVertical = 12.dp,
                        modifier = Modifier.testTag("continue_career_button")
                    ) {
                        Text(
                            text = "CONTINUE CAREER",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                    }
                }
                KeresButton(
                    onClick = { GameSession.newRun() },
                    primaryCtx = true,
                    minHeight = 48.dp,
                    contentPaddingHorizontal = 20.dp,
                    contentPaddingVertical = 12.dp,
                    modifier = Modifier.testTag("new_career_button")
                ) {
                    Text(
                        text = "NEW CAREER",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                }
                KeresButton(
                    onClick = { GameSession.startDemo() },
                    greenAccent = true,
                    minHeight = 48.dp,
                    contentPaddingHorizontal = 20.dp,
                    contentPaddingVertical = 12.dp,
                    modifier = Modifier.testTag("watch_ai_button")
                ) {
                    Text(
                        text = "WATCH AI PLAY",
                        fontSize = 13.sp,
                        letterSpacing = 1.4.sp
                    )
                }
                KeresButton(
                    onClick = { GameSession.openHelp() },
                    minHeight = 48.dp,
                    contentPaddingHorizontal = 20.dp,
                    contentPaddingVertical = 12.dp,
                    modifier = Modifier.testTag("title_manual_button")
                ) {
                    Text(
                        text = "FIELD MANUAL",
                        fontSize = 13.sp,
                        letterSpacing = 1.4.sp
                    )
                }
                KeresButton(
                    onClick = { GameSession.toggleSound() },
                    minHeight = 48.dp,
                    contentPaddingHorizontal = 20.dp,
                    contentPaddingVertical = 12.dp,
                    modifier = Modifier.testTag("title_sound_button")
                ) {
                    Text(
                        text = if (state.soundOn) "SOUND: ON" else "SOUND: OFF",
                        fontSize = 13.sp,
                        letterSpacing = 1.4.sp
                    )
                }
            }

            if (state.titleNote.isNotEmpty()) {
                Text(
                    text = state.titleNote,
                    fontFamily = RobotoMonoFamily,
                    fontSize = 10.sp,
                    color = if (state.titleNoteError) KeresColors.Red else KeresColors.Dim,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Text(
                text = "TOUCH: left stick moves/RCS (screen-relative) · right stick turns · right rail is throttle · tap to target\n" +
                    "GAMEPAD/KB: WASD move/RCS · Q/E turn · SHIFT/CTRL throttle · M/SPACE cut/mine · F use · V alt · L light · X match · Z brake · T target · G gear · C array · P AI · ESC pause",
                fontFamily = RobotoMonoFamily,
                fontSize = 9.sp,
                lineHeight = 14.sp,
                color = KeresColors.Dim,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
