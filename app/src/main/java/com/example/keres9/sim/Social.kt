package com.example.keres9.sim

import com.example.keres9.core.Agent
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.fm
import com.example.keres9.core.hy
import com.example.keres9.core.pk
import com.example.keres9.core.rand
import com.example.keres9.world.SITES

private val LINES = listOf(
    "After the 2029 Compute Accords carved up Earth orbital slots, the Eastern Cable Consortium sank its capital into Shackleton Lift while the Western Syndicate automated belt haulage.",
    "Everyone thought agentic AI in 2026 meant endless abundance. Instead two conglomerates own the orbital haul fleets and humans sign bunk-bonds to cut ore with hand lasers.",
    "KERES-9 was the Geneva neutral registry keeping Eastern tether tariffs from choking Western claim-jumpers. The day its logs went dark, both blocs blamed the sun.",
    "Helion Minerals' autonomous drones were staking rocks long before the blackout. Funny how Syndicate transponders sit on bodies nobody officially mined.",
    "Ice down at the Depot sells for twice what it does up here. If you had a lander, you would be rich by Thursday.",
    "Manager Liu has been running shifts since the cable anchored. Never lost a hand to vacuum — though she'll dock a quarter share if you miss the horn.",
    "Comets come in venting. Cut past the frozen crust and shine your beam deep inside — Mars transit pays well for clean volatiles.",
    "The cable is the only thing on the Moon that hangs straight. Everything else obeys orbital mechanics."
)

private val RUMOR_GOODS = listOf("ice", "fe", "vol", "pgm")

fun rumor(): String {
    val W = GS.W
    val rf = ::rand
    val r = rf()
    if (r < 0.3 && W != null) {
        val g = pk(rf, RUMOR_GOODS)
        val L = W.stations.filter { it.econ != null && !it.gone }
        var lo: com.example.keres9.core.Body? = null
        var hi: com.example.keres9.core.Body? = null
        for (s in L) {
            if (lo == null || price(s, g) < price(lo, g)) lo = s
            if (hi == null || price(s, g, sell = true) > price(hi, g, sell = true)) hi = s
        }
        val def = GD[g]
        if (lo != null && hi != null && lo !== hi && def != null) {
            return "${def.n}: ${lo.name} sells at ${price(lo, g)}₡, ${hi.name} pays ${price(hi, g, sell = true)}₡. Do the math."
        }
    }
    if (r < 0.5 && W != null) {
        val un = W.contacts.filter { !it.done && !it.b.hidden && (it.kind == "claim" || it.kind == "core") }
        if (un.isNotEmpty()) {
            val c = pk(rf, un)
            val what = if (c.kind == "claim") "a transponder where there should not be one." else "cold, structured, very old electronics."
            return "Drone pilots keep pinging something odd inside ${c.b.name} — $what About ${fm(hy(c.x - SITES.sel.x, c.y - SITES.sel.y))} m from SELENE HIGH."
        }
    }
    if (r < 0.6 && storyAtOrPast("lander") && W?.orphan != null) {
        return "Everyone is talking about the Orphan. Half a kilometre of metal and a Helion beacon nobody admits to."
    }
    return pk(rf, LINES)
}

fun agentLine(a: Agent): String = when (a.role) {
    "hauler" -> "Bulk run between the stations. Same lane every day. Credits: ${fm(a.credits)}₡."
    "pros" -> {
        val rock = a.rock
        if (rock != null) {
            val status = if (rock.cat != 0) "At least it is on the books now." else "Uncatalogued. Could be anything in there."
            "Working ${rock.name}. $status"
        } else {
            "Between rocks. Looking."
        }
    }
    "lander" -> "Rim shuttle. Down the well, up the well. My knees hate gravity."
    else -> "Catalog drone operator. We map what the haul engines drop. Too slow, the office says."
}
