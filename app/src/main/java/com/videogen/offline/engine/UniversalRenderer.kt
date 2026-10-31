package com.videogen.offline.engine

import android.graphics.*
import kotlin.math.sin
import kotlin.random.Random

class UniversalRenderer(private val w: Int, private val h: Int) {
    
    private val stars = List(120) { Triple(Random.nextFloat()*w, Random.nextFloat()*h*0.7f, Random.nextFloat()*2f+0.5f) }
    private val rainDrops = List(200) { Triple(Random.nextFloat()*w, Random.nextFloat()*h, Random.nextFloat()*10f+15f) }
    private val snowflakes = List(120) { Pair(Random.nextFloat()*w, Random.nextFloat()*h) }

    fun renderFrame(scene: UniversalScene, frameCount: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val time = frameCount / 30f

        // 1. LANGIT
        drawSky(canvas, scene)

        // 2. LATAR (bintang/gunung/pohon dari AI dengan layer 0-1)
        drawBackground(canvas, scene, time)

        // 3. KARAKTER & SEMUA BENTUK (urut by layer)
        val sorted = scene.shapes.sortedBy { it.layer }
        for (shape in sorted) {
            drawShape(canvas, shape, time)
        }

        // 4. CUACA DI DEPAN
        drawWeather(canvas, scene, time)

        // 5. VIGNETTE
        drawVignette(canvas)
        return bmp
    }

    private fun drawSky(canvas: Canvas, scene: UniversalScene) {
        val paint = Paint()
        val top = safeColor(scene.skyTop, Color.parseColor("#4A90E2"))
        val bottom = safeColor(scene.skyBottom, Color.parseColor("#B0E0E6"))
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }

    private fun drawBackground(canvas: Canvas, scene: UniversalScene, time: Float) {
        if (scene.time == "night" || scene.weather == "stars") {
            val p = Paint().apply { color = Color.WHITE }
            for ((x, y, r) in stars) {
                p.alpha = (sin(time*3f+x*0.01f)*127+128).toInt().coerceIn(0,255)
                canvas.drawCircle(x, y, r, p)
            }
            p.alpha = 255
        }
        // Matahari/Bulan
        val sunP = Paint().apply { isAntiAlias = true }
        val sunX = w * 0.85f; val sunY = h * 0.18f
        if (scene.time == "night") {
            sunP.shader = RadialGradient(sunX, sunY, 120f, intArrayOf(Color.WHITE, Color.TRANSPARENT), floatArrayOf(0.3f,1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(sunX, sunY, 120f, sunP); sunP.shader = null
            sunP.color = Color.parseColor("#E8E8E8"); canvas.drawCircle(sunX, sunY, 40f, sunP)
        } else {
            sunP.shader = RadialGradient(sunX, sunY, 180f, intArrayOf(Color.parseColor("#FFF8DC"), Color.TRANSPARENT), floatArrayOf(0.3f,1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(sunX, sunY, 180f, sunP); sunP.shader = null
            sunP.color = Color.parseColor("#FFD700"); canvas.drawCircle(sunX, sunY, 45f, sunP)
        }
    }

    private fun drawShape(canvas: Canvas, s: DrawShape, time: Float) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = safeColor(s.color, Color.WHITE)
        }
        var cx = s.x / 100f * w
        var cy = s.y / 100f * h
        var sw = s.w / 100f * w
        var sh = s.h / 100f * h
        var needsRestore = false

        when (s.animation) {
            "flap" -> {
                canvas.save(); needsRestore = true
                canvas.rotate(sin(time*12f)*30f, cx, cy)
            }
            "sway" -> cx += sin(time*3f + s.x*0.1f) * 6f
            "breathe" -> { val k = 1f + sin(time*2f)*0.08f; sw *= k; sh *= k }
            "walk" -> cy += sin(time*10f + s.x*0.2f) * 4f
            "float" -> cy += sin(time*2f + s.y*0.1f) * 8f
            "rotate" -> { canvas.save(); needsRestore = true; canvas.rotate(time*120f, cx, cy) }
            "blink" -> { if ((time*2f % 1f) > 0.9f) sh *= 0.15f }
        }

        when (s.shape) {
            "circle" -> canvas.drawCircle(cx, cy, sw/2f, paint)
            "ellipse" -> canvas.drawOval(RectF(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2), paint)
            "rect" -> canvas.drawRect(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2, paint)
            "triangle" -> {
                if (s.points.size >= 3) {
                    val path = Path(); val p0 = s.points[0]
                    path.moveTo(p0.first/100f*w, p0.second/100f*h)
                    for (i in 1 until s.points.size) {
                        val p = s.points[i]; path.lineTo(p.first/100f*w, p.second/100f*h)
                    }
                    path.close(); canvas.drawPath(path, paint)
                }
            }
            "polygon" -> {
                if (s.points.size >= 3) {
                    val path = Path(); val p0 = s.points[0]
                    path.moveTo(p0.first/100f*w, p0.second/100f*h)
                    for (i in 1 until s.points.size) {
                        val p = s.points[i]; path.lineTo(p.first/100f*w, p.second/100f*h)
                    }
                    path.close(); canvas.drawPath(path, paint)
                }
            }
            "line" -> {
                if (s.points.size >= 2) {
                    paint.strokeWidth = maxOf(2f, sw*0.15f)
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(
                        s.points[0].first/100f*w, s.points[0].second/100f*h,
                        s.points[1].first/100f*w, s.points[1].second/100f*h, paint)
                }
            }
            "arc" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = maxOf(2f, sw*0.1f)
                canvas.drawArc(RectF(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2), 0f, 180f, false, paint)
            }
        }
        if (needsRestore) canvas.restore()
    }

    private fun drawWeather(canvas: Canvas, scene: UniversalScene, time: Float) {
        val paint = Paint().apply { isAntiAlias = true }
        when (scene.weather) {
            "rain" -> {
                paint.color = Color.parseColor("#88FFFFFF"); paint.strokeWidth = 2f
                for ((x, y, spd) in rainDrops) {
                    val dy = (y + time*600f*spd/15f) % h
                    canvas.drawLine(x, dy, x-4f, dy+18f, paint)
                }
            }
            "snow" -> {
                paint.color = Color.WHITE
                for ((x, y) in snowflakes) {
                    val sy = (y + time*40f) % h
                    val sx = x + sin(time*1.5f + x*0.01f)*10f
                    paint.alpha = (150 + sin(time + x)*100).toInt().coerceIn(0,255)
                    canvas.drawCircle(sx, sy, 3f, paint)
                }
                paint.alpha = 255
            }
            "fire" -> {
                for (i in 0..60) {
                    val fx = (i * 37f) % w
                    val phase = (time*1.5f + i*0.1f) % 1f
                    val fy = h*0.85f - phase*h*0.4f
                    paint.color = if (i%2==0) Color.argb(((1-phase)*200).toInt().coerceIn(0,255),255,100,0)
                                   else Color.argb(((1-phase)*200).toInt().coerceIn(0,255),255,220,0)
                    canvas.drawCircle(fx, fy, 8f*(1f-phase), paint)
                }
            }
            "sand" -> {
                paint.color = Color.parseColor("#EDC9AF")
                for ((x, y, spd) in rainDrops) {
                    val dy = (y + sin(time*2f + x)*20f) % h
                    canvas.drawCircle((x + time*200f) % w, dy, 2f, paint)
                }
            }
        }
    }

    private fun drawVignette(canvas: Canvas) {
        val paint = Paint().apply {
            shader = RadialGradient(w/2f, h/2f, w*0.9f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.parseColor("#77000000")),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun safeColor(s: String, fallback: Int): Int = try { Color.parseColor(s) } catch(e: Exception) { fallback }
}
