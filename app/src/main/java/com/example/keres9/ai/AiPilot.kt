package com.example.keres9.ai

import android.util.Log
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.CLS
import com.example.keres9.core.Contact
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.Job
import com.example.keres9.core.MAT
import com.example.keres9.core.Pick
import com.example.keres9.core.Ship
import com.example.keres9.core.TAU
import com.example.keres9.core.Targetable
import com.example.keres9.core.Vec2
import com.example.keres9.core.ad
import com.example.keres9.core.cl
import com.example.keres9.core.fm
import com.example.keres9.core.hy
import com.example.keres9.sim.STORY
import com.example.keres9.sim.alive
import com.example.keres9.sim.boardable
import com.example.keres9.sim.cmass
import com.example.keres9.sim.contactFreq
import com.example.keres9.sim.dropJob
import com.example.keres9.sim.hold
import com.example.keres9.sim.inBody
import com.example.keres9.sim.integ
import com.example.keres9.sim.mw
import com.example.keres9.sim.price
import com.example.keres9.sim.sackN
import com.example.keres9.sim.services
import com.example.keres9.sim.standoff
import com.example.keres9.sim.yard
import com.example.keres9.world.SITES
import com.example.keres9.world.cw
import com.example.keres9.world.portW
import com.example.keres9.world.ray
import java.util.IdentityHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class TaskCancelledException : RuntimeException("AI task cancelled")

class TaskScope {
    internal var nextStep: Continuation<Unit>? = null
    internal var completed: Boolean = false
    internal var cancelled: Boolean = false

    suspend fun yieldFrame() {
        if (cancelled) throw TaskCancelledException()
        suspendCoroutineUninterceptedOrReturn<Unit> { cont ->
            nextStep = cont
            COROUTINE_SUSPENDED
        }
    }
}

class FrameTask(val name: String, val t0: Double = GS.W?.time ?: 0.0, block: suspend TaskScope.() -> Unit) {
    val scope = TaskScope()
    private var started = false
    private val initialCont: Continuation<Unit> = block.createCoroutine(
        receiver = scope,
        completion = Continuation(EmptyCoroutineContext) { res ->
            scope.completed = true
            res.onFailure { ex ->
                if (ex !is TaskCancelledException) {
                    val msg = ex.message ?: ex.toString()
                    if (AP.lastErr != msg) {
                        Log.e("KeresAI", "AI task error in $name", ex)
                        AP.lastErr = msg
                    }
                }
            }
        }
    )

    fun step(): Boolean {
        if (scope.completed || scope.cancelled) return true
        val c = if (!started) {
            started = true
            initialCont
        } else {
            scope.nextStep
        }
        scope.nextStep = null
        if (c == null) {
            scope.completed = true
            return true
        }
        c.resume(Unit)
        return scope.completed || scope.cancelled
    }

    fun cancel() {
        scope.cancelled = true
        scope.nextStep = null
        scope.completed = true
    }
}

object AiUiBridge {
    var stationOpenFn: () -> Boolean = { false }
    var frozenFn: () -> Boolean = { false }
    var dialogOpenFn: () -> Boolean = { false }
    var appInBackgroundFn: () -> Boolean = { false }
    var closeDialogFn: () -> Unit = {}
    var selectTabFn: (String) -> Unit = {}
    var closeStationFn: () -> Unit = {}
    var panelActFn: (String, String?, String?) -> Unit = { _, _, _ -> }
    var clickButtonFn: (String) -> Unit = {}
    var modeFn: (String) -> Unit = {}
    var freqChangedFn: (Double) -> Unit = {}
    var onAiStateChanged: () -> Unit = {}
}

object AP {
    var on: Boolean = false
    var demo: Boolean = false
    var task: FrameTask? = null
    var act: String = "—"
    var cap: String = ""
    var sub: String = ""
    var warp: Int = 1
    var wantWarp: Boolean = false
    val log = mutableListOf<String>()
    var metaT: Double = 0.0
    var started: Double = 0.0
    var towing: Boolean = false
    var towT: Double = 0.0
    var dlgT: Double = 0.0
    var catHold: Double = 0.0
    var didHere: Boolean = false
    var mineExit: String = ""
    var lastErr: String? = null
    var aiPickTag: String? = null

    val mined = IdentityHashMap<Body, Int>()
    val noBite = IdentityHashMap<Body, Int>()
    val noBiteT = IdentityHashMap<Body, Double>()
    val badPort = mutableMapOf<String, Double>()
    val badCells = mutableSetOf<Long>()
}

private fun say(cap: String, sub: String? = null) {
    val t = GS.W?.time?.roundToInt() ?: 0
    if (cap != AP.cap) {
        AP.log.add("${t}s $cap")
        if (AP.log.size > 400) AP.log.removeAt(0)
    }
    AP.cap = cap
    if (sub != null) AP.sub = sub
}

private fun aiLog(m: String) {
    val t = GS.W?.time?.roundToInt() ?: 0
    AP.log.add("${t}s · $m")
    if (AP.log.size > 400) AP.log.removeAt(0)
}

private fun act(t: String) {
    AP.act = t
}

private fun sIdx(id: String): Int = STORY.indexOfFirst { it.id == id }
private fun at(id: String): Boolean = (GS.G?.story ?: 0) == sIdx(id)
private fun past(id: String): Boolean = (GS.G?.story ?: 0) > sIdx(id)

private suspend fun TaskScope.wait(s: Double, fn: ((Double) -> Unit)? = null) {
    var t = 0.0
    while (t < s) {
        fn?.invoke(t)
        yieldFrame()
        t += NAV.dt
    }
}

private suspend fun TaskScope.until(cond: () -> Boolean, s: Double, fn: ((Double) -> Unit)? = null): Boolean {
    var t = 0.0
    while (t < s) {
        if (cond()) return true
        fn?.invoke(t)
        yieldFrame()
        t += NAV.dt
    }
    return cond()
}

private suspend fun TaskScope.clickUI(tag: String, enabled: Boolean, pause: Double = 0.6, action: () -> Unit): Boolean {
    if (!enabled) return false
    AP.aiPickTag = tag
    wait(pause * 0.6)
    action()
    AP.aiPickTag = null
    wait(pause * 0.4)
    return true
}

private suspend fun TaskScope.tab(t: String) {
    if (!AiUiBridge.stationOpenFn()) return
    if (GS.G?.tab != t) {
        clickUI("tab_$t", true, 0.5) {
            AiUiBridge.selectTabFn(t)
        }
    }
}

private suspend fun TaskScope.closePanel() {
    if (AiUiBridge.stationOpenFn()) {
        clickUI("station_close", true, 0.4) {
            AiUiBridge.closeStationFn()
        }
    }
}

private fun fleet(cls: String): Ship? = GS.G?.fleet?.firstOrNull { it.cls == cls && !it.dead }
private fun onSurface(o: Targetable?): Boolean = o != null && tel(o).alt < 300.0
private fun onSurfaceSuit(): Boolean {
    val ev = GS.ev ?: return false
    return tel(ev).alt < 300.0
}

private fun ctxK(): String = GS.PC.ctx.k

private fun rmPos(st: Body, kind: String): Vec2? {
    val rm = st.rooms.firstOrNull { it.kind == kind } ?: return null
    return cw(st, rm.x + rm.w / 2.0 - 0.5, rm.y + rm.h / 2.0 - 0.5)
}

private fun hareP(): Int = CLS["hare"]?.price ?: 6500
private fun wrenP(): Int = CLS["wren"]?.price ?: 2400

private fun fuelStop(): Body {
    val pl = GS.pl
    val W = GS.W
    if (pl != null && pl.cls == "hare" && onSurface(pl)) return SITES.depot
    var best = SITES.sel
    var bd = 1e9
    if (W != null && pl != null) {
        for (s in W.stations) {
            if (s.econ == null || s.surf || s.gone || s.leaving || s.transfer != null || s.ports.isEmpty()) continue
            val d = hy(s.x - pl.x, s.y - pl.y)
            if (d < bd) {
                bd = d
                best = s
            }
        }
    }
    return best
}

private fun reserve(): Double {
    val pl = GS.pl ?: return 20.0
    val st = fuelStop()
    val k = pl.burn / max(0.1, pl.thrust)
    val off = if (onSurface(pl)) 0.0 else abs(hy(pl.x, pl.y) - C.RS) * 0.05
    return max(pl.fuelMax * 0.22, pl.fuelMax * 0.06 + 6.0 + k * 40.0 + hy(st.x - pl.x, st.y - pl.y) * 0.012 + off)
}

// ─── EVA acts ────────────────────────────────────────────────────────────
private suspend fun TaskScope.walkTo(x: Double, y: Double, tol: Double = 3.0, secs: Double = 150.0, ref: Targetable? = null): Boolean {
    var path: EvaPath? = null
    var i = 0
    var rt = 0.0
    var best = 1e9
    var bestT = 0.0
    var ax = GS.ev?.x ?: 0.0
    var ay = GS.ev?.y ?: 0.0
    var aT = 0.0
    var kick = 0.0
    var kicks = 0
    var kx = 0.0
    var ky = 0.0
    val x0 = GS.ev?.x ?: 0.0
    val y0 = GS.ev?.y ?: 0.0
    var t = 0.0
    while (t < secs) {
        val ev = GS.ev
        if (ev == null || ev.hide) return false
        val d = hy(x - ev.x, y - ev.y)
        if (d < tol) return true
        if (d < best - 1.0) {
            best = d
            bestT = t
        }
        if (hy(ev.x - ax, ev.y - ay) > 0.5) {
            ax = ev.x
            ay = ev.y
            aT = t
        } else if (t - aT > 4.0 && kick <= 0.0 && d > tol + 6.0 && kicks < 3) {
            kicks++
            var bd = -1.0
            for (k in 0 until 16) {
                val a = (k / 16.0) * TAU
                val ux = cos(a)
                val uy = sin(a)
                val h = ray(ev.x, ev.y, ux, uy, 20.0)
                val f = (h?.d ?: 20.0) + ((ux * (x - ev.x) + uy * (y - ev.y)) / max(0.001, d)) * 3.0
                if (f > bd) {
                    bd = f
                    kx = ux
                    ky = uy
                }
            }
            kick = 0.45
            aT = t
            path = null
            aiLog("suit pinned — kicking free")
            if (kicks == 3 && hy(ev.x - x0, ev.y - y0) < 0.05) {
                aiLog("suit stalled at (${ev.x.roundToInt()}, ${ev.y.roundToInt()})")
            }
        }
        if (kick > 0.0) {
            kick -= NAV.dt
            if (hy(ev.vx, ev.vy) < 5.0) stickW(kx, ky)
            yieldFrame()
            t += NAV.dt
            continue
        }
        rt -= NAV.dt
        if (rt <= 0.0 || path == null) {
            path = evaRoute(x, y)
            i = 0
            rt = 1.6
        }
        val pts = path.points
        while (i < pts.size - 1 && hy(pts[i].x - ev.x, pts[i].y - ev.y) < 2.6) {
            i++
            if (path.hop) rt = 0.0
        }
        val pt = pts[i]
        jetTo(pt.x, pt.y, if (i == pts.size - 1) 7.0 else 9.0, ref)
        if (t - bestT > 25.0) return false
        yieldFrame()
        t += NAV.dt
    }
    return false
}

private suspend fun TaskScope.useRoom(st: Body, kind: String, tabName: String?): Boolean {
    val p = rmPos(st, kind) ?: return false
    walkTo(p.x, p.y, 4.0)
    until({ GS.PC.ctx.f != null && (ctxK() == "crew" || ctxK() == "terminal") }, 6.0) {
        jetTo(p.x, p.y, 3.0)
    }
    if (GS.PC.ctx.f == null) return false
    press("ctx")
    until({ AiUiBridge.stationOpenFn() }, 2.0)
    if (tabName != null) tab(tabName)
    return AiUiBridge.stationOpenFn()
}

private suspend fun TaskScope.rideLift(top: Boolean): Boolean {
    val st = if (top) SITES.lift else SITES.sel
    val p = rmPos(st, "lift") ?: return false
    act(if (top) "UP THE CABLE" else "DOWN THE CABLE")
    say(
        if (top) "Walking to the lift hall — the cable runs 1.4 km straight up to SELENE HIGH."
        else "Back to the lift dock. Going down the well."
    )
    if (!walkTo(p.x, p.y, 5.0)) return false
    say("Waiting for the car…")
    until({ ctxK() == "lift" }, 120.0) { jetTo(p.x, p.y, 3.0) }
    if (ctxK() != "lift") return false
    wait(0.4)
    press("ctx")
    until({ GS.G?.ride != null }, 2.0)
    say(
        if (top) "Riding up. The Rim drops away; the band of parked rocks fills the sky."
        else "Riding down. Shackleton's rim rises to meet the car."
    )
    until({ GS.G?.ride == null }, 120.0) { AP.wantWarp = true }
    return true
}

private data class OreCellPick(
    val cx: Int,
    val cy: Int,
    val x: Double,
    val y: Double,
    val m: Int,
    val sx: Double,
    val sy: Double,
    val key: Long
)

private val ORE_DIRS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

private fun oreCell(b: Body, nx: Double, ny: Double, R: Double = 60.0, gold: Boolean = false): OreCellPick? {
    fun open(x: Int, y: Int) = x < 0 || y < 0 || x >= b.gw || y >= b.gh || b.sol[y * b.gw + x] == 0
    val c0 = cos(b.a)
    val s0 = sin(b.a)
    var best: OreCellPick? = null
    var bc = 1e9
    for (cy in 0 until b.gh) {
        for (cx in 0 until b.gw) {
            val i = cy * b.gw + cx
            val m = b.sol[i]
            val cellKey = b.uid.toLong() * 100000L + i.toLong()
            if (m == 0 || m == 7 || AP.badCells.contains(cellKey)) continue
            var odx = 0
            var ody = 0
            var foundOd = false
            for ((ox, oy) in ORE_DIRS) {
                if (open(cx + ox, cy + oy) && open(cx + ox * 2, cy + oy * 2) && open(cx + ox * 3, cy + oy * 3)) {
                    odx = ox
                    ody = oy
                    foundOd = true
                    break
                }
            }
            if (!foundOd) continue
            val w = cw(b, cx, cy)
            val d = hy(w.x - nx, w.y - ny)
            if (d > R) continue
            val matOre = MAT.getOrNull(m)?.ore
            val ore = if (matOre != null && matOre != "parts") GD[matOre]?.p?.toDouble() ?: 1.5 else 1.5
            val cost = d - ore * (if (gold) 1.8 else 0.9)
            if (cost < bc) {
                bc = cost
                val dx = odx * c0 - ody * s0
                val dy = odx * s0 + ody * c0
                val validOre = if (matOre != null && matOre != "parts") m else 0
                best = OreCellPick(
                    cx = cx, cy = cy, x = w.x, y = w.y, m = validOre,
                    sx = w.x + dx * b.cs * 2.6, sy = w.y + dy * b.cs * 2.6,
                    key = cellKey
                )
            }
        }
    }
    return best
}

private suspend fun TaskScope.evaCut(site: Body, home: Ship, stop: () -> Boolean) {
    val G = GS.G ?: return
    val sackCap = floor(C.EVA.sack * (if (G.sackUp > 0.0) G.sackUp else 1.0)).toInt()
    for (k in 0 until 40) {
        if (stop()) break
        val tgt = oreCell(site, home.x, home.y, 70.0)
        if (tgt == null) {
            wait(1.0)
            continue
        }
        val oreName = if (tgt.m != 0) GD[MAT[tgt.m]?.ore]?.n ?: "rock" else "rock"
        say("Cutting $oreName — the hand cutter is slow, but the sack fills itself.", "SACK ${sackN(GS.ev)}/$sackCap")
        if (!walkTo(tgt.sx, tgt.sy, 3.0, 25.0, site)) {
            AP.badCells.add(tgt.key)
            continue
        }
        var t = 0.0
        while (t < 14.0 && !stop()) {
            val ev = GS.ev ?: break
            jetTo(tgt.sx, tgt.sy, 2.0, site)
            val e = evaFace(atan2(tgt.y - ev.y, tgt.x - ev.x))
            inp.mine = e < 0.5
            if (site.sol[tgt.cy * site.gw + tgt.cx] == 0 && t > 0.5) break
            AP.sub = "SACK ${sackN(ev)}/$sackCap · air ${ev.suit.roundToInt()}%"
            yieldFrame()
            t += NAV.dt
        }
    }
}

private suspend fun TaskScope.stowAt(s: Ship) {
    say("Back to MULE-3 to stow the sack in the crew tally.")
    walkTo(s.x, s.y, 9.0, 40.0, s)
}

private suspend fun TaskScope.intakePaperwork(): Boolean {
    val G = GS.G ?: return false
    act("INTAKE")
    say("Disembarking PSV MERIDIAN — crossing east into SELENE HIGH Intake Office.")
    if (!useRoom(SITES.sel, "intake", "intake")) return false
    wait(0.8)
    if (!G.docWaiver) {
        clickUI("intake_waiver_check", true, 0.5) { AiUiBridge.panelActFn("intake_toggle", null, "waiver") }
    }
    if (!G.docTelemetry) {
        clickUI("intake_telemetry_check", true, 0.5) { AiUiBridge.panelActFn("intake_toggle", null, "telemetry") }
    }
    if (!G.docZone) {
        clickUI("intake_zone_check", true, 0.5) { AiUiBridge.panelActFn("intake_toggle", null, "zone") }
    }
    if (!G.paperworkDone && G.docWaiver && G.docTelemetry && G.docZone) {
        say("Filing Consortium Intake ECC-2035 and drawing Room 14B keycard.")
        clickUI("intake_submit_button", true, 0.8) { AiUiBridge.panelActFn("intake_submit", null, null) }
    }
    wait(0.5)
    closePanel()
    return G.paperworkDone
}

private suspend fun TaskScope.claimBunk14B(): Boolean {
    val G = GS.G ?: return false
    act("ROOM 14B")
    say("Walking west to HAB 14 · Shared Room 14B to claim Consortium bunk.")
    if (!useRoom(SITES.hab, "home", "home")) return false
    wait(0.7)
    if (G.paperworkDone && !G.bunkClaimed) {
        clickUI("bunk_claim_button", true, 0.8) { AiUiBridge.panelActFn("bunk_claim", null, null) }
    }
    wait(0.5)
    closePanel()
    return G.bunkClaimed
}

private suspend fun TaskScope.crewShift() {
    val G = GS.G ?: return
    val W = GS.W ?: return
    val c = G.crew ?: return
    act("CREW SHIFT")
    if (c.state == "idle") {
        say("Crew office. Manager Liu Mei runs EVA crews out to uncatalogued rocks.")
        if (!useRoom(SITES.sel, "crew", "crew")) return
        wait(1.2)
        val canBoard = c.state == "idle" && GS.ev != null && !GS.ev!!.hide && G.paperworkDone && G.bunkClaimed
        val ok = clickUI("shift_btn", canBoard, 0.9) {
            AiUiBridge.panelActFn("shift", null, null)
        }
        if (!ok) {
            closePanel()
            return
        }
        say("Aboard MULE-3 with two bunkmates. Liu flies; we ride.")
    }
    until({ c.state == "site" || c.state == "idle" }, 160.0) { AP.wantWarp = true }
    if (c.state != "site") return
    val site = c.site ?: return
    val s = c.ship
    wait(0.8)
    if (site.cat == 0) {
        say("Hand scanner ping — ${site.name} is not in any catalog. First survey pays.")
        press("scan")
        wait(1.2)
    }
    val ev0 = GS.ev
    val nb = if (ev0 != null) {
        W.rocks.firstOrNull { b -> b.cat == 0 && !b.hidden && b !== site && hy(b.x - ev0.x, b.y - ev0.y) - b.rad < 220.0 }
    } else null
    if (nb != null && c.t > 140.0 && ev0 != null) {
        say("Another uncatalogued body ${(hy(nb.x - ev0.x, nb.y - ev0.y) - nb.rad).roundToInt()} m off — jetting over to ping it.")
        val p = standoff(nb, ev0.x, ev0.y, 30.0)
        walkTo(p.x, p.y, 8.0, 50.0)
        press("scan")
        wait(1.0)
    }
    fun full(): Boolean {
        val ev = GS.ev ?: return true
        return sackN(ev) >= floor(C.EVA.sack * (if (G.sackUp > 0.0) G.sackUp else 1.0)).toInt() - 1
    }
    fun storm(): Boolean {
        val fl = W.flare
        return fl != null && (fl.on || fl.warn > 0.0)
    }
    fun airLow(): Boolean {
        val ev = GS.ev ?: return true
        val airSeconds = ev.suit * C.EVA.air * (if (G.airUp > 0.0) G.airUp else 1.0) / 100.0 / (if (storm()) 3.5 else 1.0)
        return airSeconds < hy(ev.x - s.x, ev.y - s.y) / 4.0 + 30.0
    }
    while (c.state == "site" && !c.horn && GS.ev != null && !GS.ev!!.hide) {
        evaCut(site, s) {
            val ev = GS.ev
            full() || c.horn || airLow() || storm() || (ev != null && hy(ev.x - s.x, ev.y - s.y) > 160.0)
        }
        val ev = GS.ev
        if (ev == null || ev.hide) break
        if (hy(ev.x - s.x, ev.y - s.y) > 160.0) {
            aiLog("drifted ${hy(ev.x - s.x, ev.y - s.y).roundToInt()} m from MULE-3 — jetting back")
            walkTo(s.x, s.y, 9.0, 90.0, s)
        }
        if (storm()) {
            say("Proton storm — sheltering at the MULE until it passes.")
            val evStorm = GS.ev
            if (evStorm != null && hy(evStorm.x - s.x, evStorm.y - s.y) > 9.0) {
                walkTo(s.x, s.y, 8.0, 40.0, s)
            }
            until({ !storm() || c.horn || GS.ev == null || GS.ev!!.hide }, 240.0) {
                jetTo(s.x, s.y, 2.0, s)
            }
            continue
        }
        stowAt(s)
        val evAfter = GS.ev
        if (evAfter != null && !evAfter.hide && evAfter.suit < 60.0) {
            say("Air low — topping up at the MULE.")
            if (hy(evAfter.x - s.x, evAfter.y - s.y) > 10.0) walkTo(s.x, s.y, 8.0, 40.0, s)
            until({ (GS.ev?.suit ?: 100.0) > 95.0 || c.horn }, 8.0) { jetTo(s.x, s.y, 2.0, s) }
        }
    }
    say("SHIFT HORN. Back to the hatch before Liu reels us in.")
    val evEnd = GS.ev
    if (evEnd != null && !evEnd.hide) {
        walkTo(s.x, s.y, 9.0, 60.0, s)
        until({ ctxK() == "crewboard" }, 10.0) { jetTo(s.x, s.y, 2.0, s) }
        press("ctx")
    }
    say("Heading home. The tally gets split at the office.")
    until({ c.state == "idle" }, 200.0) { AP.wantWarp = true }
    wait(1.0)
}

private suspend fun TaskScope.catalogOffice() {
    val G = GS.G ?: return
    act("CATALOG OFFICE")
    say("Catalog Office: survey bounties${if (G.bounty > 0) " (${fm(G.bounty)}₡ pending)" else ""}.")
    if (!useRoom(SITES.sel, "catalog", "catalog")) return
    wait(0.8)
    clickUI("bounty_btn", G.bounty > 0) { AiUiBridge.panelActFn("bounty", null, null) }
    val coresHave = hold()?.c?.get("core") ?: 0
    clickUI("cores_btn", coresHave > 0) { AiUiBridge.panelActFn("cores", null, null) }
    if (at("file")) {
        say("Filing the Orphan claim. In our name — with the KERES-9 logs attached.")
        clickUI("file_btn", true, 1.2) { AiUiBridge.panelActFn("file", null, null) }
    }
    wait(0.8)
    closePanel()
}

private suspend fun TaskScope.buyShipAt(st: Body, cls: String): Boolean {
    val G = GS.G ?: return false
    act("SHIPYARD")
    say(
        if (cls == "wren") "Enough saved. A WREN tug: slow, small, ours."
        else "The big one: a HARE surface lander. Pad to orbit and back down."
    )
    if (!useRoom(st, "yard", "yard")) return false
    wait(1.0)
    val item = yard(st).firstOrNull { it.cls == cls }
    val ok = clickUI("buyship_$cls", item != null && !item.owned && G.cr >= item.c, 1.0) {
        AiUiBridge.panelActFn("buyship", null, cls)
    }
    wait(1.0)
    closePanel()
    return ok
}

private suspend fun TaskScope.boardOwn(s: Ship): Boolean {
    act("BOARDING")
    say("Out to the ${s.name}.")
    fun ready(): Boolean = ctxK() == "board" && boardable() === s
    val walkTask = FrameTask("walkBoard") { walkTo(s.x, s.y, 8.0, 90.0) }
    while (!ready()) {
        if (walkTask.step()) break
        yieldFrame()
    }
    walkTask.cancel()
    until(::ready, 15.0) { jetTo(s.x, s.y, 3.0) }
    if (!ready()) {
        val distStr = GS.ev?.let { hy(it.x - s.x, it.y - s.y).roundToInt().toString() } ?: "?"
        aiLog("could not reach the ${s.name} ($distStr m, ${s.dock?.ty ?: "loose"})")
        return false
    }
    press("ctx")
    until({ GS.pl === s }, 2.0)
    return GS.pl === s
}

private suspend fun TaskScope.sellSack(st: Body) {
    val ev = GS.ev ?: return
    if (sackN(ev) <= 0) return
    if (!useRoom(st, "market", "market")) return
    sellAll(null)
    closePanel()
}

// ─── ship acts ───────────────────────────────────────────────────────────
private suspend fun TaskScope.undockShip() {
    val pl = GS.pl ?: return
    val d = pl.dock ?: return
    closePanel()
    if ((d.ty == "pad" && pl.tailLand) || d.ty == "ground") {
        if (d.ty == "pad" && d.b != null && !d.b.surf) {
            act("UNDOCK")
            say("Easing the HARE off the ${d.b.name} pad.")
            until({ GS.PC.thrArm }, 1.5) { inp.thr = 0.0 }
            until({ pl.dock == null }, 3.0) { inp.thr = 0.3 }
            val px = pl.x
            val py = pl.y
            until({ hy(pl.x - px, pl.y - py) > 30.0 }, 6.0) { inp.thr = 0.25 }
            inp.thr = 0.0
        }
        return
    }
    until({ val k = GS.PC.alt?.k; k == "undock" || k == "release" }, 2.0)
    press("alt")
    until({ pl.dock == null }, 2.0)
    val px = pl.x
    val py = pl.y
    val a = pl.a + PI
    until({ hy(pl.x - px, pl.y - py) > 25.0 }, 8.0) {
        fly(px + cos(a) * 30.0, py + sin(a) * 30.0, vmax = 6.0, rcsOnly = true, hold = true)
    }
}

private suspend fun TaskScope.flyTo(
    x: Double,
    y: Double,
    tol: Double = 8.0,
    secs: Double = 200.0,
    vmax: Double = 70.0,
    rv: Double = 1.5,
    noWarp: Boolean = false,
    skipFn: (() -> Body?)? = null
): Boolean {
    var best = 1e9
    var bestT = 0.0
    var t = 0.0
    while (t < secs) {
        val pl = GS.pl ?: return false
        val tt = flyRoute(x, y, vmax = vmax, skip = skipFn?.invoke())
        if (!tt.wp && tt.d < tol && tt.rv < rv) return true
        val dd = hy(x - pl.x, y - pl.y)
        if (dd < best - 2.0) {
            best = dd
            bestT = t
        } else if (dd > max(40.0, tol * 1.5) && ((t - bestT > 12.0 && hy(pl.vx, pl.vy) < 1.5) || t - bestT > 35.0)) {
            aiLog("flyTo stalled ${dd.roundToInt()} m short — backing out the open way and abandoning that leg.")
            var ba = 0.0
            var bf = -1.0
            for (k in 0 until 24) {
                val a = (k / 24.0) * TAU
                val h = ray(pl.x, pl.y, cos(a), sin(a), 80.0)
                val f = h?.d ?: 80.0
                if (f > bf) {
                    bf = f
                    ba = a
                }
            }
            val ox = pl.x + cos(ba) * 35.0
            val oy = pl.y + sin(ba) * 35.0
            until({ hy(ox - pl.x, oy - pl.y) < 6.0 }, 10.0) {
                fly(ox, oy, vmax = 5.0, stop = 1.0)
            }
            return false
        }
        if (tt.d > 300.0 && !noWarp) AP.wantWarp = true
        val pct = ((pl.fuel / max(1.0, pl.fuelMax)) * 100.0).roundToInt()
        AP.sub = "${tt.mode} · ${tt.d.roundToInt()} m · ${"%.1f".format(hy(pl.vx, pl.vy))} m/s · fuel $pct%"
        yieldFrame()
        t += NAV.dt
    }
    return false
}

private suspend fun TaskScope.dockAt(st: Body, ty: String = "port"): Boolean {
    val pl = GS.pl ?: return false
    val W = GS.W ?: return false
    fun pkPort(idx: Int) = "${st.name}#$idx"
    fun isBadPort(idx: Int) = W.time - (AP.badPort[pkPort(idx)] ?: -1e9) < 600.0

    if (ty == "pad") {
        val pads = st.pads.filter { it.occ == null }.sortedBy { p ->
            val w = portW(st, p)
            hy(w.x - pl.x, w.y - pl.y)
        }
        if (pads.isEmpty()) return false
        val p = pads[0]
        val w0 = portW(st, p)
        act("LANDING")
        say("Final approach to ${st.name}.")
        val nx = cos(w0.dir)
        val ny = sin(w0.dir)
        if (!pl.gear) press("gear")
        val hx = w0.x + nx * 140.0
        val hy0 = w0.y + ny * 140.0
        flyTo(hx, hy0, tol = 25.0, vmax = 40.0, rv = 4.0) {
            if (hy(hx - pl.x, hy0 - pl.y) < st.rad + 160.0) st else null
        }
        say("Hover. Nose up, gear down, settle on the plume.")
        var t = 0.0
        while (t < 120.0) {
            if (pl.dock != null && pl.dock?.ty != "ground") break
            val r = approachPad(st, p)
            AP.sub = "TOUCHDOWN · ${"%.1f".format(r.h)} m · lateral ${"%.1f".format(r.lat)} · ${"%.1f".format(hy(pl.vx, pl.vy))} m/s"
            yieldFrame()
            t += NAV.dt
        }
        return pl.dock != null && pl.dock?.ty == "pad"
    }

    var ports = st.ports.mapIndexedNotNull { idx, port ->
        if (port.occ != null || (st === SITES.sel && idx == 2)) null else idx to port
    }
    if (ports.any { !isBadPort(it.first) }) {
        ports = ports.filter { !isBadPort(it.first) }
    }
    if (ports.isEmpty()) return false
    ports = ports.sortedBy { (_, p) ->
        val w = portW(st, p)
        hy(w.x - pl.x, w.y - pl.y)
    }
    val (pIdx, p) = ports[0]
    val w0 = portW(st, p)
    act("DOCKING")
    say("Lining up on a berth at ${st.name}.")
    val nx = cos(w0.dir)
    val ny = sin(w0.dir)
    val ax0 = w0.x + nx * (45.0 + pl.nose)
    val ay0 = w0.y + ny * (45.0 + pl.nose)
    flyTo(ax0, ay0, tol = 10.0, vmax = 35.0, rv = 2.0) {
        if (hy(ax0 - pl.x, ay0 - pl.y) < 70.0) st else null
    }
    var bestA = 1e9
    var bestT = 0.0
    var t = 0.0
    while (t < 90.0) {
        if (pl.dock != null) break
        val r = approachPort(st, p)
        if (r.along < bestA - 0.5) {
            bestA = r.along
            bestT = t
        } else if (t - bestT > 15.0 && r.along > 4.0) {
            aiLog("Berth ${pkPort(pIdx)} approach hung at ${"%.1f".format(r.along)} m — trying another.")
            break
        }
        val g = GS.PC.assist
        AP.sub = "CLAMP · ${"%.1f".format(r.along)} m · lat ${"%.1f".format(r.lat)} · ${(r.ae * 57.0).roundToInt()}° · ${"%.2f".format(hy(pl.vx, pl.vy))} m/s"
        if (g != null && g.pPort === p && g.ok && ctxK() == "clamp") {
            press("ctx")
            yieldFrame()
        }
        yieldFrame()
        t += NAV.dt
    }
    if (pl.dock == null) AP.badPort[pkPort(pIdx)] = W.time
    return pl.dock != null
}

private suspend fun TaskScope.harpoon(b: Body, from: Vec2? = null, aim: Vec2? = null): Boolean {
    val pl = GS.pl ?: return false
    if (b.dead) return false
    act("HARPOON")
    val fx = from?.x ?: pl.x
    val fy = from?.y ?: pl.y
    fun faceA(): Double {
        if (aim == null) return atan2(b.y - pl.y, b.x - pl.x)
        val m = alive(pl, "mine").firstOrNull()
        val o = if (m != null) mw(pl, m) else Vec2(pl.x, pl.y)
        return atan2(aim.y - o.y, aim.x - o.x)
    }
    val so = standoff(b, fx, fy, pl.rad + 6.0)
    say("Matching ${b.name} and harpooning on.")
    if (from != null && hy(from.x - pl.x, from.y - pl.y) > 40.0) {
        val R = b.rad + 40.0
        val aT = atan2(from.y - b.y, from.x - b.x)
        if (hy(pl.x - b.x, pl.y - b.y) > b.rad + 110.0) {
            flyTo(b.x + cos(aT) * R, b.y + sin(aT) * R, tol = 25.0, vmax = 35.0, rv = 4.0)
        }
        var k = 0
        while (k < 12 && hy(pl.x - b.x, pl.y - b.y) < b.rad + 110.0) {
            val aC = atan2(pl.y - b.y, pl.x - b.x)
            val dA = ad(aT, aC)
            if (abs(dA) < 0.55) break
            val a = aC + (if (dA < 0.0) -1.0 else 1.0) * 0.5
            if (!flyTo(b.x + cos(a) * R, b.y + sin(a) * R, tol = 20.0, vmax = 14.0, rv = 5.0)) break
            k++
        }
        if (hy(from.x - pl.x, from.y - pl.y) > 40.0) {
            val distFrom = hy(from.x - pl.x, from.y - pl.y)
            flyTo(from.x, from.y, tol = 25.0, vmax = if (distFrom > 300.0) 35.0 else 18.0, rv = 4.0) {
                if (hy(pl.x - b.x, pl.y - b.y) < b.rad + 90.0) b else null
            }
        }
    }
    flyTo(so.x, so.y, tol = 30.0, vmax = 30.0, rv = 3.0) {
        if (hy(so.x - pl.x, so.y - pl.y) < 70.0) b else null
    }
    var t = 0.0
    while (t < 45.0) {
        if (pl.dock != null) return true
        var so2 = standoff(b, fx, fy, pl.rad + (if (t > 15.0) 1.0 else 4.0))
        if (t > 6.0 && ctxK() != "anchor") {
            val dx = b.x - pl.x
            val dy = b.y - pl.y
            val d = hy(dx, dy).let { if (it == 0.0) 1.0 else it }
            val h = ray(pl.x, pl.y, dx / d, dy / d, d)
            if (h != null && h.b === b) {
                so2 = Vec2(h.x - (dx / d) * (pl.rad + 2.0), h.y - (dy / d) * (pl.rad + 2.0))
            }
        }
        val tt = fly(so2.x, so2.y, vmax = 3.0, stop = 1.0, face = faceA(), rcsOnly = true, tvx = b.vx, tvy = b.vy)
        AP.sub = "HARPOON · ${tt.d.roundToInt()} m · ${"%.1f".format(tt.rv)} m/s · ${if (GS.PC.ctx.ok) "READY" else "closing"}"
        val angTol = if (aim != null) (if (t > 20.0) 0.18 else 0.12) else (if (t > 10.0) 0.6 else 0.2)
        if (ctxK() == "anchor" && GS.PC.ctx.ok && tt.rv < 1.5 && abs(ad(faceA(), pl.a)) < angTol) {
            press("ctx")
            yieldFrame()
        }
        yieldFrame()
        t += NAV.dt
    }
    return pl.dock != null
}

private suspend fun TaskScope.mineHere(stop: (() -> Boolean)? = null) {
    val pl = GS.pl ?: return
    act("MINING")
    if (GS.PC.scan) {
        press("scan")
        yieldFrame()
    }
    var miss = 0.0
    var last = cmass(pl)
    var idle = 0.0
    var sT = 9.0
    var slewTo: Double? = null
    var t = 0.0
    while (t < 120.0) {
        val d = pl.dock
        if (d == null || d.ty != "anchor") {
            AP.mineExit = "sheared"
            return
        }
        if (cmass(pl) >= pl.cargoMax - 1 || (stop != null && stop())) {
            AP.mineExit = "full"
            return
        }
        sT += NAV.dt
        if (GS.PC.scan && sT > 0.5) {
            press("scan")
            sT = 0.0
        }
        inp.mine = true
        miss = if (pl.beam?.hit == true) 0.0 else miss + NAV.dt
        if (miss == 0.0) slewTo = null
        if (miss > 0.6 && slewTo != null) {
            val er = ad(slewTo, pl.a)
            inp.rx = if (abs(er) < 0.02) 0.0 else (if (er < 0.0) -1.0 else 1.0) * cl(abs(er) * 3.0, 0.45, 1.0)
            if (abs(er) < 0.02 && miss > 3.0) slewTo = null
            if (miss > 10.0) {
                AP.mineExit = "miss8"
                return
            }
        } else if (miss > 0.6) {
            val b = d.b ?: return
            val m0 = alive(pl, "mine").firstOrNull()
            val o = if (m0 != null) mw(pl, m0) else Vec2(pl.x, pl.y)
            var ba: Double? = null
            var bd = 9.0
            for (k in -12..12) {
                if (abs(k * 0.0625) > 0.75) continue
                val a = b.a + (d.ao0 ?: 0.0) + k * 0.0625
                val h = ray(o.x, o.y, cos(a), sin(a), pl.lasR)
                val e = abs(ad(a, pl.a))
                if (h != null && h.b === b && e > 0.07 && e < bd) {
                    bd = e
                    ba = a
                }
            }
            if (ba == null) {
                AP.mineExit = "miss"
                return
            }
            slewTo = ba
            miss = 0.61
        }
        val m = cmass(pl)
        if (m > last) {
            last = m
            idle = 0.0
        } else {
            idle += NAV.dt
        }
        if (idle > 25.0) {
            AP.mineExit = "idle"
            return
        }
        val pct = ((pl.fuel / max(1.0, pl.fuelMax)) * 100.0).roundToInt()
        AP.sub = "LASER · hold $m/${pl.cargoMax} · fuel $pct%"
        yieldFrame()
        t += NAV.dt
    }
}

private fun oreVal(b: Body): Double {
    var v = 0.0
    for ((g, q) in b.comp) {
        v += q * (GD[g]?.p ?: 0.0)
    }
    return v / max(1, b.cells).toDouble()
}

private fun pickRock(want: String?): Body? {
    val pl = GS.pl ?: return null
    val W = GS.W ?: return null
    var best: Body? = null
    var bc = -1e9
    for (b in W.rocks) {
        if (b.dead || b.hidden || b.inbound || b.kin || b.cells < 120) continue
        if (abs(b.va) * b.rad > 1.2) continue
        if (W.time - (AP.noBiteT[b] ?: -1e9) < 400.0) continue
        val d = hy(b.x - pl.x, b.y - pl.y)
        val score = if (want != null) (((b.comp[want] ?: 0).toDouble() / b.cells) * 100.0) else oreVal(b)
        val c = score * 10.0 - d / 25.0 - (AP.mined[b] ?: 0) * 6.0 - (AP.noBite[b] ?: 0) * 40.0
        if (c > bc) {
            bc = c
            best = b
        }
    }
    return best
}

private suspend fun TaskScope.mineRun(want: String?, until0: (() -> Boolean)? = null) {
    val pl = GS.pl ?: return
    val W = GS.W ?: return
    val b = pickRock(want) ?: return
    act("PROSPECTING")
    val catInfo = if (b.cat != 0) " (${b.typeN})" else " — uncatalogued, array on to survey it en route"
    val wantInfo = if (want != null) " for ${GD[want]?.n ?: want}" else ""
    say("Heading for ${b.name}$catInfo$wantInfo.")
    if (b.cat == 0 && !GS.PC.scan) press("scan")
    if (!harpoon(b)) {
        if (GS.PC.scan) press("scan")
        AP.mined[b] = (AP.mined[b] ?: 0) + 2
        AP.noBite[b] = (AP.noBite[b] ?: 0) + 1
        AP.noBiteT[b] = W.time
        aiLog("${b.name} refused the harpoon — marking it.")
        return
    }
    if (GS.PC.scan) press("scan")
    for (k in 0 until 6) {
        mineHere(until0)
        AP.mined[b] = (AP.mined[b] ?: 0) + 1
        if (cmass(pl) >= pl.cargoMax - 1 || (until0 != null && until0()) || pl.fuel < max(pl.fuelMax * 0.2, reserve() * 1.4)) break
        undockShip()
        val a = atan2(pl.y - b.y, pl.x - b.x) + 0.6
        val p = Vec2(b.x + cos(a) * (b.rad + 30.0), b.y + sin(a) * (b.rad + 30.0))
        if (!harpoon(b, p)) break
    }
    undockShip()
}

private suspend fun TaskScope.sellAll(keep: String? = null) {
    val st = GS.G?.panel ?: return
    val econ = st.econ ?: return
    val G = GS.G ?: return
    tab("market")
    clickUI("qty_MAX", true, 0.4) { AiUiBridge.panelActFn("qty", null, "MAX") }
    for (k in 0 until 8) {
        val h = hold() ?: break
        val goodToSell = econ.keys.firstOrNull { g ->
            GD[g]?.story != true && (h.c[g] ?: 0) > 0 && g != keep && !(g == "core" && G.cores < 3)
        } ?: break
        clickUI("sell_$goodToSell", true, 0.5) {
            AiUiBridge.panelActFn("sell", goodToSell, null)
        }
    }
}

private suspend fun TaskScope.service(st: Body) {
    val G = GS.G ?: return
    tab("service")
    for (lab in listOf("REFUEL", "REPAIR")) {
        G.svc = services(st)
        val idx = G.svc.indexOfFirst { it.l.startsWith(lab) && it.ok && G.cr >= (if (it.min > 0) it.min else it.c) }
        if (idx >= 0) {
            clickUI("svc_$idx", true, 0.5) { AiUiBridge.panelActFn("svc", null, idx.toString()) }
        }
    }
    val pl = GS.pl
    val saving = if (pl != null && pl.cls == "wren" && past("shards") && fleet("hare") == null) hareP() + 200 else 0
    G.svc = services(st)
    val upgRe = Regex("HOLD|CARGO|ENGINE|LASER", RegexOption.IGNORE_CASE)
    val u = G.svc.indexOfFirst { v ->
        upgRe.containsMatchIn(v.l) && v.ok &&
            G.cr >= (if (v.min > 0) v.min else v.c) &&
            G.cr > v.c * 3 + 600 &&
            G.cr - v.c > saving
    }
    if (u >= 0) {
        say("Flush — buying a refit: ${G.svc[u].l}.")
        clickUI("svc_$u", true, 0.7) { AiUiBridge.panelActFn("svc", null, u.toString()) }
    }
}

private fun jobBody(j: Job): Body? = GS.W?.rocks?.firstOrNull { it.uid == j.body }
private fun jobDest(j: Job): Body? = GS.W?.stations?.firstOrNull { it.name == j.to }

private fun jobOK(j: Job): Boolean {
    val pl = GS.pl ?: return false
    val W = GS.W ?: return false
    if (j.kind == "survey") {
        val b = jobBody(j)
        if (b == null || b.cat != 0 || b.hidden || b.dead) return false
        return W.stations.any { q ->
            q.econ != null && !q.surf && !q.gone && q.transfer == null && q.ports.isNotEmpty() &&
                hy(q.x - b.x, q.y - b.y) < 1150.0
        }
    }
    if (j.kind != "deliver" || j.title.startsWith("UP-WELL") || j.g !in listOf("fe", "ice", "vol") || j.n > pl.cargoMax - 4) {
        return false
    }
    val t = jobDest(j)
    return t != null && !t.gone && !t.leaving && (pl.cls == "hare" || !t.surf)
}

private suspend fun TaskScope.takeJobs(st: Body) {
    val G = GS.G ?: return
    if (GS.pl == null || st.econ == null) return
    for (j in G.missions.toList()) {
        if ((!jobOK(j) && !(j.kind == "deliver" && j.got > 0)) || j.aiTry >= 3) {
            dropJob(j.id)
        }
    }
    val L = (G.jobs[st.name] ?: emptyList()).filter(::jobOK).sortedBy { if (it.kind == "survey") 0 else 1 }
    if (L.isEmpty() || G.missions.size >= 3) return
    tab("jobs")
    for (j in L) {
        if (G.missions.size >= 3) break
        say("Contract board: signing ${j.title} (${fm(j.reward)}₡).")
        clickUI("accept_${j.id}", true, 0.5) { AiUiBridge.panelActFn("accept", null, j.id) }
    }
}

private suspend fun TaskScope.surveyRun(b: Body): Boolean {
    val pl = GS.pl ?: return false
    act("SURVEY")
    say("Contract survey: ${b.name}. Array on, nose on the rock.")
    if (!GS.PC.scan) press("scan")
    val d0 = hy(b.x - pl.x, b.y - pl.y).let { if (it == 0.0) 1.0 else it }
    val R = b.rad + 150.0
    val tx = b.x + ((pl.x - b.x) / d0) * R
    val ty = b.y + ((pl.y - b.y) / d0) * R
    var t = 0.0
    while (t < 150.0 && b.cat == 0) {
        if (GS.pl == null || b.dead) return false
        val nearRock = hy(b.x - pl.x, b.y - pl.y) < pl.scanR * 0.8
        val tt = if (nearRock) {
            fly(tx, ty, vmax = 4.0, stop = 1.0, face = atan2(b.y - pl.y, b.x - pl.x))
        } else {
            flyRoute(tx, ty, vmax = 30.0)
        }
        if (tt.d > 300.0) AP.wantWarp = true
        val pct = (min(1.0, b.survey) * 100.0).roundToInt()
        AP.sub = "ARRAY · surveying ${b.name} · ${hy(b.x - pl.x, b.y - pl.y).roundToInt()} m · $pct%"
        yieldFrame()
        t += NAV.dt
    }
    if (GS.PC.scan) press("scan")
    if (b.cat != 0) say("${b.name} catalogued — contract paid.")
    return b.cat != 0
}

private suspend fun TaskScope.officeTab(st: Body) {
    if (st !== SITES.sel) return
    val G = GS.G ?: return
    tab("catalog")
    clickUI("bounty_btn", G.bounty > 0) { AiUiBridge.panelActFn("bounty", null, null) }
    val coresHave = hold()?.c?.get("core") ?: 0
    clickUI("cores_btn", coresHave > 0) { AiUiBridge.panelActFn("cores", null, null) }
    if (at("file")) {
        say("Filing the Orphan claim at the Catalog Office.")
        clickUI("file_btn", true, 1.2) { AiUiBridge.panelActFn("file", null, null) }
    }
}

private suspend fun TaskScope.visit(st: Body, ty: String, what: String? = null): Boolean {
    if (!dockAt(st, ty)) return false
    wait(0.6)
    if (ctxK() == "services") {
        press("ctx")
        until({ AiUiBridge.stationOpenFn() }, 2.0)
    }
    if (!AiUiBridge.stationOpenFn()) return true
    officeTab(st)
    if (what != "nosell") sellAll(if (what == "keepcore") "core" else null)
    service(st)
    takeJobs(st)
    wait(0.5)
    closePanel()
    AP.didHere = true
    return true
}

private fun bestBuyer(): Body? {
    val pl = GS.pl ?: return null
    val W = GS.W ?: return null
    val G = GS.G ?: return null
    val L = W.stations.filter { s ->
        s.econ != null && !s.gone && !s.leaving && (pl.cls == "hare" || !s.surf) && (s.ports.isNotEmpty() || s.pads.isNotEmpty())
    }
    var best: Body? = null
    var bv = -1e9
    for (s in L) {
        var v = 0.0
        for ((g, q) in pl.cargo) {
            if (s.econ?.containsKey(g) == true && GD[g]?.story != true) {
                v += q * price(s, g, true)
            }
        }
        for (j in G.missions) {
            val qty = pl.cargo[j.g] ?: 0
            if (j.kind == "deliver" && j.to == s.name && qty > 0) {
                v += j.reward * min(1.0, qty.toDouble() / max(1, j.n - j.got))
            }
        }
        val d = hy(s.x - pl.x, s.y - pl.y)
        v -= d * 0.2
        if (s.transfer != null) v -= 160.0
        if (d > 900.0 && pl.fuel < pl.fuelMax * 0.45) v -= 5000.0
        if (v > bv) {
            bv = v
            best = s
        }
    }
    return best
}

private data class ShallowSide(val a: Double, val x: Double, val y: Double, val dep: Double, val from: Vec2)

private fun shallowSide(c: Contact, t: Vec2 = Vec2(c.x, c.y)): ShallowSide? {
    val b = c.b
    var best: ShallowSide? = null
    for (k in 0 until 24) {
        val a = (k / 24.0) * TAU
        val ux = cos(a)
        val uy = sin(a)
        val h = ray(b.x + ux * (b.rad + 6.0), b.y + uy * (b.rad + 6.0), -ux, -uy, b.rad + 6.0)
        if (h == null || h.b !== b) continue
        val dep = hy(h.x - t.x, h.y - t.y)
        if (best == null || dep < best.dep) {
            best = ShallowSide(
                a = a, x = h.x, y = h.y, dep = dep,
                from = Vec2(b.x + ux * (b.rad + 60.0), b.y + uy * (b.rad + 60.0))
            )
        }
    }
    return best
}

private fun noseErr(tx: Double, ty: Double, kind: String = "mine"): Double {
    val pl = GS.pl ?: return 0.0
    val m = alive(pl, kind).firstOrNull()
    val o = if (m != null) mw(pl, m) else Vec2(pl.x, pl.y)
    return abs(ad(atan2(ty - o.y, tx - o.x), pl.a))
}

private suspend fun TaskScope.perchFor(
    c: Contact,
    tPos: Vec2 = Vec2(c.x, c.y),
    nearDist: Double = (GS.pl?.lasR ?: C.MINE.rng) - 10.0
): Boolean {
    val pl = GS.pl ?: return false
    val b = c.b
    if (pl.dock != null && pl.dock?.ty == "anchor" && pl.dock?.b === b &&
        hy(tPos.x - pl.x, tPos.y - pl.y) < nearDist && noseErr(tPos.x, tPos.y) < 0.25
    ) {
        return true
    }
    for (k in 0 until 2) {
        if (pl.dock != null) undockShip()
        val sd = shallowSide(c, tPos) ?: return false
        if (!harpoon(b, sd.from, tPos)) return false
        if (noseErr(tPos.x, tPos.y) < 0.3) return true
        aiLog("Perch on ${b.name} came out skewed (${"%.2f".format(noseErr(tPos.x, tPos.y))} rad off) — re-biting.")
    }
    return pl.dock != null && noseErr(tPos.x, tPos.y) < 0.4
}

private fun shardOf(c: Contact): Pick? {
    val W = GS.W ?: return null
    var best: Pick? = null
    var bd = 1e9
    for (p in W.picks) {
        if (p.dead || p.held || p.type != "core") continue
        val d = hy(p.x - c.x, p.y - c.y)
        if ((p.home === c.b || d < c.b.rad + 10.0) && d < bd) {
            bd = d
            best = p
        }
    }
    return best
}

private suspend fun TaskScope.deepScan(c: Contact): Boolean {
    val pl = GS.pl ?: return false
    act("DEEP SCAN")
    say("Array contact inside ${c.b.name}. Perching on the rock and sweeping the dial for its resonance.")
    if (!perchFor(c)) return false
    if (!GS.PC.scan) press("scan")
    var dir = 1.0
    var sweep = true
    var quiet = 0.0
    var t = 0.0
    while (t < 70.0 && !c.done) {
        if (sweep) {
            GS.PC.freq += dir * 22.0 * NAV.dt
            if (GS.PC.freq > 199.0 || GS.PC.freq < 21.0) dir = -dir
            if (GS.PC.sig > 0.01) {
                sweep = false
                quiet = 0.0
            }
        } else {
            val f = contactFreq(c)
            GS.PC.freq += cl(f - GS.PC.freq, -5.0 * NAV.dt, 5.0 * NAV.dt)
            quiet = if (GS.PC.sig > 0.0) 0.0 else quiet + NAV.dt
            if (quiet > 1.5) sweep = true
        }
        GS.PC.freq = cl(GS.PC.freq, 20.0, 200.0)
        AiUiBridge.freqChangedFn(GS.PC.freq)
        AP.sub = "ARRAY · ${"%.1f".format(GS.PC.freq)} kHz · signal ${(GS.PC.sig * 100.0).roundToInt()}% · lock ${(GS.PC.lock * 100.0).roundToInt()}%"
        yieldFrame()
        t += NAV.dt
    }
    if (GS.PC.scan) press("scan")
    if (!c.done) {
        aiLog("Deep scan of ${c.b.name} failed (array off-axis ${"%.2f".format(noseErr(c.x, c.y, "scan"))} rad, ${hy(c.x - pl.x, c.y - pl.y).roundToInt()} m).")
    }
    return c.done
}

private suspend fun TaskScope.cutCore(c: Contact): Boolean {
    val pl = GS.pl ?: return false
    act("CORE SHARD")
    val have = pl.cargo["core"] ?: 0
    fun got(): Boolean = (pl.cargo["core"] ?: 0) > have
    var sh = shardOf(c) ?: return false
    if (sh.home == null) {
        say("The ${c.b.name} shard is drifting free. Flying over to scoop it.")
        if (pl.dock != null) undockShip()
        flyTo(sh.x, sh.y, tol = 5.0, vmax = 20.0, rv = 1.5)
        until(::got, 8.0) { fly(sh.x, sh.y, vmax = 4.0, stop = 1.0) }
        return got()
    }
    say("Cutting a tunnel to the KERES-9 shard inside ${c.b.name}.")
    var tries = 0
    while (tries < 2 && !got()) {
        sh = shardOf(c) ?: sh
        if (!perchFor(c, Vec2(sh.x, sh.y), 66.0)) return false
        var sT = 9.0
        var open = 0.0
        var t = 0.0
        while (t < 120.0 && !got()) {
            if (pl.dock == null) break
            sT += NAV.dt
            if (GS.PC.scan && sT > 0.5) {
                press("scan")
                sT = 0.0
            }
            inp.mine = true
            val m = alive(pl, "mine").firstOrNull()
            val o = if (m != null) mw(pl, m) else Vec2(pl.x, pl.y)
            val er = ad(atan2(sh.y - o.y, sh.x - o.x), pl.a)
            inp.rx = if (abs(er) < 0.012) 0.0 else (if (er < 0.0) -1.0 else 1.0) * cl(abs(er) * 4.0, 0.25, 1.0)
            val d = hy(sh.x - pl.x, sh.y - pl.y)
            val bm = pl.beam
            if (bm != null && !bm.hit && hy(bm.x1 - bm.x0, bm.y1 - bm.y0) > d) {
                open += NAV.dt
            } else {
                open = 0.0
            }
            AP.sub = "LASER → shard · ${d.roundToInt()} m${if (open > 0.0) " · tunnel open" else ""}"
            if (open > 12.0) break
            yieldFrame()
            t += NAV.dt
        }
        inp.mine = false
        inp.rx = 0.0
        until(::got, 6.0)
        if (!got()) undockShip()
        tries++
    }
    if (pl.dock != null && pl.dock?.ty == "anchor") undockShip()
    if (got()) say("Shard aboard. The tractor pulled it out of the tunnel.")
    return got()
}

private suspend fun TaskScope.launch() {
    val pl = GS.pl ?: return
    act("LAUNCH")
    say("PAD TO ORBIT. Throttle up — the HARE stands on its plume.")
    val th = atan2(pl.y, pl.x)
    until({ GS.PC.thrArm }, 1.0) { inp.thr = 0.0 }
    until({ GS.pl == null || GS.pl?.dock == null }, 3.0) {
        val p = GS.pl ?: return@until
        inp.thr = 1.0
        inp.rx = cl(ad(atan2(p.y, p.x), p.a) * 2.6 - p.va * 1.9, -1.0, 1.0)
    }
    until({ GS.pl == null || tel(GS.pl!!).alt > 120.0 }, 20.0) {
        val p = GS.pl ?: return@until
        inp.thr = 0.85
        inp.rx = cl(ad(atan2(p.y, p.x), p.a) * 2.6 - p.va * 1.9, -1.0, 1.0)
        AP.sub = "CLIMB · ${tel(p).alt.roundToInt()} m"
    }
    say("Pitching over. Gravity turn toward the synchronous band, 1.4 km up.")
    val tgtX = cos(th + 0.08) * (C.RS - 140.0)
    val tgtY = sin(th + 0.08) * (C.RS - 140.0)
    flyTo(tgtX, tgtY, tol = 40.0, vmax = 55.0, rv = 4.0)
}

// ─── career brain ────────────────────────────────────────────────────────
private fun choose(): FrameTask {
    val G = GS.G
    val ev = GS.ev
    val c = G?.crew
    val wren = fleet("wren")
    val hare = fleet("hare")
    val wantHare = G != null && hare == null && past("shards") && G.cr >= hareP() + 200
    if (G?.ride != null) {
        return FrameTask("ride") {
            until({ GS.G?.ride == null }, 200.0) { AP.wantWarp = true }
        }
    }
    if (ev != null && !ev.hide) {
        if (!ev.lightOn) press("light")
        if (c != null && c.state != "idle") return FrameTask("shift") { crewShift() }
        if (onSurfaceSuit()) {
            if (G != null && !G.bunkClaimed) {
                return if (G.paperworkDone) FrameTask("bunk") { claimBunk14B() }
                else FrameTask("lift") { rideLift(true) }
            }
            if (hare != null && hare.dock?.b === SITES.depot) {
                return FrameTask("board") { boardOwn(hare) }
            }
            if (wantHare) {
                return FrameTask("buy") {
                    if (GS.ev != null && sackN(GS.ev) > 0) sellSack(SITES.depot)
                    buyShipAt(SITES.depot, "hare")
                }
            }
            return FrameTask("lift") { rideLift(true) }
        }
        if (G != null && !G.paperworkDone) {
            return FrameTask("intake") { intakePaperwork() }
        }
        if (G != null && !G.bunkClaimed) {
            return FrameTask("lift") { rideLift(false) }
        }
        if ((G?.bounty ?: 0) > 160 || ((ev.sack["core"] ?: 0) > 0 && (G?.cores ?: 0) < 3) || at("file")) {
            return FrameTask("catalog") { catalogOffice() }
        }
        if (wantHare) return FrameTask("lift") { rideLift(false) }
        if (hare != null && hare.dock?.b?.surf == true) return FrameTask("lift") { rideLift(false) }
        if (wren != null && past("hull")) return FrameTask("board") { boardOwn(wren) }
        if (wren == null && G != null && G.cr >= wrenP() + 120 && past("shift")) {
            return FrameTask("buy") { buyShipAt(SITES.sel, "wren") }
        }
        if (sackN(ev) > 0) return FrameTask("sell") { sellSack(SITES.sel) }
        return FrameTask("shift") { crewShift() }
    }
    if (GS.pl == null) return FrameTask("idle") { wait(1.0) }
    return FrameTask("fly") { flight() }
}

private fun veinShot(b: Body, want: String): Pair<Vec2, Vec2>? {
    val pl = GS.pl ?: return null
    val m = MAT.indexOfFirst { it?.ore == want }
    if (m < 0) return null
    val reach = pl.lasR - 14.0
    val aS = atan2(pl.y - b.y, pl.x - b.x)
    var bestAim: Vec2? = null
    var bestFrom: Vec2? = null
    var bd = 1e9
    for (i in b.veins) {
        if (b.sol[i] != m) continue
        val w = cw(b, i % b.gw, i / b.gw)
        val r = hy(w.x - b.x, w.y - b.y).let { if (it == 0.0) 1.0 else it }
        val ux = (w.x - b.x) / r
        val uy = (w.y - b.y) / r
        val h = ray(b.x + ux * (b.rad + 40.0), b.y + uy * (b.rad + 40.0), -ux, -uy, b.rad + 40.0)
        if (h == null || h.b !== b) continue
        val surf = hy(h.x - b.x, h.y - b.y)
        val depth = surf - r
        val cost = depth + (if (depth > reach) 500.0 else 0.0) + abs(ad(atan2(uy, ux), aS)) * surf * 0.35
        if (cost < bd) {
            bd = cost
            bestAim = Vec2(w.x, w.y)
            bestFrom = Vec2(h.x + ux * 30.0, h.y + uy * 30.0)
        }
    }
    return if (bestAim != null && bestFrom != null) bestFrom to bestAim else null
}

private suspend fun TaskScope.mineRunOn(b: Body, want: String?) {
    val pl = GS.pl ?: return
    act("PROSPECTING")
    var v = if (want != null) veinShot(b, want) else null
    val okFirst = if (v != null) harpoon(b, v.first, v.second) else harpoon(b)
    if (!okFirst) return
    for (k in 0 until 5) {
        mineHere()
        if (cmass(pl) >= pl.cargoMax - 1 || pl.fuel < max(pl.fuelMax * 0.2, reserve() * 1.4)) break
        undockShip()
        v = if (want != null) veinShot(b, want) else null
        val a = atan2(pl.y - b.y, pl.x - b.x) + 0.5
        val p = v?.first ?: Vec2(b.x + cos(a) * (b.rad + 30.0), b.y + sin(a) * (b.rad + 30.0))
        val okNext = if (v != null) harpoon(b, p, v.second) else harpoon(b, p)
        if (!okNext) break
    }
    undockShip()
}

private suspend fun TaskScope.flight() {
    val G = GS.G ?: return
    val W = GS.W ?: return
    val s = GS.pl ?: return
    val wren = s.cls == "wren"
    val hare = s.cls == "hare"
    val d = s.dock
    if (d != null && d.ty != "anchor") {
        val st = d.b
        if (st != null && st.tabs.isNotEmpty() && !AP.didHere) {
            AP.didHere = true
            until({ ctxK() == "services" || AiUiBridge.stationOpenFn() }, 1.5)
            if (ctxK() == "services" && !AiUiBridge.stationOpenFn()) {
                press("ctx")
                until({ AiUiBridge.stationOpenFn() }, 2.0)
            }
            if (AiUiBridge.stationOpenFn()) {
                officeTab(st)
                sellAll("core")
                service(st)
                takeJobs(st)
                wait(0.5)
                closePanel()
            }
        }
        if (wren && past("shards") && fleet("hare") == null && G.cr >= hareP() + 200 && st === SITES.sel) {
            say("Parking the WREN. Down the cable to buy a lander.")
            press("ctx")
            until({ AiUiBridge.stationOpenFn() }, 2.0)
            tab("service")
            clickUI("eva_btn", true) { AiUiBridge.panelActFn("eva", null, null) }
            return
        }
        AP.didHere = false
        say(if (hare && st != null && st.surf) "Lift-off from ${st.name}." else "Undocking.")
        if (hare && (d.ty == "pad" || d.ty == "ground") && onSurface(s)) {
            launch()
            return
        }
        undockShip()
        return
    }
    if (d != null && d.ty == "anchor") {
        undockShip()
        return
    }
    if (wren && past("shards") && fleet("hare") == null && G.cr >= hareP() + 200) {
        act("HOMEWARD")
        say("Enough for the lander. Back to SELENE HIGH.")
        visit(SITES.sel, "port", "keepcore")
        return
    }
    if (s.fuel < min(s.fuelMax * 0.5, max(s.fuelMax * 0.28, reserve() * 1.6)) || integ(s) < 0.5) {
        val st = if (integ(s) < 0.5) {
            if (hare && onSurface(s)) SITES.depot else SITES.sel
        } else {
            fuelStop()
        }
        act("SERVICE")
        say("Fuel ${((s.fuel / max(1.0, s.fuelMax)) * 100.0).roundToInt()}% — heading for ${st.name} to top up.")
        visit(st, if (st.surf) "pad" else "port")
        return
    }
    val hasCores = (s.cargo["core"] ?: 0) > 0
    val moreCoresNeeded = (G.cores + (s.cargo["core"] ?: 0) >= 3) || !W.contacts.any { q -> q.kind == "core" && !q.done }
    if ((at("file") || (hasCores && moreCoresNeeded)) && W.time > AP.catHold) {
        act("CATALOG")
        say(if (at("file")) "Flying to SELENE HIGH to file the claim." else "Core shards aboard — taking them to the Catalog Office.")
        val c0 = s.cargo["core"] ?: 0
        val s0 = G.story
        visit(SITES.sel, "port", "nosell")
        if (GS.pl != null && (GS.pl!!.cargo["core"] ?: 0) >= c0 && G.story == s0) {
            AP.catHold = W.time + 600.0
            aiLog("Catalog Office visit changed nothing — not retrying for a while.")
        }
        return
    }
    if (cmass(s) >= s.cargoMax - 2) {
        var st = bestBuyer() ?: SITES.sel
        if (hare && at("water") && (s.cargo["ice"] ?: 0) >= 20) st = SITES.depot
        if (at("orphan") && (s.cargo["pgm"] ?: 0) > 0) st = SITES.gaia
        if (st.surf) {
            val r = hy(st.x, st.y).let { if (it == 0.0) 1.0 else it }
            val ox = (st.x / r) * C.RS
            val oy = (st.y / r) * C.RS
            val k = s.burn / max(0.1, s.thrust)
            val need = s.fuelMax * 0.28 + k * 48.0 + 0.009 * hy(ox - s.x, oy - s.y) + 6.0
            if (s.fuel < need && s.fuel < s.fuelMax * 0.92 && !onSurface(s)) {
                val fs = fuelStop()
                act("SERVICE")
                val keep = hare && at("water") && (s.cargo["ice"] ?: 0) >= 20
                say(
                    "Hold full, but the descent wants more propellant — topping up at ${fs.name}" +
                        if (keep) " first (the ice is for the Depot)." else " and selling there instead."
                )
                visit(fs, "port", if (keep) "nosell" else null)
                return
            }
        }
        act("DELIVERY")
        val destTag = st.transfer?.let { " (${it.dest}-bound)" } ?: ""
        say("Hold full — ${if (st.surf) "down the well to " else "selling at "}${st.name}$destTag.")
        visit(st, if (st.surf) "pad" else "port")
        return
    }
    if (at("ghost") || at("shards")) {
        val kind = if (at("ghost")) "claim" else "core"
        fun ready(q: Contact) = W.time > q.aiNext
        fun fail(q: Contact) {
            q.aiFail++
            q.aiNext = W.time + min(900.0, 120.0 * q.aiFail)
            aiLog("${q.b.name} $kind attempt failed (${q.aiFail}) — parking it for ${((q.aiNext - W.time) / 60.0).roundToInt()} min.")
        }
        val L = W.contacts.filter { q ->
            q.kind == kind && !q.done && !q.b.hidden && !q.b.dead && ready(q)
        }.sortedBy { hy(it.x - s.x, it.y - s.y) }
        if (kind == "core") {
            val R = W.contacts.firstOrNull { q -> q.kind == "core" && q.done && !q.cut && !q.b.dead && ready(q) }
            if (R != null) {
                R.aiNext = W.time + 90.0
                if (cutCore(R)) R.cut = true else fail(R)
                return
            }
        }
        if (L.isNotEmpty()) {
            L[0].aiNext = W.time + 90.0
            if (!deepScan(L[0])) {
                fail(L[0])
                return
            }
            if (kind == "core") {
                if (cutCore(L[0])) L[0].cut = true else fail(L[0])
            }
            return
        }
    }
    val sj = G.missions.firstOrNull { j -> j.kind == "survey" && jobOK(j) && j.aiTry < 3 }
    if (sj != null && cmass(s) < s.cargoMax - 2) {
        sj.aiTry++
        jobBody(sj)?.let { surveyRun(it) }
        return
    }
    if (hare && at("launch")) {
        val b = pickRock("ice")
        if (b != null) {
            say("First orbit in the HARE — harpooning ${b.name}.")
            harpoon(b)
            undockShip()
        }
        return
    }
    if (at("orphan") && W.orphan != null && !W.orphan!!.hidden) {
        say("The Orphan. Half a kilometre of metal with a platinum heart.")
        AP.mined.remove(W.orphan)
        mineRunOn(W.orphan!!, "pgm")
        return
    }
    val want = if (hare && at("water")) "ice" else null
    mineRun(want)
}

fun apAbort(why: String? = null) {
    AP.task?.cancel()
    AP.task = null
    AP.didHere = false
    AP.aiPickTag = null
    AP.wantWarp = false
    resetInp()
    if (why != null) aiLog(why)
}

private fun setTask(name: String, block: suspend TaskScope.() -> Unit) {
    AP.task?.cancel()
    AP.task = FrameTask(name, GS.W?.time ?: 0.0, block)
}

fun apThink(dt: Double, simRunning: Boolean) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    if (!AP.on || G.mode != "play" || (GS.pl == null && GS.ev == null)) return
    NAV.dt = dt
    NAV.t += dt
    resetInp()
    AP.wantWarp = false
    if (!simRunning) return
    val pl = GS.pl
    val ev = GS.ev
    val keepTasks = setOf("SERVICE", "DOCKING", "DELIVERY", "CATALOG", "HOMEWARD", "LANDING", "LAUNCH")
    if (pl != null && ev == null && AP.task != null &&
        pl.fuel < min(reserve(), pl.fuelMax * 0.45) &&
        (pl.dock == null || pl.dock?.ty == "anchor") &&
        AP.act !in keepTasks
    ) {
        aiLog("Tank at ${((pl.fuel / max(1.0, pl.fuelMax)) * 100.0).roundToInt()}% — dropping ${AP.act} to refuel.")
        AP.task?.cancel()
        AP.task = null
        AP.act = "SERVICE"
    }
    if (pl != null && (pl.dock == null || pl.dock?.ty == "anchor") && ev == null && pl.fuel <= 0.5 && !AP.towing) {
        AP.towing = true
        AP.towT = 0.0
        say("Tanks dry. Calling the recovery tow.")
        AiUiBridge.modeFn("pause")
        return
    }
    val c = G.crew
    if (ev != null && !ev.hide && ev.suit < 12.0 && !(c != null && c.state == "site") && !AP.towing && inBody(ev.x, ev.y) == null) {
        AP.towing = true
        AP.towT = 0.0
        say("Air critical. Calling rescue.")
        AiUiBridge.modeFn("pause")
        return
    }
    if (AP.task == null) {
        AP.task = choose()
    }
    val curTask = AP.task ?: return
    val done = curTask.step()
    if (done) {
        AP.task = null
    } else if (W.time - curTask.t0 > 600.0) {
        say("That is taking too long — rethinking.")
        curTask.cancel()
        AP.task = null
    }
}

fun apMeta(dt: Double, defZoomFn: () -> Double) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    if (!AP.on) return
    if (G.mode == "dead" || G.mode == "win") {
        AP.metaT += dt
        val tag = if (G.mode == "dead") "respawn_button" else "keep_button"
        if (AP.metaT > 2.2) AP.aiPickTag = tag
        if (AP.metaT > 4.0) {
            AP.metaT = 0.0
            AP.aiPickTag = null
            AP.task?.cancel()
            AP.task = null
            AiUiBridge.clickButtonFn(tag)
        }
        say(
            if (G.mode == "dead") "Signal lost. Rim medics will patch us up."
            else "THE ORPHAN IS FREE. The band is ours to catalog."
        )
    } else {
        AP.metaT = 0.0
    }
    if (G.mode == "pause" && G.autoPaused && !AiUiBridge.appInBackgroundFn()) {
        G.autoPaused = false
        AiUiBridge.modeFn("play")
    }
    if (G.mode == "pause" && AP.towing) {
        AP.towT += dt
        AP.aiPickTag = if (AP.towT > 0.8) "tow_button" else null
        if (AP.towT > 2.0) {
            AP.towing = false
            AP.aiPickTag = null
            AP.task?.cancel()
            AP.task = null
            AP.didHere = false
            resetInp()
            AiUiBridge.clickButtonFn("tow_button")
        }
    }
    if (AiUiBridge.dialogOpenFn()) {
        AP.dlgT += dt
        if (AP.dlgT > 6.0) {
            AiUiBridge.closeDialogFn()
            AP.dlgT = 0.0
        }
    } else {
        AP.dlgT = 0.0
    }
    val ev = GS.ev
    val pl = GS.pl
    if (G.mode == "play" && GS.cam.user <= 0.0) {
        if (ev != null && !ev.hide) {
            GS.cam.tz = cl(defZoomFn() * 1.5, 0.35, 3.4)
        } else if (pl != null && pl.dock == null) {
            val v = hy(pl.vx, pl.vy)
            GS.cam.tz = cl(defZoomFn() * cl(1.1 - v / 90.0, 0.35, 1.0), 0.3, 3.4)
        }
    }
    AP.warp = if (AP.wantWarp && W.meteors.isEmpty()) 3 else 1
}

fun apSet(on: Boolean, demo: Boolean? = null) {
    AP.on = on
    if (demo != null) AP.demo = demo
    AP.task?.cancel()
    AP.task = null
    AP.warp = 1
    AP.towing = false
    AP.aiPickTag = null
    resetInp()
    if (!AP.on) {
        if (AiUiBridge.stationOpenFn()) AiUiBridge.closeStationFn()
    } else {
        AP.started = GS.W?.time ?: 0.0
        AP.act = "TAKING OVER"
        AP.cap = "The AI has the controls. Touch the sticks or press AI to take them back."
        AP.sub = ""
    }
    AiUiBridge.onAiStateChanged()
}
