package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.CLS
import com.example.keres9.core.FeedEntry
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.HoldView
import com.example.keres9.core.ServiceItem
import com.example.keres9.core.Ship
import com.example.keres9.core.YardItem
import com.example.keres9.core.cl
import com.example.keres9.core.fm
import com.example.keres9.core.hy
import com.example.keres9.world.SITES
import com.example.keres9.world.cw
import com.example.keres9.world.pF
import com.example.keres9.world.pR
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

fun feed(m: String, k: String = "") {
    val W = GS.W ?: return
    W.feed.add(0, FeedEntry(W.time, m, k))
    if (W.feed.size > 50) W.feed.removeAt(W.feed.lastIndex)
}

/** what the player can trade from right now: the ship's hold, or the suit's sack */
fun hold(): HoldView? {
    val pl = GS.pl
    if (pl != null) return HoldView(pl.cargo, pl.cargoMax, cmass(pl), pl.name, pl)
    val ev = GS.ev
    val G = GS.G
    if (ev != null && G != null) {
        val cap = floor(C.EVA.sack * (if (G.sackUp > 0.0) G.sackUp else 1.0)).toInt()
        return HoldView(ev.sack, cap, sackN(ev), "SUIT SACK", null)
    }
    return null
}

private fun holdAdd(h: HoldView, g: String, q: Int): Int {
    val ship = h.ship
    if (ship != null) return cadd(ship, g, q)
    val t = min(q, h.max - h.used)
    if (t > 0) h.c[g] = (h.c[g] ?: 0) + t
    return max(0, t)
}

private fun fever(st: Body): Double {
    val tr = st.transfer ?: return 1.0
    val W = GS.W ?: return 1.0
    return if (tr.depart > 0.0) 1.0 + cl(1.0 - (tr.depart - W.time) / 240.0, 0.0, 1.0) * 0.35 else 1.0
}

fun price(st: Body, g: String, sell: Boolean = false): Int {
    val e = st.econ?.get(g) ?: return 0
    val def = GD[g] ?: return 0
    val p = def.p * e.mul * cl((e.ref / (e.stock + 6.0)).pow(0.5), 0.45, 2.4) * fever(st)
    return max(1, (if (sell) p * 0.88 else p).roundToInt())
}

fun trade(st: Body, g: String, n: Int, buy: Boolean) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    val h = hold() ?: return
    val e = st.econ?.get(g) ?: return
    val def = GD[g] ?: return
    if (buy) {
        var k = 0
        while (k < n && G.cr >= price(st, g) && e.stock >= 1.0) {
            val p = price(st, g)
            val liveHold = hold() ?: break
            if (holdAdd(liveHold, g, 1) <= 0) break
            G.cr -= p
            e.stock -= 1.0
            k++
        }
        if (k > 0) {
            AU.blip()
            Hooks.toast("BOUGHT $k ${def.n}", null)
        }
    } else {
        var k = 0
        var earned = 0
        while (k < n && (h.c[g] ?: 0) > 0) {
            val p = price(st, g, sell = true)
            h.c[g] = (h.c[g] ?: 0) - 1
            G.cr += p
            earned += p
            e.stock += 1.0
            k++
        }
        if ((h.c[g] ?: 0) <= 0) h.c.remove(g)
        h.ship?.let { stats(it) }
        if (k > 0) {
            AU.cash()
            W.stats.earned += earned
            W.stats.delivered += k
            Hooks.toast("SOLD $k ${def.n} · +${fm(earned)}₡", "good")
            feed("You sold $k ${def.n} to ${st.name}.")
            Hooks.delivered(st, g, k)
        }
    }
}

fun econTick(dt: Double) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    G._e += dt
    if (G._e < 5.0) return
    val step = G._e / 60.0
    G._e = 0.0
    for (st in W.stations) {
        val econ = st.econ ?: continue
        for ((g, q) in econ) {
            if (q.cons > 0.0) q.stock = max(0.0, q.stock - q.cons * step)
            if (q.prod > 0.0) q.stock = min(q.ref * 3.0, q.stock + q.prod * step)
            if (q.ref > 0.0 && q.stock < q.ref * 0.2) q.stock += q.ref * 0.05 * step
            q.hist.add(price(st, g))
            if (q.hist.size > 26) q.hist.removeAt(0)
        }
    }
    for (st in W.stations) patchStation(st, 3)
}

private val PATCH_NEIGHBORS = arrayOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)

fun patchStation(st: Body, n: Int) {
    val sol0 = st.sol0 ?: return
    val flr0 = st.flr0 ?: return
    val W = GS.W ?: return
    val ev = GS.ev
    if (st.cut.isEmpty()) return
    for (k in 0 until n) {
        if (st.cut.isEmpty()) break
        val i = st.cut.removeAt(0)
        if (st.sol[i] != 0 || sol0[i] == 0) continue
        val cx = i % st.gw
        val cy = i / st.gw
        val w = cw(st, cx, cy)
        val blocked = W.ships.any { s -> !s.dead && hy(s.x - w.x, s.y - w.y) < s.rad + st.cs } ||
            (ev != null && hy(ev.x - w.x, ev.y - w.y) < 4.0)
        if (blocked) {
            st.cut.add(i)
            continue
        }
        st.sol[i] = sol0[i]
        st.flr[i] = flr0[i]
        st.dmg.remove(i)
        for ((dx, dy) in PATCH_NEIGHBORS) {
            val x = cx + dx
            val y = cy + dy
            if (x in 0 until st.gw && y in 0 until st.gh) pR(st, x, y)
        }
        pF(st, cx, cy)
    }
}

private data class UpgSpec(val n: String, val d: String, val c0: Int)
private val UPG = linkedMapOf(
    "eng" to UpgSpec("ENGINE TUNE", "thrust +16%", 900),
    "cargo" to UpgSpec("CARGO RACKS", "hold +12", 700),
    "laser" to UpgSpec("LASER FOCUS", "cut rate & range", 800),
    "fuel" to UpgSpec("AUX TANKS", "propellant +35", 600),
    "scan" to UpgSpec("ARRAY GAIN", "scanner range +160 m", 650)
)

private data class SuitUpgSpec(val key: String, val n: String, val d: String, val c: Int, val v: Double)
private val SUIT_UPG = listOf(
    SuitUpgSpec("airUp", "SUIT · BIG TANK", "air +60%", 260, 1.6),
    SuitUpgSpec("sackUp", "SUIT · DEEP SACK", "sack +75%", 320, 1.75),
    SuitUpgSpec("suitUp", "SUIT · HOT CUTTER", "hand cutter +70%", 380, 1.7)
)

fun shipAt(st: Body): Ship? {
    val G = GS.G ?: return null
    return G.fleet.firstOrNull { !it.dead && it.dock?.b === st }
}

fun services(st: Body): List<ServiceItem> {
    val G = GS.G ?: return emptyList()
    val pl = GS.pl
    val L = mutableListOf<ServiceItem>()
    val s = if (pl != null && pl.dock?.b === st) pl else shipAt(st)
    if (s != null) {
        val need = ceil(s.fuelMax - s.fuel).toInt()
        val fp = price(st, "fuel").let { if (it <= 0) 6 else it }
        L.add(
            ServiceItem(
                l = "REFUEL ${s.name}",
                d = "$need units propellant @ $fp₡",
                c = max(0, need * fp),
                min = max(0, min(need * fp, fp)),
                ok = need > 0,
                f = {
                    val k = min(need, G.cr / fp)
                    s.fuel += k
                    G.cr -= k * fp
                    st.econ?.get("fuel")?.let { fe -> fe.stock = max(0.0, fe.stock - k) }
                    AU.blip()
                }
            )
        )
        var dmg = 0.0
        for (m in s.mods) dmg += m.max - max(0.0, m.hp)
        val rc = ceil(dmg * 2.2).toInt()
        L.add(
            ServiceItem(
                l = "REPAIR ${s.name}",
                d = "hull ${(integ(s) * 100.0).roundToInt()}% → 100%",
                c = rc,
                ok = dmg > 0.0,
                f = {
                    for (m in s.mods) {
                        m.hp = m.max
                        m.dead = false
                    }
                    G.cr -= rc
                    stats(s)
                    s.dirty = true
                    AU.thunk()
                }
            )
        )
        if (st.transfer == null) {
            for ((k, spec) in UPG) {
                val lv = s.upg[k] ?: 0
                val c = (spec.c0 * (1.0 + lv * 0.8)).roundToInt()
                if (k == "eng" && s.cls == "wren" && lv >= 1) continue
                if (lv < 3) {
                    L.add(
                        ServiceItem(
                            l = "${spec.n} · L${lv + 1}",
                            d = "${spec.d} (${s.name})",
                            c = c,
                            ok = true,
                            f = {
                                s.upg[k] = lv + 1
                                G.cr -= c
                                val f = s.fuel
                                stats(s)
                                s.fuel = min(s.fuelMax, f)
                                AU.chime()
                            }
                        )
                    )
                }
            }
        }
    }
    if (st === SITES.sel || st === SITES.depot) {
        for (spec in SUIT_UPG) {
            val cur = when (spec.key) {
                "airUp" -> G.airUp
                "sackUp" -> G.sackUp
                else -> G.suitUp
            }
            if (cur < spec.v) {
                L.add(
                    ServiceItem(
                        l = spec.n,
                        d = spec.d,
                        c = spec.c,
                        ok = true,
                        f = {
                            when (spec.key) {
                                "airUp" -> G.airUp = spec.v
                                "sackUp" -> G.sackUp = spec.v
                                "suitUp" -> G.suitUp = spec.v
                            }
                            G.cr -= spec.c
                            AU.chime()
                        }
                    )
                )
            }
        }
    }
    if (L.isEmpty()) {
        L.add(ServiceItem(l = "NO SERVICES", d = "bring a hull here to refuel, repair or refit", c = 0, ok = false, f = {}))
    }
    return L
}

fun yard(st: Body): List<YardItem> {
    val G = GS.G ?: return emptyList()
    return st.yard.mapNotNull { cls ->
        val K = CLS[cls] ?: return@mapNotNull null
        val owned = G.fleet.any { !it.dead && it.cls == cls }
        val desc = if (cls == "wren") {
            "Orbit-only tug. Weak engine (cannot lift off the surface), mining laser, array, 26 hold. Berths at SELENE HIGH."
        } else {
            "Surface-to-orbit lander. Triple engine, landing gear, 44 hold, laser & array. Sits on Shackleton PAD 1 — fly it up the well yourself."
        }
        YardItem(cls = cls, n = K.n, c = K.price, owned = owned, d = desc)
    }
}

fun buyShip(st: Body, cls: String): Ship? {
    val G = GS.G ?: return null
    val K = CLS[cls] ?: return null
    if (G.cr < K.price) {
        Hooks.toast("Not enough credits", "bad")
        return null
    }
    if (cls == "hare") {
        val pad = st.pads.firstOrNull { it.occ == null } ?: st.pads.firstOrNull()
        if (pad == null || pad.occ != null) {
            Hooks.toast("No free berth — clear the pad first", "bad")
            return null
        }
        G.cr -= K.price
        val s = mkOwned(cls, st.x, st.y)
        dockPad(s, st, pad, silent = true)
        sprite(s)
        G.fleet.add(s)
        G.tier = max(G.tier, 3)
        AU.chime(); AU.cash()
        Hooks.toast("HARE IS YOURS · on ${pad.name} — BOARD from the yard or float over and press F", "good")
        feed("A new ${K.n} changed hands at ${st.name}.", "good")
        Hooks.bought(s)
        return s
    } else {
        val port = st.ports.firstOrNull { it.berth && it.occ == null }
            ?: st.ports.firstOrNull { it.occ == null && !it.crew }
        if (port == null || port.occ != null) {
            Hooks.toast("No free berth — clear the pad first", "bad")
            return null
        }
        G.cr -= K.price
        val s = mkOwned(cls, st.x, st.y)
        dockPort(s, st, port, silent = true)
        sprite(s)
        G.fleet.add(s)
        G.tier = max(G.tier, 2)
        AU.chime(); AU.cash()
        Hooks.toast("WREN IS YOURS · clamped at a berth — BOARD from the yard or float over and press F", "good")
        feed("A new ${K.n} changed hands at ${st.name}.", "good")
        Hooks.bought(s)
        return s
    }
}

fun bountyFor(b: Body): Int {
    val typeBonus = when (b.type) {
        "comet" -> 40
        "metal" -> 30
        else -> 0
    }
    return (25.0 + b.rVal * 1.6 + typeBonus + b.cham.count { !it.open } * 12.0).roundToInt()
}

fun collectBounty(): Int {
    val G = GS.G ?: return 0
    val W = GS.W ?: return 0
    if (G.bounty <= 0) return 0
    val v = G.bounty
    G.cr += v
    W.stats.earned += v
    G.bounty = 0
    AU.cash()
    Hooks.toast("CATALOG OFFICE pays ${fm(v)}₡ in survey bounties", "good")
    return v
}
