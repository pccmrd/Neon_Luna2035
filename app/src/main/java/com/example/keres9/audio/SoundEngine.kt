package com.example.keres9.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural audio engine preserving the WebAudio soundscape from audio.js:
 * engine rumble bed, hull drone (55Hz/82.4Hz + filtered noise), periodic harmonic swells,
 * mining laser buzz, EVA air hiss, danger heartbeat, and discrete SFX (puff, shoot, boom,
 * thunk, chime, tick, geiger, ui, blip, warn, alarm, whoosh, cash).
 */
object AU {
    private const val SR = 22050
    private const val CHUNK = 512

    @Volatile
    var on: Boolean = true
        private set

    @Volatile
    private var paused: Boolean = false

    @Volatile
    private var started: Boolean = false

    @Volatile
    private var engineLevel: Double = 0.0

    @Volatile
    private var miningOn: Boolean = false

    @Volatile
    private var airOn: Boolean = false

    @Volatile
    private var dangerOn: Boolean = false

    private var masterGain: Double = 0.8
    private var engGainSmooth: Double = 0.0
    private var engCutoffSmooth: Double = 220.0
    private var mineGainSmooth: Double = 0.0
    private var airGainSmooth: Double = 0.0
    private var puffTimeMs: Long = 0L

    private class Voice(
        val type: String, // "sine", "square", "saw", "triangle", "noise"
        val f0: Double,
        val f1: Double,
        val durSamples: Int,
        val gain: Double,
        val attackSamples: Int = 0,
        val filterType: String = "none", // "none", "lp", "hp", "bp"
        val cutoff0: Double = 1000.0,
        val cutoff1: Double = 1000.0,
        val q: Double = 1.0,
        var delaySamples: Int = 0
    ) {
        var pos: Int = 0
        var phase: Double = 0.0
        var lpState: Double = 0.0
        var hpState: Double = 0.0
        var bpLow: Double = 0.0
        var bpBand: Double = 0.0
    }

    private val voices = CopyOnWriteArrayList<Voice>()
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun init() {
        if (started) return
        started = true
        val minBuf = AudioTrack.getMinBufferSize(
            SR,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = max(minBuf, CHUNK * 4)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        try {
            track.play()
        } catch (_: Throwable) {
            return
        }
        val t = Thread({ runAudioLoop(track) }, "Keres9Audio")
        t.isDaemon = true
        t.start()
        thread = t
    }

    private fun runAudioLoop(track: AudioTrack) {
        val pcm = ShortArray(CHUNK)
        var ph55 = 0.0
        var ph82 = 0.0
        var phLas = 0.0
        var engLp = 0.0
        var hullLp = 0.0
        var airLp = 0.0
        var lasBpLow = 0.0
        var lasBpBand = 0.0
        var nextSwellSamples = (SR * 3.5).toInt()
        var dangerTimerSamples = 0

        val swellRoots = doubleArrayOf(73.42, 87.31, 98.0, 65.41)
        val swellMults = doubleArrayOf(1.0, 1.5, 2.245)

        while (started) {
            if (paused || (!on && masterGain < 0.002)) {
                try {
                    Thread.sleep(25)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            // Periodic harmonic swells
            nextSwellSamples -= CHUNK
            if (nextSwellSamples <= 0) {
                nextSwellSamples = ((9.0 + Random.nextDouble() * 8.0) * SR).toInt()
                val r = swellRoots[Random.nextInt(swellRoots.size)]
                for (i in swellMults.indices) {
                    voices.add(
                        Voice(
                            type = "triangle",
                            f0 = r * swellMults[i],
                            f1 = r * swellMults[i],
                            durSamples = (SR * 9.0).toInt(),
                            gain = 0.018 / (i + 1),
                            attackSamples = (SR * 3.2).toInt()
                        )
                    )
                }
            }

            // Danger heartbeat loop
            if (dangerOn) {
                dangerTimerSamples -= CHUNK
                if (dangerTimerSamples <= 0) {
                    dangerTimerSamples = (SR * 1.15).toInt()
                    voices.add(Voice("sine", 58.0, 40.0, (SR * 0.14).toInt(), 0.28))
                    voices.add(Voice("sine", 52.0, 36.0, (SR * 0.12).toInt(), 0.20, delaySamples = (SR * 0.19).toInt()))
                }
            } else {
                dangerTimerSamples = 0
            }

            val targetMaster = if (on) 0.8 else 0.0
            val targetEng = engineLevel * 0.15
            val targetEngFreq = 200.0 + engineLevel * 900.0
            val targetMine = if (miningOn) 1.0 else 0.0
            val targetAir = if (airOn) 0.013 else 0.0

            val toRemove = mutableListOf<Voice>()

            for (i in 0 until CHUNK) {
                masterGain += (targetMaster - masterGain) * 0.002
                engGainSmooth += (targetEng - engGainSmooth) * 0.001
                engCutoffSmooth += (targetEngFreq - engCutoffSmooth) * 0.001
                mineGainSmooth += (targetMine - mineGainSmooth) * 0.002
                airGainSmooth += (targetAir - airGainSmooth) * 0.0008

                val nz = Random.nextDouble() * 2.0 - 1.0

                // Hull drone: 55Hz + 82.4Hz + soft lowpass noise
                ph55 += 55.0 / SR
                if (ph55 >= 1.0) ph55 -= 1.0
                ph82 += 82.4 / SR
                if (ph82 >= 1.0) ph82 -= 1.0
                val hullAlpha = 2.0 * PI * 250.0 / (2.0 * PI * 250.0 + SR)
                hullLp += (nz - hullLp) * hullAlpha
                var mix = sin(ph55 * 2.0 * PI) * 0.04 + sin(ph82 * 2.0 * PI) * 0.024 + hullLp * 0.014

                // Engine rumble bed
                if (engGainSmooth > 0.0005) {
                    val ea = 2.0 * PI * engCutoffSmooth / (2.0 * PI * engCutoffSmooth + SR)
                    engLp += (nz - engLp) * ea
                    mix += engLp * engGainSmooth
                }

                // Mining laser continuous saw + bandpass noise
                if (mineGainSmooth > 0.0005) {
                    phLas += 84.0 / SR
                    if (phLas >= 1.0) phLas -= 1.0
                    val saw = (phLas * 2.0 - 1.0) * 0.04
                    val fBp = 2.0 * sin(PI * 920.0 / SR)
                    val qBp = 1.0 / 7.0
                    val high = nz - lasBpLow - qBp * lasBpBand
                    lasBpBand += fBp * high
                    lasBpLow += fBp * lasBpBand
                    mix += (saw + lasBpBand * 0.045) * mineGainSmooth
                }

                // Suit EVA air hiss
                if (airGainSmooth > 0.0002) {
                    val aa = 2.0 * PI * 1300.0 / (2.0 * PI * 1300.0 + SR)
                    airLp += (nz - airLp) * aa
                    mix += airLp * airGainSmooth
                }

                // Discrete voices
                for (v in voices) {
                    if (v.delaySamples > 0) {
                        v.delaySamples--
                        continue
                    }
                    if (v.pos >= v.durSamples) {
                        toRemove.add(v)
                        continue
                    }
                    val progress = v.pos.toDouble() / max(1, v.durSamples)
                    val env = if (v.attackSamples > 0) {
                        if (v.pos < v.attackSamples) {
                            v.pos.toDouble() / v.attackSamples
                        } else {
                            max(0.0, 1.0 - (v.pos - v.attackSamples).toDouble() / max(1, v.durSamples - v.attackSamples))
                        }
                    } else {
                        exp(-progress * 5.0)
                    }
                    val freq = if (v.f0 == v.f1) v.f0 else v.f0 * (max(1.0, v.f1) / max(1.0, v.f0)).pow(progress)
                    v.phase += freq / SR
                    if (v.phase >= 1.0) v.phase -= 1.0
                    var sig = when (v.type) {
                        "sine" -> sin(v.phase * 2.0 * PI)
                        "square" -> if (v.phase < 0.5) 1.0 else -1.0
                        "saw" -> v.phase * 2.0 - 1.0
                        "triangle" -> if (v.phase < 0.25) v.phase * 4.0 else if (v.phase < 0.75) 2.0 - v.phase * 4.0 else v.phase * 4.0 - 4.0
                        else -> nz
                    }
                    if (v.filterType != "none") {
                        val fc = if (v.cutoff0 == v.cutoff1) v.cutoff0 else v.cutoff0 * (max(20.0, v.cutoff1) / max(20.0, v.cutoff0)).pow(progress)
                        when (v.filterType) {
                            "lp" -> {
                                val al = 2.0 * PI * fc / (2.0 * PI * fc + SR)
                                v.lpState += (sig - v.lpState) * al
                                sig = v.lpState
                            }
                            "hp" -> {
                                val al = SR / (2.0 * PI * fc + SR)
                                val out = al * (v.hpState + sig - v.lpState)
                                v.lpState = sig
                                v.hpState = out
                                sig = out
                            }
                            "bp" -> {
                                val f = min(0.8, 2.0 * sin(PI * fc / SR))
                                val qInv = 1.0 / max(0.5, v.q)
                                val h = sig - v.bpLow - qInv * v.bpBand
                                v.bpBand += f * h
                                v.bpLow += f * v.bpBand
                                sig = v.bpBand
                            }
                        }
                    }
                    mix += sig * v.gain * env
                    v.pos++
                }
                if (toRemove.isNotEmpty()) {
                    voices.removeAll(toRemove)
                    toRemove.clear()
                }

                // Soft compressor / limiter
                val out = mix * masterGain
                val limited = if (out > 0.75) 0.75 + (out - 0.75) * 0.2 else if (out < -0.75) -0.75 + (out + 0.75) * 0.2 else out
                pcm[i] = (limited.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
            }
            try {
                track.write(pcm, 0, CHUNK)
            } catch (_: Throwable) {
                break
            }
        }
    }

    fun setOn(v: Boolean) {
        on = v
    }

    fun setPaused(p: Boolean) {
        paused = p
        try {
            if (p) audioTrack?.pause() else audioTrack?.play()
        } catch (_: Throwable) {
        }
    }

    fun engine(v: Double) {
        engineLevel = v.coerceIn(0.0, 1.0)
    }

    fun puff() {
        val now = System.currentTimeMillis()
        if (now - puffTimeMs < 95L) return
        puffTimeMs = now
        nz(0.09, 1600.0, 700.0, 0.07, "bp", 2.0)
    }

    fun shoot(g: Double = 1.0) {
        osc("square", 150.0, 52.0, 0.08, 0.16 * g)
        nz(0.05, 2400.0, 900.0, 0.1 * g, "hp")
    }

    fun boom(s: Double = 1.0) {
        nz(0.5 + 0.3 * s, 2200.0, 110.0, 0.45 * min(1.4, s), "lp")
        osc("sine", 120.0, 26.0, 0.6 + 0.3 * s, 0.35 * min(1.2, s))
    }

    fun thunk() {
        osc("sine", 190.0, 70.0, 0.11, 0.3)
        nz(0.04, 900.0, 300.0, 0.14, "lp")
    }

    fun chime() {
        val freqs = doubleArrayOf(660.0, 880.0, 1320.0)
        for (i in freqs.indices) {
            osc("sine", freqs[i], freqs[i], 0.3, 0.1, delaySec = i * 0.09)
        }
    }

    fun tick(h: Double) {
        val f = 1100.0 + h * 500.0
        osc("sine", f, f, 0.025, 0.05)
    }

    fun geiger() {
        nz(0.012, 3400.0, 3000.0, 0.06, "hp")
    }

    fun ui() {
        osc("square", 1500.0, 1200.0, 0.018, 0.05)
    }

    fun blip() {
        osc("sine", 880.0, 660.0, 0.07, 0.06)
    }

    fun warn() {
        osc("square", 440.0, 440.0, 0.1, 0.09)
        osc("square", 330.0, 330.0, 0.12, 0.09, delaySec = 0.14)
    }

    fun alarm() {
        for (i in 0 until 3) {
            osc("saw", 620.0, 380.0, 0.22, 0.07, delaySec = i * 0.26)
        }
    }

    fun whoosh(g: Double = 1.0) {
        nz(0.35, 4200.0, 600.0, 0.12 * g, "bp", 3.0)
    }

    fun cash() {
        osc("sine", 1046.0, 1046.0, 0.09, 0.08)
        osc("sine", 1568.0, 1568.0, 0.16, 0.08, delaySec = 0.08)
    }

    fun mine(isOn: Boolean) {
        miningOn = isOn
    }

    fun air(isOn: Boolean) {
        airOn = isOn
    }

    fun danger(isOn: Boolean) {
        dangerOn = isOn
    }

    private fun osc(t: String, f0: Double, f1: Double, d: Double, g: Double, delaySec: Double = 0.0) {
        if (!started || paused) return
        if (voices.size > 40) return
        voices.add(Voice(t, f0, f1, max(1, (d * SR).toInt()), g, delaySamples = (delaySec * SR).toInt()))
    }

    private fun nz(d: Double, f0: Double, f1: Double, g: Double, ft: String = "lp", q: Double = 1.0) {
        if (!started || paused) return
        if (voices.size > 40) return
        voices.add(Voice("noise", 0.0, 0.0, max(1, (d * SR).toInt()), g, filterType = ft, cutoff0 = f0, cutoff1 = f1, q = q))
    }
}
