package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.Bit
import com.example.keres9.core.C
import com.example.keres9.core.Chamber
import com.example.keres9.core.Contact
import com.example.keres9.core.GS
import com.example.keres9.core.PAL
import com.example.keres9.core.ScanEntry
import com.example.keres9.core.SurveyProgress
import com.example.keres9.core.ad
import com.example.keres9.core.hy
import com.example.keres9.core.rand
import com.example.keres9.world.cw
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class ScanOrigin(val x: Double, val y: Double, val a: Double, val r: Double)

fun scanOrg(): ScanOrigin {
    val ev = GS.ev
    val pl = GS.pl
    if (ev != null || pl == null) {
        return if (ev != null) ScanOrigin(ev.x, ev.y, ev.a, C.EVA.ping * 1.7)
        else ScanOrigin(0.0, 0.0, 0.0, C.EVA.ping * 1.7)
    }
    val m = alive(pl, "scan").firstOrNull()
    val w = if (m != null) mw(pl, m) else null
    return ScanOrigin(w?.x ?: pl.x, w?.y ?: pl.y, pl.a, pl.scanR)
}

/** a contact's current (drifting) resonance */
fun contactFreq(c: Contact): Double {
    val t = GS.W?.time ?: 0.0
    return c.f + sin(t * 0.35 + c.ph) * 1.8 + sin(t * 0.11 + c.ph * 2.0) * 0.9
}

fun updScan(dt: Double) {
    val W = GS.W ?: return
    val PC = GS.PC
    val pl = GS.pl
    val ev = GS.ev
    PC.list.clear()
    PC.sig = 0.0
    PC.survey = null
    if (!PC.scan || ev != null || pl == null) {
        PC.lock = 0.0
        return
    }
    val o = scanOrg()
    var sv: com.example.keres9.core.Body? = null
    var sp = -1.0
    for (b in W.rocks) {
        if (b.cat != 0 || b.dead) continue
        val dx = b.x - o.x
        val dy = b.y - o.y
        val d = hy(dx, dy) - b.rad * 0.6
        if (d > o.r) continue
        val half = atan2(b.rad, max(1.0, hy(dx, dy)))
        if (abs(ad(atan2(dy, dx), o.a)) > C.SCAN.cone + half) continue
        b.survey += dt / (C.SCAN.survey * (0.6 + b.rVal / 40.0))
        if (b.survey > sp) {
            sp = b.survey
            sv = b
        }
        if (b.survey >= 1.0) Hooks.onCatalog(b, "array")
    }
    if (sv != null && sv.cat == 0) {
        PC.survey = SurveyProgress(sv, min(1.0, sv.survey))
    }
    var best: Contact? = null
    var bs = 0.0
    for (c in W.contacts) {
        if (c.done) continue
        val dx = c.x - o.x
        val dy = c.y - o.y
        val d = hy(dx, dy)
        if (d > o.r || abs(ad(atan2(dy, dx), o.a)) > C.SCAN.cone) continue
        val f = contactFreq(c)
        val e = abs(PC.freq - f)
        val s = if (e < C.SCAN.band) (1.0 - e / C.SCAN.band) * (1.0 - (d / o.r) * 0.45) else 0.0
        PC.list.add(ScanEntry(c, f, s, d))
        if (s > bs) {
            bs = s
            best = c
        }
    }
    PC.sig = bs
    if (best != null && bs > 0.05) {
        if (PC.lockC !== best) {
            PC.lockC = best
            PC.lock = 0.0
        }
        PC.lock = min(1.0, PC.lock + dt * bs * 0.62)
        if (rand() < dt * (2.0 + bs * 26.0)) AU.geiger()
        if (rand() < dt * 4.0) AU.tick(bs)
        if (PC.lock >= 1.0) resolve(best)
    } else {
        PC.lock = max(0.0, PC.lock - dt * 0.5)
        PC.lockC = null
    }
    PC.scanLabel = when {
        best != null -> "DEEP CONTACT ▸ ${best.label} · ${hy(best.x - o.x, best.y - o.y).roundToInt()}m · signal ${(bs * 100.0).roundToInt()}% · HOLD THE LOCK"
        PC.survey != null -> "SURVEYING ${PC.survey!!.b.name} · ${(PC.survey!!.p * 100.0).roundToInt()}% — keep it in the cone"
        else -> "sweep the cone across bodies · uncatalogued rock is surveyed automatically · drag the dial for deep resonances"
    }
}

fun resolve(c: Contact) {
    val W = GS.W ?: return
    val PC = GS.PC
    c.done = true
    PC.lock = 0.0
    PC.lockC = null
    W.stats.found++
    AU.chime()
    bit(
        Bit(
            x = c.x, y = c.y, vx = 0.0, vy = 0.0, life = 0.9, max = 0.9, s = 10.0,
            c = if (c.kind == "claim") PAL.helion else PAL.mag, t = "ring"
        )
    )
    val ch = nearestCham(c)
    if (ch != null) ch.disc = true
    val msg = when (c.kind) {
        "void" -> "HOLLOW CHAMBER · ${ch?.biome ?: ""} · sealed. Cut through the wall."
        "vug" -> "METAL VUG — platinum-group crystals lining a sealed pocket."
        "vein" -> {
            c.b.veinOn = true
            "ORE VEIN RESOLVED — platinum-group highlighted through rock."
        }
        "claim" -> "HELION MINERALS claim transponder — on an UNCATALOGUED body. Somebody staked this before the catalog went dark."
        "core" -> "KERES-9 CORE SHARD — a memory lattice from the dead catalog, sealed in rock. Cut it out and carry it home."
        "beacon" -> "SOURCE PINNED — ${c.label}"
        else -> c.label
    }
    Hooks.toast("DEEP SCAN · $msg", "good")
    feed("Array resolved a ${c.kind} contact inside ${c.b.name}.", "good")
    Hooks.resolved(c)
}

private fun nearestCham(c: Contact): Chamber? {
    val b = c.b
    var ch: Chamber? = null
    var bd = 1e9
    for (q in b.cham) {
        val w = cw(b, q.cx, q.cy)
        val d = hy(w.x - c.x, w.y - c.y)
        if (d < bd) {
            bd = d
            ch = q
        }
    }
    return if (bd < b.cs * 6.0) ch else null
}
