package com.example.keres9.world

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.example.keres9.core.BackgroundAssets
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.Chamber
import com.example.keres9.core.Contact
import com.example.keres9.core.EconomyItem
import com.example.keres9.core.GS
import com.example.keres9.core.GlowSpot
import com.example.keres9.core.LightSource
import com.example.keres9.core.MAT
import com.example.keres9.core.PAL
import com.example.keres9.core.Pad
import com.example.keres9.core.Pick
import com.example.keres9.core.Port
import com.example.keres9.core.Room
import com.example.keres9.core.SeededRng
import com.example.keres9.core.StationCrew
import com.example.keres9.core.TAU
import com.example.keres9.core.TransferInfo
import com.example.keres9.core.Vec2
import com.example.keres9.core.FogPlume
import com.example.keres9.core.WorldState
import com.example.keres9.core.hy
import com.example.keres9.core.lp
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.pk
import com.example.keres9.core.rand
import com.example.keres9.core.ri
import com.example.keres9.core.rr
import com.example.keres9.core.sh
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

object SITES {
    lateinit var hab: Body
    lateinit var lift: Body
    lateinit var depot: Body
    lateinit var tycho: Body
    lateinit var sel: Body
    lateinit var vul: Body
    lateinit var ares: Body
    lateinit var gaia: Body
}

fun addPick(x: Double, y: Double, t: String, q: Int, vx: Double = 0.0, vy: Double = 0.0): Pick {
    val p = Pick(
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        a = rand() * TAU,
        va = (rand() - 0.5) * 0.6,
        type = t,
        qty = q,
        r = 2.2 + q * 0.7
    )
    GS.W?.picks?.add(p)
    return p
}

/** scanner contact anchored to a body (follows it if the body moves) */
fun addContact(r: () -> Double, kind: String, x: Double, y: Double, b: Body, label: String): Contact {
    val c = cos(-b.a)
    val s = sin(-b.a)
    val dx = x - b.x
    val dy = y - b.y
    val ct = Contact(
        x = x,
        y = y,
        b = b,
        kind = kind,
        label = label,
        f = rr(r, 30.0, 190.0),
        ph = r() * TAU,
        lx = dx * c - dy * s,
        ly = dx * s + dy * c
    )
    GS.W?.contacts?.add(ct)
    b.contacts.add(ct)
    return ct
}

/** world pose on a longitude/radius; bodies are turned so local -y points away from the Moon */
data class PoseAngle(val x: Double, val y: Double, val a: Double)
fun atPose(th: Double, r: Double): PoseAngle = PoseAngle(cos(th) * r, sin(th) * r, th + PI / 2.0)

private val ROCK_TYPES = mapOf(
    "rock" to "stony (S-type)",
    "metal" to "metallic (M-type)",
    "ice" to "carbonaceous / icy (C-type)",
    "comet" to "cometary nucleus"
)

data class RockOpts(
    val rich: Boolean = false,
    val claim: Boolean = false,
    val core: Boolean = false
)

fun mkRock(r: () -> Double, x: Double, y: Double, id: Int, R: Double, type: String, o: RockOpts = RockOpts()): Body {
    val gw = ceil(R * 2.0).toInt() + 8
    val b = Body("rock", x, y, gw, gw, C.CS, ri(r, 0, 1_000_000_000))
    b.uid = id
    b.name = "UNC-" + (1000 + ((id * 7919) % 9000))
    b.rVal = R
    b.type = type
    b.cat = 0
    b.a = r() * TAU
    b.origin = if (type == "comet") {
        pk(r, listOf("Oort cloud", "Kuiper belt", "Jupiter-family"))
    } else {
        pk(r, listOf("main belt", "main belt", "Hungaria group", "near-Earth", "Hilda group"))
    }
    val c0 = gw / 2.0
    val p1 = r() * TAU
    val p2 = r() * TAU
    val h1 = ri(r, 2, 4)
    val h2 = ri(r, 5, 8)
    val ell = if (type == "comet") 0.75 else 1.0
    fun rad(t: Double): Double = R * (0.78 + 0.12 * sin(t * h1 + p1) + 0.08 * sin(t * h2 + p2))
    val orig = ByteArray(gw * gw)
    for (cy in 0 until gw) {
        for (cx in 0 until gw) {
            val dx = cx - c0
            val dy = (cy - c0) / ell
            val d = hy(dx, dy)
            if (d < rad(atan2(dy, dx))) {
                val i = cy * gw + cx
                orig[i] = 1
                val n = (((cx * 2654435761L) xor (cy * 40503L) xor b.seed.toLong()) and 0xFFFFFFFFL)
                b.sol[i] = if (n % 100L < 22L) 2 else 1
                if (type == "ice" && d > R * 0.62 && n % 100L < 70L) b.sol[i] = 3
                if (type == "metal" && n % 100L < 30L) b.sol[i] = 4
                if (type == "comet") b.sol[i] = when {
                    n % 100L < 55L -> 3
                    n % 100L < 82L -> 8
                    else -> 1
                }
            }
        }
    }
    // nickel-iron veins
    val veinCount = ri(r, if (type == "metal") 4 else 1, if (type == "metal") 8 else 4)
    for (v in 0 until veinCount) {
        var vx = c0 + rr(r, -R * 0.5, R * 0.5)
        var vy = c0 + rr(r, -R * 0.5, R * 0.5)
        var va = r() * TAU
        val steps = ri(r, 18, 50)
        for (s in 0 until steps) {
            va += rr(r, -0.5, 0.5)
            vx += cos(va)
            vy += sin(va)
            val cx = round(vx).toInt()
            val cy = round(vy).toInt()
            if (cx < 1 || cy < 1 || cx >= gw - 1 || cy >= gw - 1) break
            val i = cy * gw + cx
            if (b.sol[i] != 0 && b.sol[i] != 3) b.sol[i] = 4
        }
    }
    // platinum-group pockets
    val pgmCount = ri(r, if (type == "metal") 2 else 0, if (type == "metal") 5 else 2) + (if (o.rich) 8 else 0)
    for (p in 0 until pgmCount) {
        val px = round(c0 + rr(r, -R * 0.45, R * 0.45)).toInt()
        val py = round(c0 + rr(r, -R * 0.45, R * 0.45)).toInt()
        val kCount = ri(r, 3, 8) + (if (o.rich) 6 else 0)
        for (k in 0 until kCount) {
            val cx = px + ri(r, -2, 2)
            val cy = py + ri(r, -2, 2)
            if (cx > 0 && cy > 0 && cx < gw && cy < gw && b.sol[cy * gw + cx] != 0) {
                b.sol[cy * gw + cx] = 5
                b.veins.add(cy * gw + cx)
            }
        }
    }
    if (o.rich) {
        for (p in 0 until 16) {
            val t = (p / 16.0) * TAU + rr(r, -0.15, 0.15)
            val d = rad(t) * rr(r, 0.7, 0.84)
            val px = round(c0 + cos(t) * d).toInt()
            val py = round(c0 + sin(t) * d * ell).toInt()
            for (k in 0 until 9) {
                val cx = px + ri(r, -2, 2)
                val cy = py + ri(r, -2, 2)
                if (cx > 0 && cy > 0 && cx < gw && cy < gw && b.sol[cy * gw + cx] != 0) {
                    b.sol[cy * gw + cx] = 5
                    b.veins.add(cy * gw + cx)
                }
            }
        }
    }
    // surface craters (vapour plumes on comets)
    for (c2 in 0 until ri(r, 2, 5)) {
        val t = r() * TAU
        val cr = ri(r, 3, 7)
        val cd = rad(t) * 0.92
        val ccx = c0 + cos(t) * cd
        val ccy = c0 + sin(t) * cd * ell
        val yStart = max(0, (ccy - cr).toInt())
        val yEnd = min(gw, (ccy + cr).toInt())
        val xStart = max(0, (ccx - cr).toInt())
        val xEnd = min(gw, (ccx + cr).toInt())
        for (cy in yStart until yEnd) {
            for (cx in xStart until xEnd) {
                if (hy(cx - ccx, cy - ccy) < cr && orig[cy * gw + cx].toInt() != 0) {
                    b.sol[cy * gw + cx] = 0
                    b.flr[cy * gw + cx] = 1
                }
            }
        }
        if (type == "ice" || type == "comet") {
            b.fog.add(FogPlume((ccx - c0) * C.CS, (ccy - c0) * C.CS, cr * C.CS * 1.5, r() * TAU, if (type == "comet") t else null))
        }
    }
    fun disc(cx: Double, cy: Double, cr: Double, f: Int) {
        val y0 = max(1, (cy - cr).toInt())
        val y1 = min(gw - 1, (cy + cr + 1).toInt())
        val x0 = max(1, (cx - cr).toInt())
        val x1 = min(gw - 1, (cx + cr + 1).toInt())
        for (y2 in y0 until y1) {
            for (x2 in x0 until x1) {
                if (hy(x2 - cx, y2 - cy) < cr && orig[y2 * gw + x2].toInt() != 0) {
                    b.sol[y2 * gw + x2] = 0
                    b.flr[y2 * gw + x2] = f
                }
            }
        }
    }
    fun path(x0: Double, y0: Double, x1: Double, y1: Double, w: Double, f: Int) {
        val n = ceil(hy(x1 - x0, y1 - y0) * 2.0).toInt() + 1
        for (s in 0..n) {
            val t = s.toDouble() / n
            disc(lp(x0, x1, t) + sin(t * 9.0 + p1) * 1.4, lp(y0, y1, t) + cos(t * 7.0 + p2) * 1.4, w / 2.0, f)
        }
    }
    // chambers
    if (R >= 17.0) {
        val n = min(5, 1 + floor(R / 13.0).toInt())
        for (i in 0 until n) {
            val t = r() * TAU
            val d = r() * R * 0.5
            var biome = "void"
            var chR = ri(r, 5, min(14.0, R * 0.34).toInt())
            if (o.claim && i == 0) {
                biome = "claim"
                chR = max(chR, 7)
            } else if (o.core && i == (if (o.claim) 1 else 0)) {
                biome = "core"
                chR = max(chR, 5)
            } else if (o.rich && i == 0) {
                biome = "vug"
                chR = max(chR, 11)
            } else {
                val q = r()
                biome = when {
                    type == "ice" || type == "comet" -> if (q < 0.6) "ice" else "void"
                    q < 0.35 -> "void"
                    q < 0.7 -> "geode"
                    type == "metal" -> "vug"
                    else -> "ice"
                }
            }
            val ch = Chamber(
                cx = round(c0 + cos(t) * d).toInt(),
                cy = round(c0 + sin(t) * d * ell).toInt(),
                r = chR,
                biome = biome
            )
            val f = when (biome) {
                "ice" -> 5
                "geode", "vug" -> 7
                "claim", "core" -> 8
                else -> 1
            }
            disc(ch.cx.toDouble(), ch.cy.toDouble(), ch.r.toDouble(), f)
            if (biome == "geode" || biome == "vug" || biome == "ice") {
                val kMax = (ch.r * (if (biome == "vug" && o.rich) 7.0 else 2.2)).toInt()
                for (k in 0 until kMax) {
                    val t2 = r() * TAU
                    val d2 = ch.r + rr(r, 0.0, if (biome == "vug" && o.rich) 4.0 else 1.4)
                    val cx = round(ch.cx + cos(t2) * d2).toInt()
                    val cy = round(ch.cy + sin(t2) * d2).toInt()
                    if (cx > 0 && cy > 0 && cx < gw && cy < gw && b.sol[cy * gw + cx] != 0) {
                        b.sol[cy * gw + cx] = when (biome) {
                            "geode" -> 6
                            "vug" -> 5
                            else -> 3
                        }
                    }
                }
            }
            if (biome == "claim" || biome == "core") {
                b.lights.add(
                    LightSource(
                        (ch.cx + 0.5 - c0) * C.CS,
                        (ch.cy + 0.5 - c0) * C.CS,
                        if (biome == "claim") PAL.helion else PAL.cyan,
                        40.0,
                        true
                    )
                )
            }
            b.cham.add(ch)
        }
        for (i in 1 until b.cham.size) {
            if (b.cham[i].biome != "core" && b.cham[i - 1].biome != "core") {
                path(b.cham[i - 1].cx.toDouble(), b.cham[i - 1].cy.toDouble(), b.cham[i].cx.toDouble(), b.cham[i].cy.toDouble(), 2.0, 1)
            }
        }
        val ch = b.cham.firstOrNull { it.biome != "core" && it.biome != "claim" && !(o.rich && it.biome == "vug") }
        if (ch != null && r() < 0.75) {
            val t = atan2(ch.cy - c0, ch.cx - c0) + rr(r, -0.4, 0.4)
            path(ch.cx.toDouble(), ch.cy.toDouble(), c0 + cos(t) * (R + 3.0), c0 + sin(t) * (R + 3.0), if (ch.r >= 8) 8.0 else 3.0, 1)
        }
    }
    // flood-fill from border
    val open = ByteArray(gw * gw)
    val st = ArrayList<Int>(gw * 4)
    for (cx in 0 until gw) {
        if (b.sol[cx] == 0) st.add(cx)
        val i = (gw - 1) * gw + cx
        if (b.sol[i] == 0) st.add(i)
    }
    for (cy in 0 until gw) {
        val i = cy * gw
        if (b.sol[i] == 0) st.add(i)
        val j = i + gw - 1
        if (b.sol[j] == 0) st.add(j)
    }
    while (st.isNotEmpty()) {
        val i = st.removeAt(st.lastIndex)
        if (open[i].toInt() != 0 || b.sol[i] != 0) continue
        open[i] = 1
        val cx = i % gw
        val cy = i / gw
        if (cx > 0 && b.sol[i - 1] == 0) st.add(i - 1)
        if (cx < gw - 1 && b.sol[i + 1] == 0) st.add(i + 1)
        if (cy > 0 && b.sol[i - gw] == 0) st.add(i - gw)
        if (cy < gw - 1 && b.sol[i + gw] == 0) st.add(i + gw)
    }
    for (ch in b.cham) {
        ch.open = open[ch.cy * gw + ch.cx].toInt() != 0
        val wp = cw(b, ch.cx, ch.cy)
        if (!ch.open || ch.biome == "claim" || ch.biome == "core") {
            val cKind = when (ch.biome) {
                "claim" -> "claim"
                "core" -> "core"
                "vug" -> "vug"
                else -> "void"
            }
            val cLabel = when (ch.biome) {
                "claim" -> "structured transponder · sealed"
                "core" -> "cold memory lattice · sealed"
                "vug" -> "dense metal pocket"
                else -> "hollow chamber · ${ch.biome}"
            }
            ch.contact = addContact(r, cKind, wp.x, wp.y, b, cLabel)
        }
        val loot: List<Pair<String, Int>> = when (ch.biome) {
            "geode" -> listOf("ree" to ri(r, 1, 3))
            "ice" -> listOf("ice" to ri(r, 2, 4))
            "vug" -> listOf("pgm" to ri(r, 1, 3))
            "claim" -> listOf("parts" to ri(r, 1, 3), "pgm" to ri(r, 1, 2))
            "core" -> listOf("core" to 1)
            "void" -> if (r() < 0.4) listOf("fe" to ri(r, 1, 2)) else emptyList()
            else -> emptyList()
        }
        for ((g, q) in loot) {
            val p = addPick(wp.x + rr(r, -6.0, 6.0), wp.y + rr(r, -6.0, 6.0), g, q)
            val c = cos(-b.a)
            val sn = sin(-b.a)
            val dx = p.x - b.x
            val dy = p.y - b.y
            p.home = b
            p.hx = dx * c - dy * sn
            p.hy = dx * sn + dy * c
        }
    }
    if (b.veins.size > 3) {
        val v = cw(b, b.veins[0] % gw, b.veins[0] / gw)
        addContact(r, "vein", v.x, v.y, b, "platinum-group concentration")
    }
    for (cy in 0 until gw) {
        for (cx in 0 until gw) {
            pR(b, cx, cy)
            pF(b, cx, cy)
        }
    }
    for (cy in 1 until gw - 1) {
        for (cx in 1 until gw - 1) {
            val i = cy * gw + cx
            if (b.flr[i] == 0) continue
            val col: Int? = when {
                b.sol[i - 1] == 6 || b.sol[i + 1] == 6 || b.sol[i - gw] == 6 || b.sol[i + gw] == 6 -> PAL.mag
                b.sol[i - 1] == 5 || b.sol[i + 1] == 5 -> if (r() < 0.3) PAL.gold else null
                b.flr[i] == 5 && r() < 0.05 -> PAL.ice
                else -> null
            }
            if (col != null) {
                b.glow.add(GlowSpot((cx + 0.5 - c0) * C.CS, (cy + 0.5 - c0) * C.CS, col, rr(r, 5.0, 12.0), open[i].toInt() != 0))
            }
        }
    }
    val comp = mutableMapOf<String, Int>()
    var tot = 0
    for (m in b.sol) {
        if (m == 0) continue
        tot++
        val ore = MAT.getOrNull(m)?.ore ?: "rock"
        comp[ore] = (comp[ore] ?: 0) + 1
    }
    b.comp = comp
    b.cells = tot
    b.typeN = ROCK_TYPES[type] ?: type
    b.rad = R * C.CS + 10.0
    ensureBodyBitmaps(b)
    return b
}

// ─── station construction kit ────────────────────────────────────────────
private fun wall(b: Body, x: Int, y: Int, w: Int, h: Int) {
    for (cy in y until y + h) for (cx in x until x + w) {
        if (cx == x || cy == y || cx == x + w - 1 || cy == y + h - 1) b.sol[cy * b.gw + cx] = 7
    }
}
private fun block(b: Body, x: Int, y: Int, w: Int, h: Int, m: Int = 7) {
    for (cy in y until y + h) for (cx in x until x + w) {
        if (cx in 0 until b.gw && cy in 0 until b.gh) b.sol[cy * b.gw + cx] = m
    }
}
private fun fill(b: Body, x: Int, y: Int, w: Int, h: Int, f: Int) {
    for (cy in y until y + h) for (cx in x until x + w) {
        b.flr[cy * b.gw + cx] = f
    }
}
private fun doorH(b: Body, x: Int, y: Int, w: Int) {
    for (cx in x until x + w) {
        b.sol[y * b.gw + cx] = 0
        b.flr[y * b.gw + cx] = 2
    }
}
private fun doorV(b: Body, x: Int, y: Int, h: Int) {
    for (cy in y until y + h) {
        b.sol[cy * b.gw + x] = 0
        b.flr[cy * b.gw + x] = 2
    }
}
private fun mouth(b: Body, x: Int, y: Int, w: Int, h: Int) {
    for (cy in y until y + h) for (cx in x until x + w) {
        val i = cy * b.gw + cx
        b.sol[i] = 0
        b.flr[i] = 3
    }
}
private fun room(b: Body, x: Int, y: Int, w: Int, h: Int, kind: String, name: String) {
    wall(b, x, y, w, h)
    b.rooms.add(Room(x, y, w, h, kind, name))
    fill(b, x + 1, y + 1, w - 2, h - 2, 2)
    val col = when (kind) {
        "bar", "home", "cargo" -> PAL.amber
        "crew", "market" -> PAL.gold
        "catalog", "lift" -> PAL.cyan
        "yard" -> PAL.green
        "workshop" -> PAL.ember
        else -> PAL.amber
    }
    b.lights.add(LightSource((x + w / 2.0) * b.cs - (b.gw * b.cs) / 2.0, (y + h / 2.0) * b.cs - (b.gh * b.cs) / 2.0, col, max(w, h) * b.cs * 0.75, false))
}
private fun port(b: Body, cx: Int, cy: Int, dir: Double, berth: Boolean = false) {
    b.ports.add(Port(cx, cy, dir, berth, null))
}
private fun pad(b: Body, cx: Int, cy: Int, dir: Double, name: String = "PAD ${b.pads.size + 1}") {
    b.pads.add(Pad(cx, cy, dir, name, null))
}
private fun lc(b: Body, cx: Double, cy: Double): Vec2 = Vec2((cx + 0.5) * b.cs - (b.gw * b.cs) / 2.0, (cy + 0.5) * b.cs - (b.gh * b.cs) / 2.0)

private val COL_2C3642 = parseHexColor("#2c3642")
private val COL_4A4640 = parseHexColor("#4a4640")
private val COL_38434F = parseHexColor("#38434f")
private val COL_FFB347 = parseHexColor("#ffb347")
private val COL_2A333F = parseHexColor("#2a333f")
private val COL_8A6A2F = parseHexColor("#8a6a2f")
private val COL_1F6F8F = parseHexColor("#1f6f8f")
private val COL_C96F2F = parseHexColor("#c96f2f")
private val COL_2F4F6F = parseHexColor("#2f4f6f")
private val COL_6B5A3F = parseHexColor("#6b5a3f")
private val COL_4F8F5A = parseHexColor("#4f8f5a")
private val COL_5A4F6B = parseHexColor("#5a4f6b")
private val COL_5A5148 = parseHexColor("#5a5148")
private val COL_41546B = parseHexColor("#41546b")
private val COL_53D9FF = parseHexColor("#53d9ff")
private val COL_FFD166 = parseHexColor("#ffd166")
private val COL_6B6156 = parseHexColor("#6b6156")

private fun paintSt(b: Body, r: () -> Double) {
    for (cy in 0 until b.gh) {
        for (cx in 0 until b.gw) {
            val i = cy * b.gw + cx
            if (b.flr[i] != 0) {
                val n = ((((cx * 73856093L) xor (cy * 19349663L) xor b.seed.toLong()) and 0xFFFFFFFFL) % 100L).toInt()
                b.roofPixels[i] = if (cx % 7 == 0 || cy % 7 == 0) COL_2C3642 else sh(if (b.surf) COL_4A4640 else COL_38434F, 0.9 + n / 400.0)
                if (b.flr[i] == 2 && n < 6) b.roofPixels[i] = COL_FFB347
            } else {
                b.roofPixels[i] = 0
            }
            pF(b, cx, cy)
            if (b.sol[i] != 0) {
                pR(b, cx, cy)
                b.floorPixels[i] = COL_2A333F
            }
        }
    }
    for (rm in b.rooms) {
        for (cy in (rm.y + 2) until (rm.y + rm.h - 2)) {
            for (cx in (rm.x + 2) until (rm.x + rm.w - 2)) {
                val i = cy * b.gw + cx
                val n = ((((cx * 2654435761L) xor (cy * 40503L) xor b.seed.toLong()) and 0xFFFFFFFFL) % 100L).toInt()
                when (rm.kind) {
                    "market" -> if (cy % 4 == 1 && cx % 3 != 0) b.floorPixels[i] = if (n < 50) COL_8A6A2F else COL_1F6F8F
                    "bar" -> if (n < 8) b.floorPixels[i] = COL_C96F2F
                    "catalog" -> if (cy % 3 == 0 && cx % 2 == 0) b.floorPixels[i] = if (n < 40) COL_1F6F8F else COL_2F4F6F
                    "crew" -> if (cy % 4 == 2 && n < 60) b.floorPixels[i] = COL_6B5A3F
                    "home" -> if (n < 10) b.floorPixels[i] = if (n < 4) COL_4F8F5A else COL_5A4F6B
                    "cargo" -> if (n < 14) b.floorPixels[i] = if (n < 7) COL_8A6A2F else COL_5A5148
                    "yard" -> if (cy % 5 == 0) b.floorPixels[i] = COL_41546B
                    "workshop" -> if (cy % 4 == 2 && cx % 2 == 0) b.floorPixels[i] = COL_41546B
                    "lift" -> if (cx == rm.x + rm.w / 2) b.floorPixels[i] = COL_53D9FF
                }
            }
        }
    }
    for (p in b.pads) {
        for (t in -4..4) {
            val px = p.cx + t
            if (px in 0 until b.gw && p.cy in 0 until b.gh) {
                b.roofPixels[p.cy * b.gw + px] = if (kotlin.math.abs(t) == 4 || t == 0) COL_FFD166 else COL_6B6156
            }
        }
    }
    val crewCols = listOf(PAL.text, PAL.amber, PAL.cyan, PAL.bio)
    val cCount = b.crewN ?: ri(r, 6, 11)
    for (k in 0 until cCount) {
        if (b.rooms.isEmpty()) break
        val rm = b.rooms[ri(r, 0, b.rooms.lastIndex)]
        b.crew.add(
            StationCrew(
                lx = (rm.x + 2.0 + r() * (rm.w - 4)) * b.cs - (b.gw * b.cs) / 2.0,
                ly = (rm.y + 2.0 + r() * (rm.h - 4)) * b.cs - (b.gh * b.cs) / 2.0,
                rm = rm,
                c = pk(r, crewCols),
                sp = rr(r, 3.0, 7.0),
                t = r() * 4.0
            )
        )
    }
    b.sol0 = b.sol.copyOf()
    b.flr0 = b.flr.copyOf()
    b.roofDirty = true
    b.floorDirty = true
    ensureBodyBitmaps(b)
}

private data class EconSpec(val s: Double, val c: Double, val p: Double, val m: Double)
private val ECON = mapOf(
    "selene" to mapOf("ice" to EconSpec(80.0, 0.5, 0.0, 1.0), "fe" to EconSpec(60.0, 0.3, 0.0, 0.95), "vol" to EconSpec(20.0, 0.15, 0.0, 1.0), "pgm" to EconSpec(10.0, 0.05, 0.0, 0.9), "ree" to EconSpec(6.0, 0.04, 0.0, 0.9), "food" to EconSpec(60.0, 0.6, 0.0, 1.35), "parts" to EconSpec(70.0, 0.2, 0.2, 1.0), "fuel" to EconSpec(220.0, 0.5, 0.3, 1.1), "core" to EconSpec(0.0, 0.0, 0.0, 1.0)),
    "vulcan" to mapOf("ice" to EconSpec(60.0, 1.2, 0.0, 1.15), "fe" to EconSpec(80.0, 1.4, 0.0, 1.3), "vol" to EconSpec(30.0, 0.4, 0.0, 1.1), "pgm" to EconSpec(14.0, 0.1, 0.0, 1.05), "ree" to EconSpec(4.0, 0.02, 0.0, 0.8), "food" to EconSpec(30.0, 0.3, 0.0, 1.2), "parts" to EconSpec(90.0, 0.0, 0.6, 0.85), "fuel" to EconSpec(300.0, 0.0, 1.6, 0.7), "core" to EconSpec(0.0, 0.0, 0.0, 0.5)),
    "mars" to mapOf("ice" to EconSpec(40.0, 2.2, 0.0, 1.6), "fe" to EconSpec(40.0, 0.4, 0.0, 1.0), "vol" to EconSpec(16.0, 1.0, 0.0, 1.7), "pgm" to EconSpec(8.0, 0.05, 0.0, 1.0), "ree" to EconSpec(6.0, 0.05, 0.0, 1.05), "food" to EconSpec(40.0, 1.0, 0.0, 1.4), "parts" to EconSpec(30.0, 0.3, 0.0, 1.2), "fuel" to EconSpec(180.0, 0.5, 0.0, 1.0), "core" to EconSpec(0.0, 0.0, 0.0, 0.6)),
    "earth" to mapOf("ice" to EconSpec(60.0, 0.2, 0.0, 0.8), "fe" to EconSpec(60.0, 0.2, 0.0, 0.8), "vol" to EconSpec(12.0, 0.1, 0.0, 0.9), "pgm" to EconSpec(6.0, 0.6, 0.0, 1.55), "ree" to EconSpec(3.0, 0.5, 0.0, 1.6), "food" to EconSpec(30.0, 0.2, 0.0, 1.2), "parts" to EconSpec(50.0, 0.1, 0.0, 1.0), "fuel" to EconSpec(160.0, 0.4, 0.0, 1.0), "core" to EconSpec(0.0, 0.0, 0.0, 0.6)),
    "depot" to mapOf("ice" to EconSpec(30.0, 2.6, 0.0, 2.1), "fe" to EconSpec(40.0, 1.0, 0.0, 1.6), "vol" to EconSpec(14.0, 0.8, 0.0, 1.6), "pgm" to EconSpec(6.0, 0.05, 0.0, 0.9), "ree" to EconSpec(4.0, 0.04, 0.0, 0.9), "food" to EconSpec(120.0, 0.0, 1.2, 0.6), "parts" to EconSpec(70.0, 0.3, 0.2, 0.9), "fuel" to EconSpec(200.0, 0.2, 0.6, 1.0), "core" to EconSpec(0.0, 0.0, 0.0, 0.6)),
    "tycho" to mapOf("ice" to EconSpec(30.0, 1.4, 0.0, 1.8), "fe" to EconSpec(30.0, 1.6, 0.0, 1.9), "vol" to EconSpec(10.0, 0.4, 0.0, 1.4), "pgm" to EconSpec(10.0, 0.3, 0.0, 1.25), "ree" to EconSpec(6.0, 0.2, 0.0, 1.2), "food" to EconSpec(60.0, 0.4, 0.4, 0.8), "parts" to EconSpec(100.0, 0.0, 1.0, 0.65), "fuel" to EconSpec(160.0, 0.2, 0.4, 1.0), "core" to EconSpec(0.0, 0.0, 0.0, 0.6))
)

private fun mkEcon(r: () -> Double, type: String): MutableMap<String, EconomyItem> {
    val D = ECON[type]!!
    val e = linkedMapOf<String, EconomyItem>()
    for ((g, spec) in D) {
        e[g] = EconomyItem(
            stock = round(spec.s * rr(r, 0.75, 1.25)),
            cons = spec.c,
            prod = spec.p,
            ref = max(4.0, spec.s),
            mul = spec.m
        )
    }
    return e
}

private fun mkSt(r: () -> Double, x: Double, y: Double, a: Double, gw: Int, gh: Int, cs: Double, name: String, fac: String, type: String): Body {
    val b = Body("station", x, y, gw, gh, cs, ri(r, 0, 1_000_000_000))
    b.a = a
    b.name = name
    b.fac = fac
    b.type = type
    b.econ = if (ECON.containsKey(type)) mkEcon(r, type) else null
    b.tabs = listOf("market", "service", "bar")
    return b
}

fun mkSelene(r: () -> Double, th: Double): Body {
    val p = atPose(th, C.RS)
    val b = mkSt(r, p.x, p.y, p.a, 96, 46, C.TS, "SELENE HIGH", "selene", "selene")
    b.tabs = listOf("market", "service", "crew", "yard", "catalog", "bar")
    b.yard = listOf("wren")
    fill(b, 3, 3, 90, 40, 3)
    wall(b, 2, 2, 92, 42)
    room(b, 2, 2, 20, 21, "crew", "CREW OFFICE")
    room(b, 21, 2, 20, 21, "market", "EXCHANGE")
    room(b, 40, 2, 19, 21, "catalog", "CATALOG OFFICE")
    room(b, 58, 2, 19, 21, "bar", "THE TERMINATOR")
    room(b, 76, 2, 18, 21, "yard", "SHIPYARD")
    fill(b, 3, 23, 90, 5, 2)
    room(b, 2, 28, 38, 16, "cargo", "CARGO HOLD")
    room(b, 39, 28, 18, 16, "lift", "LIFT DOCK")
    room(b, 56, 28, 38, 16, "workshop", "WORKSHOP")
    for (x in intArrayOf(10, 30, 48, 66, 84)) doorH(b, x, 22, 3)
    for (x in intArrayOf(20, 47, 74)) doorH(b, x, 28, 3)
    mouth(b, 47, 43, 2, 1)
    mouth(b, 2, 23, 1, 5)
    mouth(b, 93, 23, 1, 5)
    port(b, 93, 10, 0.0, true)
    port(b, 93, 36, 0.0, true)
    port(b, 2, 10, PI)
    port(b, 2, 36, PI)
    pad(b, 30, 2, -PI / 2.0, "TOP PAD A")
    pad(b, 66, 2, -PI / 2.0, "TOP PAD B")
    for (cx in 8 until 92 step 12) {
        val pos = lc(b, cx.toDouble(), 25.0)
        b.lights.add(LightSource(pos.x, pos.y, PAL.amber, 30.0, false))
    }
    b.liftDock = lc(b, 47.5, 37.0)
    b.cableTop = lc(b, 47.5, 44.5)
    paintSt(b, r)
    return b
}

fun mkRefinery(r: () -> Double, th: Double, rad: Double): Body {
    val p = atPose(th, rad)
    val b = mkSt(r, p.x, p.y, p.a, 84, 44, C.TS, "VULCAN REFINERY", "selene", "vulcan")
    wall(b, 4, 4, 60, 36)
    fill(b, 5, 5, 58, 34, 3)
    mouth(b, 4, 14, 1, 14)
    for ((tx, ty, tr) in listOf(Triple(73, 9, 6), Triple(73, 35, 6))) {
        for (cy in (ty - tr)..(ty + tr)) {
            for (cx in (tx - tr)..(tx + tr)) {
                if (hy((cx - tx).toDouble(), (cy - ty).toDouble()) < tr && cx > 0 && cy > 0 && cx < b.gw && cy < b.gh) {
                    b.sol[cy * b.gw + cx] = 7
                }
            }
        }
    }
    room(b, 40, 26, 24, 14, "workshop", "SMELTER CONTROL")
    room(b, 40, 4, 24, 12, "market", "ORE INTAKE")
    doorV(b, 40, 31, 3)
    doorV(b, 40, 9, 3)
    block(b, 14, 18, 3, 8)
    block(b, 26, 18, 3, 8)
    port(b, 64, 21, 0.0)
    port(b, 4, 8, PI)
    port(b, 4, 34, PI)
    pad(b, 22, 4, -PI / 2.0, "INTAKE PAD")
    pad(b, 22, 39, PI / 2.0, "UNDER PAD")
    val pos = lc(b, 22.0, 22.0)
    b.lights.add(LightSource(pos.x, pos.y, PAL.ember, 80.0, true))
    paintSt(b, r)
    return b
}

private val ROMAN = arrayOf("", "", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")
fun mkTransfer(r: () -> Double, th: Double, rad: Double, dest: String, gen: Int = 1): Body {
    val p = atPose(th, rad)
    val baseName = if (dest == "mars") "ARES TRANSIT" else "GAIA RETURN"
    val suffix = if (gen > 1) " " + ROMAN[gen % 10] else ""
    val b = mkSt(r, p.x, p.y, p.a, 74, 20, C.TS, baseName + suffix, "transit", dest)
    b.transfer = TransferInfo(dest, gen, 0.0)
    b.tabs = listOf("market", "service", "bar")
    b.crewN = 5
    block(b, 0, 5, 8, 10)
    for (cy in 6 until 14 step 3) block(b, 0, cy, 1, 2, 0)
    room(b, 7, 6, 52, 8, "cargo", "CARGO SPINE")
    room(b, 58, 3, 14, 14, "bar", "TRANSIT HAB")
    doorV(b, 58, 9, 2)
    for (x0 in intArrayOf(11, 25, 47)) {
        block(b, x0, 1, 6, 5)
        block(b, x0, 14, 6, 5)
    }
    port(b, 20, 6, -PI / 2.0)
    port(b, 40, 6, -PI / 2.0)
    port(b, 39, 13, PI / 2.0)
    port(b, 72, 9, 0.0)
    mouth(b, 7, 9, 1, 2)
    val pos = lc(b, 3.0, 10.0)
    b.lights.add(LightSource(pos.x, pos.y, PAL.ember, 40.0, false))
    paintSt(b, r)
    return b
}

private fun surfPose(th: Double, gh: Int, cs: Double, fr: Int): PoseAngle {
    val S = surfR(th)
    return atPose(th, S + fr * cs - (gh * cs) / 2.0)
}

fun mkHab(r: () -> Double, th: Double): Body {
    val p = surfPose(th, 10, 3.0, 9)
    val b = mkSt(r, p.x, p.y, p.a, 18, 10, 3.0, "HAB 14", "rim", "hab")
    b.surf = true
    b.tabs = listOf("home")
    b.crewN = 0
    room(b, 1, 1, 16, 9, "home", "HAB 14 · YOUR BUNK")
    doorV(b, 16, 6, 3)
    doorV(b, 1, 6, 3)
    val pos = lc(b, 9.0, 3.0)
    b.lights.add(LightSource(pos.x, pos.y, PAL.amber, 30.0, false))
    paintSt(b, r)
    b.econ = null
    return b
}

fun mkLiftTerm(r: () -> Double, th: Double): Body {
    val p = surfPose(th, 20, 3.0, 19)
    val b = mkSt(r, p.x, p.y, p.a, 20, 20, 3.0, "SELENE LIFT · GROUND", "selene", "lift")
    b.surf = true
    b.tabs = listOf("bar")
    b.crewN = 2
    room(b, 2, 9, 16, 11, "lift", "LIFT HALL")
    block(b, 8, 0, 1, 10)
    block(b, 11, 0, 1, 10)
    doorH(b, 9, 9, 2)
    doorV(b, 2, 15, 4)
    doorV(b, 17, 15, 4)
    b.liftDock = lc(b, 9.5, 16.0)
    b.cableTop = lc(b, 9.5, -0.5)
    val pos = lc(b, 9.5, 2.0)
    b.lights.add(LightSource(pos.x, pos.y, PAL.cyan, 26.0, true))
    paintSt(b, r)
    b.econ = null
    return b
}

fun mkDepot(r: () -> Double, th: Double): Body {
    val p = surfPose(th, 12, 3.0, 11)
    val b = mkSt(r, p.x, p.y, p.a, 66, 12, 3.0, "SHACKLETON DEPOT", "rim", "depot")
    b.surf = true
    b.tabs = listOf("market", "service", "yard", "bar")
    b.yard = listOf("hare")
    room(b, 1, 1, 14, 11, "market", "COLONY EXCHANGE")
    room(b, 14, 1, 16, 11, "yard", "LANDER YARD")
    doorV(b, 1, 8, 3)
    doorV(b, 29, 8, 3)
    doorV(b, 14, 8, 3)
    block(b, 34, 10, 30, 1)
    pad(b, 42, 10, -PI / 2.0, "PAD 1")
    pad(b, 56, 10, -PI / 2.0, "PAD 2")
    for (x in intArrayOf(35, 49, 63)) {
        val pos = lc(b, x.toDouble(), 8.0)
        b.lights.add(LightSource(pos.x, pos.y, PAL.gold, 22.0, false))
    }
    paintSt(b, r)
    return b
}

fun mkTycho(r: () -> Double, th: Double): Body {
    val p = surfPose(th, 12, 3.0, 11)
    val b = mkSt(r, p.x, p.y, p.a, 48, 12, 3.0, "TYCHO WORKS", "rim", "tycho")
    b.surf = true
    b.tabs = listOf("market", "service", "bar")
    room(b, 1, 1, 20, 11, "workshop", "FOUNDRY")
    doorV(b, 20, 8, 3)
    doorV(b, 1, 8, 3)
    block(b, 24, 10, 22, 1)
    pad(b, 30, 10, -PI / 2.0, "TYCHO PAD 1")
    pad(b, 41, 10, -PI / 2.0, "TYCHO PAD 2")
    val pos = lc(b, 10.0, 6.0)
    b.lights.add(LightSource(pos.x, pos.y, PAL.ember, 40.0, true))
    paintSt(b, r)
    return b
}

private fun background(r: () -> Double): BackgroundAssets {
    val stars = mutableListOf<Bitmap>()
    val paint = Paint()
    for (L in 0 until 2) {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val count = if (L == 0) 220 else 120
        for (k in 0 until count) {
            val q = r()
            val (cr, cg, cb, ca) = when {
                q < 0.7 -> listOf(216, 226, 236, (rr(r, 0.3, 0.9) * 255).toInt())
                q < 0.85 -> listOf(168, 236, 255, (rr(r, 0.3, 0.8) * 255).toInt())
                else -> listOf(255, 209, 150, (rr(r, 0.25, 0.7) * 255).toInt())
            }
            paint.color = Color.argb(ca, cr, cg, cb)
            val big = L == 1 && r() < 0.3
            val sx = (r() * 512).toInt().toFloat()
            val sy = (r() * 512).toInt().toFloat()
            val sz = if (big) 2f else 1f
            c.drawRect(sx, sy, sx + sz, sy + sz, paint)
        }
        stars.add(bmp)
    }
    val earth = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    val ex = Canvas(earth)
    val ep = Paint(Paint.ANTI_ALIAS_FLAG)
    val saveLayer = ex.saveLayer(0f, 0f, 128f, 128f, null)
    ep.color = Color.rgb(29, 79, 143)
    ex.drawCircle(64f, 64f, 60f, ep)
    ep.color = Color.argb(191, 80, 140, 80)
    for (k in 0 until 9) {
        ex.save()
        val cx = (30.0 + r() * 70.0).toFloat()
        val cy = (30.0 + r() * 70.0).toFloat()
        val rx = rr(r, 8.0, 22.0).toFloat()
        val ry = rr(r, 5.0, 14.0).toFloat()
        ex.rotate(Math.toDegrees(r() * TAU).toFloat(), cx, cy)
        ex.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), ep)
        ex.restore()
    }
    ep.color = Color.argb(140, 240, 248, 255)
    for (k in 0 until 16) {
        ex.save()
        val cx = (10.0 + r() * 108.0).toFloat()
        val cy = (10.0 + r() * 108.0).toFloat()
        val rx = rr(r, 6.0, 26.0).toFloat()
        val ry = rr(r, 2.0, 6.0).toFloat()
        ex.rotate(Math.toDegrees(r() * TAU).toFloat(), cx, cy)
        ex.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), ep)
        ex.restore()
    }
    ep.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    ep.color = Color.BLACK
    ex.drawCircle(64f, 64f, 60f, ep)
    ep.xfermode = null
    ex.restoreToCount(saveLayer)
    return BackgroundAssets(stars, earth)
}

fun genWorld(seed: Int): WorldState {
    val rngObj = SeededRng(seed)
    val r: () -> Double = { rngObj.next() }
    val T0 = C.TH0
    fun u(d: Double) = d / C.RM
    val W = WorldState(seed)
    GS.W = W

    val thHab = T0 - u(150.0)
    val thLift = T0
    val thDepot = T0 + u(175.0)
    val thTycho = T0 + 0.62
    genMoon(seed, listOf(FlatZone(T0 + u(70.0), 330.0), FlatZone(thTycho + u(10.0), 170.0, -6.0)))
    val hab = mkHab(r, thHab)
    val lift = mkLiftTerm(r, thLift)
    val depot = mkDepot(r, thDepot)
    val tycho = mkTycho(r, thTycho)
    val sel = mkSelene(r, T0)
    val vul = mkRefinery(r, T0 + 0.3, C.RS + 70.0)
    val ares = mkTransfer(r, T0 - 0.42, C.RS + 130.0, "mars")
    val gaia = mkTransfer(r, T0 + 0.56, C.RS - 90.0, "earth")
    W.stations.addAll(listOf(sel, vul, ares, gaia, depot, tycho, lift, hab))
    SITES.hab = hab
    SITES.lift = lift
    SITES.depot = depot
    SITES.tycho = tycho
    SITES.sel = sel
    SITES.vul = vul
    SITES.ares = ares
    SITES.gaia = gaia

    val clusters = listOf(
        -0.2 to 9, -0.6 to 8, 0.16 to 9, 0.42 to 7, 0.82 to 7,
        -0.95 to 5, 1.05 to 4, -1.35 to 4, 1.5 to 3
    )
    var id = 0
    fun free(x: Double, y: Double, rad: Double): Boolean {
        for (s in W.stations) if (!s.surf && hy(s.x - x, s.y - y) < s.rad + rad + 140.0) return false
        for (a in W.rocks) if (hy(a.x - x, a.y - y) < a.rad + rad + 36.0) return false
        return kotlin.math.abs(hypot(x, y) - C.RS) < C.BAND
    }
    val cores = setOf(2, 9, 17)
    val claims = setOf(4, 12, 21)
    for ((dth, n) in clusters) {
        for (k in 0 until n) {
            val big = r() < 0.18
            val R = if (big) rr(r, 34.0, 48.0) else if (r() < 0.5) rr(r, 18.0, 33.0) else rr(r, 8.0, 17.0)
            var x = 0.0
            var y = 0.0
            var ok = false
            var t = 0
            while (!ok && t++ < 40) {
                val th = T0 + dth + rr(r, -0.1, 0.1)
                val rad = C.RS + rr(r, -C.BAND + 60.0, C.BAND - 60.0)
                x = cos(th) * rad
                y = sin(th) * rad
                ok = free(x, y, R * C.CS)
            }
            if (!ok) continue
            val type = if (r() < 0.24) "ice" else if (r() < 0.22) "metal" else "rock"
            val opts = RockOpts(
                claim = R >= 17.0 && claims.contains(id),
                core = R >= 17.0 && cores.contains(id)
            )
            val b = mkRock(r, x, y, id++, R, type, opts)
            if (r() < 0.2) b.va = rr(r, -0.025, 0.025)
            W.rocks.add(b)
        }
    }
    // guarantee story contacts exist
    fun need(kind: String, n: Int) {
        var have = W.contacts.count { it.kind == kind }
        val snap = W.rocks.toList()
        for (b in snap) {
            if (have >= n) break
            if (b.rVal < 20.0 || b.cham.any { it.biome == "core" || it.biome == "claim" }) continue
            W.contacts.removeAll { it.b === b }
            W.picks.removeAll { it.home === b }
            val opts = if (kind == "core") RockOpts(core = true) else RockOpts(claim = true)
            val nb = mkRock(r, b.x, b.y, b.uid, b.rVal, b.type, opts)
            nb.va = b.va
            val idx = W.rocks.indexOf(b)
            if (idx >= 0) W.rocks[idx] = nb
            have = W.contacts.count { it.kind == kind }
        }
    }
    need("core", 3)
    need("claim", 3)
    // three comets
    for (dth in doubleArrayOf(-0.3, 0.7, -0.8)) {
        val th = T0 + dth
        val rad = C.RS + rr(r, 160.0, 300.0)
        val x = cos(th) * rad
        val y = sin(th) * rad
        if (!free(x, y, 40.0 * C.CS)) continue
        val b = mkRock(r, x, y, id++, rr(r, 22.0, 32.0), "comet")
        b.va = rr(r, -0.01, 0.01)
        W.rocks.add(b)
    }
    // the Orphan
    run {
        val th = T0 + 1.24
        val rad = C.RS + 230.0
        val b = mkRock(r, cos(th) * rad, sin(th) * rad, 777, 52.0, "metal", RockOpts(rich = true, claim = true))
        b.orphan = true
        b.hidden = true
        b.va = 0.006
        W.rocks.add(b)
        W.orphan = b
    }
    // loose drift near the station
    val looseTypes = listOf("fe", "ice", "parts")
    for (k in 0 until 8) {
        val th = T0 + rr(r, -0.06, 0.06)
        val rad = C.RS + rr(r, -200.0, 200.0)
        addPick(cos(th) * rad, sin(th) * rad, pk(r, looseTypes), ri(r, 1, 2))
    }
    W.bodies.addAll(W.rocks)
    W.bodies.addAll(W.stations)
    W.bg = background(r)
    return W
}

fun spawnRock(seed: Int, x: Double, y: Double, R: Double, type: String, vx: Double = 0.0, vy: Double = 0.0): Body {
    val W = GS.W!!
    val rngObj = SeededRng(seed)
    val b = mkRock({ rngObj.next() }, x, y, 2000 + W.extra.size, R, type)
    b.vx = vx
    b.vy = vy
    W.rocks.add(b)
    W.bodies.add(b)
    W.extra.add(com.example.keres9.core.ExtraRockRecord(seed, x, y, R, type, false))
    return b
}

fun replaceTransfer(old: Body, gen: Int): Body {
    val W = GS.W!!
    val rngObj = SeededRng((W.seed xor (gen * 7717)))
    val th = atan2(if (old.y0 != 0.0) old.y0 else old.y, if (old.x0 != 0.0) old.x0 else old.x)
    val rad = if (old.r0 != 0.0) old.r0 else hypot(old.x, old.y)
    return mkTransfer({ rngObj.next() }, th, rad, old.transfer!!.dest, gen)
}

fun ensureShards(): Body? {
    val G = GS.G ?: return null
    val W = GS.W ?: return null
    if (G.cores >= 3) return null
    val live = W.picks.filter { !it.dead && it.type == "core" }
    W.contacts.removeAll { c ->
        c.kind == "core" && live.none { p -> p.home === c.b || hy(p.x - c.b.x, p.y - c.b.y) < c.b.rad + 15.0 }
    }
    var have = G.cores + (GS.ev?.sack?.get("core") ?: 0)
    for (s in G.fleet) if (!s.dead) have += s.cargo["core"] ?: 0
    for (p in W.picks) if (!p.dead && p.type == "core" && hy(p.x, p.y) > C.RM + 300.0) have += p.qty
    if (have >= 3) return null
    val meX = GS.pl?.x ?: GS.ev?.x
    val meY = GS.pl?.y ?: GS.ev?.y
    val busy = W.ships.mapNotNull { it.dock?.b }.toMutableSet()
    G.crew?.site?.let { busy.add(it) }
    val L = W.rocks.filter { b ->
        !b.dead && !b.orphan && !b.hidden && !b.inbound && !b.kin && b.cat == 0 && b.rVal >= 20.0 &&
            !busy.contains(b) && b.cham.none { it.biome == "core" || it.biome == "claim" } &&
            (meX == null || hy(b.x - meX, b.y - meY!!) > 700.0) &&
            kotlin.math.abs(hy(b.x, b.y) - C.RS) < C.BAND
    }
    if (L.isEmpty()) return null
    val old = L[floor(rand() * L.size).toInt().coerceIn(0, L.lastIndex)]
    W.contacts.removeAll { it.b === old }
    W.picks.removeAll { it.home === old }
    val rngObj = SeededRng((rand() * 1e9).toInt())
    val nb = mkRock({ rngObj.next() }, old.x, old.y, old.uid, old.rVal, old.type, RockOpts(core = true))
    nb.va = old.va
    nb.vx = old.vx
    nb.vy = old.vy
    val rIdx = W.rocks.indexOf(old)
    if (rIdx >= 0) W.rocks[rIdx] = nb
    val bIdx = W.bodies.indexOf(old)
    if (bIdx >= 0) W.bodies[bIdx] = nb
    return nb
}
