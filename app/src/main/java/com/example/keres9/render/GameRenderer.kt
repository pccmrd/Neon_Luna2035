package com.example.keres9.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.FC
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.PAL
import com.example.keres9.core.Pick
import com.example.keres9.core.Ship
import com.example.keres9.core.SuitState
import com.example.keres9.core.TAU
import com.example.keres9.core.Targetable
import com.example.keres9.core.TrajHit
import com.example.keres9.core.Vec2
import com.example.keres9.core.cl
import com.example.keres9.core.gacc
import com.example.keres9.core.hy
import com.example.keres9.core.lp
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.SeededRng
import com.example.keres9.core.sh
import com.example.keres9.core.withAlpha
import com.example.keres9.sim.alive
import com.example.keres9.sim.camX
import com.example.keres9.sim.camY
import com.example.keres9.sim.scanOrg
import com.example.keres9.sim.sprite
import com.example.keres9.sim.storyAtOrPast
import com.example.keres9.sim.tele
import com.example.keres9.world.MOON
import com.example.keres9.world.MOON_STEP
import com.example.keres9.world.SITES
import com.example.keres9.world.altAt
import com.example.keres9.world.ca
import com.example.keres9.world.cw
import com.example.keres9.world.daylight
import com.example.keres9.world.ensureBodyBitmaps
import com.example.keres9.world.near
import com.example.keres9.world.portW
import com.example.keres9.world.sunAng
import com.example.keres9.world.surfR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class MapTransform(
    val sc: Double,
    val cx0: Double,
    val cy0: Double,
    val rot: Double,
    val width: Float,
    val height: Float
)

object GameRenderer {
    private const val EARTH_TH = C.TH0 - 0.32

    private var bw = 0
    private var bh = 0
    private var rs = 1f
    private var k = 1.0
    private var ox = 0.0
    private var oy = 0.0
    private var cr = 1.0
    private var sr = 0.0

    private var lightBmp: Bitmap? = null
    private var lightCanvas: Canvas? = null
    private var roofLayerBmp: Bitmap? = null
    private var roofLayerCanvas: Canvas? = null

    private val faceCache = HashMap<String, Bitmap>()
    private val entBodyCache = HashMap<Any, Pair<Double, Body?>>()

    private val pFill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val pStroke = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE }
    private val pAdd = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val pAddStroke = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val pBmp = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val pBmpSmooth = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }
    private val pLightCut = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }

    private val pathA = Path()
    private val pathB = Path()
    private val rectF = RectF()
    private val gaVec = Vec2()
    private var monoTypeface: Typeface = Typeface.MONOSPACE

    private val COL_SKY = parseHexColor("#03050a")
    private val COL_MOON_REGOLITH = parseHexColor("#8a847a")
    private val COL_MOON_BOULDER = parseHexColor("#9a9488")
    private val COL_LIFT_CABLE = Color.argb(140, 160, 190, 210)
    private val COL_LIFT_CAR_DARK = parseHexColor("#2b3a4d")
    private val COL_LIFT_CAR_LIGHT = parseHexColor("#41546b")
    private val COL_LIFT_MARKER_OFF = parseHexColor("#3a1a18")
    private val COL_ROCK_LABEL = parseHexColor("#c8d2dc")
    private val COL_CREW_VISOR = parseHexColor("#53d9ff")
    private val COL_SUIT_TORSO = parseHexColor("#c9d4e0")
    private val COL_SUIT_HELM = parseHexColor("#e8eef4")
    private val COL_SUIT_PACK = parseHexColor("#8b98a8")
    private val COL_PICK_CRATE = parseHexColor("#3a4656")
    private val COL_LEGS = parseHexColor("#9fb0c4")
    private val COL_NAV_OFF_G = parseHexColor("#1a3a22")
    private val COL_SUN_GLOW_INNER = Color.argb(140, 255, 248, 230)
    private val COL_SUN_GLOW_MID = Color.argb(64, 255, 240, 200)
    private val COL_EARTH_HALO = Color.argb(64, 120, 180, 255)
    private val COL_EARTH_NIGHT = Color.argb(209, 2, 4, 10)
    private val COL_VAPOR = Color.rgb(168, 236, 255)
    private val COL_JET_STERN = parseHexColor("#ffd9a0")
    private val COL_PLUME_INNER = Color.argb(230, 255, 220, 150)
    private val COL_PLUME_MID = Color.argb(115, 255, 140, 26)
    private val COL_PLUME_CORE = Color.argb(204, 255, 240, 210)
    private val COL_CHART_BG = parseHexColor("#05080f")
    private val COL_CHART_MOON = parseHexColor("#2a2722")
    private val COL_CHART_BAND = Color.argb(31, 83, 217, 255)
    private val COL_CHART_RING = Color.argb(64, 83, 217, 255)
    private val COL_CHART_ROCK_CHAM = parseHexColor("#7a3a6a")
    private val COL_CHART_ROCK_CAT = parseHexColor("#55606c")
    private val COL_CHART_ROCK_UNK = parseHexColor("#6b5a3f")
    private val COL_SPEC_BG = parseHexColor("#060b12")
    private val COL_SPEC_GRID = Color.argb(153, 43, 58, 77)
    private val COL_SPEC_LABEL = Color.argb(153, 125, 143, 163)
    private val COL_SPEC_NOISE = Color.argb(64, 83, 217, 255)
    private val COL_HUD_BOX = Color.argb(140, 4, 7, 12)

    fun setTypeface(tf: Typeface?) {
        if (tf != null) {
            monoTypeface = tf
            pText.typeface = tf
        }
    }

    fun clearCaches() {
        entBodyCache.clear()
    }

    private fun w2s(x: Double, y: Double): Vec2 {
        val dx = x - camX()
        val dy = y - camY()
        return Vec2(ox + (dx * cr + dy * sr) * k, oy + (-dx * sr + dy * cr) * k)
    }

    private fun ensureLightBuffer(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (lightBmp == null || lightBmp!!.width != w || lightBmp!!.height != h) {
            lightBmp?.recycle()
            lightBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            lightCanvas = Canvas(lightBmp!!)
        }
    }

    private fun ensureRoofBuffer(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (roofLayerBmp == null || roofLayerBmp!!.width != w || roofLayerBmp!!.height != h) {
            roofLayerBmp?.recycle()
            roofLayerBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            roofLayerCanvas = Canvas(roofLayerBmp!!)
        }
    }

    private fun inside(b: Body): Boolean {
        val f = GS.focus() ?: return false
        val c = ca(b, f.x, f.y) ?: return false
        return b.sol[c.i] == 0 && b.flr[c.i] > 0
    }

    private fun entBody(key: Any, x: Double, y: Double): Body? {
        val W = GS.W ?: return null
        val cached = entBodyCache[key]
        if (cached != null && W.time - cached.first < 0.5 && cached.first <= W.time) {
            return cached.second
        }
        var found: Body? = null
        for (b in near(x, y, 8.0)) {
            val c = ca(b, x, y)
            if (c != null && b.sol[c.i] == 0 && b.flr[c.i] > 0) {
                found = b
                break
            }
        }
        entBodyCache[key] = W.time to found
        return found
    }

    private fun drawGlowWorld(canvas: Canvas, x: Double, y: Double, col: Int, r: Double, alpha: Double) {
        val rf = r.toFloat()
        if (rf <= 0.1f) return
        val aInt = (cl(alpha, 0.0, 1.0) * 255.0).toInt()
        if (aInt <= 0) return
        pAdd.shader = RadialGradient(
            x.toFloat(), y.toFloat(), rf,
            withAlpha(col, alpha), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x.toFloat(), y.toFloat(), rf, pAdd)
        pAdd.shader = null
    }

    private fun drawSky(canvas: Canvas) {
        val W = GS.W ?: return
        val bg = W.bg ?: return
        val cam = GS.cam
        val f = GS.focus()
        val alt = if (f != null) altAt(f.x, f.y) else 1000.0
        pFill.color = COL_SKY
        canvas.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), pFill)

        canvas.save()
        canvas.translate(bw / 2f, bh / 2f)
        canvas.rotate(-Math.toDegrees(cam.rot).toFloat())
        val D = hypot(bw.toDouble(), bh.toDouble()) / 2.0 + 10.0
        val pars = doubleArrayOf(0.02, 0.05)
        for (L in 0 until 2) {
            val bmp = bg.stars.getOrNull(L) ?: continue
            val par = pars[L]
            val tw = 512.0 * rs * (1.0 + L * 0.4)
            var oxStar = -((camX() * par * rs) % tw)
            var oyStar = -((camY() * par * rs) % tw)
            if (oxStar > 0.0) oxStar -= tw
            if (oyStar > 0.0) oyStar -= tw
            pBmpSmooth.alpha = 216
            val startX = oxStar - ceil(D / tw) * tw
            val startY = oyStar - ceil(D / tw) * tw
            var x = startX
            while (x < D) {
                var y = startY
                while (y < D) {
                    rectF.set(x.toFloat(), y.toFloat(), (x + tw).toFloat(), (y + tw).toFloat())
                    canvas.drawBitmap(bmp, null, rectF, pBmpSmooth)
                    y += tw
                }
                x += tw
            }
        }
        pBmpSmooth.alpha = 255

        val sa = sunAng(W.time)
        val R0 = min(bw, bh) * 0.42f
        val sx = (cos(sa) * R0 * 2.2f).toFloat()
        val sy = (sin(sa) * R0 * 2.2f).toFloat()
        val srRad = R0 * 0.9f
        pFill.shader = RadialGradient(
            sx, sy, srRad,
            intArrayOf(COL_SUN_GLOW_INNER, COL_SUN_GLOW_MID, Color.TRANSPARENT),
            floatArrayOf(0f, 0.08f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(sx - R0, sy - R0, sx + R0, sy + R0, pFill)
        pFill.shader = null

        val ex = (cos(EARTH_TH) * R0 * 1.05 - camX() * 0.004 * rs).toFloat()
        val ey = (sin(EARTH_TH) * R0 * 1.05 - camY() * 0.004 * rs).toFloat()
        val er = 30f * rs * (if (alt > 600.0) 1.15f else 1f)
        canvas.save()
        canvas.translate(ex, ey)
        pFill.shader = RadialGradient(
            0f, 0f, er * 1.45f,
            intArrayOf(COL_EARTH_HALO, Color.TRANSPARENT),
            floatArrayOf(0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, er * 1.45f, pFill)
        pFill.shader = null

        canvas.save()
        canvas.rotate(Math.toDegrees(W.time * 0.004).toFloat())
        rectF.set(-er, -er, er, er)
        canvas.drawBitmap(bg.earth, null, rectF, pBmpSmooth)
        canvas.restore()

        val nx = (-cos(sa)).toFloat()
        val ny = (-sin(sa)).toFloat()
        pathA.reset()
        pathA.addCircle(0f, 0f, er + 0.5f, Path.Direction.CW)
        canvas.clipPath(pathA)
        pFill.color = COL_EARTH_NIGHT
        canvas.drawCircle(nx * er * 0.9f, ny * er * 0.9f, er * 1.25f, pFill)
        canvas.restore()
        canvas.restore()
    }

    private fun drawMoon(canvas: Canvas): Boolean {
        val W = GS.W ?: return false
        val cx = camX()
        val cy = camY()
        val rc = hy(cx, cy)
        val thc = atan2(cy, cx)
        val halfW = hypot(bw.toDouble(), bh.toDouble()) / 2.0 / k
        if (rc - halfW > C.RM + 80.0) return false
        val span = if (rc < C.RM + 50.0) PI else min(PI, asin(cl(halfW / max(rc, C.RM), 0.0, 1.0)) + halfW / C.RM)
        val step = max(MOON_STEP, min(0.01, 2.2 / k / C.RM))
        val n = min(2400, ceil((2.0 * span) / step).toInt())
        val th0 = thc - span
        val dth = (2.0 * span) / n
        val depth = cl(halfW * 0.5, 20.0, 220.0)
        val Ri = C.RM - 130.0 - depth

        var prevOx = 0f
        var prevOy = 0f
        var prevIx = 0f
        var prevIy = 0f
        var prevDay = 0.0
        pathB.reset()

        for (i in 0..n) {
            val th = th0 + i * dth
            val r = surfR(th)
            val cosT = cos(th)
            val sinT = sin(th)
            val oxp = (cosT * r).toFloat()
            val oyp = (sinT * r).toFloat()
            val ixp = (cosT * Ri).toFloat()
            val iyp = (sinT * Ri).toFloat()
            val day = daylight(th, W.time)
            if (i == 0) pathB.moveTo(oxp, oyp) else pathB.lineTo(oxp, oyp)
            if (i > 0) {
                val shade = 0.3 + 0.7 * prevDay
                pFill.color = sh(COL_MOON_REGOLITH, shade)
                pathA.reset()
                pathA.moveTo(prevOx, prevOy)
                pathA.lineTo(oxp, oyp)
                pathA.lineTo(ixp, iyp)
                pathA.lineTo(prevIx, prevIy)
                pathA.close()
                canvas.drawPath(pathA, pFill)
            }
            prevOx = oxp
            prevOy = oyp
            prevIx = ixp
            prevIy = iyp
            prevDay = day
        }

        pFill.color = sh(COL_MOON_REGOLITH, 0.22 + 0.4 * daylight(thc, W.time))
        canvas.drawCircle(0f, 0f, (Ri + 1.0).toFloat(), pFill)

        pStroke.color = Color.argb(89, 230, 224, 210)
        pStroke.strokeWidth = (1.2 / k).toFloat()
        canvas.drawPath(pathB, pStroke)

        if (k > 0.5 && MOON.boulders.isNotEmpty()) {
            val bList = MOON.boulders
            val totalB = bList.size
            var lo = 0
            var hi = totalB
            val a0 = ((th0 % TAU) + TAU) % TAU
            while (lo < hi) {
                val m = (lo + hi) ushr 1
                if (bList[m].th < a0) lo = m + 1 else hi = m
            }
            for (idx in 0 until totalB) {
                val bd = bList[(lo + idx) % totalB]
                var x = bd.th - a0
                if (x < 0.0) x += TAU
                if (x > 2.0 * span) break
                val r = surfR(bd.th)
                val ux = cos(bd.th)
                val uy = sin(bd.th)
                val tx = -uy
                val ty = ux
                val s = bd.s
                val d = daylight(bd.th, W.time)
                val bx = ux * (r + s * 0.3)
                val by = uy * (r + s * 0.3)
                pFill.color = sh(COL_MOON_BOULDER, 0.32 + d * 0.68)
                pathA.reset()
                pathA.moveTo((bx - tx * s).toFloat(), (by - ty * s).toFloat())
                pathA.lineTo((bx - tx * s * 0.5 + ux * s * 0.9).toFloat(), (by - ty * s * 0.5 + uy * s * 0.9).toFloat())
                pathA.lineTo((bx + tx * s * 0.6 + ux * s * 0.7).toFloat(), (by + ty * s * 0.6 + uy * s * 0.7).toFloat())
                pathA.lineTo((bx + tx * s).toFloat(), (by + ty * s).toFloat())
                pathA.close()
                canvas.drawPath(pathA, pFill)
            }
        }
        return true
    }

    private fun drawLift(canvas: Canvas) {
        val W = GS.W ?: return
        val L = W.lift ?: return
        val E = L.E ?: return
        pStroke.color = COL_LIFT_CABLE
        pStroke.strokeWidth = max(0.5, 1.2 / k).toFloat()
        val nxBase = -(E.t.y - E.g.y) / L.len
        val nyBase = (E.t.x - E.g.x) / L.len
        for (o in doubleArrayOf(-1.4, 1.4)) {
            val nx = (nxBase * o).toFloat()
            val ny = (nyBase * o).toFloat()
            canvas.drawLine(E.g.x.toFloat() + nx, E.g.y.toFloat() + ny, E.t.x.toFloat() + nx, E.t.y.toFloat() + ny, pStroke)
        }
        val bl = sin(W.time * 3.0) > 0.0
        pFill.color = if (bl) PAL.red else COL_LIFT_MARKER_OFF
        var s = 100.0
        while (s < L.len) {
            val kf = s / L.len
            val x = (E.g.x + (E.t.x - E.g.x) * kf).toFloat()
            val y = (E.g.y + (E.t.y - E.g.y) * kf).toFloat()
            canvas.drawRect(x - 0.9f, y - 0.9f, x + 0.9f, y + 0.9f, pFill)
            s += 100.0
        }
        val aDeg = Math.toDegrees(atan2(E.t.y - E.g.y, E.t.x - E.g.x)).toFloat()
        for (car in L.cars) {
            canvas.save()
            canvas.translate(car.x.toFloat(), car.y.toFloat())
            canvas.rotate(aDeg)
            pFill.color = COL_LIFT_CAR_LIGHT
            canvas.drawRect(-5f, -4f, 5f, 4f, pFill)
            pFill.color = COL_LIFT_CAR_DARK
            canvas.drawRect(-4f, -3f, 4f, 3f, pFill)
            pFill.color = if (car.rider) PAL.cyan else Color.argb(204, 255, 209, 102)
            canvas.drawRect(-1f, -3f, 1f, 3f, pFill)
            pFill.color = if (car.dir != 0) PAL.green else PAL.amber
            canvas.drawRect(4f, -0.8f, 5.2f, 0.8f, pFill)
            canvas.restore()
            if (car.dir != 0) drawGlowWorld(canvas, car.x, car.y, PAL.cyan, 12.0, 0.25)
        }
    }

    private fun drawStationProps(canvas: Canvas, b: Body) {
        val bwWorld = b.gw * b.cs
        val bhWorld = b.gh * b.cs
        val cs = b.cs.toFloat()
        for (rm in b.rooms) {
            val rx = (rm.x * b.cs - bwWorld / 2.0).toFloat()
            val ry = (rm.y * b.cs - bhWorld / 2.0).toFloat()
            val rw = rm.w * cs
            val rh = rm.h * cs
            val x1 = rx + cs * 1.4f
            val y1 = ry + cs * 1.4f
            val x2 = rx + rw - cs * 1.4f
            val y2 = ry + rh - cs * 1.4f
            val mx = (rx + rx + rw) / 2f
            val my = (ry + ry + rh) / 2f
            when (rm.kind) {
                "home" -> {
                    // Shared room bunk beds along top wall + lockers + terminal table
                    val cols = if (rm.w >= 14) 2 else 1
                    for (cIdx in 0 until cols) {
                        val bx = x1 + cIdx * cs * 6.0f
                        pFill.color = parseHexColor("#2f4156")
                        canvas.drawRect(bx, y1, bx + cs * 4.5f, y1 + cs * 2.1f, pFill)
                        pFill.color = parseHexColor("#89a4c7")
                        canvas.drawRect(bx + 0.6f, y1 + 0.5f, bx + cs * 1.4f, y1 + cs * 1.6f, pFill)
                        pFill.color = parseHexColor("#5d7591")
                        canvas.drawRect(bx + cs * 1.6f, y1 + 0.5f, bx + cs * 4.2f, y1 + cs * 1.7f, pFill)
                    }
                    pFill.color = parseHexColor("#6b5a3f")
                    canvas.drawRect(x2 - cs * 3.0f, my - cs * 1.0f, x2 - cs * 0.4f, my + cs * 1.0f, pFill)
                    pFill.color = PAL.amber
                    canvas.drawRect(x2 - cs * 2.4f, my - cs * 0.4f, x2 - cs * 1.2f, my + cs * 0.4f, pFill)
                }
                "intake" -> {
                    // Reception desk, biometric security arch, intake waiting bench
                    pFill.color = parseHexColor("#25364a")
                    canvas.drawRect(mx - cs * 3.5f, my - cs * 1.2f, mx + cs * 3.5f, my + cs * 0.8f, pFill)
                    pFill.color = PAL.cyan
                    canvas.drawRect(mx - cs * 2.2f, my - cs * 0.7f, mx - cs * 0.4f, my + cs * 0.2f, pFill)
                    pFill.color = PAL.gold
                    canvas.drawRect(mx + cs * 0.6f, my - cs * 0.6f, mx + cs * 2.5f, my + cs * 0.3f, pFill)
                    pStroke.color = PAL.cyan
                    pStroke.strokeWidth = 0.8f
                    canvas.drawRect(x1 + cs * 0.4f, y1 + cs * 0.4f, x1 + cs * 2.4f, y1 + cs * 3.2f, pStroke)
                    pFill.color = parseHexColor("#546a82")
                    canvas.drawRect(x2 - cs * 4.5f, y2 - cs * 1.8f, x2 - cs * 0.4f, y2 - cs * 0.4f, pFill)
                }
                "crew" -> {
                    // Briefing table & Liu's telemetry console
                    pFill.color = parseHexColor("#4c3f30")
                    canvas.drawRect(mx - cs * 3.2f, my - cs * 1.8f, mx + cs * 3.2f, my + cs * 1.8f, pFill)
                    pStroke.color = PAL.amber
                    pStroke.strokeWidth = 0.6f
                    canvas.drawRect(mx - cs * 2.8f, my - cs * 1.4f, mx + cs * 2.8f, my + cs * 1.4f, pStroke)
                    pFill.color = PAL.cyan
                    canvas.drawRect(x1 + cs * 0.4f, y1 + cs * 0.3f, x1 + cs * 4.2f, y1 + cs * 1.1f, pFill)
                }
                "bar" -> {
                    // Counter with amber light strip + lounge booths
                    pFill.color = parseHexColor("#3a281d")
                    canvas.drawRect(x1 + cs * 0.5f, y1 + cs * 0.6f, x2 - cs * 0.5f, y1 + cs * 2.2f, pFill)
                    pFill.color = PAL.amber
                    canvas.drawRect(x1 + cs * 0.6f, y1 + cs * 2.0f, x2 - cs * 0.6f, y1 + cs * 2.35f, pFill)
                    for (t in 0..1) {
                        val tx = mx + (t - 0.5f) * cs * 6.0f
                        pFill.color = parseHexColor("#4b392e")
                        canvas.drawCircle(tx, y2 - cs * 2.4f, cs * 1.1f, pFill)
                    }
                }
                "catalog" -> {
                    // Holo-chart table
                    pFill.color = parseHexColor("#1d2b3b")
                    canvas.drawCircle(mx, my, cs * 2.4f, pFill)
                    pStroke.color = PAL.cyan
                    pStroke.strokeWidth = 0.7f
                    canvas.drawCircle(mx, my, cs * 1.9f, pStroke)
                    canvas.drawCircle(mx, my, cs * 1.0f, pStroke)
                }
                "market", "cargo" -> {
                    // Crates & exchange ticker console
                    pFill.color = parseHexColor("#785e36")
                    canvas.drawRect(x1 + cs * 0.4f, y1 + cs * 0.4f, x1 + cs * 2.6f, y1 + cs * 2.2f, pFill)
                    pFill.color = parseHexColor("#4b6278")
                    canvas.drawRect(x1 + cs * 3.0f, y1 + cs * 0.6f, x1 + cs * 5.0f, y1 + cs * 2.4f, pFill)
                }
                "workshop", "yard" -> {
                    // Overhead gantry rails
                    pStroke.color = parseHexColor("#7f8c9a")
                    pStroke.strokeWidth = 0.9f
                    canvas.drawLine(x1, my - cs * 1.8f, x2, my - cs * 1.8f, pStroke)
                    canvas.drawLine(x1, my + cs * 1.8f, x2, my + cs * 1.8f, pStroke)
                }
                "lift" -> {
                    // Cable shaft collar
                    pStroke.color = PAL.cyan
                    pStroke.strokeWidth = 0.8f
                    canvas.drawCircle(mx, my, cs * 2.5f, pStroke)
                }
            }
        }
    }

    private fun drawFloor(canvas: Canvas, b: Body, revealNear: Boolean) {
        val W = GS.W ?: return
        if (b.kind == "station" && b.roofA >= 0.995 && !revealNear) return
        ensureBodyBitmaps(b)
        val w = (b.gw * b.cs).toFloat()
        val h = (b.gh * b.cs).toFloat()
        canvas.save()
        canvas.translate(b.x.toFloat(), b.y.toFloat())
        canvas.rotate(Math.toDegrees(b.a).toFloat())
        rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        b.floorBmp?.let { canvas.drawBitmap(it, null, rectF, pBmp) }
        if (b.kind == "station" && (b.roofA < 0.72 || revealNear)) {
            drawStationProps(canvas, b)
        }
        val showSealedGlow = b.roofA < 0.68 || revealNear
        for (g in b.glow) {
            if (!g.leak && !showSealedGlow) continue
            val a = if (b.roofA < 0.5 || revealNear) 0.9 else if (g.leak) 0.35 else 0.0
            if (a > 0.0) drawGlowWorld(canvas, g.lx, g.ly, g.c, g.r, a)
        }
        if (b.roofA < 0.55 || revealNear) {
            for (cr in b.crew) {
                val bob = (sin(W.time * 1.3 + cr.lx) * 0.3).toFloat()
                pFill.color = cr.c
                canvas.drawRect(cr.lx.toFloat() - 1.2f, cr.ly.toFloat() - 1.2f + bob, cr.lx.toFloat() + 1.2f, cr.ly.toFloat() + 1.2f + bob, pFill)
                pFill.color = COL_CREW_VISOR
                canvas.drawRect(cr.lx.toFloat() - 0.4f, cr.ly.toFloat() - 1.6f + bob, cr.lx.toFloat() + 0.6f, cr.ly.toFloat() - 0.8f + bob, pFill)
            }
        }
        canvas.restore()
    }

    private fun drawStationExterior(canvas: Canvas, b: Body, w: Float, h: Float, extA: Double) {
        val W = GS.W
        val hw = w / 2f
        val hh = h / 2f
        val lineCol = when {
            b.surf -> PAL.green
            b.fac == "transit" -> PAL.ice
            else -> PAL.cyan
        }
        val strobe = sin((W?.time ?: 0.0) * 3.5 + b.seed) > 0.35

        when (b.type) {
            "selene" -> {
                // Consortium deep-blue photovoltaic solar wings on north & south gimbal booms
                for (signY in floatArrayOf(-1f, 1f)) {
                    val wingY = if (signY < 0f) -hh - 26f else hh + 6f
                    val boomY = if (signY < 0f) -hh + 6f else hh - 6f
                    for (sx in floatArrayOf(-hw * 0.68f, hw * 0.20f)) {
                        pStroke.color = withAlpha(parseHexColor("#9fb4c8"), extA * 0.75)
                        pStroke.strokeWidth = 2.0f
                        canvas.drawLine(sx + 28f, boomY, sx + 28f, wingY + 10f, pStroke)

                        pFill.color = withAlpha(parseHexColor("#142a44"), extA * 0.96)
                        canvas.drawRect(sx, wingY, sx + 58f, wingY + 20f, pFill)

                        pStroke.color = withAlpha(PAL.cyan, extA * 0.42)
                        pStroke.strokeWidth = 0.75f
                        for (g in 1..3) {
                            val gx = sx + g * 14.5f
                            canvas.drawLine(gx, wingY + 1f, gx, wingY + 19f, pStroke)
                        }
                        pStroke.color = withAlpha(PAL.gold, extA * 0.65)
                        pStroke.strokeWidth = 1.1f
                        canvas.drawLine(sx + 1f, wingY + 10f, sx + 57f, wingY + 10f, pStroke)

                        pStroke.color = withAlpha(parseHexColor("#9eb4ca"), extA * 0.7)
                        pStroke.strokeWidth = 0.9f
                        canvas.drawRect(sx, wingY, sx + 58f, wingY + 20f, pStroke)
                    }
                }
                // Hull rim highlight & central spine truss rails
                pStroke.color = withAlpha(parseHexColor("#9db4cc"), extA * 0.45)
                pStroke.strokeWidth = 1.1f
                canvas.drawRect(-hw + 8f, -hh + 8f, hw - 8f, hh - 8f, pStroke)

                pStroke.color = withAlpha(PAL.amber, extA * 0.55)
                pStroke.strokeWidth = 1.0f
                canvas.drawLine(-hw + 12f, -4f, hw - 12f, -4f, pStroke)
                canvas.drawLine(-hw + 12f, 4f, hw - 12f, 4f, pStroke)

                // Twin sensor cupola domes & sweeping radar arms (centered on hull bays, not rooms)
                val sweep = ((W?.time ?: 0.0) * 1.4).toFloat()
                for (cyCup in floatArrayOf(-44f, 48f)) {
                    pFill.color = withAlpha(parseHexColor("#3a4e66"), extA * 0.95)
                    canvas.drawCircle(0f, cyCup, 14f, pFill)
                    pStroke.color = withAlpha(PAL.cyan, extA * 0.72)
                    pStroke.strokeWidth = 1.0f
                    canvas.drawCircle(0f, cyCup, 14f, pStroke)
                    canvas.drawCircle(0f, cyCup, 7f, pStroke)
                    canvas.drawLine(0f, cyCup, cos(sweep) * 12f, cyCup + sin(sweep) * 12f, pStroke)
                }
                // Docking collar brackets at west & east airlock mouths
                pStroke.color = withAlpha(PAL.cyan, extA * 0.8)
                pStroke.strokeWidth = 1.5f
                canvas.drawLine(-hw + 8f, -10f, -hw + 8f, 10f, pStroke)
                canvas.drawLine(hw - 8f, -10f, hw - 8f, 10f, pStroke)
                if (strobe) {
                    pFill.color = withAlpha(PAL.red, extA * 0.9)
                    canvas.drawCircle(-hw + 10f, -hh + 10f, 2f, pFill)
                    canvas.drawCircle(hw - 10f, -hh + 10f, 2f, pFill)
                    pFill.color = withAlpha(PAL.green, extA * 0.9)
                    canvas.drawCircle(-hw + 10f, hh - 10f, 2f, pFill)
                    canvas.drawCircle(hw - 10f, hh - 10f, 2f, pFill)
                }
            }
            "liner" -> {
                // Sleek PSV MERIDIAN passenger liner carapace outline & chamfered nose
                pStroke.color = withAlpha(parseHexColor("#c2d2e2"), extA * 0.75)
                pStroke.strokeWidth = 1.2f
                pathA.reset()
                pathA.moveTo(-hw + 28f, -hh + 12f)
                pathA.lineTo(hw - 36f, -hh + 12f)
                pathA.lineTo(hw - 20f, -hh + 20f)
                pathA.lineTo(hw - 20f, hh - 20f)
                pathA.lineTo(hw - 36f, hh - 12f)
                pathA.lineTo(-hw + 28f, hh - 12f)
                pathA.close()
                canvas.drawPath(pathA, pStroke)

                // Twin glowing thermal radiator fins north & south
                for (signY in floatArrayOf(-1f, 1f)) {
                    val fy0 = if (signY < 0f) -hh - 17f else hh - 4f
                    val fy1 = if (signY < 0f) -hh + 4f else hh + 17f
                    pFill.color = withAlpha(parseHexColor("#1a2b3e"), extA * 0.96)
                    canvas.drawRect(-hw * 0.34f, fy0, hw * 0.28f, fy1, pFill)
                    pStroke.color = withAlpha(PAL.cyan, extA * 0.55)
                    pStroke.strokeWidth = 0.8f
                    for (gx in -32..24 step 14) {
                        canvas.drawLine(gx.toFloat(), fy0 + 2f, gx.toFloat(), fy1 - 2f, pStroke)
                    }
                    pStroke.color = withAlpha(parseHexColor("#8fa8c2"), extA * 0.75)
                    pStroke.strokeWidth = 1.0f
                    canvas.drawRect(-hw * 0.34f, fy0, hw * 0.28f, fy1, pStroke)
                }

                // Consortium crest ring + forward observation bridge canopy blister
                pStroke.color = withAlpha(PAL.gold, extA * 0.75)
                pStroke.strokeWidth = 1.1f
                canvas.drawCircle(-8f, 0f, 8.5f, pStroke)
                pFill.color = withAlpha(parseHexColor("#174666"), extA * 0.95)
                canvas.drawCircle(hw - 34f, 0f, 6.5f, pFill)
                pStroke.color = withAlpha(PAL.cyan, extA * 0.85)
                canvas.drawCircle(hw - 34f, 0f, 6.5f, pStroke)

                // Twin stern fusion engine bells + idle ion glow
                for (ey in floatArrayOf(-12f, 12f)) {
                    pFill.color = withAlpha(parseHexColor("#3b4a5c"), extA * 0.95)
                    canvas.drawRect(-hw - 3f, ey - 4.5f, -hw + 2f, ey + 4.5f, pFill)
                    drawGlowWorld(canvas, -hw - 3.0, ey.toDouble(), PAL.cyan, 11.0, extA * 0.35)
                }

                // East airlock boarding gantry alignment stripes & wingtip strobes
                pStroke.color = withAlpha(PAL.cyan, extA * 0.85)
                pStroke.strokeWidth = 1.2f
                canvas.drawLine(hw - 18f, -11f, hw, -11f, pStroke)
                canvas.drawLine(hw - 18f, 11f, hw, 11f, pStroke)
                if (strobe) {
                    pFill.color = withAlpha(PAL.red, extA * 0.9)
                    canvas.drawCircle(-hw * 0.04f, -hh - 16f, 1.8f, pFill)
                    pFill.color = withAlpha(PAL.green, extA * 0.9)
                    canvas.drawCircle(-hw * 0.04f, hh + 16f, 1.8f, pFill)
                }
            }
            "vulcan" -> {
                // Spherical cryo propellant tank collar rings & smelter heat-exchanger glow
                for (cyTank in floatArrayOf(-52f, 52f)) {
                    pStroke.color = withAlpha(parseHexColor("#b4c6d8"), extA * 0.8)
                    pStroke.strokeWidth = 1.3f
                    canvas.drawCircle(124f, cyTank, 23f, pStroke)
                    pStroke.color = withAlpha(PAL.amber, extA * 0.55)
                    canvas.drawLine(82f, cyTank, 101f, cyTank, pStroke)
                }
                pStroke.color = withAlpha(PAL.ember, extA * 0.65)
                pStroke.strokeWidth = 1.1f
                canvas.drawRect(-100f, -38f, -28f, 38f, pStroke)
                drawGlowWorld(canvas, 32.0, 0.0, PAL.ember, 28.0, extA * 0.25)
            }
            "mars", "earth" -> {
                // Interplanetary cycler dorsal pipe spine, hab ring & comms mast
                val tint = if (b.type == "mars") PAL.ember else PAL.cyan
                pStroke.color = withAlpha(parseHexColor("#a5b8cc"), extA * 0.7)
                pStroke.strokeWidth = 1.2f
                canvas.drawLine(-hw + 32f, -6f, hw - 52f, -6f, pStroke)
                canvas.drawLine(-hw + 32f, 6f, hw - 52f, 6f, pStroke)
                pStroke.color = withAlpha(tint, extA * 0.75)
                canvas.drawCircle(hw - 36f, 0f, 18f, pStroke)
                drawGlowWorld(canvas, -hw + 4.0, 0.0, tint, 18.0, extA * 0.28)
            }
            "hab" -> {
                // Sintered lunar dome ribs + exterior warm skylight hood + whip comms antenna
                pStroke.color = withAlpha(parseHexColor("#9c9386"), extA * 0.75)
                pStroke.strokeWidth = 1.2f
                canvas.drawRect(-hw + 3f, -hh + 3f, hw - 3f, hh - 3f, pStroke)
                for (rx in floatArrayOf(-hw * 0.55f, 0f, hw * 0.55f)) {
                    canvas.drawLine(rx, -hh + 3f, rx, hh - 3f, pStroke)
                }
                pFill.color = withAlpha(PAL.amber, extA * 0.42)
                canvas.drawRect(-hw * 0.48f, -hh + 6f, hw * 0.48f, -hh + 11f, pFill)
                pStroke.color = withAlpha(PAL.dim, extA * 0.8)
                pStroke.strokeWidth = 0.9f
                canvas.drawLine(hw - 6f, -hh + 3f, hw - 6f, -hh - 8f, pStroke)
                if (strobe) {
                    pFill.color = withAlpha(PAL.red, extA * 0.95)
                    canvas.drawCircle(hw - 6f, -hh - 8f, 1.5f, pFill)
                }
            }
            "lift" -> {
                // Cable anchor pylons & illuminated elevator ring
                pStroke.color = withAlpha(PAL.cyan, extA * 0.75)
                pStroke.strokeWidth = 1.2f
                canvas.drawCircle(0f, 15f, 10f, pStroke)
                pStroke.color = withAlpha(parseHexColor("#9fb4c8"), extA * 0.75)
                canvas.drawLine(-4.5f, -hh, -4.5f, -2f, pStroke)
                canvas.drawLine(4.5f, -hh, 4.5f, -2f, pStroke)
            }
            "depot", "tycho" -> {
                // Ribbed lunar hangar frame + surface landing pad floodlight brackets
                pStroke.color = withAlpha(lineCol, extA * 0.65)
                pStroke.strokeWidth = 1.1f
                canvas.drawRect(-hw + 3f, -hh + 3f, -6f, hh - 3f, pStroke)
                var rxRib = -hw + 12f
                while (rxRib <= -14f) {
                    canvas.drawLine(rxRib, -hh + 3f, rxRib, hh - 3f, pStroke)
                    rxRib += 14f
                }
                pStroke.color = withAlpha(PAL.gold, extA * 0.7)
                canvas.drawLine(6f, hh - 4f, hw - 4f, hh - 4f, pStroke)
            }
            else -> {
                pFill.color = withAlpha(parseHexColor("#223042"), extA * 0.85)
                canvas.drawRect(-hw * 0.35f, -hh - 14f, hw * 0.35f, -hh, pFill)
                canvas.drawRect(-hw * 0.35f, hh, hw * 0.35f, hh + 14f, pFill)
                pStroke.color = withAlpha(lineCol, extA * 0.55)
                pStroke.strokeWidth = 0.9f
                canvas.drawRect(-hw * 0.35f, -hh - 14f, hw * 0.35f, -hh, pStroke)
                canvas.drawRect(-hw * 0.35f, hh, hw * 0.35f, hh + 14f, pStroke)
            }
        }
    }

    private fun drawRoof(canvas: Canvas, b: Body, includeBeacons: Boolean = true) {
        val W = GS.W ?: return
        ensureBodyBitmaps(b)
        val w = (b.gw * b.cs).toFloat()
        val h = (b.gh * b.cs).toFloat()
        canvas.save()
        canvas.translate(b.x.toFloat(), b.y.toFloat())
        canvas.rotate(Math.toDegrees(b.a).toFloat())
        pBmp.alpha = (cl(b.roofA, 0.0, 1.0) * 255.0).toInt()
        rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        b.roofBmp?.let { canvas.drawBitmap(it, null, rectF, pBmp) }
        pBmp.alpha = 255
        if (b.kind == "station" && b.roofA > 0.25) {
            drawStationExterior(canvas, b, w, h, cl((b.roofA - 0.25) / 0.75, 0.0, 1.0))
        }
        if (b.veinOn) {
            val alpha = ((0.45 + 0.35 * sin(W.time * 3.0)) * 255.0).toInt()
            pFill.color = Color.argb(alpha, 255, 209, 102)
            for (i in b.veins) {
                if (b.sol[i] != 0) {
                    val rx = ((i % b.gw) * b.cs - w / 2.0).toFloat()
                    val ry = ((i / b.gw) * b.cs - h / 2.0).toFloat()
                    canvas.drawRect(rx, ry, rx + b.cs.toFloat(), ry + b.cs.toFloat(), pFill)
                }
            }
        }
        for (f in b.fog) {
            for (idx in 0 until 3) {
                val t = W.time * 0.16 + f.ph + idx * 2.1
                val fx = (f.lx + cos(t) * f.r * 0.22).toFloat()
                val fy = (f.ly + sin(t * 0.8) * f.r * 0.16).toFloat()
                val r = (f.r * (0.6 + idx * 0.18)).toFloat()
                val a = 0.07 - idx * 0.015
                pAdd.shader = RadialGradient(fx, fy, r, withAlpha(COL_VAPOR, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawCircle(fx, fy, r, pAdd)
                pAdd.shader = null
            }
            if (f.jet != null) {
                val ja = f.jet
                val L = 40.0 + 10.0 * sin(W.time * 2.0 + f.ph)
                val jx = (f.lx + cos(ja) * L).toFloat()
                val jy = (f.ly + sin(ja) * L).toFloat()
                val fx = f.lx.toFloat()
                val fy = f.ly.toFloat()
                pAddStroke.strokeWidth = (f.r * 0.6).toFloat()
                pAddStroke.shader = LinearGradient(fx, fy, jx, jy, Color.argb(56, 200, 240, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawLine(fx, fy, jx, jy, pAddStroke)
                pAddStroke.shader = null
            }
        }
        canvas.restore()

        if (includeBeacons) {
            drawRoofBeacons(canvas, b, w)
        }
    }

    private fun drawRoofBeacons(canvas: Canvas, b: Body, w: Float = (b.gw * b.cs).toFloat()) {
        val W = GS.W ?: return
        if (b.roofA > 0.4 && b.kind == "station") {
            val bl = sin(W.time * 4.0) > 0.0
            val alpha = if (bl) 242.0 / 255.0 else 89.0 / 255.0
            for (p in b.ports) {
                val w2 = portW(b, p)
                val col = if (p.occ != null) PAL.red else if (p.berth) PAL.gold else PAL.green
                pFill.color = withAlpha(col, alpha)
                canvas.drawRect(w2.x.toFloat() - 1.4f, w2.y.toFloat() - 1.4f, w2.x.toFloat() + 1.4f, w2.y.toFloat() + 1.4f, pFill)
            }
            val padAlpha = if (bl) 230.0 / 255.0 else 102.0 / 255.0
            for (p in b.pads) {
                val w2 = portW(b, p)
                val col = if (p.occ != null) PAL.red else PAL.gold
                pFill.color = withAlpha(col, padAlpha)
                canvas.drawRect(w2.x.toFloat() - 1.6f, w2.y.toFloat() - 1.6f, w2.x.toFloat() + 1.6f, w2.y.toFloat() + 1.6f, pFill)
            }
        }
        if (b.transfer != null && !b.gone && b.leaving) {
            val ex = b.x - cos(b.a) * (w / 2.0 + 4.0)
            val ey = b.y - sin(b.a) * (w / 2.0 + 4.0)
            drawGlowWorld(canvas, ex, ey, COL_JET_STERN, 60.0, 0.8)
        }
    }

    private fun drawShip(canvas: Canvas, s: Ship) {
        val W = GS.W ?: return
        if (s.dead) return
        if (s.dirty || s.spr == null) sprite(s)
        canvas.save()
        canvas.translate(s.x.toFloat(), s.y.toFloat())
        canvas.rotate(Math.toDegrees(s.a).toFloat())
        val w = (s.gw * C.SS).toFloat()
        val h = (s.gh * C.SS).toFloat()
        if (s.thr > 0.0 && s.fuel > 0.0 && alive(s, "eng").isNotEmpty()) {
            val fl = ((if (s.tailLand) 22.0 else 16.0) * s.thr + 6.0 + Random.nextDouble() * 3.0).toFloat()
            pAdd.shader = RadialGradient(
                -w / 2f - 4f, 0f, fl,
                intArrayOf(COL_PLUME_INNER, COL_PLUME_MID, Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(-w / 2f - 4f, 0f, fl, pAdd)
            pAdd.shader = null
            pAdd.color = COL_PLUME_CORE
            pathA.reset()
            pathA.moveTo(-w / 2f, -1.8f)
            pathA.lineTo(-w / 2f - 6f - (s.thr * 18.0 * (0.8 + Random.nextDouble() * 0.4)).toFloat(), 0f)
            pathA.lineTo(-w / 2f, 1.8f)
            pathA.close()
            canvas.drawPath(pathA, pAdd)
        }
        rectF.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        s.spr?.let { canvas.drawBitmap(it, null, rectF, pBmpSmooth) }
        if (s.gear && s.tailLand) {
            pStroke.color = if (s.gearOK) COL_LEGS else PAL.red
            pStroke.strokeWidth = 1.1f
            canvas.drawLine(-w / 2f + 3f, -h / 2f + 1f, -w / 2f - 2.5f, -h / 2f - 3.5f, pStroke)
            canvas.drawLine(-w / 2f + 3f, h / 2f - 1f, -w / 2f - 2.5f, h / 2f + 3.5f, pStroke)
            pFill.color = COL_LEGS
            canvas.drawRect(-w / 2f - 3.2f, -h / 2f - 4.5f, -w / 2f - 1.8f, -h / 2f - 2f, pFill)
            canvas.drawRect(-w / 2f - 3.2f, h / 2f + 2f, -w / 2f - 1.8f, h / 2f + 4.5f, pFill)
        } else if (s.gear) {
            pStroke.color = PAL.green
            pStroke.strokeWidth = 1.1f
            canvas.drawLine(-3f, h / 2f, -5f, h / 2f + 3.5f, pStroke)
            canvas.drawLine(3f, h / 2f, 5f, h / 2f + 3.5f, pStroke)
        }
        if (s.hitT > 0.0) {
            pFill.color = Color.argb((min(0.5, s.hitT) * 255.0).toInt(), 255, 240, 210)
            canvas.drawRect(-w / 2f, -h / 2f, w / 2f, h / 2f, pFill)
        }
        val nv = sin(W.time * 5.0 + s.x * 0.01) > 0.0
        pFill.color = if (nv) PAL.red else COL_LIFT_MARKER_OFF
        canvas.drawRect(-w / 2f, -h / 2f, -w / 2f + 1.4f, -h / 2f + 1.4f, pFill)
        pFill.color = if (nv) PAL.green else COL_NAV_OFF_G
        canvas.drawRect(-w / 2f, h / 2f - 1.4f, -w / 2f + 1.4f, h / 2f, pFill)
        canvas.restore()

        if (s.own && !s.isPl) {
            pStroke.color = withAlpha(PAL.cyan, 0.5)
            pStroke.strokeWidth = (1.0 / k).toFloat()
            canvas.drawCircle(s.x.toFloat(), s.y.toFloat(), (s.rad + 3.0).toFloat(), pStroke)
        }
    }

    private fun drawPick(canvas: Canvas, p: Pick) {
        val W = GS.W ?: return
        val g = GD[p.type]
        canvas.save()
        canvas.translate(p.x.toFloat(), p.y.toFloat())
        canvas.rotate(Math.toDegrees(p.a).toFloat())
        if (p.type == "core") {
            drawGlowWorld(canvas, 0.0, 0.0, PAL.cyan, 9.0, 0.5)
            pFill.color = PAL.cyan
            pathA.reset()
            pathA.moveTo(0f, -3.4f)
            pathA.lineTo(3f, 0f)
            pathA.lineTo(0f, 3.4f)
            pathA.lineTo(-3f, 0f)
            pathA.close()
            canvas.drawPath(pathA, pFill)
        } else if (p.type == "parts" || p.type == "food") {
            pFill.color = COL_PICK_CRATE
            canvas.drawRect(-2.6f, -2.6f, 2.6f, 2.6f, pFill)
            if (g != null) {
                pFill.color = g.c
                canvas.drawRect(-2.6f, -0.6f, 2.6f, 0.6f, pFill)
                canvas.drawRect(-0.6f, -2.6f, 0.6f, 2.6f, pFill)
            }
        } else if (g != null) {
            pFill.color = g.c
            canvas.drawRect(-2.2f, -1.6f, 2.2f, 1.6f, pFill)
            canvas.drawRect(-1.4f, -2.4f, 1.4f, 2.4f, pFill)
            pFill.color = sh(g.c, 1.4)
            canvas.drawRect(-1.4f, -2.4f, 0f, -1.2f, pFill)
        }
        canvas.restore()
        if (p.rev > W.time) {
            val a = ((0.5 + 0.5 * sin(W.time * 7.0)) * 255.0).toInt()
            pStroke.color = Color.argb(a, 255, 209, 102)
            pStroke.strokeWidth = (1.2 / k).toFloat()
            canvas.drawRect(p.x.toFloat() - 5f, p.y.toFloat() - 5f, p.x.toFloat() + 5f, p.y.toFloat() + 5f, pStroke)
        }
    }

    private fun suitFig(
        canvas: Canvas,
        x: Double,
        y: Double,
        a: Double,
        col: Int,
        walk: Double,
        grounded: Boolean,
        light: Boolean,
        beam: com.example.keres9.core.BeamInfo?
    ) {
        val up = atan2(y, x)
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        if (grounded) {
            canvas.rotate(Math.toDegrees(up + PI / 2.0).toFloat())
            val ph = (sin(walk * 9.0) * 0.6).toFloat()
            val face = if (cos(a - (up + PI / 2.0)) >= 0.0) 1f else -1f
            pFill.color = COL_SUIT_TORSO
            canvas.drawRect(-1.3f, -2.8f, 1.3f, 0.4f, pFill)
            pFill.color = COL_SUIT_HELM
            canvas.drawRect(-1.1f, -4.4f, 1.1f, -2.6f, pFill)
            pFill.color = PAL.cyan
            val vx = if (face > 0f) 0f else -1f
            canvas.drawRect(vx, -4.1f, vx + 1f, -3.1f, pFill)
            if (light) {
                pFill.color = COL_JET_STERN
                canvas.drawRect(vx + 0.2f, -4.7f, vx + 0.9f, -4.2f, pFill)
            }
            pFill.color = COL_SUIT_PACK
            val px = -face * 1.3f - (if (face > 0f) 1f else 0f)
            canvas.drawRect(px, -2.6f, px + 1f, 0f, pFill)
            pFill.color = col
            canvas.drawRect(-1.1f + ph, 0.4f, -0.2f + ph, 2.6f, pFill)
            canvas.drawRect(0.2f - ph, 0.4f, 1.1f - ph, 2.6f, pFill)
        } else {
            canvas.rotate(Math.toDegrees(a).toFloat())
            pFill.color = COL_SUIT_TORSO
            canvas.drawRect(-1.8f, -1.6f, 1.8f, 1.6f, pFill)
            pFill.color = COL_SUIT_PACK
            canvas.drawRect(-2.6f, -1.2f, -1.6f, 1.2f, pFill)
            pFill.color = PAL.cyan
            canvas.drawRect(0.6f, -1.0f, 2.0f, 0.6f, pFill)
            if (light) {
                pFill.color = COL_JET_STERN
                canvas.drawRect(1.7f, -0.6f, 2.4f, 0.6f, pFill)
            }
            pFill.color = col
            canvas.drawRect(-1.2f, 1.4f, -0.2f, 2.8f, pFill)
            canvas.drawRect(0.4f, 1.4f, 1.4f, 2.8f, pFill)
        }
        canvas.restore()
        if (beam != null) {
            val f = 0.7 + Random.nextDouble() * 0.3
            pStroke.color = if (beam.miss) Color.argb(64, 255, 180, 90) else Color.argb((0.85 * f * 255.0).toInt(), 255, 200, 120)
            pStroke.strokeWidth = 0.8f
            canvas.drawLine(beam.x0.toFloat(), beam.y0.toFloat(), beam.x1.toFloat(), beam.y1.toFloat(), pStroke)
            if (!beam.miss) drawGlowWorld(canvas, beam.x1, beam.y1, PAL.amber, 5.0, 0.7)
        }
    }

    private fun drawEVA(canvas: Canvas, e: SuitState) {
        if (e.dead || e.hide) return
        suitFig(canvas, e.x, e.y, e.a, PAL.amber, e.bob, e.grounded > 0.0 && tele(e).g > 0.4, e.lightOn, e.beam)
        pStroke.color = if (e.suit > 30.0) PAL.cyan else PAL.red
        pStroke.strokeWidth = (1.2 / k).toFloat()
        rectF.set(e.x.toFloat() - 6f, e.y.toFloat() - 6f, e.x.toFloat() + 6f, e.y.toFloat() + 6f)
        canvas.drawArc(rectF, -90f, (360.0 * (e.suit / 100.0)).toFloat(), false, pStroke)
        if (e.pingT < 1.0) {
            pStroke.color = Color.argb(((1.0 - e.pingT) * 255.0).toInt(), 83, 217, 255)
            pStroke.strokeWidth = (2.0 / k).toFloat()
            canvas.drawCircle(e.x.toFloat(), e.y.toFloat(), (e.pingT * C.EVA.ping).toFloat(), pStroke)
        }
        if (e.jet > 0.0) drawGlowWorld(canvas, e.x, e.y, PAL.ice, 6.0, 0.4)
    }

    private fun drawMates(canvas: Canvas) {
        val W = GS.W ?: return
        for (m in W.suits) {
            if (m.st == "in") continue
            suitFig(canvas, m.x, m.y, m.a, m.c, W.time, false, true, m.beam)
        }
    }

    private fun drawFx(canvas: Canvas) {
        val W = GS.W ?: return
        for (s in W.ships) {
            val b = s.beam
            if (b == null || s.dead) continue
            val f = 0.7 + Random.nextDouble() * 0.3
            pAddStroke.color = Color.argb((0.35 * f * 255.0).toInt(), 83, 217, 255)
            pAddStroke.strokeWidth = 4f
            canvas.drawLine(b.x0.toFloat(), b.y0.toFloat(), b.x1.toFloat(), b.y1.toFloat(), pAddStroke)
            pAddStroke.color = Color.argb((0.9 * f * 255.0).toInt(), 220, 250, 255)
            pAddStroke.strokeWidth = 1.2f
            canvas.drawLine(b.x0.toFloat(), b.y0.toFloat(), b.x1.toFloat(), b.y1.toFloat(), pAddStroke)
            drawGlowWorld(canvas, b.x1, b.y1, PAL.cyan, 8.0, 0.7)
        }
        for (m in W.meteors) {
            val L = 0.08
            val sz = (1.0 + m.r).toFloat()
            pAddStroke.color = Color.argb(89, 255, 180, 90)
            pAddStroke.strokeWidth = sz * 2.4f
            canvas.drawLine((m.x - m.vx * L).toFloat(), (m.y - m.vy * L).toFloat(), m.x.toFloat(), m.y.toFloat(), pAddStroke)
            pAddStroke.color = Color.argb(242, 255, 240, 210)
            pAddStroke.strokeWidth = sz
            canvas.drawLine((m.x - m.vx * L * 0.4).toFloat(), (m.y - m.vy * L * 0.4).toFloat(), m.x.toFloat(), m.y.toFloat(), pAddStroke)
        }
        for (p in W.bits) {
            val t = cl(p.life / p.max, 0.0, 1.0)
            val aInt = (t * 255.0).toInt()
            when (p.t) {
                "ring" -> {
                    pAddStroke.color = withAlpha(p.c, t * 0.8)
                    pAddStroke.strokeWidth = 2.5f
                    canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), (p.s * (1.0 - t) * 6.0 + 2.0).toFloat(), pAddStroke)
                }
                "flash" -> drawGlowWorld(canvas, p.x, p.y, p.c, p.s * (1.4 - t), t)
                "shock" -> {
                    pStroke.color = Color.argb((t * 0.5 * 255.0).toInt(), 255, 230, 190)
                    pStroke.strokeWidth = (1.0 + t * 3.0).toFloat()
                    canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), (p.s * (1.0 - t) * 9.0 + 3.0).toFloat(), pStroke)
                }
                "fire" -> {
                    val r = (p.s * (0.6 + (1.0 - t) * 0.8)).toFloat()
                    pAdd.shader = RadialGradient(
                        p.x.toFloat(), p.y.toFloat(), max(0.1f, r),
                        intArrayOf(
                            Color.argb(aInt, 255, 240, 200),
                            Color.argb((t * 0.7 * 255.0).toInt(), 255, 140, 26),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, pAdd)
                    pAdd.shader = null
                }
                "gas", "smoke" -> {
                    pFill.color = withAlpha(p.c, t * (if (p.t == "smoke") 0.35 else 0.22))
                    val r = (p.s * (if (p.t == "smoke") 2.0 - t else 1.0)).toFloat()
                    canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, pFill)
                }
                "dust" -> {
                    pFill.color = withAlpha(p.c, min(1.0, t * 1.4) * 0.8)
                    val half = (p.s / 2.0).toFloat()
                    canvas.drawRect(p.x.toFloat() - half, p.y.toFloat() - half, p.x.toFloat() + half, p.y.toFloat() + half, pFill)
                }
                "thrust" -> {
                    pAdd.color = withAlpha(p.c, t)
                    canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), (p.s * t).toFloat(), pAdd)
                }
                else -> {
                    pAddStroke.color = withAlpha(p.c, t)
                    pAddStroke.strokeWidth = (p.s * 0.6).toFloat()
                    canvas.drawLine(p.x.toFloat(), p.y.toFloat(), (p.x - p.vx * 0.02).toFloat(), (p.y - p.vy * 0.02).toFloat(), pAddStroke)
                }
            }
        }
        for (d in W.deb) {
            canvas.save()
            canvas.translate(d.x.toFloat(), d.y.toFloat())
            canvas.rotate(Math.toDegrees(d.a).toFloat())
            pFill.color = withAlpha(d.c, cl(d.life / 12.0, 0.0, 1.0))
            val hw = (d.w / 2.0).toFloat()
            val hh = (d.h / 2.0).toFloat()
            canvas.drawRect(-hw, -hh, hw, hh, pFill)
            canvas.restore()
        }
        for (s in W.ships) {
            val d = s.dock
            if (s.dead || d == null || d.ty != "anchor" || d.b == null || hy(s.x - camX(), s.y - camY()) >= 900.0) continue
            val t = cw(d.b, d.cx, d.cy)
            pStroke.color = PAL.amber
            pStroke.strokeWidth = 1.3f
            pathA.reset()
            pathA.moveTo(s.x.toFloat(), s.y.toFloat())
            pathA.quadTo(
                ((s.x + t.x) / 2.0 + sin(W.time * 3.0) * 2.0).toFloat(),
                ((s.y + t.y) / 2.0).toFloat(),
                t.x.toFloat(),
                t.y.toFloat()
            )
            canvas.drawPath(pathA, pStroke)
            pFill.color = PAL.amber
            canvas.drawRect(t.x.toFloat() - 1.6f, t.y.toFloat() - 1.6f, t.x.toFloat() + 1.6f, t.y.toFloat() + 1.6f, pFill)
        }
    }

    private fun drawTraj(canvas: Canvas) {
        val pl = GS.pl
        if (pl == null || pl.dead || pl.dock != null || GS.ev != null) {
            GS.PC.traj = null
            return
        }
        var x = pl.x
        var y = pl.y
        var vx = pl.vx
        var vy = pl.vy
        val dt = 0.25
        val N = 360
        var hit: TrajHit? = null
        pathA.reset()
        pathA.moveTo(x.toFloat(), y.toFloat())
        for (i in 0 until N) {
            gacc(x, y, vx, vy, gaVec)
            vx += gaVec.x * dt
            vy += gaVec.y * dt
            x += vx * dt
            y += vy * dt
            if (i % 2 == 0) pathA.lineTo(x.toFloat(), y.toFloat())
            if (hy(x, y) < surfR(atan2(y, x))) {
                hit = TrajHit(x, y, i * dt)
                break
            }
        }
        val kf = k.toFloat()
        pStroke.color = Color.argb(115, 83, 217, 255)
        pStroke.strokeWidth = 1.2f / kf
        pStroke.pathEffect = DashPathEffect(floatArrayOf(4f / kf, 5f / kf), 0f)
        canvas.drawPath(pathA, pStroke)
        pStroke.pathEffect = null
        GS.PC.traj = hit
        if (hit != null) {
            pStroke.color = PAL.red
            pStroke.strokeWidth = 1.6f / kf
            val hx = hit.x.toFloat()
            val hy0 = hit.y.toFloat()
            canvas.drawLine(hx - 4f, hy0 - 4f, hx + 4f, hy0 + 4f, pStroke)
            canvas.drawLine(hx + 4f, hy0 - 4f, hx - 4f, hy0 + 4f, pStroke)
        }
    }

    private fun cutL(lc: Canvas, x: Float, y: Float, r: Float, a: Double) {
        if (r <= 0.5f) return
        val a0 = (cl(a, 0.0, 1.0) * 255.0).toInt()
        val a1 = (cl(a * 0.55, 0.0, 1.0) * 255.0).toInt()
        pLightCut.shader = RadialGradient(
            x, y, r,
            intArrayOf(Color.argb(a0, 0, 0, 0), Color.argb(a1, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        lc.drawCircle(x, y, r, pLightCut)
        pLightCut.shader = null
    }

    private data class CastBeam(val origin: Vec2, val pts: List<Vec2>, val hits: List<Vec2>, val range: Double)

    private fun castDissolveFan(
        wx: Double,
        wy: Double,
        ang: Double,
        range: Double,
        half: Double,
        rays: Int,
        stepBackWall: Double = 1.2
    ): CastBeam {
        val bodies = near(wx, wy, range + 16.0)
        val pts = ArrayList<Vec2>(rays + 1)
        for (i in 0..rays) {
            val ra = ang - half + (i.toDouble() / rays) * (half * 2.0)
            val dx = cos(ra)
            val dy = sin(ra)
            var d = 1.5
            var hx = wx + dx * range
            var hy0 = wy + dy * range
            var hit = false
            while (d < range) {
                val px = wx + dx * d
                val py = wy + dy * d
                for (b in bodies) {
                    val c = ca(b, px, py)
                    if (c != null && b.sol[c.i] != 0) {
                        val bd = max(0.0, d - stepBackWall)
                        hx = wx + dx * bd
                        hy0 = wy + dy * bd
                        hit = true
                        break
                    }
                }
                if (hit) break
                d += 1.4
            }
            pts.add(w2s(hx, hy0))
        }
        return CastBeam(w2s(wx, wy), pts, emptyList(), range)
    }

    private fun cutDissolveFan(lc: Canvas, beam: CastBeam, stops: FloatArray, alphas: IntArray) {
        val rPx = (beam.range * k).toFloat()
        if (rPx <= 1f || beam.pts.size < 2) return
        val ox0 = beam.origin.x.toFloat()
        val oy0 = beam.origin.y.toFloat()
        pathA.reset()
        pathA.moveTo(ox0, oy0)
        for (pt in beam.pts) pathA.lineTo(pt.x.toFloat(), pt.y.toFloat())
        pathA.close()
        val cols = IntArray(alphas.size) { idx ->
            val a = alphas[idx].coerceIn(0, 255)
            if (a == 0) Color.TRANSPARENT else Color.argb(a, 0, 0, 0)
        }
        pLightCut.shader = RadialGradient(ox0, oy0, rPx, cols, stops, Shader.TileMode.CLAMP)
        lc.drawPath(pathA, pLightCut)
        pLightCut.shader = null
    }

    private fun castLightCone(wx: Double, wy: Double, ang: Double, range: Double, half: Double, rays: Int = 15): CastBeam {
        val bodies = near(wx, wy, range + 16.0)
        val pts = ArrayList<Vec2>(rays + 1)
        val hits = ArrayList<Vec2>(4)
        for (i in 0..rays) {
            val ra = ang - half + (i.toDouble() / rays) * (half * 2.0)
            val dx = cos(ra)
            val dy = sin(ra)
            var d = 2.0
            var hx = wx + dx * range
            var hy0 = wy + dy * range
            var hit = false
            while (d < range) {
                val px = wx + dx * d
                val py = wy + dy * d
                for (b in bodies) {
                    val c = ca(b, px, py)
                    if (c != null && b.sol[c.i] != 0) {
                        hx = px
                        hy0 = py
                        hit = true
                        break
                    }
                }
                if (hit) break
                d += 1.8
            }
            val sp = w2s(hx, hy0)
            pts.add(sp)
            if (hit && i % 3 == 0) hits.add(sp)
        }
        return CastBeam(w2s(wx, wy), pts, hits, range)
    }

    private fun cutCastCone(lc: Canvas, beam: CastBeam, colStrength: Double) {
        val rPx = (beam.range * k).toFloat()
        if (rPx <= 1f || beam.pts.size < 2) return
        val ox0 = beam.origin.x.toFloat()
        val oy0 = beam.origin.y.toFloat()
        pathA.reset()
        pathA.moveTo(ox0, oy0)
        for (pt in beam.pts) pathA.lineTo(pt.x.toFloat(), pt.y.toFloat())
        pathA.close()
        val a0 = (cl(colStrength, 0.0, 1.0) * 255.0).toInt()
        val a1 = (cl(colStrength * 0.72, 0.0, 1.0) * 255.0).toInt()
        pLightCut.shader = RadialGradient(
            ox0, oy0, rPx,
            intArrayOf(Color.argb(a0, 0, 0, 0), Color.argb(a1, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        lc.drawPath(pathA, pLightCut)
        pLightCut.shader = null
    }

    private fun drawAdditiveCone(canvas: Canvas, beam: CastBeam, beamCol: Int, alphaCore: Double, bounceR: Double) {
        val rPx = (beam.range * k).toFloat()
        if (rPx <= 1f || beam.pts.size < 2) return
        val ox0 = beam.origin.x.toFloat()
        val oy0 = beam.origin.y.toFloat()
        pathA.reset()
        pathA.moveTo(ox0, oy0)
        for (pt in beam.pts) pathA.lineTo(pt.x.toFloat(), pt.y.toFloat())
        pathA.close()
        pAdd.shader = RadialGradient(
            ox0, oy0, rPx,
            intArrayOf(withAlpha(beamCol, alphaCore), withAlpha(beamCol, alphaCore * 0.45), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(pathA, pAdd)
        pAdd.shader = null

        val br = (bounceR * k).toFloat()
        if (br > 2f && beam.hits.isNotEmpty()) {
            for (hp in beam.hits) {
                val hx = hp.x.toFloat()
                val hy0 = hp.y.toFloat()
                pAdd.shader = RadialGradient(
                    hx, hy0, br,
                    withAlpha(beamCol, alphaCore * 1.1), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                canvas.drawCircle(hx, hy0, br, pAdd)
            }
            pAdd.shader = null
        }
    }

    private fun lighting(canvas: Canvas) {
        val W = GS.W ?: return
        val f = GS.focus() ?: return
        val pl = GS.pl
        val ev = GS.ev
        val insBody = entBody(f, f.x, f.y)
        val ins = insBody != null
        val t = tele(f)
        val night = if (t.alt < 400.0) (1.0 - daylight(atan2(f.y, f.x), W.time)) * cl(1.0 - t.alt / 400.0, 0.0, 1.0) else 0.0
        val baseDark = when {
            insBody?.kind == "rock" -> 0.92
            insBody?.kind == "station" -> 0.68
            ev != null && !ev.hide -> 0.22
            else -> 0.0
        }
        val dark = max(baseDark, night * 0.62)

        val playerShipCone = if (pl != null && !pl.dead && ev == null && pl.lightOn) {
            castLightCone(pl.x + cos(pl.a) * pl.nose, pl.y + sin(pl.a) * pl.nose, pl.a, 215.0, 0.34, 15)
        } else null
        val playerEvaCone = if (ev != null && !ev.dead && !ev.hide && ev.lightOn) {
            castLightCone(ev.x, ev.y, ev.a, 160.0, 0.44, 15)
        } else null

        if (dark >= 0.03) {
            ensureLightBuffer(bw, bh)
            val lc = lightCanvas ?: return
            val lb = lightBmp ?: return
            lc.drawColor(Color.argb((dark * 255.0).toInt(), 2, 4, 10), PorterDuff.Mode.SRC)

            for (b in W.bodies) {
                if (abs(b.x - camX()) > b.rad + 900.0 || abs(b.y - camY()) > b.rad + 900.0) continue
                val c = cos(b.a)
                val s = sin(b.a)
                val bodyOpen = b.roofA < 0.72
                for (L in b.lights) {
                    if (!bodyOpen && !L.fl) continue
                    val p = w2s(b.x + L.lx * c - L.ly * s, b.y + L.lx * s + L.ly * c)
                    val flFactor = if (L.fl) 0.55 + 0.45 * sin(W.time * 9.0 + L.lx) else 1.0
                    val strength = if (bodyOpen) 0.95 else 0.28
                    cutL(lc, p.x.toFloat(), p.y.toFloat(), (L.r * k).toFloat(), min(0.95, strength * flFactor))
                }
            }
            if (pl != null && !pl.dead && ev == null) {
                playerShipCone?.let { cutCastCone(lc, it, 0.96) }
                val p = w2s(pl.x, pl.y)
                cutL(lc, p.x.toFloat(), p.y.toFloat(), (28.0 * k).toFloat(), 0.6)
            }
            if (ev != null && !ev.dead && !ev.hide) {
                playerEvaCone?.let { cutCastCone(lc, it, 1.0) }
                val p = w2s(ev.x, ev.y)
                cutL(lc, p.x.toFloat(), p.y.toFloat(), (26.0 * k).toFloat(), 0.78)
            }
            for (m in W.suits) {
                if (m.st == "in") continue
                val p = w2s(m.x, m.y)
                cutL(lc, p.x.toFloat(), p.y.toFloat(), (42.0 * k).toFloat(), 0.65)
            }
            for (s in W.ships) {
                val bm = s.beam
                if (bm != null && !s.dead) {
                    val p = w2s(bm.x1, bm.y1)
                    cutL(lc, p.x.toFloat(), p.y.toFloat(), (38.0 * k).toFloat(), 0.75)
                }
                if (!s.dead && s.thr > 0.1) {
                    val p = w2s(s.x, s.y)
                    cutL(lc, p.x.toFloat(), p.y.toFloat(), (60.0 * k * s.thr).toFloat(), 0.6)
                }
            }
            W.lift?.cars?.forEach { car ->
                val p = w2s(car.x, car.y)
                cutL(lc, p.x.toFloat(), p.y.toFloat(), (18.0 * k).toFloat(), 0.6)
            }
            canvas.drawBitmap(lb, 0f, 0f, pBmp)
        }

        val washAlpha = if (ins) 0.22 else 0.11
        playerShipCone?.let { drawAdditiveCone(canvas, it, COL_JET_STERN, washAlpha, 22.0) }
        playerEvaCone?.let { drawAdditiveCone(canvas, it, COL_JET_STERN, washAlpha * 1.1, 16.0) }
    }

    private fun hudFontSize(px: Float): Float = max(8f, px * rs)

    private fun arrow(canvas: Canvas, x: Double, y: Double, col: Int, label: String) {
        val p = w2s(x, y)
        val m = 26.0 * rs
        if (p.x > m && p.x < bw - m && p.y > m && p.y < bh - m) return
        val cx = bw / 2.0
        val cy = bh / 2.0
        val a = atan2(p.y - cy, p.x - cx)
        val cosA = cos(a).let { if (abs(it) < 1e-6) 1e-6 else it }
        val sinA = sin(a).let { if (abs(it) < 1e-6) 1e-6 else it }
        val t = min((bw / 2.0 - m) / abs(cosA), (bh / 2.0 - m) / abs(sinA))
        val ex = (cx + cos(a) * t).toFloat()
        val ey = (cy + sin(a) * t).toFloat()

        canvas.save()
        canvas.translate(ex, ey)
        canvas.rotate(Math.toDegrees(a).toFloat())
        pFill.color = withAlpha(col, 216.0 / 255.0)
        val sz = 1.0f * max(1f, rs * 0.6f)
        pathA.reset()
        pathA.moveTo(8f * sz, 0f)
        pathA.lineTo(-5f * sz, -5f * sz)
        pathA.lineTo(-5f * sz, 5f * sz)
        pathA.close()
        canvas.drawPath(pathA, pFill)
        canvas.restore()

        pText.textSize = hudFontSize(8.5f)
        pText.color = col
        pText.textAlign = Paint.Align.CENTER
        val f = GS.focus()
        val dist = if (f != null) hy(x - f.x, y - f.y).roundToInt() else 0
        val tx = cl(ex.toDouble(), 44.0 * rs, bw - 44.0 * rs).toFloat()
        val ty = cl((ey + 14f * rs).toDouble(), 14.0 * rs, bh - 6.0 * rs).toFloat()
        canvas.drawText("$label ${dist}m", tx, ty, pText)
    }

    private fun hudCanvas(canvas: Canvas) {
        val W = GS.W ?: return
        val G = GS.G ?: return
        val pl = GS.pl
        val ev = GS.ev
        val cam = GS.cam
        val PC = GS.PC
        val f = GS.focus()

        for (ct in W.contacts) {
            if (ct.b.hidden) continue
            if (!ct.done && ct.hint <= W.time) continue
            if (abs(ct.x - camX()) > bw / k || abs(ct.y - camY()) > bh / k) continue
            val p = w2s(ct.x, ct.y)
            val px = p.x.toFloat()
            val py = p.y.toFloat()
            val col = if (ct.done) {
                when (ct.kind) {
                    "claim" -> PAL.helion
                    "core" -> PAL.cyan
                    else -> PAL.mag
                }
            } else {
                Color.argb(77, 255, 94, 196)
            }
            pStroke.color = col
            pStroke.strokeWidth = 1.4f
            pathA.reset()
            pathA.moveTo(px, py - 6f)
            pathA.lineTo(px + 6f, py)
            pathA.lineTo(px, py + 6f)
            pathA.lineTo(px - 6f, py)
            pathA.close()
            canvas.drawPath(pathA, pStroke)
            if (ct.done) {
                pText.textSize = hudFontSize(8f)
                pText.color = col
                pText.textAlign = Paint.Align.CENTER
                val lbl = when (ct.kind) {
                    "core" -> "CORE SHARD"
                    "claim" -> "HELION CLAIM"
                    else -> ct.kind.uppercase()
                }
                canvas.drawText(lbl, px, py - 9f, pText)
            }
        }

        if (PC.scan && ev == null && pl != null) {
            val o = scanOrg()
            val p = w2s(o.x, o.y)
            val px = p.x.toFloat()
            val py = p.y.toFloat()
            val R = (o.r * k).toFloat()
            val a = o.a - cam.rot
            pAdd.shader = RadialGradient(px, py, max(1f, R), Color.argb(36, 83, 217, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            rectF.set(px - R, py - R, px + R, py + R)
            canvas.drawArc(rectF, Math.toDegrees(a - C.SCAN.cone).toFloat(), Math.toDegrees(C.SCAN.cone * 2.0).toFloat(), true, pAdd)
            pAdd.shader = null

            val sw = (((W.time * 0.8) % 1.0) * R).toFloat()
            pStroke.color = Color.argb(89, 83, 217, 255)
            pStroke.strokeWidth = 1.2f
            rectF.set(px - sw, py - sw, px + sw, py + sw)
            canvas.drawArc(rectF, Math.toDegrees(a - C.SCAN.cone).toFloat(), Math.toDegrees(C.SCAN.cone * 2.0).toFloat(), false, pStroke)

            for (s in PC.list) {
                val q = w2s(s.c.x, s.c.y)
                val qx = q.x.toFloat()
                val qy = q.y.toFloat()
                pFill.color = if (s.s > 0.0) PAL.mag else Color.argb(89, 255, 94, 196)
                canvas.drawCircle(qx, qy, (3.0 + s.s * 4.0).toFloat(), pFill)
                if (s.s > 0.1) {
                    pText.textSize = hudFontSize(9f)
                    pText.color = pFill.color
                    pText.textAlign = Paint.Align.LEFT
                    canvas.drawText("${"%.1f".format(s.f)}kHz", qx + 7f, qy + 3f, pText)
                }
            }
            PC.survey?.let { sv ->
                val b = sv.b
                val q = w2s(b.x, b.y)
                val rr = (b.rad * k + 6.0).toFloat()
                pStroke.color = PAL.cyan
                pStroke.strokeWidth = 2f
                rectF.set(q.x.toFloat() - rr, q.y.toFloat() - rr, q.x.toFloat() + rr, q.y.toFloat() + rr)
                canvas.drawArc(rectF, -90f, (360.0 * sv.p).toFloat(), false, pStroke)
            }
        }

        val g = PC.assist
        if (g != null && !PC.scan && ev == null) {
            val p = w2s(g.w.x, g.w.y)
            val d = g.w.dir - cam.rot
            val nx = cos(d)
            val ny = sin(d)
            val L = 70.0 * k
            pStroke.color = if (g.ok) PAL.green else PAL.amber
            pStroke.strokeWidth = 1.2f
            pStroke.pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
            for (s in doubleArrayOf(-1.0, 1.0)) {
                val o = C.DOCK.l * k * s
                canvas.drawLine(
                    (p.x - ny * o).toFloat(),
                    (p.y + nx * o).toFloat(),
                    (p.x + nx * L - ny * o).toFloat(),
                    (p.y + ny * L + nx * o).toFloat(),
                    pStroke
                )
            }
            pStroke.pathEffect = null
        }

        pText.textAlign = Paint.Align.CENTER
        for (b in W.bodies) {
            if (b.hidden) continue
            if (abs(b.x - camX()) > bw / k || abs(b.y - camY()) > bh / k) continue
            if (b.kind == "rock" && PC.tgt !== b && k < 0.35) continue
            val up = atan2(b.y, b.x)
            val p = w2s(b.x + cos(up) * b.rad * 0.8, b.y + sin(up) * b.rad * 0.8)
            pText.textSize = hudFontSize(if (b.kind == "rock") 9f else 11f)
            val disc = b.cham.any { it.disc }
            pText.color = when {
                b.kind == "rock" -> when {
                    b.cat != 0 -> if (disc) PAL.mag else COL_ROCK_LABEL
                    else -> Color.argb(216, 255, 179, 71)
                }
                b.surf -> PAL.green
                else -> PAL.amber
            }
            val title = b.name + if (b.kind == "rock" && b.cat == 0) " ?" else ""
            canvas.drawText(title, p.x.toFloat(), p.y.toFloat() - 4f, pText)

            if (b.kind == "rock" && b.cat != 0 && (PC.tgt === b || k > 0.8)) {
                pText.textSize = hudFontSize(7f)
                pText.color = PAL.dim
                val top = b.comp.entries
                    .filter { it.key != "rock" }
                    .sortedByDescending { it.value }
                    .take(3)
                    .joinToString(" · ") { "${it.key.uppercase()} ${((it.value.toDouble() / max(1, b.cells)) * 100.0).roundToInt()}%" }
                canvas.drawText(top.ifEmpty { "barren" }, p.x.toFloat(), p.y.toFloat() + 7f, pText)
            } else if (b.kind == "station" && b.transfer != null) {
                val tr = b.transfer!!
                pText.textSize = hudFontSize(8f)
                pText.color = PAL.dim
                val left = max(0.0, tr.depart - W.time)
                val mins = floor(left / 60.0).toInt()
                val secs = floor(left % 60.0).toInt().toString().padStart(2, '0')
                val txt = when {
                    b.gone -> ""
                    b.leaving -> "DEPARTING"
                    b.arr != null -> "ARRIVING"
                    else -> "${if (tr.dest == "mars") "→ MARS" else "→ EARTH"} · departs $mins:$secs"
                }
                if (txt.isNotEmpty()) canvas.drawText(txt, p.x.toFloat(), p.y.toFloat() + 8f, pText)
            }
        }

        for (s in W.ships) {
            if (s.dead || s.isPl || (hy(s.x - camX(), s.y - camY()) > 600.0 / max(0.3, k) && PC.tgt !== s)) continue
            val up = atan2(s.y, s.x)
            val p = w2s(s.x + cos(up) * (s.rad + 5.0), s.y + sin(up) * (s.rad + 5.0))
            pText.textSize = hudFontSize(8f)
            pText.color = if (s.own) PAL.cyan else FC[s.fac] ?: PAL.dim
            canvas.drawText(s.name, p.x.toFloat(), p.y.toFloat(), pText)
        }

        W.nav?.let { nav ->
            val p = w2s(nav.x, nav.y)
            val px = p.x.toFloat()
            val py = p.y.toFloat()
            pStroke.color = PAL.mag
            pStroke.strokeWidth = 1.2f
            canvas.drawLine(px - 7f, py, px + 7f, py, pStroke)
            canvas.drawLine(px, py - 7f, px, py + 7f, pStroke)
            canvas.drawCircle(px, py, (10.0 + sin(W.time * 3.0) * 2.0).toFloat(), pStroke)
            arrow(canvas, nav.x, nav.y, PAL.mag, nav.l)
        }
        PC.tgt?.let { t -> if (!t.dead) arrow(canvas, t.x, t.y, PAL.cyan, "TGT") }
        for (j in G.missions) {
            val st = W.stations.firstOrNull { it.name == j.to }
            if (st != null) arrow(canvas, st.x, st.y, PAL.green, st.name.split(" ")[0])
        }
        for (s in G.fleet) {
            if (!s.dead && !s.isPl) arrow(canvas, s.x, s.y, PAL.cyan, s.name)
        }

        if (f != null && !(ev != null && !ev.hide)) {
            flightHUD(canvas, f)
        } else if (ev != null && !ev.hide) {
            suitHUD(canvas, ev)
        }

        val c = G.crew
        if (c != null && c.state == "site" && ev != null && !ev.hide) {
            arrow(canvas, c.ship.x, c.ship.y, PAL.amber, "MULE-3")
        }
        if (ev != null && !ev.hide && G.tier == 1) {
            val sid = com.example.keres9.sim.storyObj().id
            when (sid) {
                "arrive" -> arrow(canvas, SITES.sel.x - 160.0, SITES.sel.y, PAL.cyan, "SELENE")
                "intake" -> arrow(canvas, SITES.sel.x - 140.0, SITES.sel.y + 45.0, PAL.gold, "INTAKE")
                "bunk" -> {
                    val onSurf = altAt(ev.x, ev.y) < 300.0
                    if (onSurf) arrow(canvas, SITES.hab.x, SITES.hab.y, PAL.green, "HAB 14B")
                    else arrow(canvas, SITES.sel.x, SITES.sel.y + 40.0, PAL.cyan, "LIFT ▼")
                }
                "commute" -> arrow(canvas, SITES.lift.x, SITES.lift.y, PAL.cyan, "LIFT ▲")
                "sign" -> arrow(canvas, SITES.sel.x - 140.0, SITES.sel.y - 45.0, PAL.amber, "CREW")
            }
        }
        pText.textAlign = Paint.Align.LEFT
    }

    private fun flightHUD(canvas: Canvas, f: Targetable) {
        val pl = GS.pl
        val PC = GS.PC
        val t = if (pl != null && pl === f) tele(pl) else com.example.keres9.sim.Telemetry(
            r = hy(f.x, f.y),
            alt = altAt(f.x, f.y),
            vr = 0.0,
            vt = 0.0,
            g = 0.0,
            band = hy(f.x, f.y) - C.RS
        )
        val x = bw / 2f
        val y = bh - 64f * rs
        val vs = t.vr
        val hs = t.vt
        val altTxt = if (t.alt > 2000.0) "${"%.2f".format(t.alt / 1000.0)}km" else "${t.alt.roundToInt()}m"
        val vsTxt = "${if (vs >= 0.0) "+" else ""}${"%.1f".format(vs)}"
        val items = arrayOf(
            "ALT" to altTxt,
            "V↕" to vsTxt,
            "V↔" to "%.1f".format(hs),
            "g" to "%.2f".format(t.g),
            "BAND" to "${if (t.band >= 0.0) "+" else ""}${t.band.roundToInt()}"
        )
        val wv = 68f * rs
        pText.textAlign = Paint.Align.CENTER
        items.forEachIndexed { i, it ->
            val px = x + (i - 2) * wv
            pFill.color = COL_HUD_BOX
            canvas.drawRect(px - wv / 2f + 3f, y - 14f * rs, px + wv / 2f - 3f, y + 12f * rs, pFill)
            pText.color = PAL.dim
            pText.textSize = hudFontSize(7f)
            canvas.drawText(it.first, px, y - 4f * rs, pText)
            pText.color = if (it.first == "V↕" && vs < -4.0 && t.alt < 150.0) PAL.red else PAL.text
            pText.textSize = hudFontSize(10f)
            canvas.drawText(it.second, px, y + 8f * rs, pText)
        }
        val traj = PC.traj
        if (pl != null && pl.dock == null && traj != null && traj.t < 20.0 && t.alt < 600.0) {
            val bad = hy(pl.vx, pl.vy) > C.TOUCH.v
            pText.textSize = hudFontSize(11f)
            pText.color = if (bad) PAL.red else PAL.amber
            val msg = (if (bad) "IMPACT " else "CONTACT ") +
                "${"%.1f".format(traj.t)}s" +
                (if (bad) " — BURN" else "") +
                (if (pl.tailLand && !pl.gear) " · GEAR UP!" else "")
            canvas.drawText(msg, x, y - 26f * rs, pText)
        }
        if (pl != null && PC.sas != 0) {
            pText.textSize = hudFontSize(8f)
            pText.color = PAL.cyan
            val sasLbl = when (PC.sas) {
                1 -> "UP"
                2 -> "RETRO"
                3 -> "PRO"
                else -> ""
            }
            canvas.drawText("SAS $sasLbl", x, y + 22f * rs, pText)
        }
        if (pl != null && pl.dock == null && hy(pl.vx, pl.vy) > 0.5) {
            val p = w2s(pl.x, pl.y)
            val a = atan2(pl.vy, pl.vx) - GS.cam.rot
            val R = (pl.rad + 10.0) * k
            val mx = (p.x + cos(a) * R).toFloat()
            val my = (p.y + sin(a) * R).toFloat()
            pStroke.color = PAL.green
            pStroke.strokeWidth = 1.5f
            canvas.drawCircle(mx, my, 4f, pStroke)
            canvas.drawLine(
                (p.x + cos(a) * (R + 4.0)).toFloat(),
                (p.y + sin(a) * (R + 4.0)).toFloat(),
                (p.x + cos(a) * (R + 9.0)).toFloat(),
                (p.y + sin(a) * (R + 9.0)).toFloat(),
                pStroke
            )
        }
    }

    private fun suitHUD(canvas: Canvas, ev: SuitState) {
        val W = GS.W ?: return
        val G = GS.G ?: return
        val x = bw / 2f
        val y = bh - 64f * rs
        var n = 0
        for ((_, qty) in ev.sack) n += qty
        val maxSack = floor(C.EVA.sack * (if (G.sackUp > 0.0) G.sackUp else 1.0)).toInt()
        pFill.color = COL_HUD_BOX
        canvas.drawRect(x - 120f * rs, y - 14f * rs, x + 120f * rs, y + 12f * rs, pFill)
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = hudFontSize(9f)
        pText.color = if (ev.suit < 30.0) PAL.red else PAL.text
        val stormTag = if (W.flare?.on == true) "   ☢ STORM" else ""
        val lampTag = if (ev.lightOn) "   LAMP ON" else "   LAMP OFF"
        canvas.drawText("AIR ${ev.suit.roundToInt()}%   SACK $n/$maxSack$lampTag$stormTag", x, y + 3f * rs, pText)
    }

    fun renderWorld(canvas: Canvas, widthPx: Float, heightPx: Float, density: Float, dt: Double) {
        val W = GS.W ?: return
        val G = GS.G ?: return
        bw = max(1, widthPx.roundToInt())
        bh = max(1, heightPx.roundToInt())
        rs = cl(density.toDouble(), 1.0, 3.0).toFloat() * 0.65f
        val cam = GS.cam
        val ev = GS.ev

        drawSky(canvas)

        k = cam.z * (density / 1.5)
        cr = cos(cam.rot)
        sr = sin(cam.rot)
        ox = bw / 2.0 + (Random.nextDouble() - 0.5) * cam.shake * rs
        oy = bh / 2.0 + (Random.nextDouble() - 0.5) * cam.shake * rs

        val hw = hypot(bw.toDouble(), bh.toDouble()) / 2.0 / max(0.05, k)
        val cx = camX()
        val cy = camY()
        val vis = ArrayList<Body>(16)
        for (b in W.bodies) {
            if (b.hidden && !(b.orphan && storyAtOrPast("lander"))) continue
            if (abs(b.x - cx) < b.rad + hw + 60.0 && abs(b.y - cy) < b.rad + hw + 60.0) {
                vis.add(b)
            }
        }
        val fadeK = 1.0 - exp(-5.0 * dt)
        for (b in vis) {
            b.roofA = lp(b.roofA, if (inside(b)) 0.06 else 1.0, fadeK)
        }

        val pl = GS.pl
        val f = GS.focus()
        val evVisible = ev != null && !ev.dead && !ev.hide
        val viewX = when {
            evVisible -> ev!!.x
            pl != null && !pl.dead -> pl.x
            else -> f?.x ?: cx
        }
        val viewY = when {
            evVisible -> ev!!.y
            pl != null && !pl.dead -> pl.y
            else -> f?.y ?: cy
        }
        val viewAng = when {
            evVisible -> ev!!.a
            pl != null && !pl.dead -> pl.a
            else -> 0.0
        }
        val coneRange = if (evVisible) 128.0 else 150.0
        val proxRange = if (evVisible) 46.0 else 58.0
        val hasDissolveTarget = f != null && vis.any { b -> hy(b.x - viewX, b.y - viewY) < b.rad + coneRange }

        canvas.save()
        canvas.translate(ox.toFloat(), oy.toFloat())
        canvas.scale(k.toFloat(), k.toFloat())
        canvas.rotate(Math.toDegrees(-cam.rot).toFloat())
        canvas.translate((-cx).toFloat(), (-cy).toFloat())

        drawMoon(canvas)
        drawLift(canvas)

        val picksIn = ArrayList<Pick>()
        val picksOut = ArrayList<Pick>()
        for (p in W.picks) {
            if (!p.dead && abs(p.x - cx) < hw + 40.0 && abs(p.y - cy) < hw + 40.0) {
                if (entBody(p, p.x, p.y) != null) picksIn.add(p) else picksOut.add(p)
            }
        }
        val shipsIn = ArrayList<Ship>()
        val shipsOut = ArrayList<Ship>()
        for (s in W.ships) {
            if (!s.dead && abs(s.x - cx) < hw + 60.0 && abs(s.y - cy) < hw + 60.0) {
                if (entBody(s, s.x, s.y) != null) shipsIn.add(s) else shipsOut.add(s)
            }
        }
        val evIn = evVisible && entBody(ev!!, ev.x, ev.y) != null

        for (b in vis) {
            val revealNear = f != null && hy(b.x - viewX, b.y - viewY) < b.rad + coneRange
            drawFloor(canvas, b, revealNear)
        }
        for (s in shipsIn) drawShip(canvas, s)
        if (evVisible && evIn) drawEVA(canvas, ev!!)
        for (p in picksIn) drawPick(canvas, p)

        if (hasDissolveTarget) {
            ensureRoofBuffer(bw, bh)
            val rc = roofLayerCanvas
            val rb = roofLayerBmp
            if (rc != null && rb != null) {
                rc.drawColor(0, PorterDuff.Mode.CLEAR)
                rc.save()
                rc.translate(ox.toFloat(), oy.toFloat())
                rc.scale(k.toFloat(), k.toFloat())
                rc.rotate(Math.toDegrees(-cam.rot).toFloat())
                rc.translate((-cx).toFloat(), (-cy).toFloat())
                for (b in vis) drawRoof(rc, b, includeBeacons = false)
                rc.restore()

                val viewCone = castDissolveFan(viewX, viewY, viewAng, coneRange, 0.58, 44, stepBackWall = 1.2)
                cutDissolveFan(
                    rc,
                    viewCone,
                    stops = floatArrayOf(0f, 0.48f, 0.78f, 1f),
                    alphas = intArrayOf(242, 190, 95, 0)
                )
                val proxFan = castDissolveFan(viewX, viewY, viewAng, proxRange, PI, 48, stepBackWall = 1.2)
                cutDissolveFan(
                    rc,
                    proxFan,
                    stops = floatArrayOf(0f, 0.52f, 1f),
                    alphas = intArrayOf(232, 138, 0)
                )

                canvas.restore()
                canvas.drawBitmap(rb, 0f, 0f, pBmp)
                canvas.save()
                canvas.translate(ox.toFloat(), oy.toFloat())
                canvas.scale(k.toFloat(), k.toFloat())
                canvas.rotate(Math.toDegrees(-cam.rot).toFloat())
                canvas.translate((-cx).toFloat(), (-cy).toFloat())
                for (b in vis) drawRoofBeacons(canvas, b)
            } else {
                for (b in vis) drawRoof(canvas, b)
            }
        } else {
            for (b in vis) drawRoof(canvas, b)
        }

        for (s in shipsOut) drawShip(canvas, s)
        if (evVisible && !evIn) drawEVA(canvas, ev!!)
        for (p in picksOut) drawPick(canvas, p)

        drawMates(canvas)
        drawFx(canvas)
        drawTraj(canvas)

        canvas.restore()

        lighting(canvas)
        hudCanvas(canvas)

        if (cam.flash > 0.0) {
            pFill.color = Color.argb((min(0.6, cam.flash) * 255.0).toInt(), 255, 240, 220)
            canvas.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), pFill)
        }
        if (W.flare?.on == true) {
            val a = ((0.04 + 0.03 * sin(W.time * 6.0)) * 255.0).toInt()
            pFill.color = Color.argb(a, 255, 60, 40)
            canvas.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), pFill)
        }
    }

    fun drawChart(canvas: Canvas, w: Float, h: Float, big: Boolean): MapTransform {
        pFill.color = COL_CHART_BG
        canvas.drawRect(0f, 0f, w, h, pFill)
        val W = GS.W ?: return MapTransform(1.0, 0.0, 0.0, 0.0, w, h)
        val G = GS.G ?: return MapTransform(1.0, 0.0, 0.0, 0.0, w, h)
        val f = GS.focus()
        val fx = f?.x ?: 0.0
        val fy = f?.y ?: -C.RS
        val sc: Double
        val cx0: Double
        val cy0: Double
        val rot: Double
        if (big) {
            sc = min(w / 10600.0, h / 5900.0)
            cx0 = 0.0
            cy0 = -2600.0
            rot = 0.0
        } else {
            sc = 0.03 * (w / 126.0)
            cx0 = fx
            cy0 = fy
            rot = GS.cam.rot
        }
        val px = (1.0 / sc).toFloat()

        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.rotate(Math.toDegrees(-rot).toFloat())
        canvas.scale(sc.toFloat(), sc.toFloat())
        canvas.translate((-cx0).toFloat(), (-cy0).toFloat())

        pFill.color = COL_CHART_MOON
        canvas.drawCircle(0f, 0f, C.RM.toFloat(), pFill)
        pStroke.color = COL_CHART_BAND
        pStroke.strokeWidth = (C.BAND * 2.0).toFloat()
        canvas.drawCircle(0f, 0f, C.RS.toFloat(), pStroke)
        pStroke.color = COL_CHART_RING
        pStroke.strokeWidth = px
        canvas.drawCircle(0f, 0f, C.RS.toFloat(), pStroke)

        val saDeg = Math.toDegrees(sunAng(W.time)).toFloat()
        pFill.color = Color.argb(115, 0, 0, 0)
        val rmF = C.RM.toFloat()
        rectF.set(-rmF, -rmF, rmF, rmF)
        canvas.drawArc(rectF, saDeg + 90f, 180f, true, pFill)

        W.lift?.E?.let { E ->
            pStroke.color = Color.argb(153, 160, 190, 210)
            pStroke.strokeWidth = px
            canvas.drawLine(E.g.x.toFloat(), E.g.y.toFloat(), E.t.x.toFloat(), E.t.y.toFloat(), pStroke)
        }

        for (a in W.rocks) {
            if (a.hidden && !(a.orphan && storyAtOrPast("lander"))) continue
            val d = a.cham.any { it.disc }
            pFill.color = when {
                a.orphan -> PAL.helion
                a.cat != 0 -> if (d) COL_CHART_ROCK_CHAM else COL_CHART_ROCK_CAT
                else -> COL_CHART_ROCK_UNK
            }
            val r = max(if (big) 2.2 * px else 1.5 * px, a.rVal * C.CS).toFloat()
            canvas.drawCircle(a.x.toFloat(), a.y.toFloat(), r, pFill)
            if (big && (a.orphan || d)) {
                canvas.save()
                canvas.translate(a.x.toFloat(), a.y.toFloat())
                canvas.scale(px, px)
                pText.color = if (a.orphan) PAL.helion else PAL.mag
                pText.textSize = 9f
                pText.textAlign = Paint.Align.CENTER
                canvas.drawText(if (a.orphan) "THE ORPHAN" else a.name, 0f, -8f, pText)
                canvas.restore()
            }
        }

        for (s in W.stations) {
            pFill.color = if (s.surf) PAL.green else PAL.amber
            val r = (if (big) 4f else 2.5f) * px
            canvas.drawRect(s.x.toFloat() - r, s.y.toFloat() - r, s.x.toFloat() + r, s.y.toFloat() + r, pFill)
            if (big) {
                canvas.save()
                canvas.translate(s.x.toFloat(), s.y.toFloat())
                canvas.scale(px, px)
                pText.color = pFill.color
                pText.textSize = 10f
                pText.textAlign = Paint.Align.CENTER
                canvas.drawText(s.name, 0f, if (s.surf) 16f else -8f, pText)
                canvas.restore()
            }
        }

        for (s in W.ships) {
            if (s.dead || s.isPl) continue
            pFill.color = if (s.own) PAL.cyan else FC[s.fac] ?: PAL.dim
            canvas.drawRect(s.x.toFloat() - px, s.y.toFloat() - px, s.x.toFloat() + px, s.y.toFloat() + px, pFill)
        }

        if (big) {
            for (j in G.missions) {
                val st = W.stations.firstOrNull { it.name == j.to } ?: continue
                pStroke.color = PAL.green
                pStroke.strokeWidth = 1.5f * px
                canvas.drawCircle(st.x.toFloat(), st.y.toFloat(), 12f * px, pStroke)
            }
        }

        W.nav?.let { nav ->
            pStroke.color = PAL.mag
            pStroke.strokeWidth = 1.5f * px
            canvas.drawCircle(nav.x.toFloat(), nav.y.toFloat(), (if (big) 7f else 3f) * px, pStroke)
        }

        pFill.color = PAL.cyan
        canvas.drawCircle(fx.toFloat(), fy.toFloat(), (if (big) 4f else 2.6f) * px, pFill)
        canvas.restore()
        return MapTransform(sc, cx0, cy0, rot, w, h)
    }

    fun drawSpectrum(canvas: Canvas, w: Float, h: Float) {
        val W = GS.W ?: return
        val PC = GS.PC
        pFill.color = COL_SPEC_BG
        canvas.drawRect(0f, 0f, w, h, pFill)
        pStroke.color = COL_SPEC_GRID
        pStroke.strokeWidth = 1f
        pText.color = COL_SPEC_LABEL
        pText.textSize = max(9f, h * 0.16f)
        pText.textAlign = Paint.Align.CENTER
        for (f in 20..200 step 20) {
            val px = ((f - 20f) / 180f) * w
            canvas.drawLine(px, 0f, px, h, pStroke)
            canvas.drawText(f.toString(), px, h - 2f, pText)
        }
        pathA.reset()
        val steps = min(240, max(60, w.toInt() / 2))
        for (i in 0..steps) {
            val px = (i.toFloat() / steps) * w
            val y = h - 10f - (abs(sin(px * 0.7 + W.time * 9.0) * 2.0 + Random.nextDouble() * 1.5) * (h / 56f)).toFloat()
            if (i == 0) pathA.moveTo(px, y) else pathA.lineTo(px, y)
        }
        pStroke.color = COL_SPEC_NOISE
        canvas.drawPath(pathA, pStroke)

        for (s in PC.list) {
            val px = (((s.f - 20.0) / 180.0) * w).toFloat()
            val barH = ((6.0 + s.s * 38.0) * (h / 56.0)).toFloat()
            val baseY = h - 10f
            val col = if (s.s > 0.05) PAL.mag else Color.argb(77, 255, 94, 196)
            pFill.shader = LinearGradient(px, baseY - barH, px, baseY, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(px - 3f, baseY - barH, px + 3f, baseY, pFill)
            pFill.shader = null
        }

        val dx = (((PC.freq - 20.0) / 180.0) * w).toFloat()
        pStroke.color = PAL.cyan
        pStroke.strokeWidth = 2.2f
        canvas.drawLine(dx, 0f, dx, h - 8f, pStroke)
    }

    fun drawScope(canvas: Canvas, w: Float, h: Float) {
        pFill.color = COL_SPEC_BG
        canvas.drawRect(0f, 0f, w, h, pFill)
        val g = GS.PC.assist ?: return
        val sc = w / 86f
        val cx = w / 2f
        val cy = h * 0.68f
        pStroke.color = Color.argb(204, 43, 58, 77)
        pStroke.strokeWidth = max(1f, sc * 0.8f)
        for (r in intArrayOf(10, 20, 30)) {
            canvas.drawCircle(cx, cy, r * sc, pStroke)
        }
        pStroke.color = if (g.ok) PAL.green else PAL.amber
        pStroke.strokeWidth = 2f * sc
        canvas.drawLine(cx - 18f * sc, cy, cx + 18f * sc, cy, pStroke)

        val sx = cx + cl(g.lat * 1.1, -34.0, 34.0).toFloat() * sc
        val sy = cy - cl((g.d - 4.0) * 1.1, -30.0, 44.0).toFloat() * sc
        canvas.save()
        canvas.translate(sx, sy)
        canvas.rotate(Math.toDegrees(-g.ae).toFloat())
        pFill.color = if (g.a) PAL.green else PAL.red
        pathA.reset()
        pathA.moveTo(0f, 7f * sc)
        pathA.lineTo(-5f * sc, -5f * sc)
        pathA.lineTo(5f * sc, -5f * sc)
        pathA.close()
        canvas.drawPath(pathA, pFill)
        canvas.restore()

        pText.textAlign = Paint.Align.CENTER
        pText.textSize = max(9f, 7.5f * sc)
        pText.color = PAL.dim
        canvas.drawText(if (g.ty == "pad") "TAIL / PAD" else "NOSE / PORT", cx, 10f * sc, pText)
        pText.color = if (g.ok) PAL.green else PAL.red
        canvas.drawText(if (g.ok) "COMMIT" else "HOLD", cx, h - 4f * sc, pText)
    }

    fun drawSparkline(canvas: Canvas, w: Float, h: Float, hist: List<Int>, col: Int) {
        if (hist.size < 2) return
        val mn = hist.minOrNull()?.toDouble() ?: 0.0
        val mx = hist.maxOrNull()?.toDouble() ?: 1.0
        val rg = max(1.0, mx - mn)
        pathA.reset()
        hist.forEachIndexed { i, v ->
            val px = (i.toFloat() / (hist.size - 1)) * w
            val py = h - 2f - (((v - mn) / rg) * (h - 4f)).toFloat()
            if (i == 0) pathA.moveTo(px, py) else pathA.lineTo(px, py)
        }
        pStroke.color = col
        pStroke.strokeWidth = max(1.5f, h * 0.08f)
        canvas.drawPath(pathA, pStroke)
    }

    fun renderFaceBitmap(seed: Int, fac: String): Bitmap {
        val key = "${seed}_$fac"
        faceCache[key]?.let { return it }
        val bmp = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint()
        val rObj = SeededRng(if (seed != 0) seed else 1)
        val c = FC[fac] ?: PAL.dim
        p.color = parseHexColor("#0b1018"); canvas.drawRect(0f, 0f, 28f, 28f, p)
        p.color = parseHexColor("#2b3a4d"); canvas.drawRect(4f, 3f, 24f, 25f, p)
        p.color = parseHexColor("#41546b"); canvas.drawRect(5f, 4f, 23f, 10f, p)
        p.color = c; canvas.drawRect(6f, 9f, 22f, 17f, p)
        p.color = parseHexColor("#0a0e14"); canvas.drawRect(7f, 10f, 21f, 16f, p)
        p.color = c
        val e1 = 9 + ((seed ushr 3) % 3)
        val e2 = 15 + ((seed ushr 5) % 3)
        canvas.drawRect(e1.toFloat(), 12f, (e1 + 2).toFloat(), 14f, p)
        canvas.drawRect(e2.toFloat(), 12f, (e2 + 2).toFloat(), 14f, p)
        p.color = parseHexColor("#8b98a8"); canvas.drawRect(4f, 18f, 24f, 20f, p)
        p.color = sh(c, 0.65); canvas.drawRect(6f, 21f, 22f, 24f, p)
        p.color = Color.argb(89, 0, 0, 0)
        for (i in 0 until 7) {
            val sx = (4 + floor(rObj.next() * 20.0).toInt()).toFloat()
            val sy = (3 + floor(rObj.next() * 22.0).toInt()).toFloat()
            canvas.drawRect(sx, sy, sx + 1f, sy + 1f, p)
        }
        faceCache[key] = bmp
        return bmp
    }
}
