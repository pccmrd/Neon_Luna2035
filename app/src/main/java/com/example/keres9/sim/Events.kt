package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.Bit
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.EventsState
import com.example.keres9.core.FlareEvent
import com.example.keres9.core.GS
import com.example.keres9.core.HaulEvent
import com.example.keres9.core.Meteor
import com.example.keres9.core.ShowerEvent
import com.example.keres9.core.cl
import com.example.keres9.core.hy
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.pk
import com.example.keres9.core.rand
import com.example.keres9.core.ri
import com.example.keres9.core.rr
import com.example.keres9.world.SITES
import com.example.keres9.world.replaceTransfer
import com.example.keres9.world.spawnRock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private val COL_HAUL_PLUME = parseHexColor("#53d9ff")
private val COL_TRANSFER_PLUME = parseHexColor("#ffd9a0")
private val ROCK_TYPES = listOf("rock", "metal", "ice")

fun eventsInit() {
    val W = GS.W ?: return
    W.evt = EventsState(next = 150.0, haul = null, shower = null)
    W.flare = FlareEvent(on = false, t = 0.0, warn = 0.0, next = 420.0 + rand() * 200.0)
    SITES.ares.transfer?.let { it.depart = W.time + 780.0 }
    SITES.gaia.transfer?.let { it.depart = W.time + 1080.0 }
}

private fun banner(t: String, k: String? = null) {
    Hooks.banner(t, k)
}

fun eventsTick(dt: Double) {
    val W = GS.W ?: return
    val E = W.evt ?: return
    E.next -= dt
    if (E.next <= 0.0) {
        E.next = rr(::rand, 200.0, 320.0)
        if (E.haul == null) startHaul() else if (E.shower == null) startShower()
    }
    stepHaul(dt)
    stepShower(dt)
    stepFlare(dt)
    stepTransfers(dt)
}

private fun startHaul() {
    val W = GS.W ?: return
    val E = W.evt ?: return
    val rf = ::rand
    val th = C.TH0 + rr(rf, -0.9, 0.9)
    val rad = C.RS + rr(rf, -250.0, 250.0)
    val tx = cos(th) * rad
    val ty = sin(th) * rad
    for (b in W.rocks) {
        if (hy(b.x - tx, b.y - ty) < b.rad + 200.0) return
    }
    val comet = rf() < 0.35
    val R0 = if (comet) rr(rf, 18.0, 28.0) else rr(rf, 14.0, 30.0)
    val sx = cos(th + 0.05) * (C.RS + 2600.0)
    val sy = sin(th + 0.05) * (C.RS + 2600.0)
    val b = spawnRock(ri(rf, 1, 1_000_000_000), sx, sy, R0, if (comet) "comet" else pk(rf, ROCK_TYPES))
    b.inbound = true
    b.kin = true
    val tug = mkShip("tug", "selene", "HAUL ENGINE ${ri(rf, 10, 99)}", sx, sy)
    tug.rail = true
    tug.ghost = true
    E.haul = HaulEvent(b = b, tug = tug, tx = tx, ty = ty, t = 0.0, dur = 75.0, sx = sx, sy = sy, stage = "in")
    banner("INBOUND HAUL · ${tug.name} delivering a ${if (comet) "comet nucleus" else "body"} — uncatalogued")
    feed("${tug.name} is bringing a new ${if (comet) "comet" else "asteroid"} into the band.", "warn")
    W.stats.events++
}

private fun stepHaul(dt: Double) {
    val W = GS.W ?: return
    val E = W.evt ?: return
    val H = E.haul ?: return
    val rf = ::rand
    H.t += dt
    val b = H.b
    val tug = H.tug
    if (H.stage == "in") {
        val k = cl(H.t / H.dur, 0.0, 1.0)
        val e = 1.0 - (1.0 - k).pow(3.0)
        val px = b.x
        val py = b.y
        b.x = H.sx + (H.tx - H.sx) * e
        b.y = H.sy + (H.ty - H.sy) * e
        b.vx = (b.x - px) / dt
        b.vy = (b.y - py) / dt
        val dir = atan2(H.ty - H.sy, H.tx - H.sx)
        tug.x = b.x - cos(dir) * (b.rad + 14.0)
        tug.y = b.y - sin(dir) * (b.rad + 14.0)
        tug.a = dir + PI
        tug.thr = if (k < 0.95) 1.0 - k * 0.6 else 0.0
        tug.vx = b.vx
        tug.vy = b.vy
        if (k >= 1.0) {
            b.vx = 0.0; b.vy = 0.0
            b.inbound = false; b.kin = false
            H.stage = "out"; H.t = 0.0
            val rec = W.extra.firstOrNull { !it.done && abs(it.x - H.sx) < 1.0 && abs(it.y - H.sy) < 1.0 }
            if (rec != null) {
                rec.x = H.tx
                rec.y = H.ty
                rec.done = true
            }
            feed("${b.name} parked in the band. Catalog status: UNKNOWN.", "warn")
            val f = GS.focus()
            if (f != null && hy(f.x - b.x, f.y - b.y) < 1500.0) {
                Hooks.toast("NEW BODY PARKED · ${b.name} — first survey pays a bonus", "good")
            }
            b.fresh = true
        }
        if (tug.thr > 0.1 && hy(tug.x - GS.cam.x, tug.y - GS.cam.y) < 1500.0) {
            for (i in 0 until 2) {
                bit(
                    Bit(
                        x = tug.x - cos(tug.a) * 10.0,
                        y = tug.y - sin(tug.a) * 10.0,
                        vx = -cos(tug.a) * 70.0 + rr(rf, -8.0, 8.0),
                        vy = -sin(tug.a) * 70.0 + rr(rf, -8.0, 8.0),
                        life = 0.4, max = 0.4, s = 3.0, c = COL_HAUL_PLUME, t = "thrust"
                    )
                )
            }
        }
    } else {
        val r = hy(tug.x, tug.y).let { if (it == 0.0) 1.0 else it }
        val ux = tug.x / r
        val uy = tug.y / r
        tug.a = atan2(uy, ux)
        tug.thr = 1.0
        tug.vx += ux * 6.0 * dt
        tug.vy += uy * 6.0 * dt
        tug.x += tug.vx * dt
        tug.y += tug.vy * dt
        if (H.t > 40.0) {
            tug.dead = true
            E.haul = null
        }
    }
    sprite(tug)
}

private fun startShower() {
    val W = GS.W ?: return
    val E = W.evt ?: return
    val f = GS.focus() ?: return
    if (hypot(f.x, f.y) < C.RS - 800.0) return
    E.shower = ShowerEvent(
        t = -12.0,
        dur = 22.0,
        a = atan2(f.y, f.x) + rr(::rand, -1.2, 1.2),
        x = f.x,
        y = f.y
    )
    banner("MICROMETEOROID STREAM in 12 s — get behind rock or dock", "bad")
    AU.alarm()
    W.stats.events++
}

private fun stepShower(dt: Double) {
    val W = GS.W ?: return
    val E = W.evt ?: return
    val S = E.shower ?: return
    val rf = ::rand
    S.t += dt
    if (S.t > 0.0 && S.t < S.dur && rf() < dt * 9.0) {
        val f = GS.focus()
        val fx = f?.x ?: S.x
        val fy = f?.y ?: S.y
        val dx = -cos(S.a)
        val dy = -sin(S.a)
        val o = rr(rf, -420.0, 420.0)
        val x0 = fx + cos(S.a) * 700.0 - dy * o
        val y0 = fy + sin(S.a) * 700.0 + dx * o
        val sp = rr(rf, 160.0, 240.0)
        W.meteors.add(
            Meteor(
                x = x0, y = y0, px = x0, py = y0,
                vx = dx * sp, vy = dy * sp,
                life = 6.0, r = rr(rf, 0.5, 1.4)
            )
        )
    }
    if (S.t > S.dur) E.shower = null
}

private fun stepFlare(dt: Double) {
    val W = GS.W ?: return
    val F = W.flare ?: return
    if (!F.on && F.warn <= 0.0) {
        F.next -= dt
        if (F.next <= 0.0) {
            F.warn = 40.0
            banner("SOLAR PARTICLE EVENT in 40 s — EVA crews get under a roof. Suit air burns ×3.5 outside.", "bad")
            AU.alarm()
            W.stats.events++
        }
    } else if (F.warn > 0.0) {
        F.warn -= dt
        if (F.warn <= 0.0) {
            F.on = true
            F.t = 35.0
            banner("PROTON STORM — shelter in a hull or pressurised room", "bad")
        }
    } else if (F.on) {
        F.t -= dt
        if (F.t <= 0.0) {
            F.on = false
            F.next = rr(::rand, 500.0, 800.0)
            banner("Storm passed. Back to work.")
        }
    }
}

private fun stepTransfers(dt: Double) {
    val W = GS.W ?: return
    val rf = ::rand
    for (key in arrayOf("ares", "gaia")) {
        val st = if (key == "ares") SITES.ares else SITES.gaia
        val T = st.transfer ?: continue
        if (st.gone) {
            if (W.time > T.back) arriveReplacement(key, st)
            continue
        }
        if (st.leaving) {
            val r = hy(st.x, st.y).let { if (it == 0.0) 1.0 else it }
            val ux = st.x / r
            val uy = st.y / r
            st.vx += (ux * 3.0 - uy * 4.0) * dt
            st.vy += (uy * 3.0 + ux * 4.0) * dt
            val ex = st.x - cos(st.a) * st.gw * st.cs * 0.5
            val ey = st.y - sin(st.a) * st.gw * st.cs * 0.5
            if (hy(ex - GS.cam.x, ey - GS.cam.y) < 2400.0) {
                for (i in 0 until 3) {
                    bit(
                        Bit(
                            x = ex + rr(rf, -10.0, 10.0),
                            y = ey + rr(rf, -10.0, 10.0),
                            vx = st.vx - cos(st.a) * 120.0 + rr(rf, -10.0, 10.0),
                            vy = st.vy - sin(st.a) * 120.0 + rr(rf, -10.0, 10.0),
                            life = 0.7, max = 0.7, s = rr(rf, 4.0, 8.0),
                            c = COL_TRANSFER_PLUME, t = "thrust"
                        )
                    )
                }
            }
            if (r > C.RS + 3200.0) {
                st.gone = true
                T.back = W.time + 100.0
                W.bodies.remove(st)
                W.stations.remove(st)
                for (c in W.contacts) if (c.b === st) c.done = true
            }
            continue
        }
        val left = T.depart - W.time
        if (left < 240.0 && !T.warned) {
            T.warned = true
            banner("${st.name} departs for ${if (T.dest == "mars") "MARS" else "EARTH"} in 4:00 — last-minute cargo pays a premium", "warn")
            feed("${st.name} opened its final loading window.", "warn")
        }
        if (left <= 0.0) depart(st)
    }
}

private fun depart(st: Body) {
    val W = GS.W ?: return
    val pl = GS.pl
    val ev = GS.ev
    st.leaving = true
    st.x0 = st.x
    st.y0 = st.y
    st.r0 = hy(st.x, st.y)
    for (s in W.ships) {
        if (s.dock?.b === st) {
            undock(s)
            if (s === pl) Hooks.toast("${st.name} is leaving — you were cast off", "warn")
        }
    }
    if (ev != null && !ev.hide) {
        val c = cos(-st.a)
        val n = sin(-st.a)
        val dx = ev.x - st.x
        val dy = ev.y - st.y
        val lx = dx * c - dy * n
        val ly = dx * n + dy * c
        if (abs(lx) < st.gw * st.cs / 2.0 && abs(ly) < st.gh * st.cs / 2.0) {
            Hooks.toast("You are aboard a departing ship — get out through an airlock!", "bad")
        }
    }
    val destLabel = if (st.transfer?.dest == "mars") "MARS" else "EARTH"
    banner("${st.name} BURNING FOR $destLabel", "warn")
    AU.boom(0.6)
    feed("${st.name} departed the band.", "good")
}

private fun arriveReplacement(key: String, old: Body) {
    val W = GS.W ?: return
    val oldTr = old.transfer ?: return
    val gen = oldTr.gen + 1
    val b = replaceTransfer(old, gen)
    val tx = b.x
    val ty = b.y
    val r = hy(tx, ty).let { if (it == 0.0) 1.0 else it }
    val sx = (tx / r) * (r + 2200.0)
    val sy = (ty / r) * (r + 2200.0)
    b.x = sx
    b.y = sy
    b.kin = true
    val arr = com.example.keres9.core.ArrivalAnim(t = 0.0, dur = 70.0, sx = sx, sy = sy, tx = tx, ty = ty)
    b.arr = arr
    b.mover = { q, dt ->
        val A = q.arr
        if (A != null) {
            A.t += dt
            val k = cl(A.t / A.dur, 0.0, 1.0)
            val e = 1.0 - (1.0 - k).pow(3.0)
            val px = q.x
            val py = q.y
            q.x = A.sx + (A.tx - A.sx) * e
            q.y = A.sy + (A.ty - A.sy) * e
            q.vx = (q.x - px) / dt
            q.vy = (q.y - py) / dt
            if (k >= 1.0) {
                q.arr = null
                q.mover = null
                q.kin = false
                q.vx = 0.0
                q.vy = 0.0
            }
        }
    }
    b.transfer?.let { it.depart = W.time + rr(::rand, 900.0, 1300.0) }
    W.bodies.add(b)
    W.stations.add(b)
    if (key == "ares") SITES.ares = b else SITES.gaia = b
    mkJobs(b)
    banner("${b.name} arriving in the band — new loading window open")
    feed("${b.name} arrived. Buyers aboard.", "good")
}

fun flareOn(): Boolean = GS.W?.flare?.on == true
