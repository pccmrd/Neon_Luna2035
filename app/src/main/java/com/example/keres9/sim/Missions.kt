package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.Body
import com.example.keres9.core.Contact
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.GameState
import com.example.keres9.core.Job
import com.example.keres9.core.SeededRng
import com.example.keres9.core.StoryStep
import com.example.keres9.core.fm
import com.example.keres9.core.hy
import com.example.keres9.core.pk
import com.example.keres9.core.ri
import com.example.keres9.world.SITES
import kotlin.math.min
import kotlin.math.roundToInt

fun newGame(): GameState = GameState()

val STORY: List<StoryStep> = listOf(
    StoryStep("arrive", "ARRIVAL ABOARD MERIDIAN", "Step out of PSV MERIDIAN's east airlock and jet 60 m east into SELENE HIGH to report at the INTAKE OFFICE.", 0),
    StoryStep("intake", "CONSORTIUM INTAKE", "In the INTAKE OFFICE (lower-left room), press F at the terminal, sign all 3 consortium clauses, and claim your Room 14B keycard.", 120),
    StoryStep("bunk", "ROOM 14B ON THE RIM", "Ride SELENE LIFT ▼ down to Shackleton Rim, walk west to HAB 14 · SHARED ROOM 14B, and claim your assigned bunk [F].", 0),
    StoryStep("commute", "MORNING MUSTER", "Muster east into SELENE LIFT with your crewmates and ride the cable ▲ up to SELENE HIGH.", 0),
    StoryStep("sign", "REPORT TO MANAGER LIU", "Float to the CREW OFFICE (upper-left), press F at Manager Liu Mei's terminal and BOARD MULE-3.", 0),
    StoryStep("shift", "FIRST SHIFT", "On site: cut rock with [MINE] (use [LIGHT] inside dark caves), fill your sack, and board MULE-3 before the horn.", 150),
    StoryStep("survey", "EYES ON THE DARK", "Survey 3 uncatalogued bodies — [C] hand scanner on EVA near rock, or a ship array. Consortium bounties pay at the CATALOG OFFICE.", 200),
    StoryStep("hull", "YOUR OWN HULL", "Work shifts, sell your finds and save 2,400₡ for a WREN orbital tug at the SELENE HIGH shipyard.", 0),
    StoryStep("sell", "SELL TO THE STARS", "Mine from your WREN and sell 8+ units to ARES TRANSIT (Mars), GAIA RETURN (Earth) or VULCAN REFINERY.", 300),
    StoryStep("ghost", "GHOST TAGS", "Autonomous Helion probes staked bodies before KERES-9 died. Deep-scan [C, tune the dial] and resolve 2 HELION claim transponders.", 400),
    StoryStep("shards", "KERES-9'S MEMORY", "Cut 3 KERES-9 core shards out of sealed dark chambers and deliver them at the CATALOG OFFICE on SELENE HIGH.", 1500),
    StoryStep("lander", "DOWN THE WELL", "Ride the lift down and buy a HARE surface lander at SHACKLETON DEPOT (6,500₡).", 0),
    StoryStep("launch", "PAD TO ORBIT", "Throttle up off PAD 1, pitch over, climb 1,200 m into the band and dock or harpoon on. All manual.", 500),
    StoryStep("water", "WATER FOR THE RIM", "Fly ice down the well: sell 20 Water Ice at SHACKLETON DEPOT from the HARE on the pad.", 900),
    StoryStep("orphan", "STAKE THE ORPHAN", "The Orphan is on your chart. Cut into its platinum heart and sell 12 Platinum Group to GAIA RETURN.", 2500),
    StoryStep("file", "FILE THE CLAIM", "At the CATALOG OFFICE, file the Orphan claim in your own name — and the unredacted KERES-9 logs with it.", 0),
    StoryStep("free", "FREE PROSPECTOR", "The band is yours: between Consortium tariffs and Syndicate claims, an independent cutter still writes the log.", 0)
)

fun storyIndex(id: String): Int = STORY.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

fun storyAtOrPast(id: String): Boolean = (GS.G?.story ?: 0) >= storyIndex(id)

fun storyObj(): StoryStep {
    val idx = min(GS.G?.story ?: 0, STORY.lastIndex)
    return STORY[idx]
}

private fun inc(k: String, n: Int = 1) {
    val P = GS.G?.prog ?: return
    P[k] = (P[k] ?: 0) + n
}

private fun prog(k: String): Int = GS.G?.prog?.get(k) ?: 0

data class PendingToast(var delay: Double, val msg: String, val kind: String?)
val pendingToasts = mutableListOf<PendingToast>()

fun stepPendingToasts(dt: Double) {
    if (pendingToasts.isEmpty()) return
    val it = pendingToasts.iterator()
    while (it.hasNext()) {
        val p = it.next()
        p.delay -= dt
        if (p.delay <= 0.0) {
            it.remove()
            Hooks.toast(p.msg, p.kind)
        }
    }
}

private fun done(text: String? = null) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    val s = storyObj()
    if (s.r > 0) {
        G.cr += s.r
        W.stats.earned += s.r
    }
    AU.chime()
    val rw = if (s.r > 0) " · +${fm(s.r)}₡" else ""
    val suf = if (text != null) " — $text" else ""
    Hooks.toast("✓ ${s.t}$rw$suf", "good")
    feed("Milestone: ${s.t}", "good")
    G.story++
    val n = storyObj()
    pendingToasts.add(PendingToast(1.6, "NEXT ▸ ${n.t} — ${n.d}", "warn"))
    Hooks.storyStep(G.story)
    checkStory()
}

fun story(
    ev0: String,
    st: Body? = null,
    g: String? = null,
    k: Int = 0,
    contact: Contact? = null,
    roomKind: String? = null,
    roomBody: Body? = null,
    liftTop: Boolean = false,
    shiftUnits: Int = 0
) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    val s = storyObj()
    if (ev0 == "catalog") inc("cat")
    if (ev0 == "sold" && st != null) {
        if (st.transfer != null || st === SITES.vul) inc("sold", k)
        if (st === SITES.depot && g == "ice") inc("ice", k)
        if (st === SITES.gaia && g == "pgm") inc("pgm", k)
    }
    if (ev0 == "resolved" && contact?.kind == "claim") inc("claims")
    when (s.id) {
        "arrive" -> if ((ev0 == "room" && roomBody === SITES.sel) || ev0 == "intake") done("welcome aboard SELENE HIGH — head into the INTAKE OFFICE")
        "intake" -> if (ev0 == "intake" || G.paperworkDone) done("Room 14B keycard authorized")
        "bunk" -> if (ev0 == "bunk" || G.bunkClaimed) done("bunk locked in — muster east at the Lift for 06:00 shift")
        "commute" -> if (ev0 == "lift" && liftTop) done("report to Manager Liu in Crew Office (upper-left)")
        "sign" -> if (ev0 == "board") done("MULE-3 is lifting")
        "shift" -> if (ev0 == "shift" && shiftUnits >= 1) done("you are a hand now")
        "survey" -> if (prog("cat") >= 3) done("the office pays per body")
        "hull" -> if (G.fleet.any { it.cls == "wren" && !it.dead }) done("she is slow, but she is yours")
        "sell" -> if (prog("sold") >= 8) done("volume is money")
        "ghost" -> if (prog("claims") >= 2) done("HELION MINERALS has been busy")
        "shards" -> if (G.cores >= 3) {
            W.orphan?.let { it.hidden = false }
            done("the logs show a body KERES-9 flagged hours before it died — the Orphan")
        }
        "lander" -> if (G.fleet.any { it.cls == "hare" && !it.dead }) done("PAD 1 is yours")
        "launch" -> if (ev0 == "orbitdock") done("pad to orbit, hands on")
        "water" -> if (prog("ice") >= 20) done("the Rim drinks tonight")
        "orphan" -> if (prog("pgm") >= 12) done("GAIA RETURN logged the provenance")
        "file" -> if (ev0 == "filed") {
            G.win = true
            done()
            Hooks.win()
        }
    }
}

private val PASSIVE_STORY_IDS = setOf("intake", "bunk", "survey", "hull", "sell", "ghost", "shards", "lander", "water", "orphan")

fun checkStory() {
    val s = storyObj()
    if (PASSIVE_STORY_IDS.contains(s.id)) story("tick")
}

private fun orbStations(): List<Body> = listOf(SITES.sel, SITES.vul, SITES.ares, SITES.gaia).filter { !it.gone }

private val MARS_GOODS = listOf("ice", "vol", "food")
private val EARTH_GOODS = listOf("pgm", "ree", "fe")
private val SURF_GOODS = listOf("ice", "fe", "vol")
private val ORB_GOODS = listOf("fe", "ice", "parts")
private val UPWELL_GOODS = listOf("food", "parts")

fun mkJobs(st: Body) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    if (st.econ == null || (st.surf && st !== SITES.depot && st !== SITES.tycho)) return
    val rngObj = SeededRng((G.jseed++ * 7919) xor W.seed)
    val rf: () -> Double = { rngObj.next() }
    val L = mutableListOf<Job>()
    for (k in 0 until 3) {
        val q = rf()
        if (q < 0.45) {
            val dests = (orbStations() + listOf(SITES.depot, SITES.tycho)).filter { it !== st }
            if (dests.isEmpty()) continue
            val to = pk(rf, dests)
            val goods = when {
                to.transfer != null -> if (to.transfer?.dest == "mars") MARS_GOODS else EARTH_GOODS
                to.surf -> SURF_GOODS
                else -> ORB_GOODS
            }
            val g = pk(rf, goods)
            val def = GD[g] ?: continue
            val n = if (g == "pgm" || g == "ree") ri(rf, 3, 6) else ri(rf, 8, 20)
            val reward = (n * def.p * (if (to.surf) 1.6 else 1.25) + 60.0).roundToInt()
            L.add(
                Job(
                    id = "j${G.jseed}$k",
                    kind = "deliver",
                    g = g,
                    n = n,
                    got = 0,
                    to = to.name,
                    from = st.name,
                    reward = reward,
                    title = "DELIVER $n ${def.n.uppercase()}",
                    desc = "Deliver $n ${def.n} to ${to.name}${if (to.surf) " (surface — lander required)" else ""}. Sell it there to count."
                )
            )
        } else if (q < 0.8) {
            val cand = W.rocks.filter { b -> b.cat == 0 && !b.hidden && hy(b.x - st.x, b.y - st.y) < 3500.0 }
            if (cand.isEmpty()) continue
            val b = pk(rf, cand)
            L.add(
                Job(
                    id = "j${G.jseed}$k",
                    kind = "survey",
                    body = b.uid,
                    to = b.name,
                    from = st.name,
                    reward = 120 + (b.rVal * 3.0).roundToInt(),
                    title = "SURVEY ${b.name}",
                    desc = "Catalog Office wants eyes on ${b.name} (${hy(b.x - st.x, b.y - st.y).roundToInt()} m out). Survey it with a hand scanner or array."
                )
            )
        } else {
            val g = pk(rf, UPWELL_GOODS)
            val def = GD[g] ?: continue
            val n = ri(rf, 6, 14)
            val from = if (g == "food") SITES.depot else SITES.tycho
            val to = SITES.sel
            L.add(
                Job(
                    id = "j${G.jseed}$k",
                    kind = "deliver",
                    g = g,
                    n = n,
                    got = 0,
                    to = to.name,
                    from = st.name,
                    reward = (n * def.p * 1.5 + 140.0).roundToInt(),
                    title = "UP-WELL RUN · ${def.n.uppercase()}",
                    desc = "Buy $n ${def.n} at ${from.name} (surface) and sell it at SELENE HIGH. Lander work."
                )
            )
        }
    }
    G.jobs[st.name] = L
}

fun accept(st: Body, id: String) {
    val G = GS.G ?: return
    if (G.missions.size >= 3) {
        Hooks.toast("Three contracts max", "warn")
        return
    }
    val list = G.jobs[st.name] ?: return
    val j = list.firstOrNull { it.id == id } ?: return
    G.missions.add(j)
    list.remove(j)
    AU.ui()
    Hooks.toast("CONTRACT SIGNED · ${j.title}", "good")
}

fun dropJob(id: String) {
    val G = GS.G ?: return
    G.missions.removeAll { it.id == id }
}

private fun payJob(j: Job) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    G.missions.remove(j)
    G.cr += j.reward
    W.stats.earned += j.reward
    AU.cash()
    Hooks.toast("CONTRACT COMPLETE · ${j.title} · +${fm(j.reward)}₡", "good")
    feed("Contract closed: ${j.title}", "good")
}

fun jobSold(st: Body, g: String, k: Int) {
    val G = GS.G ?: return
    for (j in G.missions.toList()) {
        if (j.kind == "deliver" && j.to == st.name && j.g == g) {
            j.got += k
            if (j.got >= j.n) payJob(j)
        }
    }
}

fun jobCatalog(b: Body) {
    val G = GS.G ?: return
    for (j in G.missions.toList()) {
        if (j.kind == "survey" && j.body == b.uid) payJob(j)
    }
}
