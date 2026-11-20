package com.example.keres9.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keres9.render.GameRenderer
import com.example.keres9.sim.GameSession
import com.example.keres9.sim.MarketGoodUi
import com.example.keres9.sim.StationUiState
import com.example.keres9.ui.components.ChamferPanel
import com.example.keres9.ui.components.KeresButton
import com.example.keres9.ui.theme.KeresColors
import com.example.keres9.ui.theme.RobotoMonoFamily

@Composable
fun StationSheet(
    station: StationUiState,
    aiPickTag: String?,
    modifier: Modifier = Modifier
) {
    BackHandler {
        GameSession.closeStation()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(KeresColors.Scrim)
            .clickable(enabled = true, onClick = {})
            .padding(10.dp)
    ) {
        ChamferPanel(
            backgroundColor = KeresColors.PanelSolid,
            borderColor = KeresColors.Steel2,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.stationName,
                            fontFamily = RobotoMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp,
                            color = KeresColors.Amber
                        )
                        Text(
                            text = station.subtitle,
                            fontFamily = RobotoMonoFamily,
                            fontSize = 9.sp,
                            color = KeresColors.Dim
                        )
                    }
                    if (station.canUndock) {
                        KeresButton(
                            onClick = { GameSession.undockFromStation() },
                            minHeight = 44.dp,
                            contentPaddingHorizontal = 10.dp,
                            modifier = Modifier.testTag("station_undock_button")
                        ) {
                            Text("UNDOCK", fontSize = 9.sp)
                        }
                    }
                    KeresButton(
                        onClick = { GameSession.closeStation() },
                        aiHighlight = aiPickTag == "station_close",
                        minHeight = 44.dp,
                        contentPaddingHorizontal = 10.dp,
                        modifier = Modifier.testTag("station_close_button")
                    ) {
                        Text("✕", fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = KeresColors.Steel)

                // Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    for (t in station.tabs) {
                        val active = t == station.activeTab
                        val tag = "tab_$t"
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 72.dp, minHeight = 44.dp)
                                .background(if (active) KeresColors.PanelTabActive else KeresColors.PanelTab)
                                .border(
                                    width = if (aiPickTag == tag) 2.dp else 1.dp,
                                    color = if (aiPickTag == tag) KeresColors.Green else KeresColors.Steel2
                                )
                                .drawBehind {
                                    if (active) {
                                        drawRect(
                                            color = KeresColors.Cyan,
                                            topLeft = Offset(0f, size.height - 2.dp.toPx()),
                                            size = Size(size.width, 2.dp.toPx())
                                        )
                                    }
                                }
                                .testTag(tag)
                                .clickable { GameSession.selectTab(t) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = GameSession.tabLabel(t),
                                fontFamily = RobotoMonoFamily,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                color = if (active) KeresColors.Cyan else KeresColors.Amber
                            )
                        }
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    when (station.activeTab) {
                        "market" -> MarketTabContent(station, aiPickTag)
                        "service" -> ServicesTabContent(station, aiPickTag)
                        "crew" -> CrewTabContent(station, aiPickTag)
                        "yard" -> YardTabContent(station, aiPickTag)
                        "catalog" -> CatalogTabContent(station, aiPickTag)
                        "jobs" -> JobsTabContent(station, aiPickTag)
                        "home" -> HomeTabContent(station)
                        else -> BarTabContent(station)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketTabContent(station: StationUiState, aiPickTag: String?) {
    if (station.holdSummary == null) {
        InfoRow("NO HOLD", "You need a suit sack or a docked ship to trade.")
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(station.holdSummary, fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = KeresColors.Text)
            Text(station.holdSub, fontFamily = RobotoMonoFamily, fontSize = 9.sp, color = KeresColors.Dim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (q in listOf("1", "5", "10", "MAX")) {
                val active = q == station.qtyMode
                val tag = "qty_$q"
                KeresButton(
                    onClick = { GameSession.panelAct("qty", extra = q) },
                    active = active,
                    aiHighlight = aiPickTag == tag,
                    minHeight = 40.dp,
                    contentPaddingHorizontal = 8.dp,
                    contentPaddingVertical = 4.dp,
                    modifier = Modifier.testTag(tag)
                ) {
                    Text(q, fontSize = 9.sp)
                }
            }
        }
    }
    HorizontalDivider(color = KeresColors.Divider)

    for (g in station.marketGoods) {
        MarketGoodRow(g, aiPickTag)
        HorizontalDivider(color = KeresColors.Divider)
    }
}

@Composable
private fun MarketGoodRow(g: MarketGoodUi, aiPickTag: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(g.name, fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = KeresColors.Text)
            Text(
                text = "have ${g.have} · stock ${g.stock}${g.role} · sells ${g.sellPrice}₡",
                fontFamily = RobotoMonoFamily,
                fontSize = 8.sp,
                color = KeresColors.Dim
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .height(4.dp)
                    .background(KeresColors.TrackBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(g.stockRatio)
                        .background(KeresColors.Cyan)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${g.buyPrice}₡ ", fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = KeresColors.Gold)
            val arrow = when {
                g.buyPrice > g.prevPrice -> "▲" to KeresColors.Green
                g.buyPrice < g.prevPrice -> "▼" to KeresColors.Red
                else -> "·" to KeresColors.Dim
            }
            Text(arrow.first, fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = arrow.second)
        }
        Canvas(modifier = Modifier.size(48.dp, 18.dp)) {
            drawIntoCanvas { canvas ->
                GameRenderer.drawSparkline(canvas.nativeCanvas, size.width, size.height, g.history, g.color)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeresButton(
                onClick = { GameSession.panelAct("buy", good = g.key) },
                minHeight = 44.dp,
                contentPaddingHorizontal = 8.dp,
                contentPaddingVertical = 4.dp,
                modifier = Modifier.testTag("buy_${g.key}")
            ) {
                Text("BUY", fontSize = 9.sp)
            }
            val sellTag = "sell_${g.key}"
            KeresButton(
                onClick = { GameSession.panelAct("sell", good = g.key) },
                enabled = g.have > 0,
                aiHighlight = aiPickTag == sellTag,
                minHeight = 44.dp,
                contentPaddingHorizontal = 8.dp,
                contentPaddingVertical = 4.dp,
                modifier = Modifier.testTag(sellTag)
            ) {
                Text("SELL", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ServicesTabContent(station: StationUiState, aiPickTag: String?) {
    for (s in station.servicesList) {
        val tag = "svc_${s.index}"
        ServiceActionRow(
            title = s.label,
            desc = s.desc,
            buttonLabel = s.costText,
            enabled = s.enabled,
            aiHighlight = aiPickTag == tag,
            tag = tag,
            onClick = { GameSession.panelAct("svc", extra = s.index.toString()) }
        )
    }
    if (station.showEvaButton) {
        ServiceActionRow(
            title = "STEP OUT",
            desc = "Leave ${station.evaShipName} on EVA — walk the decks, ride the lift, visit the crew office.",
            buttonLabel = "EVA",
            enabled = true,
            aiHighlight = aiPickTag == "eva_btn",
            tag = "eva_btn",
            onClick = { GameSession.panelAct("eva") }
        )
    }
    if (station.shipStatusLine != null) {
        InfoRow("VESSEL STATUS", station.shipStatusLine)
    }
}

@Composable
private fun CrewTabContent(station: StationUiState, aiPickTag: String?) {
    NpcPortraitCard(
        seed = 991 * 77 + 13,
        fac = "crew",
        name = "Foreman Lu Mei",
        role = "CREW OFFICE · SELENE HIGH",
        quote = "\"Catalog's dead, so nobody knows what's in half these rocks. That's good for us. Suit up, ride the MULE, cut what you can carry. Base pay 140₡ a shift plus 60% of what's in your sack. Bring it back to the shuttle before the horn.\""
    )
    ServiceActionRow(
        title = "MULE-3",
        desc = station.crewLineText,
        buttonLabel = "BOARD MULE-3",
        enabled = station.crewCanBoard,
        aiHighlight = aiPickTag == "shift_btn",
        tag = "shift_btn",
        onClick = { GameSession.panelAct("shift") }
    )
    InfoRow("THE CREW", station.crewMatesText)
    if (station.crewInShipNote) {
        InfoRow("ON A SHIP?", "Crew work is EVA only. Step out [EVA] inside the station to sign on.")
    }
    if (station.crewTier2Note) {
        InfoRow("YOUR OWN HULL", "Lu: \"Still welcome on the MULE when the tug is in the shop.\"")
    }
}

@Composable
private fun YardTabContent(station: StationUiState, aiPickTag: String?) {
    for (y in station.yardRows) {
        val tag = "buyship_${y.cls}"
        ServiceActionRow(
            title = y.title,
            desc = y.desc,
            buttonLabel = if (y.owned) "OWNED" else "BUY",
            enabled = y.enabled,
            aiHighlight = aiPickTag == tag,
            tag = tag,
            onClick = { GameSession.panelAct("buyship", extra = y.cls) }
        )
    }
    InfoRow("HOW SHIPS WORK", station.yardHelp)
}

@Composable
private fun CatalogTabContent(station: StationUiState, aiPickTag: String?) {
    ServiceActionRow(
        title = "SURVEY BOUNTIES",
        desc = station.bountyText,
        buttonLabel = "COLLECT",
        enabled = station.bountyEnabled,
        aiHighlight = aiPickTag == "bounty_btn",
        tag = "bounty_btn",
        onClick = { GameSession.panelAct("bounty") }
    )
    ServiceActionRow(
        title = "KERES-9 CORE SHARDS",
        desc = station.coresText,
        buttonLabel = "HAND IN ${station.coresHave}",
        enabled = station.coresHave > 0,
        aiHighlight = aiPickTag == "cores_btn",
        tag = "cores_btn",
        onClick = { GameSession.panelAct("cores") }
    )
    if (station.showFileClaim) {
        ServiceActionRow(
            title = "FILE THE ORPHAN CLAIM",
            desc = "Your survey, your cut, your provenance. The KERES-9 logs prove HELION staked it illegally.",
            buttonLabel = "FILE CLAIM",
            enabled = true,
            aiHighlight = aiPickTag == "file_btn",
            tag = "file_btn",
            onClick = { GameSession.panelAct("file") }
        )
    }
    for (r in station.catalogRocks) {
        InfoRow(r.name, r.desc)
    }
}

@Composable
private fun JobsTabContent(station: StationUiState, aiPickTag: String?) {
    InfoRow(
        "CONTRACT BOARD",
        "active ${station.activeJobsCount}/3 · payment on delivery (sell the goods at the destination)"
    )
    for (j in station.activeJobs) {
        JobCard(
            title = j.title,
            desc = j.desc,
            rewardText = j.rewardText,
            active = true,
            buttonLabel = "DROP",
            aiHighlight = false,
            tag = "drop_${j.id}",
            onClick = { GameSession.panelAct("drop", extra = j.id) }
        )
    }
    for (j in station.availableJobs) {
        val tag = "accept_${j.id}"
        JobCard(
            title = j.title,
            desc = j.desc,
            rewardText = j.rewardText,
            active = false,
            buttonLabel = "SIGN",
            aiHighlight = aiPickTag == tag,
            tag = tag,
            onClick = { GameSession.panelAct("accept", extra = j.id) }
        )
    }
}

@Composable
private fun HomeTabContent(station: StationUiState) {
    ServiceActionRow(
        title = station.homeBunkTitle,
        desc = station.homeBunkDesc,
        buttonLabel = "SLEEP & SAVE",
        enabled = true,
        aiHighlight = false,
        tag = "sleep_btn",
        onClick = { GameSession.panelAct("sleep") }
    )
    InfoRow("CAREER", station.homeCareerDesc)
    InfoRow("STORY", station.homeStoryDesc)
    if (station.homeSackNote) {
        InfoRow("SACK", "You have ore in your sack. Sell it at a market — the Depot pays well for ice.")
    }
}

@Composable
private fun BarTabContent(station: StationUiState) {
    InfoRow(station.barTitle, "${station.barSouls} souls aboard")
    NpcPortraitCard(
        seed = station.barSeed * 77 + 13,
        fac = station.barFac,
        name = "Bartender",
        role = station.stationName,
        quote = "\"${station.barRumor}\""
    )
    for (a in station.barAgents) {
        NpcPortraitCard(
            seed = a.id * 77 + 13,
            fac = a.fac,
            name = a.name,
            role = a.subtitle,
            quote = a.line
        )
    }
    InfoRow("BAND FEED", "live record")
    for (f in station.feedRows) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp)) {
            Text(
                text = f.msg,
                fontFamily = RobotoMonoFamily,
                fontSize = 10.sp,
                color = Color(f.color)
            )
            Text(
                text = "T+${f.timeSec}s",
                fontFamily = RobotoMonoFamily,
                fontSize = 8.sp,
                color = KeresColors.Dim
            )
        }
        HorizontalDivider(color = KeresColors.Divider)
    }
}

@Composable
private fun ServiceActionRow(
    title: String,
    desc: String,
    buttonLabel: String,
    enabled: Boolean,
    aiHighlight: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = KeresColors.Text)
            Text(desc, fontFamily = RobotoMonoFamily, fontSize = 9.sp, lineHeight = 13.sp, color = KeresColors.Dim)
        }
        KeresButton(
            onClick = onClick,
            enabled = enabled,
            aiHighlight = aiHighlight,
            minHeight = 44.dp,
            contentPaddingHorizontal = 10.dp,
            modifier = Modifier.testTag(tag)
        ) {
            Text(buttonLabel, fontSize = 10.sp)
        }
    }
    HorizontalDivider(color = KeresColors.Divider)
}

@Composable
private fun InfoRow(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(title, fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = KeresColors.Text)
        Text(desc, fontFamily = RobotoMonoFamily, fontSize = 9.sp, lineHeight = 13.sp, color = KeresColors.Dim)
    }
    HorizontalDivider(color = KeresColors.Divider)
}

@Composable
private fun JobCard(
    title: String,
    desc: String,
    rewardText: String,
    active: Boolean,
    buttonLabel: String,
    aiHighlight: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, KeresColors.Steel)
            .drawBehind {
                drawRect(
                    color = if (active) KeresColors.Cyan else KeresColors.Amber,
                    topLeft = Offset.Zero,
                    size = Size(2.dp.toPx(), size.height)
                )
            }
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
    ) {
        Text(title, fontFamily = RobotoMonoFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = KeresColors.Amber)
        Text(desc, fontFamily = RobotoMonoFamily, fontSize = 10.sp, lineHeight = 15.sp, color = KeresColors.Dim, modifier = Modifier.padding(vertical = 4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(rewardText, fontFamily = RobotoMonoFamily, fontSize = 11.sp, color = KeresColors.Gold)
            KeresButton(
                onClick = onClick,
                aiHighlight = aiHighlight,
                minHeight = 44.dp,
                contentPaddingHorizontal = 12.dp,
                modifier = Modifier.testTag(tag)
            ) {
                Text(buttonLabel, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun NpcPortraitCard(
    seed: Int,
    fac: String,
    name: String,
    role: String,
    quote: String
) {
    val bmp = remember(seed, fac) { GameRenderer.renderFaceBitmap(seed, fac).asImageBitmap() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Image(
            bitmap = bmp,
            contentDescription = name,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, KeresColors.Steel)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontFamily = RobotoMonoFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = KeresColors.Cyan)
            Text(role, fontFamily = RobotoMonoFamily, fontSize = 9.sp, color = KeresColors.Dim)
            Text(quote, fontFamily = RobotoMonoFamily, fontSize = 9.sp, lineHeight = 14.sp, color = KeresColors.Dim)
        }
    }
    HorizontalDivider(color = KeresColors.Divider)
}
