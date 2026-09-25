package hu.elmdash.graphics

import android.graphics.*
import android.util.LruCache
import hu.elmdash.connection.DashboardState
import hu.elmdash.obd.Pid
import hu.elmdash.trip.*
import java.util.Locale

/** Shared dashboard artwork for the Car App Library and experimental media surfaces. */
object DashboardTiles {
    private val icons = LruCache<Tile, Bitmap>(24)
    private val overviewIcons = LruCache<List<Tile>, Bitmap>(8)
    private const val Grey = 0xFF353C43.toInt()
    private const val Green = 0xFF175F43.toInt()
    private const val Blue = 0xFF175D84.toInt()
    private const val Amber = 0xFF82510D.toInt()
    private const val Red = 0xFF932D32.toInt()
    private const val Neutral = 0xFF253D50.toInt()
    data class Tile(val title: String, val value: String, val unit: String, val text: String,
        val color: Int, val symbol: String, val fresh: Boolean, val detailPage: Int,
        val description: String, val dialFraction: Int = -1)

    private fun n(v: Double?, digits: Int = 0) = v?.let {
        String.format(Locale.forLanguageTag("hu-HU"), "%.${digits}f", it)
    } ?: "—"

    fun create(s: DashboardState): List<Tile> {
        fun live(p: Pid) = s.telemetry.value(p, s.nowMs)
        fun last(p: Pid) = live(p) ?: s.telemetry.readings[p]?.lastKnownValue
        val advice = s.driving
        val rpmColor = when (advice.cue) {
            DriveCue.NO_DATA, DriveCue.STOPPED -> Grey
            DriveCue.COLD -> Blue
            DriveCue.EFFICIENT -> Green
            DriveCue.UPSHIFT, DriveCue.DOWNSHIFT, DriveCue.HIGH_LOAD, DriveCue.LOW_RPM_LOAD, DriveCue.HIGH_RPM -> Amber
            DriveCue.HOT -> Red
            else -> Neutral
        }
        val symbol = when (advice.cue) {
            DriveCue.UPSHIFT -> "up"
            DriveCue.DOWNSHIFT -> "down"
            DriveCue.EFFICIENT -> "check"
            DriveCue.HOT, DriveCue.HIGH_RPM, DriveCue.HIGH_LOAD, DriveCue.LOW_RPM_LOAD -> "alert"
            else -> "dash"
        }
        val coolantColor = when (advice.coolant) {
            CoolantBand.COLD, CoolantBand.WARMING -> Blue
            CoolantBand.NORMAL -> Green
            CoolantBand.WARM -> Amber
            CoolantBand.HOT -> Red
            CoolantBand.NO_DATA -> Grey
        }
        val rpmLive = live(Pid.RPM) != null
        val load = last(Pid.LOAD)
        val loadText = "${n(load)} % terhelés" + if (live(Pid.LOAD) == null && load != null) " (utolsó)" else ""
        val gear = advice.estimatedGear?.let { " • ≈$it. fokozat" }.orEmpty()
        return listOf(
            Tile("Fordulatszám", n(last(Pid.RPM)), "rpm", loadText + gear,
                if (rpmLive) rpmColor else Grey, if (rpmLive) symbol else "dash", rpmLive, 1,
                "${advice.cue.label}. Tájékoztató becslés.",
                (last(Pid.RPM)?.div(6_000)?.times(100)?.toInt() ?: 0).coerceIn(0, 100)),
            Tile("Vízhőfok", n(last(Pid.COOLANT)), "°C", advice.coolant.label,
                if (live(Pid.COOLANT) != null) coolantColor else Grey, "temperature", live(Pid.COOLANT) != null, 2, advice.coolant.label),
            Tile("Fogyasztás", n(s.fuelDisplay.value, 1), s.fuelDisplay.unit,
                "Átlag: ${n(s.trip.averageL100, 1)} l/100" + if (s.trip.hasGaps) " • részleges" else "",
                if (s.fuelDisplay.fresh) Neutral else Grey, "fuel", s.fuelDisplay.fresh, 3,
                if (s.fuelDisplay.fresh) s.fuelDisplay.source.label else "Utolsó ismert adat"),
            Tile("Sebesség / kapcsolat", n(last(Pid.SPEED)), "km/h", s.phase.label,
                if (live(Pid.SPEED) != null) Neutral else Grey, "road", live(Pid.SPEED) != null, 1,
                s.phase.label)
        )
    }

    /** Pane's illustration receives more space on older Auto hosts than grid thumbnails. */
    fun overview(s: DashboardState): Bitmap {
        val tiles = create(s).take(2)
        return overviewIcons[tiles] ?: run {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val rpm = tiles[0]; val water = tiles[1]
            p.color = rpm.color
            c.drawRoundRect(0f, 0f, 512f, 310f, 28f, 28f, p)
            p.color = water.color
            c.drawRoundRect(0f, 320f, 512f, 512f, 28f, 28f, p)
            val rpmInk = if (rpm.fresh) Color.WHITE else 0xFF9BA5AD.toInt()
            c.save(); c.translate(32f, -40f); c.scale(2f, 2f)
            drawSymbol(c, p, rpm.symbol, rpmInk); c.restore()
            p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.color = rpmInk
            p.typeface = Typeface.create("sans-serif", Typeface.BOLD); p.textSize = 104f
            c.drawText(rpm.value, 256f, 210f, p)
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); p.textSize = 34f
            c.drawText("rpm", 256f, 255f, p)
            p.textSize = 20f
            c.drawText(if (rpm.value == "—") "ADATRA VÁR" else if (!rpm.fresh) "UTOLSÓ ADAT"
                else "KALOS 1.2 • BECSLÉS", 256f, 289f, p)
            val waterInk = if (water.fresh) Color.WHITE else 0xFF9BA5AD.toInt()
            c.save(); c.translate(-48f, 358f); drawSymbol(c, p, "temperature", waterInk); c.restore()
            p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.color = waterInk
            p.typeface = Typeface.create("sans-serif", Typeface.BOLD); p.textSize = 76f
            c.drawText("${water.value} °C", 294f, 430f, p)
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); p.textSize = 24f
            c.drawText(if (water.value == "—") "ADATRA VÁR" else if (water.fresh) "VÍZHŐFOK"
                else "UTOLSÓ ADAT", 294f, 478f, p)
            bitmap.also { overviewIcons.put(tiles, it) }
        }
    }

    fun indicator(s: DashboardState, index: Int): Bitmap = icon(create(s)[index])

    /** AA also uses artwork as a cropped background, with controls over its lower area.
     * Keep every reading inside the upper, central 55%; the rest is a quiet opaque backdrop.
     * No host API exposes the card bounds, so the metadata also carries the essential values.
     */
    fun compactOverview(s: DashboardState): Bitmap {
        val tiles = create(s)
        val rpm = tiles[0]; val water = tiles[1]
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(0xFF101B24.toInt())
        fun box(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
            p.color = color; p.style = Paint.Style.FILL
            c.drawRoundRect(left, top, right, bottom, 12f, 12f, p)
        }
        fun text(value: String, x: Float, y: Float, size: Float, fresh: Boolean = true) {
            p.color = if (fresh) Color.WHITE else 0xFF9BA5AD.toInt()
            p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create("sans-serif", Typeface.BOLD); p.textSize = size
            c.drawText(value, x, y, p)
        }
        box(64f, 44f, 278f, 154f, rpm.color)
        box(286f, 44f, 448f, 154f, water.color)
        text(if (s.simulated) "DEMÓ • RPM" else if (s.carUnavailable) "NINCS OBD" else "RPM", 171f, 72f, 18f, rpm.fresh)
        val cue = when (rpm.symbol) { "up" -> "↑"; "down" -> "↓"; "check" -> "✓"; "alert" -> "!"; else -> "" }
        text(rpm.value, 159f, 130f, 47f, rpm.fresh)
        text(cue, 246f, 127f, 35f, rpm.fresh)
        text("VÍZ °C", 367f, 72f, 18f, water.fresh)
        text(water.value, 367f, 130f, 47f, water.fresh)
        val display = s.fuelDisplay
        val dailyFresh = s.daily.averageL100 != null
        box(64f, 164f, 252f, 277f, if (display.fresh) Neutral else Grey)
        box(260f, 164f, 448f, 277f, if (dailyFresh) Neutral else Grey)
        text("10 s · l/100", 158f, 192f, 18f, display.fresh)
        text("MA l/100", 354f, 192f, 18f, dailyFresh)
        val instantPrefix = if (display.source.estimated && display.value != null) "≈" else ""
        val dailyPrefix = if (s.daily.containsEstimate && dailyFresh) "≈" else ""
        text(instantPrefix + n(display.value, 1), 158f, 252f, 45f, display.fresh)
        text(dailyPrefix + n(s.daily.averageL100, 1), 354f, 252f, 45f, dailyFresh)
        return bitmap
    }

    private fun icon(t: Tile): Bitmap = icons[t] ?: run {
        val bitmap = Bitmap.createBitmap(448, 448, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(2f, 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = t.color
        canvas.drawRoundRect(0f, 0f, 224f, 224f, 26f, 26f, paint)
        val ink = if (t.fresh) Color.WHITE else 0xFF9BA5AD.toInt()
        paint.color = ink; paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f; paint.strokeCap = Paint.Cap.ROUND
        if (t.dialFraction >= 0) {
            paint.color = (ink and 0x00FFFFFF) or 0x44000000
            canvas.drawArc(RectF(26f, 20f, 198f, 192f), 205f, 130f, false, paint)
            paint.color = ink
            canvas.drawArc(RectF(26f, 20f, 198f, 192f), 205f, 130f * t.dialFraction / 100, false, paint)
        }
        drawSymbol(canvas, paint, t.symbol, ink)
        paint.style = Paint.Style.FILL; paint.color = ink
        paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = if (t.value.length > 4) 48f else 56f
        canvas.drawText(t.value, 112f, 142f, paint)
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); paint.textSize = 21f
        canvas.drawText(t.unit, 112f, 174f, paint)
        paint.textSize = 12f
        canvas.drawText(if (t.value == "—") "ADATRA VÁR" else if (!t.fresh) "UTOLSÓ ADAT"
            else if (t.title == "Fordulatszám") "KALOS 1.2 • BECSLÉS" else "OBD-II", 112f, 204f, paint)
        bitmap.also { icons.put(t, it) }
    }

    private fun drawSymbol(c: Canvas, p: Paint, symbol: String, ink: Int) {
        p.color = ink; p.style = Paint.Style.STROKE; p.strokeWidth = 5f
        when (symbol) {
            "up", "down" -> {
                val up = symbol == "up"
                val tip = if (up) 30f else 76f
                val end = if (up) 76f else 30f
                val wing = if (up) 48f else 58f
                c.drawLine(112f, tip, 112f, end, p)
                c.drawLine(94f, wing, 112f, tip, p); c.drawLine(130f, wing, 112f, tip, p)
            }
            "check" -> { c.drawLine(96f, 53f, 108f, 65f, p); c.drawLine(108f, 65f, 131f, 39f, p) }
            "alert" -> { c.drawLine(112f, 34f, 112f, 58f, p); c.drawPoint(112f, 73f, p) }
            "temperature" -> {
                c.drawRoundRect(102f, 27f, 120f, 66f, 9f, 9f, p); c.drawCircle(111f, 70f, 14f, p)
                c.drawLine(111f, 42f, 111f, 69f, p); c.drawLine(134f, 39f, 144f, 39f, p)
                c.drawLine(134f, 53f, 144f, 53f, p)
            }
            "fuel" -> {
                c.drawRoundRect(91f, 29f, 121f, 78f, 4f, 4f, p)
                c.drawRect(98f, 37f, 114f, 49f, p); c.drawLine(85f, 80f, 126f, 80f, p)
                c.drawLine(121f, 49f, 136f, 59f, p); c.drawLine(136f, 59f, 136f, 73f, p)
            }
            "road" -> {
                c.drawLine(99f, 30f, 83f, 81f, p); c.drawLine(125f, 30f, 141f, 81f, p)
                c.drawLine(112f, 33f, 112f, 46f, p); c.drawLine(112f, 62f, 112f, 79f, p)
            }
            else -> c.drawLine(100f, 56f, 124f, 56f, p)
        }
    }
}
