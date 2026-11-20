package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.AltAction
import com.example.keres9.core.AnchorCandidate
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.ContextAction
import com.example.keres9.core.ControlFrame
import com.example.keres9.core.DockGates
import com.example.keres9.core.GS
import com.example.keres9.core.NavTarget
import com.example.keres9.core.Ship
import com.example.keres9.core.TAU
import com.example.keres9.core.Targetable
import com.example.keres9.core.Vec2
import com.example.keres9.core.WorldPortPose
import com.example.keres9.core.ad
import com.example.keres9.core.an
import com.example.keres9.core.cl
import com.example.keres9.core.ds
import com.example.keres9.core.gacc
import com.example.keres9.core.geff
import com.example.keres9.core.hy
import com.example.keres9.core.lp
import com.example.keres9.world.ca
import com.example.keres9.world.near
import com.example.keres9.world.portW
import com.example.keres9.world.surfR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object IN {
    var lx: Double = 0.0
    var ly: Double = 0.0
    var rx: Double = 0.0
    var ry: Double = 0.0
    var thr: Double = 0.0
    var mineHold: Boolean = false
    var brakeHold: Boolean = false
    val keys = mutableSetOf<String>()
    val edge = mutableListOf<String>()
    val taps = mutableListOf<Vec2>()
    var aiLx: Double = 0.0
    var aiLy: Double = 0.0
    var aiRx: Double = 0.0
    var aiRy: Double = 0.0
    var aiThr: Double = 0.0
    var aiMine: Boolean = false
}

val HUM = ControlFrame()

data class ViewportMetrics(var widthPx: Float = 1080f, var heightPx: Float = 720f, var density: Float = 2f)
val VIEW = ViewportMetrics()

data class Telemetry(
    val r: Double,
    val alt: Double,
    val vr: Double,
    val vt: Double,
    val g: Double,
    val band: Double
)

private val GA = Vec2()
private val SAS_NAMES = arrayOf("OFF", "UP", "RETRO", "PRO")
private val ROOM_TAB = mapOf("workshop" to "service", "cargo" to "market", "lift" to null)

fun camX(): Double = GS.cam.x + GS.cam.lx
fun camY(): Double = GS.cam.y + GS.cam.ly

fun worldScale(density: Float = VIEW.density): Double = GS.cam.z * (max(1f, density) / 1.5)

fun s2w(sx: Double, sy: Double): Vec2 {
    val k = worldScale()
    val dx = (sx - VIEW.widthPx / 2.0) / k
    val dy = (sy - VIEW.heightPx / 2.0) / k
    val c = cos(GS.cam.rot)
    val s = sin(GS.cam.rot)
    return Vec2(camX() + dx * c - dy * s, camY() + dx * s + dy * c)
}

fun s2wv(x: Double, y: Double): Vec2 {
    val c = cos(GS.cam.rot)
    val s = sin(GS.cam.rot)
    return Vec2(x * c - y * s, x * s + y * c)
}

fun w2sv(x: Double, y: Double): Vec2 {
    val c = cos(-GS.cam.rot)
    val s = sin(-GS.cam.rot)
    return Vec2(x * c - y * s, x * s + y * c)
}

fun defZoom(): Double {
    val dpW = VIEW.widthPx / max(1f, VIEW.density)
    return cl(dpW / 380.0, 0.9, 2.4)
}

private fun autoZoom(f: Targetable): Double {
    val base = defZoom()
    val ev = GS.ev
    val G = GS.G
    val pl = GS.pl
    if (ev != null && !ev.hide) return base * 1.35
    val v = hy(f.vx, f.vy)
    val alt = com.example.keres9.world.altAt(f.x, f.y)
    var z = base / (1.0 + v / 22.0)
    if (G?.ride?.kind == "lift") z = base * 0.55
    if (pl?.dock != null && pl.dock?.ty != "anchor") z = base
    if (alt < 120.0 && v < 30.0) z = kotlin.math.max(z, base * 0.8)
    return cl(z, 0.22, 3.2)
}

fun updCam(dt: Double) {
    val f = GS.focus() ?: return
    val cam = GS.cam
    val k = 1.0 - exp(-4.2 * dt)
    val ev = GS.ev
    val lead = if (ev != null && !ev.hide) 0.15 else 0.45
    var tx = f.x + cl(f.vx * lead, -140.0, 140.0)
    var ty = f.y + cl(f.vy * lead, -140.0, 140.0)
    val aux = cam.aux
    if (aux != null) {
        tx = lp(tx, aux.x, 0.38)
        ty = lp(ty, aux.y, 0.38)
    }
    if (cam.snap) {
        cam.x = tx
        cam.y = ty
        cam.snap = false
    } else {
        cam.x = lp(cam.x, tx, k)
        cam.y = lp(cam.y, ty, k)
    }
    val want = atan2(cam.y, cam.x) + PI / 2.0
    cam.rot = an(cam.rot + ad(want, cam.rot) * (1.0 - exp(-3.0 * dt)))
    if (!(cam.x + cam.y + cam.rot).isFinite()) {
        val ok = (f.x + f.y).isFinite()
        cam.x = if (ok) f.x else 0.0
        cam.y = if (ok) f.y else -C.RM
        cam.aux = null
        cam.rot = atan2(cam.y, cam.x) + PI / 2.0
    }
    cam.lt -= dt
    if (cam.lt <= 0.0) {
        val q = 1.0 - exp(-2.4 * dt)
        cam.lx = lp(cam.lx, 0.0, q)
        cam.ly = lp(cam.ly, 0.0, q)
    }
    if (cam.user > 0.0) cam.user -= dt else cam.tz = autoZoom(f)
    cam.z = lp(cam.z, cam.tz, 1.0 - exp(-cam.zr * dt))
    cam.shake *= exp(-4.5 * dt)
}

fun targets(): List<Targetable> {
    val W = GS.W ?: return emptyList()
    val f = GS.focus() ?: return emptyList()
    val L = mutableListOf<Targetable>()
    for (s in W.ships) {
        if (!s.dead && !s.isPl && !s.ghost && ds(s, f) < 1800.0) L.add(s)
    }
    for (b in W.bodies) {
        if (!b.hidden && ds(b, f) < 2600.0) L.add(b)
    }
    L.sortBy { ds(it, f) }
    return L
}

fun setTgt(t: Targetable?) {
    GS.PC.tgt = t
    AU.ui()
}

fun cycleTgt() {
    val L = targets()
    if (L.isEmpty()) {
        setTgt(null)
        return
    }
    val cur = GS.PC.tgt
    val idx = L.indexOfFirst { it === cur }
    setTgt(L[(idx + 1) % L.size])
}

fun setNav(x: Double, y: Double, l: String = "WAYPOINT", quiet: Boolean = false) {
    val W = GS.W ?: return
    W.nav = NavTarget(x, y, l)
    if (!quiet) {
        AU.blip()
        Hooks.toast("NAV ▸ $l", null)
    }
}

fun pickAt(x: Double, y: Double): Targetable? {
    val W = GS.W ?: return null
    var best: Targetable? = null
    var bd = 40.0 / kotlin.math.max(0.3, GS.cam.z)
    for (s in W.ships) {
        if (s.dead || s.isPl) continue
        val d = hy(s.x - x, s.y - y) - s.rad
        if (d < bd) {
            bd = d
            best = s
        }
    }
    for (b in W.bodies) {
        if (b.hidden) continue
        val d = hy(b.x - x, b.y - y)
        if (d < b.rad + 20.0) {
            val score = d - b.rad * 0.5
            if (score < bd) {
                bd = score
                best = b
            }
        }
    }
    return best
}

fun drive(s: Ship, dx: Double, dy: Double): Boolean {
    val m = hy(dx, dy)
    if (m < 0.06) {
        s.rcx = 0.0
        s.rcy = 0.0
        return false
    }
    val l = min(1.0, m / 1.6)
    s.rcx = (dx / m) * l
    s.rcy = (dy / m) * l
    return true
}

private fun setMatch(on: Boolean) {
    GS.PC.match = on
}

fun sasName(): String = SAS_NAMES.getOrElse(GS.PC.sas) { "OFF" }

private fun refVel(pl: Ship): Vec2 {
    GS.PC.assist?.let { return Vec2(it.w.vx, it.w.vy) }
    val t = GS.PC.tgt
    if (t != null && (t is Ship || t is Body) && ds(t, pl) < 600.0) {
        return Vec2(t.vx, t.vy)
    }
    return Vec2(0.0, 0.0)
}

private fun assists(pl: Ship) {
    val PC = GS.PC
    val CTL = GS.CTL
    val man = abs(CTL.rx) > 0.05
    val st = s2wv(CTL.lx, CTL.ly)
    if (CTL.brake || PC.match) {
        val v = refVel(pl)
        gacc(pl.x, pl.y, pl.vx, pl.vy, GA)
        drive(pl, v.x + st.x * 6.0 - pl.vx - GA.x * 0.5, v.y + st.y * 6.0 - pl.vy - GA.y * 0.5)
        val assist = PC.assist
        if (assist != null && !man) {
            pl.tqi = cl(ad(assist.want, pl.a) * 2.4 + (assist.w.va - pl.va) * 1.7, -1.0, 1.0)
        } else if (!man && PC.sas == 0) {
            pl.tqi = cl(-pl.va * 2.2, -1.0, 1.0)
        }
        if (pl.fuel <= 0.0 && PC.match) {
            setMatch(false)
            Hooks.toast("MATCH offline — propellant dry", "bad")
        }
    }
    if (PC.sas != 0 && !man && pl.dock == null) {
        val v = hy(pl.vx, pl.vy)
        var want = atan2(pl.y, pl.x)
        if (PC.sas == 2 && v > 0.5) want = atan2(-pl.vy, -pl.vx)
        if (PC.sas == 3 && v > 0.5) want = atan2(pl.vy, pl.vx)
        pl.tqi = cl(ad(want, pl.a) * 2.6 - pl.va * 1.9, -1.0, 1.0)
    }
}

fun gates(
    s: Ship,
    w: WorldPortPose,
    ty: String,
    b: Body,
    pPort: com.example.keres9.core.Port? = null,
    pPad: com.example.keres9.core.Pad? = null
): DockGates {
    val tail = ty == "pad" && s.tailLand
    val isPad = ty == "pad"
    val rv = hy(s.vx - w.vx, s.vy - w.vy)
    val want = if (isPad) (if (tail) w.dir else w.dir + PI / 2.0) else w.dir + PI
    val ae = abs(ad(s.a, want))
    val pt = if (isPad) {
        if (tail) Vec2(s.x - cos(s.a) * s.nose, s.y - sin(s.a) * s.nose)
        else Vec2(s.x + cos(s.a + PI / 2.0) * s.belly, s.y + sin(s.a + PI / 2.0) * s.belly)
    } else {
        Vec2(s.x + cos(s.a) * s.nose, s.y + sin(s.a) * s.nose)
    }
    val ox = pt.x - w.x
    val oy = pt.y - w.y
    val nx = cos(w.dir)
    val ny = sin(w.dir)
    val lat = abs(ox * -ny + oy * nx)
    val d = hy(ox, oy)
    val re = abs(s.va - w.va)
    val vOk = rv < (if (isPad) C.LAND.v else C.DOCK.v)
    val aOk = ae < (if (isPad) C.LAND.a else C.DOCK.a)
    val lOk = lat < (if (isPad) C.LAND.l else C.DOCK.l)
    val rOk = re < (if (isPad) C.LAND.r else C.DOCK.r)
    val nearOk = d < (if (isPad) C.LAND.d else C.DOCK.d)
    val gearOk = !isPad || (s.gear && (s.gearOK || !s.tailLand))
    return DockGates(
        v = vOk, a = aOk, l = lOk, r = rOk, near = nearOk, gear = gearOk,
        rv = rv, ae = ae, lat = lat, re = re, d = d,
        w = w, ty = ty, want = want, b = b, pPort = pPort, pPad = pPad,
        ok = vOk && aOk && lOk && rOk && nearOk && gearOk
    )
}

fun dockCand(): DockGates? {
    val pl = GS.pl ?: return null
    val W = GS.W ?: return null
    if (pl.dock != null) return null
    var best: DockGates? = null
    for (st in W.stations) {
        if (st.dead || st.leaving || st.arr != null) continue
        if (abs(st.x - pl.x) > st.rad + 120.0 || abs(st.y - pl.y) > st.rad + 120.0) continue
        for (p in st.ports) {
            if (p.occ != null) continue
            val w = portW(st, p)
            if (hy(pl.x - w.x, pl.y - w.y) > 85.0) continue
            val g = gates(pl, w, "port", st, pPort = p)
            if (best == null || g.d < best.d) best = g
        }
        if (pl.gear || pl.tailLand) {
            for (p in st.pads) {
                if (p.occ != null) continue
                val w = portW(st, p)
                if (hy(pl.x - w.x, pl.y - w.y) > 85.0) continue
                val g = gates(pl, w, "pad", st, pPad = p)
                if (best == null || g.d < best.d) best = g
            }
        }
    }
    return best
}

fun anchCand(): AnchorCandidate? {
    val pl = GS.pl ?: return null
    val curDock = pl.dock
    if (curDock != null && curDock.ty == "anchor" && curDock.b != null) {
        return AnchorCandidate(curDock.b, curDock.cx, curDock.cy, 0.0)
    }
    if (curDock != null) return null
    var best: AnchorCandidate? = null
    val radii = doubleArrayOf(pl.rad + 3.0, pl.rad + 9.0, pl.rad + 15.0)
    for (b in near(pl.x, pl.y, 70.0)) {
        if (b.kind == "station") continue
        for (k in 0 until 16) {
            val t = (k / 16.0) * TAU
            for (rrVal in radii) {
                val x = pl.x + cos(t) * rrVal
                val y = pl.y + sin(t) * rrVal
                val c = ca(b, x, y)
                if (c != null && b.sol[c.i] != 0) {
                    val d = hy(x - pl.x, y - pl.y)
                    if (best == null || d < best.d) {
                        best = AnchorCandidate(b, c.cx, c.cy, d)
                    }
                    break
                }
            }
        }
    }
    return best
}

fun tryAnchor(c: AnchorCandidate?): Boolean {
    val pl = GS.pl ?: return false
    if (c == null || c.b.dead) return false
    val rv = hy(pl.vx - c.b.vx, pl.vy - c.b.vy)
    if (rv > C.ANCH.v) {
        AU.warn()
        Hooks.toast("Too hot to harpoon — under 3 m/s relative", "warn")
        return false
    }
    anchor(pl, c.b, c.cx, c.cy)
    setMatch(false)
    return true
}

private fun updCtx() {
    val G = GS.G ?: return
    val pl = GS.pl
    val ev = GS.ev
    val PC = GS.PC
    var ctx = ContextAction()
    var alt: AltAction? = null
    val c = G.crew
    val ride = G.ride
    if (ride != null) {
        ctx = ContextAction(
            l = if (ride.kind == "lift") "RIDING LIFT" else "ABOARD MULE-3",
            f = null,
            ok = false,
            k = "ride"
        )
    } else if (ev != null && !ev.dead) {
        val sh = shuttle()
        val here = evaRoom()
        val b = boardable()
        if (c != null && c.state == "site" && sh != null && hy(ev.x - sh.x, ev.y - sh.y) < 18.0) {
            ctx = ContextAction("BOARD MULE-3", { crewBoard() }, true, "crewboard")
        } else if (b != null) {
            ctx = ContextAction("BOARD ${b.name}", { boardShip(b) }, true, "board")
        } else if (here != null && here.rm != null) {
            val k = here.rm.kind
            val st = here.b
            if (k == "lift") {
                val end = if (st.surf) "ground" else "top"
                val car = carHere(end)
                ctx = if (car != null && !liftBusy()) {
                    ContextAction(if (end == "ground") "RIDE LIFT ▲" else "RIDE LIFT ▼", { ride(end, true) }, true, "lift")
                } else {
                    ContextAction("LIFT IN TRANSIT", null, false, "liftwait")
                }
            } else if (k == "crew") {
                ctx = ContextAction(if (c != null && c.state == "idle") "CREW OFFICE" else "CREW OUT", { Hooks.openStation(st, "crew") }, true, "crew")
            } else {
                val tab = if (ROOM_TAB.containsKey(k)) ROOM_TAB[k] else k
                if (tab != null && st.tabs.contains(tab)) {
                    ctx = ContextAction(here.rm.name, { Hooks.openStation(st, tab) }, true, "terminal")
                } else if (st.tabs.isNotEmpty()) {
                    ctx = ContextAction("TERMINAL", { Hooks.openStation(st, st.tabs[0]) }, true, "terminal")
                }
            }
        }
        alt = AltAction("SCAN", { ping() }, "ping")
    } else if (pl?.dock != null) {
        val d = pl.dock!!
        when (d.ty) {
            "anchor" -> {
                ctx = ContextAction("EVA", { enterEVA() }, true, "eva")
                alt = AltAction("RELEASE", { undock(pl) }, "release")
            }
            "ground" -> {
                ctx = ContextAction("EVA", { enterEVA() }, true, "eva")
                alt = AltAction("SAS ${sasName()}", { cycleSAS() }, "sas")
            }
            else -> {
                val b = d.b
                if (b != null && b.tabs.isNotEmpty()) {
                    ctx = ContextAction("SERVICES", { Hooks.openStation(b, null) }, true, "services")
                    alt = if (d.ty == "pad" && pl.tailLand) {
                        AltAction("EVA", { enterEVA() }, "eva")
                    } else {
                        AltAction("UNDOCK", { Hooks.closeStation(); undock(pl) }, "undock")
                    }
                } else {
                    ctx = ContextAction("EVA", { enterEVA() }, true, "eva")
                    alt = if (d.ty == "pad" && pl.tailLand) {
                        AltAction("EVA", { enterEVA() }, "eva")
                    } else {
                        AltAction("UNDOCK", { undock(pl) }, "undock")
                    }
                }
            }
        }
    } else if (pl != null && !pl.dead) {
        val g = PC.assist
        val a = anchCand()
        if (g != null && g.ty == "port") {
            ctx = ContextAction(
                l = "CLAMP",
                ok = g.ok,
                k = "clamp",
                f = {
                    if (g.ok && g.pPort != null) {
                        dockPort(pl, g.b, g.pPort)
                        setMatch(false)
                    } else {
                        AU.warn()
                        val msg = when {
                            !g.v -> "Too fast — kill ΔV [BRAKE/MATCH]"
                            !g.a -> "Align the nose with the port"
                            !g.r -> "Stop the spin"
                            !g.l -> "Center the lateral offset"
                            else -> "Drift closer"
                        }
                        Hooks.toast(msg, "warn")
                    }
                }
            )
        } else if (a != null) {
            ctx = ContextAction(
                l = "HARPOON",
                f = { tryAnchor(a) },
                k = "anchor",
                ok = a.d < kotlin.math.max(C.ANCH.d, pl.rad + 10.0) && hy(pl.vx - a.b.vx, pl.vy - a.b.vy) < C.ANCH.v
            )
        } else if (hy(pl.vx, pl.vy) < 2.2 && com.example.keres9.world.altAt(pl.x, pl.y) > 40.0) {
            ctx = ContextAction("EVA", { enterEVA() }, true, "eva")
        }
        alt = AltAction("SAS ${sasName()}", { cycleSAS() }, "sas")
    }
    PC.ctx = ctx
    PC.alt = alt
}

fun cycleSAS() {
    val PC = GS.PC
    PC.sas = (PC.sas + 1) % 4
    AU.ui()
    if (!GS.CTL.ai) {
        val extra = when (PC.sas) {
            1 -> " — nose held away from the Moon"
            2 -> " — nose held against your velocity (landing burns)"
            3 -> " — nose along your velocity"
            else -> ""
        }
        Hooks.toast("SAS · ${sasName()}$extra", null)
    }
}

private fun edges() {
    val pl = GS.pl
    val ev = GS.ev
    val PC = GS.PC
    var i = IN.edge.lastIndex
    while (i >= 0) {
        val e = IN.edge[i]
        var used = true
        when (e) {
            "match" -> if (pl != null && ev == null && pl.dock == null) {
                setMatch(!PC.match)
                AU.ui()
                if (PC.match && !GS.CTL.ai) Hooks.toast("MATCH · RCS holds the target velocity. Push the stick to creep.", null)
            }
            "target" -> cycleTgt()
            "gear" -> if (pl != null && ev == null) {
                if (!pl.tailLand && pl.cls == "wren") {
                    Hooks.toast("WREN has no landing gear — it clamps to ports", "warn")
                } else {
                    pl.gear = !pl.gear
                    AU.thunk()
                    Hooks.toast(if (pl.gear) "GEAR DOWN" else "gear up", null)
                }
            }
            "scan" -> if (ev != null) {
                ping()
            } else if (pl != null) {
                PC.scan = !PC.scan
                AU.ui()
                if (PC.scan && !PC.hints.contains("s") && !GS.CTL.ai) {
                    PC.hints.add("s")
                    Hooks.toast("ARRAY LIVE — bodies in the cone are surveyed automatically · drag the dial to find deep resonances", null)
                }
            }
            "ctx" -> PC.ctx.f?.invoke()
            "alt" -> PC.alt?.f?.invoke()
            else -> used = false
        }
        if (used) IN.edge.removeAt(i)
        i--
    }
}

fun readHuman(out: ControlFrame, dt: Double) {
    val K = IN.keys
    val kx = (if (K.contains("KeyD")) 1.0 else 0.0) - (if (K.contains("KeyA")) 1.0 else 0.0)
    val ky = (if (K.contains("KeyS")) 1.0 else 0.0) - (if (K.contains("KeyW")) 1.0 else 0.0)
    val kt = (if (K.contains("KeyE")) 1.0 else 0.0) - (if (K.contains("KeyQ")) 1.0 else 0.0)
    if (K.contains("ShiftLeft") || K.contains("ShiftRight")) IN.thr = min(1.0, IN.thr + dt * 0.85)
    if (K.contains("ControlLeft") || K.contains("ControlRight")) IN.thr = kotlin.math.max(0.0, IN.thr - dt * 1.2)
    out.lx = cl(IN.lx + kx, -1.0, 1.0)
    out.ly = cl(IN.ly + ky, -1.0, 1.0)
    out.rx = cl(IN.rx + kt, -1.0, 1.0)
    out.ry = IN.ry
    out.thr = IN.thr
    out.mine = IN.mineHold
    out.brake = IN.brakeHold
    out.ai = false
}

fun control(dt: Double) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    if (G.mode != "play") return
    val ev = GS.ev
    val pl = GS.pl
    val PC = GS.PC
    val CTL = GS.CTL
    val ride = G.ride
    if (ev != null && ev.hide && ride != null && ride.kind == "shuttle" && ride.ship != null) {
        val sh = ride.ship
        ev.x = sh.x; ev.y = sh.y; ev.vx = sh.vx; ev.vy = sh.vy; ev.suit = 100.0
    }
    updCtx()
    edges()
    if (IN.taps.isNotEmpty()) {
        for (t in IN.taps) {
            val w = s2w(t.x, t.y)
            val o = pickAt(w.x, w.y)
            if (o != null) setTgt(o) else setNav(w.x, w.y)
        }
        IN.taps.clear()
    }
    if (pl != null && !pl.dead && ev == null) {
        if (pl.dock != null && CTL.thr < 0.04) PC.thrArm = true
        if (pl.dock == null) PC.thrArm = false
        pl.thr = if (pl.dock != null && !PC.thrArm) 0.0 else CTL.thr
        val w = s2wv(CTL.lx, CTL.ly)
        pl.rcx = w.x
        pl.rcy = w.y
        pl.tqi = CTL.rx
        pl.wantM = CTL.mine && !PC.scan
        assists(pl)
        AU.engine(pl.thr)
        if (pl.fuel <= 0.0 && CTL.thr > 0.0 && !PC.hints.contains("dry")) {
            PC.hints.add("dry")
            Hooks.toast("PROPELLANT DRY — call a tow from pause", "bad")
        }
        PC.assist = dockCand()
        val g = PC.assist
        if (pl.dock == null && !(pl.liftT != null && W.time - pl.liftT!! < 3.0) && g != null && g.ty == "pad" && g.ok && g.pPad != null) {
            dockPad(pl, g.b, g.pPad)
            setMatch(false)
            PC.thrArm = false
            PC.assist = null
        }
    } else {
        PC.assist = null
        AU.engine(0.0)
    }
    if (ev != null && pl != null) {
        pl.thr = 0.0; pl.rcx = 0.0; pl.rcy = 0.0; pl.tqi = 0.0; pl.wantM = false
    }
    updScan(dt)
    updCtx()
    AU.mine((pl != null && pl.wantM && ev == null) || (ev?.beam != null && ev.beam?.miss != true))
}

fun tele(o: Targetable): Telemetry {
    val r = hy(o.x, o.y).let { if (it == 0.0) 1.0 else it }
    val ux = o.x / r
    val uy = o.y / r
    val vr = o.vx * ux + o.vy * uy
    val vt = o.vx * -uy + o.vy * ux
    return Telemetry(
        r = r,
        alt = r - surfR(atan2(o.y, o.x)),
        vr = vr,
        vt = vt,
        g = geff(r),
        band = r - C.RS
    )
}

fun tele(e: com.example.keres9.core.SuitState): Telemetry {
    val r = hy(e.x, e.y).let { if (it == 0.0) 1.0 else it }
    val ux = e.x / r
    val uy = e.y / r
    val vr = e.vx * ux + e.vy * uy
    val vt = e.vx * -uy + e.vy * ux
    return Telemetry(
        r = r,
        alt = r - surfR(atan2(e.y, e.x)),
        vr = vr,
        vt = vt,
        g = geff(r),
        band = r - C.RS
    )
}
