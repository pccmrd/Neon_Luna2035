package com.example.keres9.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object C {
    const val RM = 3000.0
    const val G0 = 4.0
    const val GM = G0 * RM * RM
    const val RS = 4500.0
    val OM = sqrt(GM / (RS * RS * RS))

    const val BAND = 420.0
    const val TH0 = -PI / 2.0
    const val CS = 3.0
    const val SS = 2.5
    const val TS = 4.0
    const val DT = 1.0 / 60.0

    object DOCK {
        const val v = 2.5
        const val a = 0.18
        const val l = 10.0
        const val r = 0.15
        const val d = 11.0
    }

    object LAND {
        const val v = 2.2
        const val a = 0.26
        const val l = 10.0
        const val r = 0.15
        const val d = 10.0
    }

    object TOUCH {
        const val v = 3.2
        const val a = 0.42
    }

    object ANCH {
        const val v = 3.0
        const val d = 18.0
    }

    object SCAN {
        const val rng = 520.0
        const val cone = 0.42
        const val band = 4.5
        const val survey = 2.6
    }

    object MINE {
        const val rng = 95.0
        const val dps = 30.0
    }

    object CUT {
        const val rng = 24.0
        const val dps = 15.0
    }

    object EVA {
        const val acc = 15.0
        const val air = 150.0
        const val ping = 120.0
        const val walk = 13.0
        const val sack = 8.0
    }

    object LIFT {
        const val v = 78.0
        const val a = 7.0
    }

    const val SHIFT = 240.0
}

object PAL {
    val amber = parseHexColor("#ffb347")
    val ember = parseHexColor("#ff8c1a")
    val rust = parseHexColor("#c96f2f")
    val cyan = parseHexColor("#53d9ff")
    val red = parseHexColor("#ff4a3d")
    val green = parseHexColor("#6df08f")
    val gold = parseHexColor("#ffd166")
    val ice = parseHexColor("#a8ecff")
    val bio = parseHexColor("#8cff9e")
    val mag = parseHexColor("#ff5ec4")
    val text = parseHexColor("#d8e2ec")
    val dim = parseHexColor("#7d8fa3")
    val steel = parseHexColor("#41546b")
    val helion = parseHexColor("#ff7a3d")
}

val FC: Map<String, Int> = mapOf(
    "selene" to PAL.cyan,
    "crew" to PAL.amber,
    "helion" to PAL.helion,
    "transit" to PAL.text,
    "rim" to PAL.green,
    "free" to PAL.text
)

data class MaterialDef(val hp: Double, val color: Int, val ore: String? = null)

val MAT: Array<MaterialDef?> = arrayOf(
    null,
    MaterialDef(26.0, parseHexColor("#5a5148"), null),      // 1 rock
    MaterialDef(60.0, parseHexColor("#3f3a36"), null),      // 2 dense rock
    MaterialDef(16.0, parseHexColor("#bfe8f2"), "ice"),     // 3 water ice
    MaterialDef(46.0, parseHexColor("#c98a5b"), "fe"),      // 4 nickel-iron
    MaterialDef(80.0, parseHexColor("#ffd166"), "pgm"),     // 5 platinum-group
    MaterialDef(52.0, parseHexColor("#c77dff"), "ree"),     // 6 rare-earth crystal
    MaterialDef(110.0, parseHexColor("#8b98a8"), "parts"),  // 7 structure / hull plate
    MaterialDef(12.0, parseHexColor("#7fa88f"), "vol")      // 8 frozen volatiles
)

val FLR: IntArray = intArrayOf(
    0,
    parseHexColor("#10141d"),
    parseHexColor("#151c28"),
    parseHexColor("#121824"),
    parseHexColor("#0d1119"),
    parseHexColor("#12202c"),
    parseHexColor("#141c18"),
    parseHexColor("#171226"),
    parseHexColor("#1a2028")
)

data class GoodDef(val n: String, val p: Double, val c: Int, val m: Double, val story: Boolean = false)

val GD: Map<String, GoodDef> = linkedMapOf(
    "ice" to GoodDef("Water Ice", 14.0, parseHexColor("#a8ecff"), 1.0),
    "fe" to GoodDef("Nickel-Iron", 20.0, parseHexColor("#c98a5b"), 1.0),
    "vol" to GoodDef("Comet Volatiles", 28.0, parseHexColor("#7fa88f"), 1.0),
    "pgm" to GoodDef("Platinum Group", 92.0, parseHexColor("#ffd166"), 1.0),
    "ree" to GoodDef("Rare-Earth Crystal", 130.0, parseHexColor("#c77dff"), 1.0),
    "food" to GoodDef("Greenhouse Food", 20.0, parseHexColor("#8cff9e"), 1.0),
    "parts" to GoodDef("Fab Parts", 36.0, parseHexColor("#8b98a8"), 1.0),
    "fuel" to GoodDef("Propellant", 2.5, parseHexColor("#ffb347"), 1.0),
    "core" to GoodDef("KERES-9 Core Shard", 260.0, parseHexColor("#53d9ff"), 1.0, story = true)
)

data class ModuleDef(val hp: Double, val c: Int)

val MD: Map<String, ModuleDef> = mapOf(
    "cock" to ModuleDef(40.0, parseHexColor("#3f6f8f")),
    "hull" to ModuleDef(30.0, parseHexColor("#41546b")),
    "eng" to ModuleDef(35.0, parseHexColor("#6b5a3f")),
    "rcs" to ModuleDef(15.0, parseHexColor("#5a6a7c")),
    "cargo" to ModuleDef(25.0, parseHexColor("#6b6156")),
    "fuel" to ModuleDef(25.0, parseHexColor("#7d5a2f")),
    "clamp" to ModuleDef(20.0, parseHexColor("#8a6a2f")),
    "gear" to ModuleDef(18.0, parseHexColor("#4f5a68")),
    "mine" to ModuleDef(22.0, parseHexColor("#1f6f8f")),
    "scan" to ModuleDef(18.0, parseHexColor("#2f6f7d")),
    "hab" to ModuleDef(25.0, parseHexColor("#4f6f5a")),
    "gun" to ModuleDef(22.0, parseHexColor("#7d3a34"))
)

data class HullClassDef(
    val n: String,
    val thr: Double,
    val rcs: Double,
    val tq: Double,
    val cargo: Int,
    val fuel: Double,
    val burn: Double,
    val gear: Boolean,
    val price: Int = 0
)

val CLS: Map<String, HullClassDef> = mapOf(
    "wren" to HullClassDef("WREN-class orbital tug", 34.0, 2.2, 2.4, 26, 100.0, 0.55, false, 2400),
    "hare" to HullClassDef("HARE-class surface lander", 215.0, 2.6, 3.0, 44, 150.0, 2.6, true, 6500),
    "mule" to HullClassDef("MULE crew shuttle", 120.0, 3.0, 2.6, 60, 400.0, 0.4, true),
    "hauler" to HullClassDef("ore hauler", 140.0, 4.0, 2.4, 60, 400.0, 0.4, true),
    "pros" to HullClassDef("prospector", 60.0, 4.0, 2.6, 30, 300.0, 0.4, false),
    "lander" to HullClassDef("colony lander", 230.0, 3.0, 3.0, 40, 600.0, 0.6, true),
    "tug" to HullClassDef("automated haul engine", 60.0, 2.0, 1.6, 0, 1e5, 0.0, false),
    "drone" to HullClassDef("catalog drone", 40.0, 6.0, 3.0, 0, 1e5, 0.0, false)
)

fun gacc(x: Double, y: Double, vx: Double, vy: Double, out: Vec2): Vec2 {
    val r2 = x * x + y * y
    val r = sqrt(r2).let { if (it == 0.0) 1.0 else it }
    val g = -C.GM / r2 + C.OM * C.OM * r
    out.x = (g * x) / r + 2.0 * C.OM * vy
    out.y = (g * y) / r - 2.0 * C.OM * vx
    return out
}

fun geff(r: Double): Double = C.GM / (r * r) - C.OM * C.OM * r
fun radius(x: Double, y: Double): Double = sqrt(x * x + y * y)
fun lon(x: Double, y: Double): Double = kotlin.math.atan2(y, x)
fun polar(th: Double, r: Double): Vec2 = Vec2(cos(th) * r, sin(th) * r)
fun upv(x: Double, y: Double): Vec2 {
    val r = sqrt(x * x + y * y).let { if (it == 0.0) 1.0 else it }
    return Vec2(x / r, y / r)
}
