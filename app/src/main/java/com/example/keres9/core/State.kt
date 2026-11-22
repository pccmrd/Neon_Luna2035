package com.example.keres9.core

import android.graphics.Bitmap
import kotlin.math.PI

interface Targetable {
    val x: Double
    val y: Double
    val vx: Double
    val vy: Double
    val name: String
    val rad: Double
    val dead: Boolean
}

data class Room(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val kind: String,
    val name: String
)

data class LightSource(
    val lx: Double,
    val ly: Double,
    val c: Int,
    val r: Double,
    val fl: Boolean = false
)

data class GlowSpot(
    val lx: Double,
    val ly: Double,
    val c: Int,
    val r: Double,
    val leak: Boolean
)

data class FogPlume(
    val lx: Double,
    val ly: Double,
    val r: Double,
    val ph: Double,
    val jet: Double?
)

data class StationCrew(
    var lx: Double,
    var ly: Double,
    var tx: Double = 0.0,
    var ty: Double = 0.0,
    var rm: Room,
    val c: Int,
    val sp: Double,
    var t: Double
)

data class Port(
    val cx: Int,
    val cy: Int,
    val dir: Double,
    val berth: Boolean = false,
    var occ: Ship? = null,
    var crew: Boolean = false
)

data class Pad(
    val cx: Int,
    val cy: Int,
    val dir: Double,
    val name: String,
    var occ: Ship? = null
)

data class Chamber(
    val cx: Int,
    val cy: Int,
    val r: Int,
    val biome: String,
    var open: Boolean = false,
    var disc: Boolean = false,
    var contact: Contact? = null
)

data class Contact(
    var x: Double,
    var y: Double,
    val b: Body,
    val kind: String,
    val label: String,
    val f: Double,
    val ph: Double,
    var done: Boolean = false,
    var hint: Double = 0.0,
    val lx: Double,
    val ly: Double,
    var aiNext: Double = 0.0,
    var aiFail: Int = 0,
    var cut: Boolean = false
)

data class EconomyItem(
    var stock: Double,
    val cons: Double,
    val prod: Double,
    val hist: MutableList<Int> = mutableListOf(),
    val ref: Double,
    val mul: Double,
    var mod: Double = 1.0
)

data class TransferInfo(
    val dest: String,
    val gen: Int,
    var depart: Double = 0.0,
    var warned: Boolean = false,
    var back: Double = 0.0
)

data class ArrivalAnim(
    var t: Double = 0.0,
    val dur: Double,
    val sx: Double,
    val sy: Double,
    val tx: Double,
    val ty: Double
)

class Body(
    val kind: String,
    override var x: Double,
    override var y: Double,
    val gw: Int,
    val gh: Int,
    val cs: Double,
    val seed: Int
) : Targetable {
    var a: Double = 0.0
    override var vx: Double = 0.0
    override var vy: Double = 0.0
    var va: Double = 0.0
    val sol: IntArray = IntArray(gw * gh)
    val flr: IntArray = IntArray(gw * gh)
    val roofMask: ByteArray = ByteArray(gw * gh)
    var sol0: IntArray? = null
    var flr0: IntArray? = null
    val dmg: MutableMap<Int, Double> = HashMap()
    val cut: MutableList<Int> = mutableListOf()
    val glow: MutableList<GlowSpot> = mutableListOf()
    val fog: MutableList<FogPlume> = mutableListOf()
    val rooms: MutableList<Room> = mutableListOf()
    val lights: MutableList<LightSource> = mutableListOf()
    val crew: MutableList<StationCrew> = mutableListOf()
    val ports: MutableList<Port> = mutableListOf()
    val pads: MutableList<Pad> = mutableListOf()
    val cham: MutableList<Chamber> = mutableListOf()
    val veins: MutableList<Int> = mutableListOf()
    val contacts: MutableList<Contact> = mutableListOf()
    override var name: String = ""
    override var rad: Double = (hy(gw.toDouble(), gh.toDouble()) * cs) / 2.0
    var roofA: Double = 1.0
    override var dead: Boolean = false
    var breached: Boolean = false

    // Rock fields
    var uid: Int = 0
    var rVal: Double = 0.0 // R in cells
    var type: String = "rock"
    var typeN: String = ""
    var origin: String = ""
    var cat: Int = 0
    var catT: Double = 0.0
    var survey: Double = 0.0
    var comp: MutableMap<String, Int> = mutableMapOf()
    var cells: Int = 0
    var worked: Int = 0
    var veinOn: Boolean = false
    var orphan: Boolean = false
    var hidden: Boolean = false
    var inbound: Boolean = false
    var fresh: Boolean = false
    var kin: Boolean = false

    // Station fields
    var fac: String = "selene"
    var econ: MutableMap<String, EconomyItem>? = null
    var tabs: List<String> = listOf("market", "service", "bar")
    var yard: List<String> = emptyList()
    var surf: Boolean = false
    var crewN: Int? = null
    var liftDock: Vec2? = null
    var cableTop: Vec2? = null
    var transfer: TransferInfo? = null
    var gone: Boolean = false
    var leaving: Boolean = false
    var arr: ArrivalAnim? = null
    var x0: Double = 0.0
    var y0: Double = 0.0
    var r0: Double = 0.0
    var mover: ((Body, Double) -> Unit)? = null

    // Cached pixel bitmaps + backing ARGB arrays
    var roofPixels: IntArray = IntArray(gw * gh)
    var floorPixels: IntArray = IntArray(gw * gh)
    var roofBmp: Bitmap? = null
    var floorBmp: Bitmap? = null
    var roofDirty: Boolean = true
    var floorDirty: Boolean = true
}

data class ShipModule(
    val t: String,
    val gx: Int,
    val gy: Int,
    var hp: Double,
    val max: Double,
    var dead: Boolean = false,
    var flash: Double = 0.0
)

data class DockState(
    val ty: String, // "port", "pad", "anchor", "ground"
    val b: Body? = null,
    val pPort: Port? = null,
    val pPad: Pad? = null,
    var cx: Int = 0,
    var cy: Int = 0,
    var lx: Double = 0.0,
    var ly: Double = 0.0,
    var ao: Double = 0.0,
    var ao0: Double? = null,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var a: Double = 0.0
)

data class BeamInfo(
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
    val hit: Boolean = false,
    val miss: Boolean = false
)

class Ship(
    val cls: String,
    val role: String,
    val fac: String,
    override var name: String,
    override var x: Double,
    override var y: Double,
    val gw: Int,
    val gh: Int,
    val tailLand: Boolean
) : Targetable {
    val kind: String = "ship"
    override var vx: Double = 0.0
    override var vy: Double = 0.0
    var a: Double = 0.0
    var va: Double = 0.0
    val mods: MutableList<ShipModule> = mutableListOf()
    val grid: IntArray = IntArray(gw * gh)
    var fuel: Double = 0.0
    var fuelMax: Double = 100.0
    var cargo: MutableMap<String, Int> = mutableMapOf()
    var cargoMax: Int = 20
    var upg: MutableMap<String, Int> = mutableMapOf()
    var dock: DockState? = null
    var gear: Boolean = tailLand
    var gearOK: Boolean = false
    override var dead: Boolean = false
    var isPl: Boolean = false
    var own: Boolean = false
    var crewShuttle: Boolean = false
    var thr: Double = 0.0
    var rcx: Double = 0.0
    var rcy: Double = 0.0
    var tqi: Double = 0.0
    var wantM: Boolean = false
    var beam: BeamInfo? = null
    var spr: Bitmap? = null
    var dirty: Boolean = true
    var ag: Agent? = null
    var hitT: Double = 0.0
    var grounded: Double = 0.0
    var settleT: Double = 0.0
    var liftT: Double? = null
    var rail: Boolean = false
    var ghost: Boolean = false
    var leaving: Boolean = false
    var lightOn: Boolean = true

    // Computed stats
    var mass: Double = 14.0
    var thrust: Double = 3.0
    var rcsA: Double = 2.0
    var tq: Double = 2.0
    var burn: Double = 0.5
    var nose: Double = 12.0
    var belly: Double = 8.0
    override var rad: Double = 14.0
    var lasR: Double = C.MINE.rng
    var lasD: Double = C.MINE.dps
    var scanR: Double = C.SCAN.rng
}

class SuitState(
    var x: Double,
    var y: Double,
    var vx: Double = 0.0,
    var vy: Double = 0.0,
    var a: Double = 0.0,
    var suit: Double = 100.0,
    val sack: MutableMap<String, Int> = mutableMapOf(),
    var carry: Pick? = null,
    var dead: Boolean = false,
    var pingT: Double = 9.0,
    var pingEnd: Double = 0.0,
    var bob: Double = 0.0,
    var home: Ship? = null,
    var grounded: Double = 0.0,
    var beam: BeamInfo? = null,
    var hide: Boolean = false,
    var jet: Double = 0.0,
    var fullT: Double = 0.0,
    var lightOn: Boolean = true
)

data class Pick(
    var x: Double,
    var y: Double,
    var vx: Double = 0.0,
    var vy: Double = 0.0,
    var a: Double = 0.0,
    var va: Double = 0.0,
    val type: String,
    var qty: Int,
    val r: Double,
    var dead: Boolean = false,
    var rev: Double = 0.0,
    var age: Double = 0.0,
    var held: Boolean = false,
    var home: Body? = null,
    var hx: Double = 0.0,
    var hy: Double = 0.0
)

data class Bit(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    var life: Double,
    val max: Double,
    var s: Double,
    val c: Int,
    val t: String
)

data class Debris(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    var a: Double,
    var va: Double,
    val w: Double,
    val h: Double,
    val c: Int,
    var life: Double,
    var hot: Double
)

data class Flash(
    val x: Double,
    val y: Double,
    val r: Double,
    var t: Double,
    val max: Double
)

data class HeatSpot(
    val x: Double,
    val y: Double,
    val b: Body,
    val cx: Int,
    val cy: Int,
    val t: Double,
    val c: Int
)

data class Meteor(
    var x: Double,
    var y: Double,
    var px: Double,
    var py: Double,
    val vx: Double,
    val vy: Double,
    var life: Double,
    val r: Double,
    var dead: Boolean = false
)

data class CrewMateSuit(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    var a: Double,
    val name: String,
    val c: Int,
    var st: String,
    var tgt: Pose? = null,
    var t: Double = 0.0,
    var n: Int = 0,
    var beam: BeamInfo? = null,
    var goT: Double = 0.0,
    var bt: Double = 0.0
)

data class Pose(val x: Double, val y: Double, val a: Double)

data class LiftCar(
    val id: String,
    var s: Double,
    var v: Double = 0.0,
    var dir: Int = 0,
    var rider: Boolean = false,
    var x: Double = 0.0,
    var y: Double = 0.0
)

data class LiftEnds(val g: Vec2, val t: Vec2)

data class LiftState(
    val term: Body,
    val top: Body,
    val cars: List<LiftCar>,
    var idle: Double = 45.0,
    var chat: Double = 0.0,
    var ci: Int = 0,
    var len: Double = 1400.0,
    var E: LiftEnds? = null
)

data class RideState(
    val kind: String, // "lift" or "shuttle"
    val car: LiftCar? = null,
    val ship: Ship? = null,
    val t0: Double = 0.0,
    val dir: Int = 0
)

data class AutopOut(
    var thr: Double = 0.0,
    var rcx: Double = 0.0,
    var rcy: Double = 0.0,
    var tqi: Double = 0.0
)

data class CrewState(
    var state: String = "idle",
    var site: Body? = null,
    var t: Double = 0.0,
    val haul: MutableMap<String, Int> = mutableMapOf(),
    val mates: MutableMap<String, Int> = mutableMapOf(),
    var mine: Int = 0,
    val ship: Ship,
    val ap: AutopOut = AutopOut(),
    var wait: Double = 0.0,
    var horn: Boolean = false,
    var grace: Double = 0.0,
    var warned: Boolean = false,
    var late: Boolean = false,
    var legT: Double = 0.0,
    var exit: Vec2? = null,
    var home: Port? = null,
    var shifts: Int = 0
)

data class Agent(
    val id: Int,
    val name: String,
    val role: String,
    val fac: String,
    val ship: Ship,
    var state: String = "idle",
    var t: Double = 0.0,
    var credits: Int = 0,
    val mood: Double = 0.0,
    val ap: AutopOut = AutopOut(),
    var alive: Boolean = true,
    var dest: Body? = null,
    var rock: Body? = null,
    var base: Body? = null,
    var final: Boolean = false,
    var dockChoice: DockPick? = null
)

data class DockPick(val pPort: Port?, val pPad: Pad?, val ty: String)

data class HaulEvent(
    val b: Body,
    val tug: Ship,
    val tx: Double,
    val ty: Double,
    var t: Double = 0.0,
    val dur: Double = 75.0,
    val sx: Double,
    val sy: Double,
    var stage: String = "in"
)

data class ShowerEvent(
    var t: Double,
    val dur: Double,
    val a: Double,
    val x: Double,
    val y: Double
)

data class EventsState(
    var next: Double = 150.0,
    var haul: HaulEvent? = null,
    var shower: ShowerEvent? = null
)

data class FlareEvent(
    var on: Boolean = false,
    var t: Double = 0.0,
    var warn: Double = 0.0,
    var next: Double = 500.0
)

data class ExtraRockRecord(
    val seed: Int,
    var x: Double,
    var y: Double,
    val R: Double,
    val type: String,
    var done: Boolean = false
)

data class FeedEntry(val t: Double, val m: String, val k: String = "")

data class NavTarget(val x: Double, val y: Double, val l: String = "WAYPOINT")

data class WorldStats(
    var mined: Int = 0,
    var earned: Int = 0,
    var found: Int = 0,
    var docked: Int = 0,
    var catalogued: Int = 0,
    var shifts: Int = 0,
    var delivered: Int = 0,
    var launches: Int = 0,
    var landings: Int = 0,
    var events: Int = 0,
    var eva: Int = 0,
    var lifts: Int = 0
)

data class Job(
    val id: String,
    val kind: String, // "deliver" or "survey"
    val g: String = "",
    val n: Int = 0,
    var got: Int = 0,
    val body: Int = 0,
    val to: String,
    val from: String,
    val reward: Int,
    val title: String,
    val desc: String,
    var aiTry: Int = 0
)

data class ServiceItem(
    val l: String,
    val d: String,
    val c: Int,
    val min: Int = 0,
    val ok: Boolean,
    val f: () -> Unit
)

data class YardItem(
    val cls: String,
    val n: String,
    val c: Int,
    val owned: Boolean,
    val d: String
)

data class HoldView(
    val c: MutableMap<String, Int>,
    val max: Int,
    val used: Int,
    val n: String,
    val ship: Ship?
)

data class StoryStep(val id: String, val t: String, val d: String, val r: Int)

data class BackgroundAssets(
    val stars: List<Bitmap>,
    val earth: Bitmap
)

class WorldState(val seed: Int) {
    var bodies: MutableList<Body> = mutableListOf()
    var rocks: MutableList<Body> = mutableListOf()
    var stations: MutableList<Body> = mutableListOf()
    var ships: MutableList<Ship> = mutableListOf()
    var picks: MutableList<Pick> = mutableListOf()
    var bits: MutableList<Bit> = mutableListOf()
    var deb: MutableList<Debris> = mutableListOf()
    var flashes: MutableList<Flash> = mutableListOf()
    var contacts: MutableList<Contact> = mutableListOf()
    var feed: MutableList<FeedEntry> = mutableListOf()
    var heat: MutableList<HeatSpot> = mutableListOf()
    var meteors: MutableList<Meteor> = mutableListOf()
    var suits: MutableList<CrewMateSuit> = mutableListOf()
    var lift: LiftState? = null
    var orphan: Body? = null
    var aid: Int = 200
    var nav: NavTarget? = null
    var time: Double = 0.0
    var extra: MutableList<ExtraRockRecord> = mutableListOf()
    var evt: EventsState? = null
    var flare: FlareEvent? = null
    var stats: WorldStats = WorldStats()
    var bg: BackgroundAssets? = null
}

class GameState {
    var mode: String = "title" // title, play, pause, dead, win
    var cr: Int = 240
    var tier: Int = 1
    var fleet: MutableList<Ship> = mutableListOf()
    var story: Int = 0
    var prog: MutableMap<String, Int> = mutableMapOf()
    var missions: MutableList<Job> = mutableListOf()
    var jobs: MutableMap<String, MutableList<Job>> = mutableMapOf()
    var bounty: Int = 0
    var ride: RideState? = null
    var crew: CrewState? = null
    var clock: Double = 0.0
    var day: Int = 1
    var save: Double = 0.0
    var sound: Boolean = true
    var qty: String = "1" // "1", "5", "10", "MAX"
    var panel: Body? = null
    var tab: String = "market"
    var svc: List<ServiceItem> = emptyList()
    var win: Boolean = false
    var jseed: Int = 1
    var _e: Double = 0.0
    var _j: Double = 0.0
    var _sh: Double = 0.0
    var _rm: Double = 0.0
    var _pn: Int = 0
    var airUp: Double = 1.0
    var sackUp: Double = 1.0
    var suitUp: Double = 1.0
    var cores: Int = 0
    var fuelWarn: Boolean = false
    var autoPaused: Boolean = false
    var paperworkDone: Boolean = false
    var bunkClaimed: Boolean = false
    var docWaiver: Boolean = false
    var docTelemetry: Boolean = false
    var docZone: Boolean = false
}

class CameraState {
    var x: Double = 0.0
    var y: Double = -3000.0
    var z: Double = 1.0
    var tz: Double = 1.0
    var rot: Double = 0.0
    var shake: Double = 0.0
    var lx: Double = 0.0
    var ly: Double = 0.0
    var lt: Double = 0.0
    var flash: Double = 0.0
    var user: Double = 0.0
    var snap: Boolean = false
    var aux: Vec2? = null
    var zr: Double = 2.5
}

data class ScanEntry(val c: Contact, val f: Double, val s: Double, val d: Double)
data class SurveyProgress(val b: Body, val p: Double)
data class TrajHit(val x: Double, val y: Double, val t: Double)

data class DockGates(
    val v: Boolean,
    val a: Boolean,
    val l: Boolean,
    val r: Boolean,
    val near: Boolean,
    val gear: Boolean,
    val rv: Double,
    val ae: Double,
    val lat: Double,
    val re: Double,
    val d: Double,
    val w: WorldPortPose,
    val ty: String,
    val want: Double,
    val b: Body,
    val pPort: Port?,
    val pPad: Pad?,
    val ok: Boolean
)

data class WorldPortPose(
    val x: Double,
    val y: Double,
    val dir: Double,
    val vx: Double,
    val vy: Double,
    val va: Double
)

data class AnchorCandidate(val b: Body, val cx: Int, val cy: Int, val d: Double)

data class ContextAction(
    val l: String = "—",
    val f: (() -> Unit)? = null,
    val ok: Boolean = false,
    val k: String = ""
)

data class AltAction(
    val l: String,
    val f: () -> Unit,
    val k: String
)

class PlayerCockpitState {
    var tgt: Targetable? = null
    var scan: Boolean = false
    var freq: Double = 110.0
    var lock: Double = 0.0
    var lockC: Contact? = null
    val list: MutableList<ScanEntry> = mutableListOf()
    var sig: Double = 0.0
    var assist: DockGates? = null
    var survey: SurveyProgress? = null
    var ctx: ContextAction = ContextAction()
    var alt: AltAction? = null
    var match: Boolean = false
    var sas: Int = 0
    var thrArm: Boolean = false
    val hints: MutableSet<String> = mutableSetOf()
    var traj: TrajHit? = null
    var scanLabel: String = "sweep the cone across rock · find a resonance · hold the lock"
}

class ControlFrame {
    var lx: Double = 0.0
    var ly: Double = 0.0
    var rx: Double = 0.0
    var ry: Double = 0.0
    var thr: Double = 0.0
    var mine: Boolean = false
    var brake: Boolean = false
    var ai: Boolean = false
}

object GS {
    var W: WorldState? = null
    var G: GameState? = null
    var pl: Ship? = null
    var ev: SuitState? = null
    var AG: MutableList<Agent> = mutableListOf()
    val cam = CameraState()
    val PC = PlayerCockpitState()
    val CTL = ControlFrame()

    fun focus(): Targetable? {
        ev?.let {
            return object : Targetable {
                override val x: Double get() = it.x
                override val y: Double get() = it.y
                override val vx: Double get() = it.vx
                override val vy: Double get() = it.vy
                override val name: String get() = "SUIT"
                override val rad: Double get() = 2.6
                override val dead: Boolean get() = it.dead
            }
        }
        pl?.let { return it }
        val r = G?.ride ?: return null
        r.ship?.let { return it }
        r.car?.let { car ->
            return object : Targetable {
                override val x: Double get() = car.x
                override val y: Double get() = car.y
                override val vx: Double get() = 0.0
                override val vy: Double get() = 0.0
                override val name: String get() = "LIFT"
                override val rad: Double get() = 5.0
                override val dead: Boolean get() = false
            }
        }
        return null
    }
}
