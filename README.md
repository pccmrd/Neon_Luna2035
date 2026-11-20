# KERES-9 — CATALOG ZERO · Moon 2035 (Android)

A 2D Newtonian lunar prospecting and surveying simulation built in Kotlin and Jetpack Compose for Android.

Haul engines drag rocks and comets in from the Belt and the Kuiper dark and park them in lunar orbit. Then the **KERES-9** catalog AI died, and nobody knows what is up there anymore. More than 300 bodies in the band are uncatalogued.

You live in HAB 14 on the Shackleton rim. Every morning you ride the Selene Lift cable up to orbit. You start as an EVA crew hand with a suit and a hand cutter, work up to a **WREN** orbital tug, and eventually fly a **HARE** surface-to-orbit tail-lander down to the pad outside your door before staking the **Orphan**.

## Core Systems Preserved

- **Analytic Moon & Synchronous Orbit Band:** Rotating lunar frame, surface gravity (`1.6 m/s²`), real-time ballistic impact projection, and day/night terminator.
- **Destructible Cellular Bodies:** Cut veins and sealed chambers out of rotating asteroids and comets; harvest water ice, nickel-iron, comet volatiles, platinum group metals, rare-earth crystal, fab parts, and KERES-9 core shards.
- **Three-Tier Career & 15-Chapter Story:** Selene Lift cable travel, Foreman Lu's MULE-3 EVA crew shifts, WREN orbital tug mining/harpooning, and HARE surface-to-orbit launches and tail-first landings.
- **Directional Array & Deep Scanning:** 20–200 kHz resonance sweep, cone tracking, and lock progress to uncover veins, sealed chambers, core shards, and HELION claim tags.
- **Dynamic Lunar Economy & NPC Traffic:** Automated supply/demand prices with trend sparklines, transfer ships (*Ares Transit* & *Gaia Return*), mission contracts, hull/suit upgrades, and sector events (inbound hauls, meteor showers, proton storms).
- **Autonomous AI Pilot (`AI` / `WATCH AI PLAY`):** Plays using the same virtual sticks, throttle rail, and UI actions with live captioning, button target highlights, and ×3 cruise time-warp; touch any flight control to resume manual piloting.
- **Procedural Audio & Local Persistence:** Real-time synthesized PCM sound effects (`AudioTrack`) and automatic career save/load via `SharedPreferences`.

## Controls

- **Touch:** Left virtual stick translates/walks (`MOVE · RCS`), right virtual stick rotates (`SPIN · TORQUE`), right rail controls main throttle (`THR`), pinch to zoom, drag background to look around, tap bodies/ships to target or empty space to set a nav waypoint.
- **Action Clusters:** `MATCH ΔV`, `BRAKE`, `TGT ▸`, `GEAR`, `SCAN`, `MINE`, plus context-sensitive `CTX` and `ALT` (SAS / Suit Scanner Ping / EVA / Undock / Harpoon / Clamp / Ride Lift).
- **Hardware Keyboard (Desktop / Tablet):** `WASD` translate/walk/hop, `Q/E` rotate, `Shift/Ctrl` throttle, `M/Space` mine/cut, `F` context action, `V` alt (ping/SAS), `X` match ΔV, `Z` brake, `T/Tab` target, `G` gear, `C` array, `P` AI pilot, `H` field manual, `Esc` pause.

## Android Project Layout

```
app/src/main/java/com/example/
  MainActivity.kt               Edge-to-edge Compose host, 60fps simulation loop, keyboard & touch integration
  keres9/core/                  Constants (Config.kt), math/PRNG (Util.kt), game & world models (State.kt)
  keres9/world/                 Lunar heightfield (Moon.kt), cellular terrain (BodyTerrain.kt), generation (WorldGen.kt)
  keres9/audio/                 Procedural PCM synthesizer (SoundEngine.kt)
  keres9/data/                  Career JSON save/load persistence (SaveRepository.kt)
  keres9/sim/                   Physics, EVA, Lift, Crew, Ships, Combat, Scanner, Economy, Missions, Events, GameSession
  keres9/ai/                    Autopilot navigation primitives (AiNav.kt) and career AI brain (AiPilot.kt)
  keres9/render/                Hardware-accelerated Canvas world, lighting, chart, scope, and spectrum renderer (GameRenderer.kt)
  keres9/ui/                    ViewModel, Material 3 industrial theme, chamfered HUD/controls, title & station modals
```
