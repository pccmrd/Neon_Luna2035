package com.example.keres9.sim

import android.content.Context
import com.example.keres9.ai.AP
import com.example.keres9.ai.AiUiBridge
import com.example.keres9.ai.NAV
import com.example.keres9.ai.apAbort
import com.example.keres9.ai.apMeta
import com.example.keres9.ai.apSet
import com.example.keres9.ai.apThink
import com.example.keres9.ai.inp
import com.example.keres9.audio.AU
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.ControlFrame
import com.example.keres9.core.DockGates
import com.example.keres9.core.FC
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.Job
import com.example.keres9.core.NavTarget
import com.example.keres9.core.PAL
import com.example.keres9.core.Ship
import com.example.keres9.core.Targetable
import com.example.keres9.core.Vec2
import com.example.keres9.core.cl
import com.example.keres9.core.fm
import com.example.keres9.core.hy
import com.example.keres9.core.lp
import com.example.keres9.data.SaveRepository
import com.example.keres9.render.GameRenderer
import com.example.keres9.world.SITES
import com.example.keres9.world.altAt
import com.example.keres9.world.cut
import com.example.keres9.world.cw
import com.example.keres9.world.genWorld
import com.example.keres9.world.replaceTransfer
import com.example.keres9.world.spawnRock
import com.example.keres9.world.stepBodies
import com.example.keres9.world.stepBodies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class ToastUiItem(
    val id: Long,
    val msg: String,
    val kind: String?,
    val count: Int = 1,
    val age: Double = 0.0,
    val fading: Boolean = false
)

data class DialogChoiceUi(
    val label: String,
    val onSelect: () -> Unit
)

data class DialogUiState(
    val name: String,
    val role: String,
    val text: String,
    val seed: Int,
    val fac: String,
    val choices: List<DialogChoiceUi>
)

data class TargetReadoutUi(
    val name: String,
    val kind: String,
    val dist: String,
    val relSpeed: String,
    val closeRate: String
)

data class DockUiState(
    val title: String,
    val spdOk: Boolean,
    val angOk: Boolean,
    val latOk: Boolean,
    val rotOk: Boolean,
    val hint: String
)

data class MarketGoodUi(
    val key: String,
    val name: String,
    val have: Int,
    val stock: Int,
    val stockRatio: Float,
    val role: String,
    val sellPrice: Int,
    val buyPrice: Int,
    val prevPrice: Int,
    val color: Int,
    val history: List<Int>
)

data class ServiceRowUi(
    val index: Int,
    val label: String,
    val desc: String,
    val costText: String,
    val enabled: Boolean
)

data class YardRowUi(
    val cls: String,
    val title: String,
    val desc: String,
    val owned: Boolean,
    val enabled: Boolean
)

data class CatalogRockUi(
    val name: String,
    val desc: String
)

data class JobRowUi(
    val id: String,
    val title: String,
    val desc: String,
    val rewardText: String,
    val active: Boolean
)

data class FeedRowUi(
    val msg: String,
    val timeSec: Int,
    val color: Int
)

data class BarAgentUi(
    val id: Int,
    val name: String,
    val subtitle: String,
    val line: String,
    val fac: String
)

data class StationUiState(
    val stationName: String,
    val subtitle: String,
    val canUndock: Boolean,
    val tabs: List<String>,
    val activeTab: String,
    val qtyMode: String,
    val holdSummary: String?,
    val holdSub: String,
    val marketGoods: List<MarketGoodUi> = emptyList(),
    val servicesList: List<ServiceRowUi> = emptyList(),
    val showEvaButton: Boolean = false,
    val evaShipName: String = "",
    val shipStatusLine: String? = null,
    val crewLineText: String = "",
    val crewShifts: Int = 0,
    val crewCanBoard: Boolean = false,
    val crewMatesText: String = "",
    val crewInShipNote: Boolean = false,
    val crewTier2Note: Boolean = false,
    val yardRows: List<YardRowUi> = emptyList(),
    val yardHelp: String = "",
    val bountyText: String = "",
    val bountyEnabled: Boolean = false,
    val coresText: String = "",
    val coresHave: Int = 0,
    val showFileClaim: Boolean = false,
    val catalogRocks: List<CatalogRockUi> = emptyList(),
    val activeJobsCount: Int = 0,
    val activeJobs: List<JobRowUi> = emptyList(),
    val availableJobs: List<JobRowUi> = emptyList(),
    val homeBunkTitle: String = "",
    val homeBunkDesc: String = "",
    val homeCareerDesc: String = "",
    val homeStoryDesc: String = "",
    val homeSackNote: Boolean = false,
    val barTitle: String = "",
    val barSouls: Int = 0,
    val barSeed: Int = 1,
    val barFac: String = "selene",
    val barRumor: String = "",
    val barAgents: List<BarAgentUi> = emptyList(),
    val feedRows: List<FeedRowUi> = emptyList()
)

data class StatEntryUi(val label: String, val value: String)

data class ControlsUiState(
    val aiOn: Boolean = false,
    val aiLx: Double = 0.0,
    val aiLy: Double = 0.0,
    val aiRx: Double = 0.0,
    val aiThr: Double = 0.0,
    val aiMine: Boolean = false,
    val humanThr: Double = 0.0,
    val matchAct: Boolean = false,
    val gearAct: Boolean = false,
    val gearEnabled: Boolean = false,
    val scanAct: Boolean = false,
    val brakeAct: Boolean = false,
    val ctxLabel: String = "—",
    val ctxReady: Boolean = false,
    val ctxEnabled: Boolean = false,
    val altLabel: String? = null,
    val aiPickTag: String? = null
)

data class HudUiState(
    val mode: String = "title",
    val hasSave: Boolean = false,
    val titleNote: String = "",
    val titleNoteError: Boolean = false,
    val soundOn: Boolean = true,
    val creditsText: String = "₡ 240",
    val badgeText: String = "TIER 1",
    val badgeIsBounty: Boolean = false,
    val fuelLabel: String = "FUEL",
    val fuelRatio: Float = 1f,
    val hullLabel: String = "HULL",
    val hullRatio: Float = 1f,
    val cargoLabel: String = "CRG",
    val cargoRatio: Float = 0f,
    val modeBadge: String = "—",
    val clockText: String = "DAY 01 · 06:00",
    val aiOn: Boolean = false,
    val aiWarp: Int = 1,
    val objText: String = "—",
    val objReward: String = "",
    val toasts: List<ToastUiItem> = emptyList(),
    val evBannerText: String? = null,
    val evBannerInfo: Boolean = false,
    val target: TargetReadoutUi? = null,
    val apAct: String = "—",
    val apCap: String = "",
    val apSub: String = "",
    val apTime: String = "",
    val dock: DockUiState? = null,
    val scanOpen: Boolean = false,
    val scanFreq: Float = 110f,
    val scanLock: Float = 0f,
    val scanLabel: String = "sweep the cone across rock · find a resonance · hold the lock",
    val helpOpen: Boolean = false,
    val mapOpen: Boolean = false,
    val confirmText: String? = null,
    val deathWhy: String = "Signal lost.",
    val endStats: List<StatEntryUi> = emptyList(),
    val dialog: DialogUiState? = null,
    val station: StationUiState? = null
)

object GameSession {
    private val _uiState = MutableStateFlow(HudUiState())
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    private val _controlsState = MutableStateFlow(ControlsUiState())
    val controlsState: StateFlow<ControlsUiState> = _controlsState.asStateFlow()

    private val _renderTick = MutableStateFlow(0L)
    val renderTick: StateFlow<Long> = _renderTick.asStateFlow()

    private val toastsList = mutableListOf<ToastUiItem>()
    private var nextToastId = 1L

    private var evBannerText: String? = null
    private var evBannerInfo: Boolean = false
    private var evBannerTimer: Double = 0.0

    private var helpOpen = false
    private var mapOpen = false
    private var confirmText: String? = null
    private var confirmAction: (() -> Unit)? = null
    private var deathWhy = "Signal lost."
    private var dialogState: DialogUiState? = null
    private var endStatsSnapshot: List<StatEntryUi> = emptyList()

    private var appInBackground = false
    private var booted = false
    private var acc = 0.0
    private var frame = 0L
    private var grab = 0.0
    private var wasSurf = false
    private var winDelayTimer = -1.0
    private var panelRefreshCounter = 0

    private val nanWarnTime = HashMap<String, Double>()
    private val TAB_NAMES = mapOf(
        "market" to "MARKET",
        "service" to "SERVICES",
        "crew" to "CREW OFFICE",
        "yard" to "SHIPYARD",
        "catalog" to "CATALOG",
        "bar" to "BAR",
        "home" to "HOME",
        "jobs" to "CONTRACTS"
    )

    fun boot(context: Context) {
        if (booted) return
        booted = true
        SaveRepository.init(context)
        AU.init()
        GameRenderer.clearCaches()
        GS.G = newGame()
        bindHooksAndAiBridge()
        syncSound()

        try {
            val saved = readSave()
            val seed = saved?.optInt("seed", Random.nextInt(1_000_000_000)) ?: Random.nextInt(1_000_000_000)
            genWorld(seed)
            liftInit()
            crewInit()
            initAgents()
            eventsInit()
            GS.W?.stations?.forEach { mkJobs(it) }

            val note = if (saved != null) {
                val t = saved.optDouble("time", 0.0)
                val gObj = saved.optJSONObject("g")
                val cr = gObj?.optInt("cr", 240) ?: 240
                val tier = gObj?.optInt("tier", 1) ?: 1
                "SAVED CAREER · day ${floor(t / 1200.0).toInt() + 1} · ${fm(cr)}₡ · tier $tier"
            } else {
                "No saved career. The band is full of rocks nobody has named."
            }

            GS.W?.lift?.E?.let { e ->
                GS.cam.x = e.g.x + 160.0
                GS.cam.y = e.g.y
            }
            titleCam()
            GS.cam.z = GS.cam.tz
            mode("title")
            _uiState.value = _uiState.value.copy(
                hasSave = saved != null,
                titleNote = note,
                titleNoteError = false
            )
            pushStates()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                titleNote = "BOOT ERROR: ${e.message ?: "unknown"}",
                titleNoteError = true
            )
        }
    }

    private fun bindHooksAndAiBridge() {
        Hooks.toast = { m, k -> toast(m, k) }
        Hooks.banner = { m, k ->
            evBannerText = m.ifBlank { null }
            evBannerInfo = k == "info"
            evBannerTimer = if (evBannerText != null) 7.0 else 0.0
            pushStates()
        }
        Hooks.closeStation = { closeStation() }
        Hooks.openStation = { b, t -> openStation(b, t) }
        Hooks.onDock = { b, ty ->
            GS.W?.let { it.stats.docked++ }
            if (b.surf && ty == "pad") {
                GS.W?.let { it.stats.landings++ }
                toast(
                    "TOUCHDOWN · ${b.name} — ${if (b.tabs.contains("market")) "F for services" else "welcome home"}",
                    "good"
                )
            } else {
                toast("${if (ty == "pad") "ON PAD · " else "CLAMPED · "}${b.name} — F for services", "good")
            }
            feed("You ${if (ty == "pad") "set down on " else "clamped at "}${b.name}.")
        }
        Hooks.onTouch = { s ->
            GS.W?.let { it.stats.landings++ }
            toast("LANDED ON REGOLITH · ${(hy(s.x, s.y) - C.RM).roundToInt()} m datum — F to step out", "good")
        }
        Hooks.onCatalog = { b, src ->
            if (b.cat == 0) {
                val W = GS.W
                val G = GS.G
                if (W != null && G != null) {
                    b.cat = 1
                    b.catT = W.time
                    b.survey = 1.0
                    val v = (bountyFor(b) * (if (b.fresh) 1.5 else 1.0)).roundToInt()
                    G.bounty += v
                    W.stats.catalogued++
                    val top = b.comp.entries
                        .filter { it.key != "rock" }
                        .sortedByDescending { it.value }
                        .take(2)
                        .joinToString(" + ") { GD[it.key]?.n ?: it.key }
                    toast(
                        "CATALOGUED · ${b.name} · ${b.typeN}${if (top.isNotEmpty()) " · $top" else ""} · bounty ${v}₡ pending",
                        "good"
                    )
                    feed(
                        "${if (src == "suit") "Your hand scanner" else "Your array"} catalogued ${b.name} (${b.typeN}).",
                        "good"
                    )
                    AU.chime()
                    story("catalog")
                    jobCatalog(b)
                    checkStory()
                }
            }
        }
        Hooks.delivered = { st, g, k ->
            story("sold", st = st, g = g, k = k)
            jobSold(st, g, k)
            checkStory()
        }
        Hooks.resolved = { c ->
            story("resolved", contact = c)
            checkStory()
        }
        Hooks.shiftDone = { n, _ ->
            story("shift", shiftUnits = n)
            checkStory()
        }
        Hooks.bought = { _ -> checkStory() }
        Hooks.liftArrive = { top ->
            story("lift", liftTop = top)
            toast(
                if (top) "SELENE HIGH · lift dock. Crew office: upper left. Exchange, catalog, bar, shipyard along the top deck."
                else "SHACKLETON RIM · ground terminal. HAB 14 west, Depot east.",
                "good"
            )
        }
        Hooks.boardCrew = { _ -> crewBoard() }
        Hooks.shipLost = { s ->
            GS.G?.fleet?.remove(s)
            feed("Your ${s.name} was destroyed.", "bad")
            toast("${s.name} LOST", "bad")
            if (s === GS.pl) playerDied("Your ${s.name} broke up.")
        }
        Hooks.suitOut = {
            playerDied(
                if (GS.W?.flare?.on == true) "Proton storm. Your suit dosimeter maxed out."
                else "Suit air ran dry."
            )
        }
        Hooks.win = {
            winDelayTimer = 1.8
        }
        Hooks.armor = { if (AP.on && AP.demo) 0.5 else 1.0 }
        Hooks.onSave = { save() }

        AiUiBridge.stationOpenFn = { GS.G?.panel != null }
        AiUiBridge.frozenFn = { frozen() }
        AiUiBridge.dialogOpenFn = { dialogState != null }
        AiUiBridge.appInBackgroundFn = { appInBackground }
        AiUiBridge.closeDialogFn = { closeDialog() }
        AiUiBridge.selectTabFn = { t -> selectTab(t) }
        AiUiBridge.closeStationFn = { closeStation() }
        AiUiBridge.panelActFn = { a, g, ex -> panelAct(a, g, ex) }
        AiUiBridge.clickButtonFn = { tag ->
            when (tag) {
                "respawn_button" -> respawn()
                "keep_button" -> keepFlyingAfterWin()
                "tow_button" -> tow()
            }
        }
        AiUiBridge.modeFn = { m -> mode(m) }
        AiUiBridge.freqChangedFn = { f ->
            GS.PC.freq = cl(f, 20.0, 200.0)
            pushStates()
        }
        AiUiBridge.onAiStateChanged = { pushStates() }
    }

    fun frozen(): Boolean =
        (GS.G?.mode in setOf("pause", "dead", "win")) ||
            helpOpen || mapOpen || confirmText != null || dialogState != null

    fun build(seed: Int) {
        GameRenderer.clearCaches()
        genWorld(seed)
        GS.AG.clear()
        GS.ev = null
        GS.pl = null
        AU.air(false)
        AU.mine(false)
        liftInit()
        GS.PC.apply {
            tgt = null
            scan = false
            match = false
            assist = null
            hints.clear()
            lock = 0.0
            lockC = null
            sas = 0
            thrArm = false
            ctx = com.example.keres9.core.ContextAction()
            alt = null
            scanLabel = "sweep the cone across rock · find a resonance · hold the lock"
        }
        toastsList.clear()
        pendingToasts.clear()
        evBannerText = null
        evBannerTimer = 0.0
        winDelayTimer = -1.0
        closeStation()
        closeDialog()
        helpOpen = false
        mapOpen = false
        confirmText = null
        confirmAction = null
        IN.thr = 0.0
        IN.lx = 0.0
        IN.ly = 0.0
        IN.rx = 0.0
        IN.ry = 0.0
        IN.mineHold = false
        IN.brakeHold = false
        IN.edge.clear()
        IN.taps.clear()
        GS.cam.aux = null
    }

    private fun wakeAtHome() {
        val h = SITES.hab
        val p = airlockSpot(h, h.x, h.y) ?: Vec2(h.x, h.y) ?: Vec2(h.x, h.y)
        val suit = mkSuit(p.x, p.y)
        suit.a = atan2(h.y, h.x) + PI / 2.0
        GS.ev = suit
        GS.cam.x = suit.x
        GS.cam.y = suit.y
        GS.cam.rot = atan2(suit.y, suit.x) + PI / 2.0
        GS.cam.snap = true
    }

    private fun freshWorld(seed: Int) {
        build(seed)
        val s = GS.G?.sound ?: true
        val g = newGame()
        g.sound = s
        GS.G = g
        syncSound()
        crewInit()
        initAgents()
        eventsInit()
        GS.W?.stations?.forEach { mkJobs(it) }
        wakeAtHome()
        wasSurf = false
    }

    private fun bkey(b: Body): String = if (b.kind == "rock") "r${b.uid}" else "s${b.name}"

    private fun shipSaveJson(s: Ship): JSONObject {
        val obj = JSONObject()
        obj.put("cls", s.cls)
        obj.put("x", s.x)
        obj.put("y", s.y)
        obj.put("a", s.a)
        obj.put("vx", s.vx)
        obj.put("vy", s.vy)
        obj.put("fuel", s.fuel)
        obj.put("gear", s.gear)
        obj.put("isPl", s.isPl)
        obj.put("cargo", JSONObject().apply { s.cargo.forEach { (k, v) -> put(k, v) } })
        obj.put("upg", JSONObject().apply { s.upg.forEach { (k, v) -> put(k, v) } })
        val modsArr = JSONArray()
        for (m in s.mods) {
            val pair = JSONArray()
            pair.put(m.hp)
            pair.put(if (m.dead) 1 else 0)
            modsArr.put(pair)
        }
        obj.put("mods", modsArr)
        val d = s.dock
        if (d != null) {
            val dobj = JSONObject()
            dobj.put("ty", d.ty)
            dobj.put("b", d.b?.let { bkey(it) })
            dobj.put("cx", d.cx)
            dobj.put("cy", d.cy)
            dobj.put("x", d.x)
            dobj.put("y", d.y)
            dobj.put("a", d.a)
            val pi = when {
                d.ty == "pad" && d.pPad != null && d.b != null -> d.b.pads.indexOf(d.pPad)
                d.pPort != null && d.b != null -> d.b.ports.indexOf(d.pPort)
                else -> -1
            }
            dobj.put("pi", pi)
            obj.put("d", dobj)
        }
        return obj
    }

    fun save() {
        val W = GS.W ?: return
        val G = GS.G ?: return
        if (AP.demo || G.mode == "title" || G.mode == "dead") return
        try {
            val root = JSONObject()
            root.put("v", 1)
            root.put("seed", W.seed)
            root.put("time", W.time)
            root.put("sound", G.sound)
            W.nav?.let { nav ->
                root.put("nav", JSONObject().apply {
                    put("x", nav.x)
                    put("y", nav.y)
                    put("l", nav.l)
                })
            }

            val terr = JSONObject()
            var count = 0
            for (b in W.bodies) {
                if (b.cut.isEmpty() || b.kind == "station") continue
                val unique = LinkedHashSet<Int>(b.cut)
                val room = 12000 - count
                if (room <= 0) break
                val arr = JSONArray()
                var added = 0
                for (idx in unique) {
                    if (added >= room) break
                    arr.put(idx)
                    added++
                }
                count += added
                terr.put(bkey(b), arr)
            }
            root.put("terr", terr)

            val extraArr = JSONArray()
            for (ex in W.extra) {
                extraArr.put(JSONObject().apply {
                    put("seed", ex.seed)
                    put("x", ex.x)
                    put("y", ex.y)
                    put("R", ex.R)
                    put("type", ex.type)
                })
            }
            root.put("extra", extraArr)

            val gObj = JSONObject()
            gObj.put("cr", G.cr)
            gObj.put("tier", G.tier)
            gObj.put("story", G.story)
            gObj.put("prog", JSONObject().apply { G.prog.forEach { (k, v) -> put(k, v) } })
            val jobsObj = JSONObject()
            G.jobs.forEach { (stName, list) ->
                val ja = JSONArray()
                for (j in list) ja.put(jobToJson(j))
                jobsObj.put(stName, ja)
            }
            gObj.put("jobs", jobsObj)
            val missionsArr = JSONArray()
            for (j in G.missions) missionsArr.put(jobToJson(j))
            gObj.put("missions", missionsArr)
            gObj.put("bounty", G.bounty)
            gObj.put("win", G.win)
            gObj.put("jseed", G.jseed)
            gObj.put("airUp", G.airUp)
            gObj.put("sackUp", G.sackUp)
            gObj.put("suitUp", G.suitUp)
            gObj.put("cores", G.cores)
            gObj.put("shifts", G.crew?.shifts ?: 0)
            root.put("g", gObj)

            val stObj = JSONObject().apply {
                put("mined", W.stats.mined)
                put("earned", W.stats.earned)
                put("found", W.stats.found)
                put("docked", W.stats.docked)
                put("catalogued", W.stats.catalogued)
                put("shifts", W.stats.shifts)
                put("delivered", W.stats.delivered)
                put("launches", W.stats.launches)
                put("landings", W.stats.landings)
                put("events", W.stats.events)
                put("eva", W.stats.eva)
                put("lifts", W.stats.lifts)
            }
            root.put("stats", stObj)

            val evCur = GS.ev
            if (G.ride == null && evCur != null && !evCur.hide) {
                root.put("ev", JSONObject().apply {
                    put("x", evCur.x)
                    put("y", evCur.y)
                    put("suit", evCur.suit)
                    put("sack", JSONObject().apply { evCur.sack.forEach { (k, v) -> put(k, v) } })
                })
            }

            val fleetArr = JSONArray()
            for (s in G.fleet) {
                if (!s.dead) fleetArr.put(shipSaveJson(s))
            }
            root.put("fleet", fleetArr)

            val econArr = JSONArray()
            for (s in W.stations) {
                val ec = s.econ ?: continue
                val eMap = JSONObject()
                ec.forEach { (g, q) -> eMap.put(g, q.stock) }
                econArr.put(JSONObject().apply {
                    put("n", s.name)
                    put("e", eMap)
                })
            }
            root.put("econ", econArr)

            val catArr = JSONArray()
            for (r in W.rocks) if (r.cat != 0) catArr.put(r.uid)
            root.put("cat", catArr)

            val ctArr = JSONArray()
            W.contacts.forEachIndexed { i, c -> if (c.done) ctArr.put(i) }
            root.put("ct", ctArr)

            val trArr = JSONArray()
            for (k in arrayOf("ares", "gaia")) {
                val site = if (k == "ares") SITES.ares else SITES.gaia
                val tr = site.transfer ?: continue
                trArr.put(JSONObject().apply {
                    put("k", k)
                    put("gen", tr.gen)
                    put("dep", tr.depart - W.time)
                })
            }
            root.put("tr", trArr)

            val feedArr = JSONArray()
            for (f in W.feed.take(20)) {
                feedArr.put(JSONObject().apply {
                    put("t", f.t)
                    put("m", f.m)
                    put("k", f.k)
                })
            }
            root.put("feed", feedArr)

            SaveRepository.saveJson(root.toString())
        } catch (_: Exception) {
        }
    }

    private fun jobToJson(j: Job): JSONObject = JSONObject().apply {
        put("id", j.id)
        put("kind", j.kind)
        put("g", j.g)
        put("n", j.n)
        put("got", j.got)
        put("body", j.body)
        put("to", j.to)
        put("from", j.from)
        put("reward", j.reward)
        put("title", j.title)
        put("desc", j.desc)
    }

    private fun jobFromJson(o: JSONObject): Job = Job(
        id = o.optString("id"),
        kind = o.optString("kind", "deliver"),
        g = o.optString("g", ""),
        n = o.optInt("n", 0),
        got = o.optInt("got", 0),
        body = o.optInt("body", 0),
        to = o.optString("to", ""),
        from = o.optString("from", ""),
        reward = o.optInt("reward", 100),
        title = o.optString("title", ""),
        desc = o.optString("desc", "")
    )

    fun readSave(): JSONObject? = SaveRepository.readSaveJson()

    fun load() {
        val d = readSave()
        if (d == null) {
            newRun()
            return
        }
        try {
            apSet(false, false)
            build(d.getInt("seed"))
            val g = newGame()
            GS.G = g
            g.sound = d.optBoolean("sound", true)
            syncSound()
            crewInit()
            initAgents()
            eventsInit()

            val gObj = d.optJSONObject("g") ?: JSONObject()
            g.cr = gObj.optInt("cr", 240)
            g.tier = gObj.optInt("tier", 1)
            g.story = gObj.optInt("story", 0)
            gObj.optJSONObject("prog")?.let { pr ->
                val keys = pr.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    g.prog[k] = pr.optInt(k, 0)
                }
            }
            gObj.optJSONObject("jobs")?.let { jb ->
                val keys = jb.keys()
                while (keys.hasNext()) {
                    val stName = keys.next()
                    val arr = jb.optJSONArray(stName) ?: continue
                    val list = mutableListOf<Job>()
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { list.add(jobFromJson(it)) }
                    }
                    g.jobs[stName] = list
                }
            }
            gObj.optJSONArray("missions")?.let { ma ->
                for (i in 0 until ma.length()) {
                    ma.optJSONObject(i)?.let { g.missions.add(jobFromJson(it)) }
                }
            }
            g.bounty = gObj.optInt("bounty", 0)
            g.win = gObj.optBoolean("win", false)
            g.jseed = gObj.optInt("jseed", 1)
            g.airUp = gObj.optDouble("airUp", 1.0)
            g.sackUp = gObj.optDouble("sackUp", 1.0)
            g.suitUp = gObj.optDouble("suitUp", 1.0)
            g.cores = gObj.optInt("cores", 0)
            g.crew?.shifts = gObj.optInt("shifts", 0)

            val W = GS.W!!
            d.optJSONObject("stats")?.let { st ->
                W.stats.mined = st.optInt("mined", 0)
                W.stats.earned = st.optInt("earned", 0)
                W.stats.found = st.optInt("found", 0)
                W.stats.docked = st.optInt("docked", 0)
                W.stats.catalogued = st.optInt("catalogued", 0)
                W.stats.shifts = st.optInt("shifts", 0)
                W.stats.delivered = st.optInt("delivered", 0)
                W.stats.launches = st.optInt("launches", 0)
                W.stats.landings = st.optInt("landings", 0)
                W.stats.events = st.optInt("events", 0)
                W.stats.eva = st.optInt("eva", 0)
                W.stats.lifts = st.optInt("lifts", 0)
            }
            W.time = d.optDouble("time", 0.0)
            d.optJSONObject("nav")?.let { nv ->
                W.nav = NavTarget(nv.optDouble("x"), nv.optDouble("y"), nv.optString("l", "WAYPOINT"))
            } ?: run { W.nav = null }

            W.feed.clear()
            d.optJSONArray("feed")?.let { fa ->
                for (i in 0 until fa.length()) {
                    val f = fa.optJSONObject(i) ?: continue
                    W.feed.add(
                        com.example.keres9.core.FeedEntry(
                            t = f.optDouble("t", 0.0),
                            m = f.optString("m", ""),
                            k = f.optString("k", "")
                        )
                    )
                }
            }

            W.extra.clear()
            d.optJSONArray("extra")?.let { ea ->
                for (i in 0 until ea.length()) {
                    val x = ea.optJSONObject(i) ?: continue
                    val b = spawnRock(
                        x.optInt("seed"),
                        x.optDouble("x"),
                        x.optDouble("y"),
                        x.optDouble("R", 8.0),
                        x.optString("type", "c")
                    )
                    b.fresh = false
                }
            }

            d.optJSONArray("tr")?.let { ta ->
                for (i in 0 until ta.length()) {
                    val t = ta.optJSONObject(i) ?: continue
                    val k = t.optString("k")
                    var st = if (k == "ares") SITES.ares else if (k == "gaia") SITES.gaia else continue
                    val gen = t.optInt("gen", 1)
                    if (gen > 1) {
                        val nb = replaceTransfer(st, gen)
                        W.bodies = W.bodies.map { if (it === st) nb else it }.toMutableList()
                        W.stations = W.stations.map { if (it === st) nb else it }.toMutableList()
                        if (k == "ares") SITES.ares = nb else SITES.gaia = nb
                        st = nb
                    }
                    st.transfer?.let { it.depart = W.time + max(60.0, t.optDouble("dep", 300.0)) }
                }
            }

            val byKey = { k: String -> W.bodies.firstOrNull { bkey(it) == k } }
            d.optJSONObject("terr")?.let { terrObj ->
                val keys = terrObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val b = byKey(k) ?: continue
                    val arr = terrObj.optJSONArray(k) ?: continue
                    for (idxPos in 0 until arr.length()) {
                        val ci = arr.optInt(idxPos, -1)
                        if (ci >= 0 && ci < b.sol.size && b.sol[ci] != 0) {
                            cut(b, ci % b.gw, ci / b.gw, 1e6, true)
                        }
                    }
                }
            }

            d.optJSONArray("econ")?.let { ea ->
                for (i in 0 until ea.length()) {
                    val es = ea.optJSONObject(i) ?: continue
                    val st = W.stations.firstOrNull { it.name == es.optString("n") } ?: continue
                    val ec = st.econ ?: continue
                    val eObj = es.optJSONObject("e") ?: continue
                    val keys = eObj.keys()
                    while (keys.hasNext()) {
                        val q = keys.next()
                        ec[q]?.let { it.stock = eObj.optDouble(q, it.stock) }
                    }
                }
            }

            d.optJSONArray("cat")?.let { caArr ->
                for (i in 0 until caArr.length()) {
                    val uid = caArr.optInt(i, -1)
                    val b = W.rocks.firstOrNull { it.uid == uid }
                    if (b != null) {
                        b.cat = 1
                        b.catT = 0.0
                    }
                }
            }

            d.optJSONArray("ct")?.let { ctArr ->
                for (i in 0 until ctArr.length()) {
                    val idx = ctArr.optInt(i, -1)
                    val c = W.contacts.getOrNull(idx) ?: continue
                    c.done = true
                    if (c.kind == "vein") {
                        c.b.veinOn = true
                    } else {
                        for (ch in c.b.cham) {
                            val w = cw(c.b, ch.cx, ch.cy)
                            if (hy(w.x - c.x, w.y - c.y) < c.b.cs * 3.0) ch.disc = true
                        }
                    }
                }
            }

            if (g.story >= 9) W.orphan?.let { it.hidden = false }

            var flying: Ship? = null
            d.optJSONArray("fleet")?.let { fa ->
                for (i in 0 until fa.length()) {
                    val fs = fa.optJSONObject(i) ?: continue
                    val s = mkOwned(fs.optString("cls", "wren"), fs.optDouble("x"), fs.optDouble("y"))
                    s.a = fs.optDouble("a", 0.0)
                    s.vx = fs.optDouble("vx", 0.0)
                    s.vy = fs.optDouble("vy", 0.0)
                    val savedFuel = fs.optDouble("fuel", s.fuel)
                    s.gear = fs.optBoolean("gear", false)
                    s.cargo.clear()
                    fs.optJSONObject("cargo")?.let { co ->
                        val keys = co.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            s.cargo[k] = co.optInt(k, 0)
                        }
                    }
                    fs.optJSONObject("upg")?.let { uo ->
                        val keys = uo.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            s.upg[k] = uo.optInt(k, 0)
                        }
                    }
                    fs.optJSONArray("mods")?.let { ma ->
                        for (mi in 0 until ma.length()) {
                            val pair = ma.optJSONArray(mi) ?: continue
                            val mod = s.mods.getOrNull(mi) ?: continue
                            mod.hp = pair.optDouble(0, mod.hp)
                            mod.dead = pair.optInt(1, 0) != 0
                        }
                    }
                    stats(s)
                    s.fuel = min(savedFuel, s.fuelMax)
                    sprite(s)
                    fs.optJSONObject("d")?.let { doObj ->
                        val ty = doObj.optString("ty")
                        if (ty == "ground") {
                            s.x = doObj.optDouble("x", s.x)
                            s.y = doObj.optDouble("y", s.y)
                            s.a = doObj.optDouble("a", s.a)
                            settle(s)
                        } else {
                            val bKey = doObj.optString("b", "")
                            val b = if (bKey.isNotEmpty()) byKey(bKey) else null
                            if (b != null) {
                                if (ty == "anchor") {
                                    anchor(s, b, doObj.optInt("cx"), doObj.optInt("cy"))
                                } else if (ty == "pad") {
                                    val p = b.pads.getOrNull(doObj.optInt("pi", -1))
                                    if (p != null && p.occ == null) dockPad(s, b, p, true)
                                } else if (ty == "port") {
                                    val p = b.ports.getOrNull(doObj.optInt("pi", -1))
                                    if (p != null && p.occ == null) dockPort(s, b, p, true)
                                }
                            }
                        }
                    }
                    g.fleet.add(s)
                    if (fs.optBoolean("isPl", false)) flying = s
                }
            }

            val evJson = d.optJSONObject("ev")
            if (flying != null) {
                flying!!.isPl = true
                GS.pl = flying
                GS.cam.x = flying!!.x
                GS.cam.y = flying!!.y
            } else if (evJson != null) {
                val suit = mkSuit(evJson.optDouble("x"), evJson.optDouble("y"))
                suit.suit = evJson.optDouble("suit", 100.0)
                evJson.optJSONObject("sack")?.let { so ->
                    val keys = so.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        suit.sack[k] = so.optInt(k, 0)
                    }
                }
                GS.ev = suit
                GS.cam.x = suit.x
                GS.cam.y = suit.y
            } else {
                wakeAtHome()
            }

            GS.cam.rot = atan2(GS.cam.y, GS.cam.x) + PI / 2.0
            GS.cam.snap = true
            for (st in W.stations) if (!g.jobs.containsKey(st.name)) mkJobs(st)
            wasSurf = GS.pl?.dock?.let { it.ty == "ground" || it.b?.surf == true } == true
            mode("play")
            GS.cam.tz = defZoom()
            toast("WELCOME BACK · day ${floor(W.time / 1200.0).toInt() + 1} in the band", "good")
        } catch (_: Exception) {
            toast("Save unreadable — fresh start", "bad")
            newRun()
        }
    }

    fun newRun() {
        SaveRepository.clearSave()
        apSet(false, false)
        AP.demo = false
        freshWorld(Random.nextInt(1_000_000_000))
        startPlay(false)
    }

    fun startDemo() {
        freshWorld(Random.nextInt(1_000_000_000))
        AP.demo = true
        startPlay(true)
        apSet(true, true)
        toast(
            "AI DEMO · the computer plays the whole career, start to finish · tap AI to take over — demo runs are never saved",
            "warn"
        )
    }

    fun startPlay(quiet: Boolean = false) {
        mode("play")
        GS.cam.tz = defZoom()
        GS.cam.user = 0.0
        if (!quiet) {
            toast("2035 · SHACKLETON RIM. You are a prospector with a suit, a bunk and 240₡.", "warn")
            pendingToasts.add(
                PendingToast(
                    2.2,
                    "Move stick walks · Jump hops · F interacts · walk east to the SELENE LIFT (follow the LIFT arrow)",
                    null
                )
            )
            pendingToasts.add(
                PendingToast(
                    5.2,
                    "Tap AI at the top bar any time to let the autopilot play.",
                    null
                )
            )
            save()
        }
        pushStates()
    }

    fun playerDied(why: String?) {
        val G = GS.G ?: return
        if (G.mode == "dead" || G.mode == "title") return
        val reason = why?.takeIf { it.isNotBlank() } ?: "Signal lost."
        feed("$reason — Rim medics brought you home.", "bad")
        if (AP.demo) {
            respawn()
            return
        }
        deathWhy = reason
        endStatsSnapshot = buildStatGrid()
        mode("dead")
    }

    fun respawn() {
        val G = GS.G ?: return
        GS.pl?.let {
            it.isPl = false
            GS.pl = null
        }
        GS.ev = null
        AU.air(false)
        G.cr = floor(G.cr * 0.85).toInt()
        G.ride = null
        G.crew?.let { if (it.state != "idle") it.late = true }
        wakeAtHome()
        GS.PC.scan = false
        GS.PC.match = false
        GS.PC.assist = null
        apAbort(if (AP.on) "Woke up in the HAB 14 medbay — starting the day over." else null)
        mode("play")
        toast("MEDBAY, HAB 14 · −15% credits. Your ships are where you left them.", "warn")
        save()
    }

    fun tow() {
        val G = GS.G ?: return
        val f = 300
        val pl = GS.pl
        val ev = GS.ev
        if (pl != null && !pl.dead) {
            val home = if (pl.cls == "hare") SITES.depot else SITES.sel
            val paid = min(G.cr, f)
            G.cr -= paid
            if (pl.dock != null) undock(pl)
            if (pl.cls == "hare") {
                val p = home.pads.firstOrNull { it.occ == null }
                if (p != null) dockPad(pl, home, p, true)
            } else {
                val p = home.ports.firstOrNull { it.berth && it.occ == null } ?: home.ports.firstOrNull { it.occ == null }
                if (p != null) dockPort(pl, home, p, true)
            }
            pl.fuel = max(pl.fuel, pl.fuelMax * 0.4)
            for (m in pl.mods) {
                if ((m.t == "rcs" || m.t == "eng") && m.dead) {
                    m.dead = false
                    m.hp = m.max * 0.4
                }
            }
            stats(pl)
            pl.dirty = true
            GS.cam.snap = true
            mode("play")
            toast(
                "TOW · −${paid}₡${if (paid < f) " (all you had)" else ""} · ${pl.name} brought to ${home.name}",
                "warn"
            )
        } else if (ev != null && !ev.hide) {
            val st = if (hy(ev.x, ev.y) > C.RM + 600.0) SITES.sel else SITES.hab
            val p = airlockSpot(st, ev.x, ev.y) ?: Vec2(st.x, st.y) ?: Vec2(st.x, st.y)
            G.cr = max(0, G.cr - 120)
            ev.x = p.x
            ev.y = p.y
            ev.vx = 0.0
            ev.vy = 0.0
            ev.suit = 100.0
            GS.cam.snap = true
            mode("play")
            toast("RESCUE · −120₡ · brought inside ${st.name}", "warn")
        } else {
            mode("play")
        }
    }

    fun toggleSound() {
        val G = GS.G ?: return
        G.sound = !G.sound
        AU.init()
        syncSound()
        save()
        pushStates()
    }

    private fun syncSound() {
        val on = GS.G?.sound ?: true
        AU.setOn(on)
    }

    fun toggleAI() {
        val G = GS.G ?: return
        if (G.mode == "title") return
        apSet(!AP.on)
        toast(
            if (AP.on) "AI PLAYER ENGAGED — sit back"
            else "MANUAL CONTROL — yours${if (AP.demo) " · demo run, not saved" else ""}",
            if (AP.on) "good" else "warn"
        )
        pushStates()
    }

    fun titleCam() {
        val dpW = VIEW.widthPx / max(1f, VIEW.density)
        GS.cam.tz = cl(dpW / 1400.0, 0.3, 0.8)
        GS.cam.user = 999.0
    }

    fun mode(m: String) {
        val G = GS.G ?: return
        G.mode = m
        if (m != "play") {
            AU.engine(0.0)
            AU.mine(false)
        }
        if (m != "title") save()
        pushStates()
    }

    fun toast(m: String, k: String? = null) {
        val last = toastsList.lastOrNull()
        if (last != null && last.msg == m) {
            toastsList[toastsList.lastIndex] = last.copy(count = last.count + 1, age = 0.0, fading = false)
            pushStates()
            return
        }
        toastsList.add(ToastUiItem(id = nextToastId++, msg = m, kind = k))
        while (toastsList.size > 4) toastsList.removeAt(0)
        pushStates()
    }

    fun tabsOf(st: Body): List<String> {
        val t = st.tabs.toMutableList()
        if (st.econ != null && !t.contains("jobs")) {
            t.add(min(2, t.size), "jobs")
        }
        return t
    }

    fun openStation(b: Body, tab: String? = null) {
        val G = GS.G ?: return
        if (b.kind != "station") return
        val T = tabsOf(b)
        if (T.isEmpty()) return
        G.panel = b
        G.tab = if (tab != null && T.contains(tab)) tab else T[0]
        AU.ui()
        pushStates()
    }

    fun selectTab(tab: String) {
        val G = GS.G ?: return
        val st = G.panel ?: return
        val T = tabsOf(st)
        if (!T.contains(tab)) return
        G.tab = tab
        AU.ui()
        pushStates()
    }

    fun closeStation() {
        GS.G?.panel = null
        pushStates()
    }

    fun undockFromStation() {
        val st = GS.G?.panel
        val pl = GS.pl
        if (pl != null && pl.dock != null && st != null && pl.dock?.b === st) {
            undock(pl)
        }
        closeStation()
    }

    private fun qtyFor(st: Body, g: String, buy: Boolean): Int {
        val G = GS.G ?: return 1
        val h = hold() ?: return 0
        if (G.qty == "MAX") {
            val q = if (buy) {
                val p = max(1, price(st, g))
                val stock = st.econ?.get(g)?.stock?.toInt() ?: 0
                min(min(G.cr / p, h.max - h.used), stock)
            } else {
                h.c[g] ?: 0
            }
            return max(1, q)
        }
        return max(1, G.qty.toIntOrNull() ?: 1)
    }

    fun panelAct(a: String, good: String? = null, extra: String? = null) {
        val G = GS.G ?: return
        val W = GS.W ?: return
        val st = G.panel ?: return
        AU.ui()
        when (a) {
            "qty" -> G.qty = if (extra == "MAX") "MAX" else (extra?.toIntOrNull() ?: 1).toString()
            "buy", "sell" -> if (good != null) trade(st, good, qtyFor(st, good, a == "buy"), a == "buy")
            "svc" -> {
                G.svc = services(st)
                val idx = extra?.toIntOrNull() ?: -1
                val s = G.svc.getOrNull(idx)
                if (s != null && s.ok) s.f()
            }
            "accept" -> if (extra != null) accept(st, extra)
            "drop" -> if (extra != null) dropJob(extra)
            "shift" -> {
                if (boardShift()) {
                    story("board")
                }
                pushStates()
                return
            }
            "buyship" -> if (extra != null) buyShip(st, extra)
            "bounty" -> collectBounty()
            "cores" -> {
                val h = hold()
                val n = h?.c?.get("core") ?: 0
                if (n > 0 && h != null) {
                    h.c.remove("core")
                    h.ship?.let { stats(it) }
                    G.cores += n
                    G.cr += n * 400
                    W.stats.earned += n * 400
                    AU.cash()
                    toast(
                        "CATALOG OFFICE takes $n core shard${if (n > 1) "s" else ""} · +${fm(n * 400)}₡ · the techs start reading the logs",
                        "good"
                    )
                    story("tick")
                }
            }
            "file" -> story("filed")
            "eva" -> {
                closeStation()
                enterEVA()
                pushStates()
                return
            }
            "sleep" -> {
                GS.ev?.let { it.suit = 100.0 }
                save()
                toast("Slept. Saved. The cable hums outside.", "good")
            }
        }
        pushStates()
    }

    fun exitToTitle() {
        save()
        val wasDemo = AP.demo
        apSet(false, false)
        AP.demo = false
        mode("title")
        val s = readSave()
        val note = when {
            s != null -> {
                val t = s.optDouble("time", 0.0)
                val cr = s.optJSONObject("g")?.optInt("cr", 0) ?: 0
                "SAVED · day ${floor(t / 1200.0).toInt() + 1} · ${fm(cr)}₡"
            }
            wasDemo -> "Demo ended. The band is waiting."
            else -> ""
        }
        titleCam()
        _uiState.value = _uiState.value.copy(
            hasSave = s != null,
            titleNote = note,
            titleNoteError = false
        )
        pushStates()
    }

    fun openHelp() {
        helpOpen = true
        AU.ui()
        pushStates()
    }

    fun closeHelp() {
        helpOpen = false
        AU.ui()
        pushStates()
    }

    fun toggleHelp() {
        if (helpOpen) closeHelp() else openHelp()
    }

    fun openMap() {
        mapOpen = true
        AU.ui()
        pushStates()
    }

    fun closeMap() {
        mapOpen = false
        AU.ui()
        pushStates()
    }

    fun showConfirm(text: String, onYes: () -> Unit) {
        confirmText = text
        confirmAction = onYes
        AU.ui()
        pushStates()
    }

    fun confirmYes() {
        val f = confirmAction
        confirmText = null
        confirmAction = null
        AU.ui()
        f?.invoke()
        pushStates()
    }

    fun confirmNo() {
        confirmText = null
        confirmAction = null
        AU.ui()
        pushStates()
    }

    fun openDialog(name: String, role: String, text: String, seed: Int, fac: String, choices: List<DialogChoiceUi>) {
        dialogState = DialogUiState(name, role, text, seed, fac, choices)
        pushStates()
    }

    fun closeDialog() {
        dialogState = null
        pushStates()
    }

    fun keepFlyingAfterWin() {
        mode("play")
        toast("The band keeps turning. Fly it as long as you like.", "good")
    }

    fun setScannerFreq(freq: Double) {
        GS.PC.freq = cl(freq, 20.0, 200.0)
        pushStates()
    }

    fun closeScanner() {
        GS.PC.scan = false
        AU.ui()
        pushStates()
    }

    fun togglePause() {
        val m = GS.G?.mode ?: return
        if (m == "play") mode("pause")
        else if (m == "pause") mode("play")
    }

    fun requestNewCareerConfirm() {
        showConfirm("Start over? Your credits, ships and every cut you made in the band will be gone.") {
            newRun()
        }
    }

    fun onAppBackground() {
        appInBackground = true
        save()
        val G = GS.G
        if (G != null && G.mode == "play" && !AP.on) {
            mode("pause")
            G.autoPaused = true
        }
    }

    fun onAppForeground() {
        appInBackground = false
        val G = GS.G
        if (G != null && G.autoPaused && G.mode == "pause") {
            G.autoPaused = false
        }
    }

    private fun globalEdges() {
        val G = GS.G ?: return
        var i = IN.edge.lastIndex
        while (i >= 0) {
            val e = IN.edge[i]
            var handled = true
            when (e) {
                "pause" -> {
                    when {
                        dialogState != null -> closeDialog()
                        confirmText != null -> confirmNo()
                        helpOpen -> closeHelp()
                        mapOpen -> closeMap()
                        G.panel != null -> closeStation()
                        G.mode == "play" -> mode("pause")
                        G.mode == "pause" -> mode("play")
                    }
                }
                "help" -> toggleHelp()
                "ai" -> toggleAI()
                else -> handled = false
            }
            if (handled) IN.edge.removeAt(i)
            i--
        }
    }

    private fun simTick(dt: Double) {
        val W = GS.W ?: return
        val G = GS.G ?: return
        econTick(dt)
        agents(dt)
        crewTick(dt)
        eventsTick(dt)
        G._j += dt
        if (G._j > 180.0) {
            G._j = 0.0
            for (st in W.stations) if (st.econ != null) mkJobs(st)
        }
        if (G.mode != "play") return
        G._sh += dt
        if (G._sh > 30.0) {
            G._sh = 0.0
            val nb = com.example.keres9.world.ensureShards()
            if (nb != null) {
                feed("Catalog Office: the array net caught another cold memory lattice — sealed inside ${nb.name}.", "good")
            }
        }
        val pl = GS.pl
        if (pl != null && pl.dead) playerDied("Your ${pl.name} broke up.")
        val ev = GS.ev
        if (ev != null && !ev.hide) {
            G._rm += dt
            if (G._rm > 0.4) {
                G._rm = 0.0
                val r = evaRoom()
                if (r != null && r.rm != null) story("room", roomKind = r.rm.kind, roomBody = r.b)
            }
        }
        val onSurf = pl?.dock?.let { it.ty == "ground" || it.b?.surf == true } == true
        if (pl != null && wasSurf && !onSurf && pl.dock == null) {
            W.stats.launches++
            feed("You launched ${pl.name} from the surface.")
        }
        wasSurf = onSurf
        if (pl != null && pl.cls == "hare" && pl.dock != null && (pl.dock?.ty == "anchor" || pl.dock?.b?.surf == false)) {
            story("orbitdock")
        }
    }

    private fun copyAiToCtl() {
        val CTL = GS.CTL
        CTL.lx = inp.lx
        CTL.ly = inp.ly
        CTL.rx = inp.rx
        CTL.ry = inp.ry
        CTL.thr = inp.thr
        CTL.mine = inp.mine
        CTL.brake = inp.brake
        CTL.ai = AP.on

        fun guard(name: String, v: Double): Double {
            if (!v.isFinite()) {
                val t = GS.W?.time ?: 0.0
                if (t - (nanWarnTime[name] ?: -1e9) > 60.0) {
                    nanWarnTime[name] = t
                }
                return 0.0
            }
            return v
        }
        CTL.lx = guard("lx", CTL.lx)
        CTL.ly = guard("ly", CTL.ly)
        CTL.rx = guard("rx", CTL.rx)
        CTL.ry = guard("ry", CTL.ry)
        CTL.thr = guard("thr", CTL.thr)
        inp.lx = CTL.lx
        inp.ly = CTL.ly
        inp.rx = CTL.rx
        inp.ry = CTL.ry
        inp.thr = CTL.thr
    }

    private fun copyFrame(src: ControlFrame, dst: ControlFrame) {
        dst.lx = src.lx
        dst.ly = src.ly
        dst.rx = src.rx
        dst.ry = src.ry
        dst.thr = src.thr
        dst.mine = src.mine
        dst.brake = src.brake
        dst.ai = src.ai
    }

    private fun stepUiTimers(dt: Double) {
        stepPendingToasts(dt)
        if (winDelayTimer > 0.0) {
            winDelayTimer -= dt
            if (winDelayTimer <= 0.0) {
                winDelayTimer = -1.0
                endStatsSnapshot = buildStatGrid()
                mode("win")
                AU.chime()
            }
        }
        if (evBannerTimer > 0.0) {
            evBannerTimer -= dt
            if (evBannerTimer <= 0.0) {
                evBannerText = null
            }
        }
        var changed = false
        var i = toastsList.lastIndex
        while (i >= 0) {
            val t = toastsList[i]
            val na = t.age + dt
            if (na >= 5.9) {
                toastsList.removeAt(i)
                changed = true
            } else {
                val nf = na >= 5.2
                if (nf != t.fading) changed = true
                toastsList[i] = t.copy(age = na, fading = nf)
            }
            i--
        }
        if (changed) pushStates()
    }

    fun tick(rawDt: Double) {
        val W = GS.W ?: return
        val G = GS.G ?: return
        val dt = if (rawDt > 0.0 && rawDt.isFinite()) min(0.05, rawDt) else 0.016

        stepUiTimers(dt)
        globalEdges()

        val run = (G.mode == "play" && !frozen()) || G.mode == "title"
        if (AP.on && G.mode == "play") apThink(dt, run)
        apMeta(dt, ::defZoom)

        if (run) {
            val warp = if (G.mode == "play" && AP.on) AP.warp else 1
            for (w in 0 until warp) {
                W.time += dt
                if (G.mode == "play") {
                    readHuman(HUM, dt)
                    if (AP.on) {
                        val touched = hy(HUM.lx, HUM.ly) > 0.35 || abs(HUM.rx) > 0.35 || HUM.mine || HUM.brake
                        grab = if (touched) grab + dt else 0.0
                        if (grab > 0.3) {
                            grab = 0.0
                            toggleAI()
                            copyFrame(HUM, GS.CTL)
                        } else {
                            copyAiToCtl()
                        }
                    } else {
                        copyFrame(HUM, GS.CTL)
                    }
                    control(dt)
                }
                acc += dt
                var n = 0
                while (acc >= C.DT && n < 4) {
                    stepBodies(C.DT)
                    stepLift(C.DT)
                    stepShips(C.DT)
                    stepMeteors(C.DT)
                    if (GS.ev != null) stepEVA(C.DT)
                    stepPicks(C.DT)
                    stepDeb(C.DT)
                    stepBits(C.DT)
                    acc -= C.DT
                    n++
                }
                if (acc > C.DT * 4.0) acc = 0.0
                simTick(dt)
                if (G.mode != "play") break
            }
            if (G.mode == "title") {
                val L = W.lift?.E
                if (L != null) {
                    val t = (kotlin.math.sin(W.time * 0.03) + 1.0) / 2.0
                    val k = 1.0 - exp(-1.1 * dt)
                    GS.cam.x = lp(GS.cam.x, L.g.x + (L.t.x - L.g.x) * t + 160.0, k)
                    GS.cam.y = lp(GS.cam.y, L.g.y + (L.t.y - L.g.y) * t, k)
                    GS.cam.rot = atan2(GS.cam.y, GS.cam.x) + PI / 2.0
                    GS.cam.z = lp(GS.cam.z, GS.cam.tz, 1.0 - exp(-2.0 * dt))
                }
            } else {
                updCam(dt)
            }
        } else {
            AU.engine(0.0)
            updCam(dt * 0.25)
        }

        IN.edge.clear()
        IN.taps.clear()

        if (G.mode == "play") {
            G.save += dt
            if (G.save > 20.0) {
                G.save = 0.0
                save()
            }
        }

        frame++
        _renderTick.value = frame

        // Smooth ghost sticks when AI is on, or when throttle/ctx state needs crisp feedback
        pushControlsState()
        if (frame % 4L == 0L) {
            if (G.panel != null && G.mode == "play") {
                panelRefreshCounter++
                if (panelRefreshCounter % 2 == 0 && (G.tab == "crew" || G.tab == "catalog")) {
                    pushStates()
                    return
                }
            }
            pushHudState()
        }
    }

    private fun pushStates() {
        pushControlsState()
        pushHudState()
    }

    private fun pushControlsState() {
        val pl = GS.pl
        val PC = GS.PC
        _controlsState.value = ControlsUiState(
            aiOn = AP.on,
            aiLx = IN.aiLx,
            aiLy = IN.aiLy,
            aiRx = IN.aiRx,
            aiThr = IN.aiThr,
            aiMine = IN.aiMine,
            humanThr = IN.thr,
            matchAct = PC.match,
            gearAct = pl?.gear == true,
            gearEnabled = pl != null && (pl.tailLand || pl.gear),
            scanAct = PC.scan,
            brakeAct = IN.brakeHold,
            ctxLabel = PC.ctx.l,
            ctxReady = PC.ctx.ok,
            ctxEnabled = PC.ctx.f != null,
            altLabel = PC.alt?.l,
            aiPickTag = AP.aiPickTag
        )
    }

    private fun pushHudState() {
        val W = GS.W ?: return
        val G = GS.G ?: return
        val pl = GS.pl
        val ev = GS.ev
        val PC = GS.PC

        val fRatio = when {
            pl != null -> cl(pl.fuel / max(1.0, pl.fuelMax), 0.0, 1.0).toFloat()
            ev != null -> cl(ev.suit / 100.0, 0.0, 1.0).toFloat()
            else -> 1f
        }
        val hRatio = if (pl != null) cl(integ(pl), 0.0, 1.0).toFloat() else 1f
        val hl = hold()
        val cRatio = if (hl != null) cl(hl.used.toDouble() / max(1, hl.max), 0.0, 1.0).toFloat() else 0f

        val day = floor(W.time / 1200.0).toInt() + 1
        val hr = (6.0 + ((W.time % 1200.0) / 1200.0) * 24.0) % 24.0
        G.day = day
        val clockStr = "DAY ${day.toString().padStart(2, '0')} · " +
            if (G.crew?.state == "site") crewLine()
            else "${floor(hr).toInt().toString().padStart(2, '0')}:${floor((hr % 1.0) * 60.0).toInt().toString().padStart(2, '0')}"

        val modeStr = when {
            G.ride != null -> if (G.ride?.kind == "lift") "LIFT" else "MULE-3"
            ev != null -> if (ev.grounded > 0.0) "WALK" else "EVA"
            pl != null -> when (pl.dock?.ty) {
                "anchor" -> "HARPOONED"
                "pad" -> "ON PAD"
                "port" -> "DOCKED"
                "ground" -> "LANDED"
                else -> if (PC.scan) "SCANNING" else "FLIGHT"
            }
            else -> "—"
        }

        AU.danger((pl != null && hRatio < 0.25f) || (ev != null && !ev.hide && ev.suit < 25.0))
        if (pl != null && fRatio < 0.12f && !G.fuelWarn) {
            G.fuelWarn = true
            toast("PROPELLANT LOW — refuel at a station or the Depot", "bad")
        }
        if (fRatio > 0.3f) G.fuelWarn = false

        val o = storyObj()
        val activeJob = G.missions.firstOrNull()
        val objTxt = "${o.t} — ${o.d}${if (activeJob != null) " ▸ contract: ${activeJob.title} → ${activeJob.to}" else ""}"
        val objRew = if (o.r > 0) "reward ${fm(o.r)}₡" else ""

        val tgt = PC.tgt
        val foc = GS.focus()
        val targetUi = if (tgt != null && !tgt.dead && foc != null) {
            val dx = tgt.x - foc.x
            val dy = tgt.y - foc.y
            val dist = hy(dx, dy)
            val ux = dx / max(1.0, dist)
            val uy = dy / max(1.0, dist)
            val rvx = foc.vx - tgt.vx
            val rvy = foc.vy - tgt.vy
            val rv = hy(rvx, rvy)
            val clg = rvx * ux + rvy * uy
            val kindLbl = when {
                tgt is Body && tgt.kind == "station" -> if (tgt.surf) "SURFACE" else "STATION"
                tgt is Body && tgt.cat != 0 -> tgt.typeN.uppercase()
                tgt is Body -> "UNCATALOGUED"
                else -> "SHIP"
            }
            TargetReadoutUi(
                name = tgt.name + if (tgt is Body && tgt.kind == "rock" && tgt.cat == 0) " ?" else "",
                kind = kindLbl,
                dist = fm(dist),
                relSpeed = "%.1f".format(rv),
                closeRate = "${if (clg >= 0) "+" else ""}${"%.1f".format(clg)}"
            )
        } else null

        val assist: DockGates? = PC.assist
        val dockUi = if (assist != null && !PC.scan && ev == null) {
            val hint = when {
                !assist.v -> "Too fast — BRAKE/MATCH"
                !assist.a -> "Align nose"
                !assist.l -> "Center lateral offset"
                !assist.r -> "Stop spin"
                !assist.gear -> "GEAR DOWN [G]"
                else -> "Drift in"
            }
            DockUiState(
                title = if (assist.ty == "pad") "PAD ENVELOPE" else "CLAMP ENVELOPE",
                spdOk = assist.v,
                angOk = assist.a,
                latOk = assist.l,
                rotOk = assist.r,
                hint = hint
            )
        } else null

        val apTimeStr = if (AP.on) "T+${floor(W.time - AP.started).toInt()}s" else ""

        _uiState.value = _uiState.value.copy(
            mode = G.mode,
            hasSave = _uiState.value.hasSave,
            titleNote = _uiState.value.titleNote,
            titleNoteError = _uiState.value.titleNoteError,
            soundOn = G.sound,
            creditsText = "₡ ${fm(G.cr)}",
            badgeText = if (G.bounty > 0) "BOUNTY ${fm(G.bounty)}" else "TIER ${G.tier}",
            badgeIsBounty = G.bounty > 0,
            fuelLabel = if (pl != null) "FUEL" else "AIR",
            fuelRatio = fRatio,
            hullLabel = if (pl != null) "HULL" else "SUIT",
            hullRatio = hRatio,
            cargoLabel = if (pl != null) "CRG" else "SACK",
            cargoRatio = cRatio,
            modeBadge = modeStr,
            clockText = clockStr,
            aiOn = AP.on,
            aiWarp = if (AP.on) AP.warp else 1,
            objText = objTxt,
            objReward = objRew,
            toasts = toastsList.toList(),
            evBannerText = evBannerText,
            evBannerInfo = evBannerInfo,
            target = targetUi,
            apAct = AP.act,
            apCap = AP.cap,
            apSub = AP.sub,
            apTime = apTimeStr,
            dock = dockUi,
            scanOpen = PC.scan && ev == null,
            scanFreq = PC.freq.toFloat(),
            scanLock = PC.lock.toFloat().coerceIn(0f, 1f),
            scanLabel = PC.scanLabel,
            helpOpen = helpOpen,
            mapOpen = mapOpen,
            confirmText = confirmText,
            deathWhy = deathWhy,
            endStats = if (G.mode == "dead" || G.mode == "win") endStatsSnapshot else emptyList(),
            dialog = dialogState,
            station = G.panel?.let { buildStationState(it) }
        )
    }

    private fun buildStatGrid(): List<StatEntryUi> {
        val W = GS.W ?: return emptyList()
        val G = GS.G ?: return emptyList()
        val s = W.stats
        return listOf(
            StatEntryUi("DAY", G.day.toString()),
            StatEntryUi("CREDITS", "${fm(G.cr)}₡"),
            StatEntryUi("ORE CUT", s.mined.toString()),
            StatEntryUi("CATALOGUED", s.catalogued.toString()),
            StatEntryUi("DEEP CONTACTS", s.found.toString()),
            StatEntryUi("SHIFTS", s.shifts.toString()),
            StatEntryUi("DELIVERED", s.delivered.toString()),
            StatEntryUi("LAUNCHES", s.launches.toString()),
            StatEntryUi("LANDINGS", s.landings.toString()),
            StatEntryUi("LIFT RIDES", s.lifts.toString()),
            StatEntryUi("EVA", s.eva.toString()),
            StatEntryUi("EARNED", "${fm(s.earned)}₡")
        )
    }

    private fun buildStationState(st: Body): StationUiState {
        val G = GS.G!!
        val W = GS.W!!
        val pl = GS.pl
        val ev = GS.ev
        val h0 = hold()
        val tabs = tabsOf(st)
        val tab = G.tab
        val canUndock = pl != null && pl.dock != null && pl.dock?.b === st && !(pl.tailLand && pl.dock?.ty == "pad")
        val subtitle = "${if (st.surf) "SURFACE · " else "ORBITAL · "}${st.fac.uppercase()}" +
            (st.transfer?.let { " · BOUND FOR ${it.dest.uppercase()}" } ?: "")

        var marketGoods = emptyList<MarketGoodUi>()
        var servicesList = emptyList<ServiceRowUi>()
        var showEvaBtn = false
        var evaShipName = ""
        var shipStatusLine: String? = null
        var crewLineTxt = ""
        var crewShifts = 0
        var crewCanBoard = false
        var crewMatesTxt = ""
        var crewInShipNote = false
        var crewTier2Note = false
        var yardRows = emptyList<YardRowUi>()
        var yardHelp = ""
        var bountyText = ""
        var bountyEnabled = false
        var coresText = ""
        var coresHave = 0
        var showFileClaim = false
        var catalogRocks = emptyList<CatalogRockUi>()
        var activeJobs = emptyList<JobRowUi>()
        var availableJobs = emptyList<JobRowUi>()
        var homeBunkTitle = ""
        var homeBunkDesc = ""
        var homeCareerDesc = ""
        var homeStoryDesc = ""
        var homeSackNote = false
        var barTitle = ""
        var barSouls = 0
        var barRumor = ""
        var barAgents = emptyList<BarAgentUi>()
        var feedRows = emptyList<FeedRowUi>()

        when (tab) {
            "market" -> {
                val ec = st.econ
                if (ec != null && h0 != null) {
                    marketGoods = ec.entries.mapNotNull { (g, e) ->
                        val gd = GD[g] ?: return@mapNotNull null
                        if (gd.story) return@mapNotNull null
                        val p = price(st, g, false)
                        val sp = price(st, g, true)
                        val prev = if (e.hist.size > 1) e.hist[e.hist.size - 2] else p
                        val have = h0.c[g] ?: 0
                        val role = when {
                            e.prod > 0.0 -> " · MADE HERE"
                            e.cons > 0.8 -> " · IN DEMAND"
                            else -> ""
                        }
                        val ratio = cl(e.stock / max(1.0, e.ref * 1.6), 0.0, 1.0).toFloat()
                        MarketGoodUi(
                            key = g,
                            name = gd.n,
                            have = have,
                            stock = e.stock.roundToInt(),
                            stockRatio = ratio,
                            role = role,
                            sellPrice = sp,
                            buyPrice = p,
                            prevPrice = prev,
                            color = gd.c,
                            history = e.hist.toList()
                        )
                    }
                }
            }
            "service" -> {
                G.svc = services(st)
                servicesList = G.svc.mapIndexed { i, v ->
                    ServiceRowUi(
                        index = i,
                        label = v.l,
                        desc = v.d,
                        costText = if (v.c > 0) "${fm(v.c)}₡" else "—",
                        enabled = v.ok && G.cr >= (if (v.min > 0) v.min else v.c)
                    )
                }
                if (pl != null && pl.dock?.b === st) {
                    showEvaBtn = true
                    evaShipName = pl.name
                }
                val s = if (pl != null && pl.dock?.b === st) pl else shipAt(st)
                if (s != null) {
                    shipStatusLine = "${s.name} STATUS · hull ${(integ(s) * 100.0).roundToInt()}% · fuel ${s.fuel.roundToInt()}/${s.fuelMax.roundToInt()} · thrust ${"%.1f".format(s.thrust)} m/s² · hold ${cmass(s)}/${s.cargoMax} · laser ${s.lasD.roundToInt()} · array ${s.scanR.roundToInt()}m"
                }
            }
            "crew" -> {
                val c = G.crew
                crewCanBoard = c != null && c.state == "idle" && ev != null && !ev.hide
                crewLineTxt = "${crewLine()} · shifts worked ${c?.shifts ?: 0}"
                crewShifts = c?.shifts ?: 0
                crewMatesTxt = "${MATES.joinToString(" · ") { it.n }} — two of them go out with you every shift."
                crewInShipNote = ev == null
                crewTier2Note = G.tier >= 2
            }
            "yard" -> {
                yardRows = yard(st).map { y ->
                    YardRowUi(
                        cls = y.cls,
                        title = "${y.n} · ${fm(y.c)}₡",
                        desc = y.d + if (y.owned) " · YOU OWN ONE" else "",
                        owned = y.owned,
                        enabled = !y.owned && G.cr >= y.c
                    )
                }
                yardHelp = if (st.surf) {
                    "The HARE sits on a pad outside. Walk out the east door and press F next to it to board. Throttle up to lift off — everything is manual."
                } else {
                    "The WREN clamps to a berth. Float through the airlock and press F beside it to board."
                }
            }
            "catalog" -> {
                val L = W.rocks.filter { it.cat != 0 }.sortedByDescending { it.catT }
                val un = W.rocks.count { it.cat == 0 && !it.hidden }
                coresHave = h0?.c?.get("core") ?: 0
                bountyText = "pending ${fm(G.bounty)}₡ · ${L.size} catalogued · $un still UNKNOWN in the band"
                bountyEnabled = G.bounty > 0
                coresText = "Memory lattices from the dead catalog AI. Office pays 400₡ each and reads the logs. Handed in: ${G.cores}/3"
                showFileClaim = storyObj().id == "file"
                catalogRocks = L.take(10).map { b ->
                    val top = b.comp.entries
                        .filter { it.key != "rock" }
                        .sortedByDescending { it.value }
                        .take(3)
                        .joinToString(", ") { e ->
                            val gn = GD[e.key]?.n ?: e.key
                            "$gn ${((e.value.toDouble() / max(1, b.cells)) * 100.0).roundToInt()}%"
                        }
                    CatalogRockUi(
                        name = b.name,
                        desc = "${b.typeN} · from the ${b.origin} · ${(b.rVal * C.CS * 2.0).roundToInt()} m · ${top.ifEmpty { "mostly rock" }}"
                    )
                }
            }
            "jobs" -> {
                activeJobs = G.missions.map { j ->
                    JobRowUi(
                        id = j.id,
                        title = "${j.title} · ACTIVE ${if (j.kind == "deliver") "${j.got}/${j.n}" else ""}",
                        desc = j.desc,
                        rewardText = "${fm(j.reward)}₡",
                        active = true
                    )
                }
                availableJobs = (G.jobs[st.name] ?: emptyList()).map { j ->
                    JobRowUi(
                        id = j.id,
                        title = j.title,
                        desc = j.desc,
                        rewardText = "${fm(j.reward)}₡",
                        active = false
                    )
                }
            }
            "home" -> {
                val fleet = G.fleet.filter { !it.dead }
                homeBunkTitle = "HAB 14 · YOUR BUNK"
                homeBunkDesc = "Rim Colony, Shackleton. Day ${G.day}. " + if (G.tier == 1) {
                    "You own a suit and a bunk. The lift is 150 m east."
                } else {
                    "Fleet: " + fleet.joinToString(", ") { s ->
                        "${s.name} @ ${s.dock?.b?.name ?: if (s.dock != null) "regolith" else "in flight"}"
                    }
                }
                val tierName = listOf(
                    "",
                    "TIER 1 · EVA crew hand",
                    "TIER 2 · orbital prospector (WREN)",
                    "TIER 3 · surface-to-orbit hauler (HARE)"
                ).getOrElse(G.tier) { "TIER ${G.tier}" }
                homeCareerDesc = "$tierName · ${fm(W.stats.earned)}₡ earned · ${W.stats.catalogued} bodies catalogued"
                homeStoryDesc = STORY.take(G.story).joinToString(" · ") { "✓ ${it.t}" }.ifEmpty { "just getting started" }
                homeSackNote = sackN(ev) > 0
            }
            else -> {
                barTitle = "THE BAR · ${st.name}"
                barSouls = st.crew.size
                barRumor = rumor()
                barAgents = GS.AG
                    .filter { it.alive && it.ship.dock?.b === st }
                    .map { a ->
                        BarAgentUi(
                            id = a.id,
                            name = a.name,
                            subtitle = "${a.role.uppercase()} · ${a.ship.name}",
                            line = agentLine(a),
                            fac = a.fac
                        )
                    }
                feedRows = W.feed.take(12).map { f ->
                    val col = when (f.k) {
                        "bad" -> PAL.red
                        "good" -> PAL.green
                        "warn" -> PAL.amber
                        else -> PAL.text
                    }
                    FeedRowUi(f.m, floor(f.t).toInt(), col)
                }
            }
        }

        val holdSum = if (h0 != null) "${h0.n} ${h0.used} / ${h0.max} · ${fm(G.cr)}₡" else null
        val holdSub = when {
            st.transfer != null -> "TRANSFER SHIP — departure premium in the last 4 minutes"
            st.surf -> "surface market — ice and metal are scarce down here"
            else -> "quantity applies to every BUY / SELL"
        }

        return StationUiState(
            stationName = st.name,
            subtitle = subtitle,
            canUndock = canUndock,
            tabs = tabs,
            activeTab = tab,
            qtyMode = G.qty,
            holdSummary = holdSum,
            holdSub = holdSub,
            marketGoods = marketGoods,
            servicesList = servicesList,
            showEvaButton = showEvaBtn,
            evaShipName = evaShipName,
            shipStatusLine = shipStatusLine,
            crewLineText = crewLineTxt,
            crewShifts = crewShifts,
            crewCanBoard = crewCanBoard,
            crewMatesText = crewMatesTxt,
            crewInShipNote = crewInShipNote,
            crewTier2Note = crewTier2Note,
            yardRows = yardRows,
            yardHelp = yardHelp,
            bountyText = bountyText,
            bountyEnabled = bountyEnabled,
            coresText = coresText,
            coresHave = coresHave,
            showFileClaim = showFileClaim,
            catalogRocks = catalogRocks,
            activeJobsCount = G.missions.size,
            activeJobs = activeJobs,
            availableJobs = availableJobs,
            homeBunkTitle = homeBunkTitle,
            homeBunkDesc = homeBunkDesc,
            homeCareerDesc = homeCareerDesc,
            homeStoryDesc = homeStoryDesc,
            homeSackNote = homeSackNote,
            barTitle = barTitle,
            barSouls = barSouls,
            barSeed = st.seed % 997,
            barFac = st.fac,
            barRumor = barRumor,
            barAgents = barAgents,
            feedRows = feedRows
        )
    }

    fun tabLabel(tab: String): String = TAB_NAMES[tab] ?: tab.uppercase()
}
