package com.videogen.offline.engine

import android.graphics.*
import kotlin.random.Random

class TemporalRenderer(private val w: Int, private val h: Int) {
    private val stars = List(120) { Triple(Random.nextFloat()*w, Random.nextFloat()*h*0.7f, Random.nextFloat()*2f+0.5f) }
    private val rainDrops = List(200) { Triple(Random.nextFloat()*w, Random.nextFloat()*h, Random.nextFloat()*10f+15f) }

    fun renderFrame(scene: TemporalScene, timeNorm: Float, frameCount: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val time = frameCount / 30f
        drawSky(canvas, scene)
        drawBackground(canvas, scene, time)
        val shapes = interpolateShapes(scene, timeNorm)
        for (shape in shapes.sortedBy { it.layer }) drawShape(canvas, shape)
        drawWeather(canvas, scene, time)
        drawVignette(canvas)
        return bmp
    }

    private fun interpolateShapes(scene: TemporalScene, t: Float): List<DrawShape> {
        if (scene.keyframes.isEmpty()) return emptyList()
        if (scene.keyframes.size == 1) return scene.keyframes[0].shapes
        var kf0 = scene.keyframes[0]
        var kf1 = scene.keyframes.last()
        for (i in 0 until scene.keyframes.size - 1) {
            if (t >= scene.keyframes[i].time && t <= scene.keyframes[i+1].time) {
                kf0 = scene.keyframes[i]; kf1 = scene.keyframes[i+1]; break
            }
        }
        val span = kf1.time - kf0.time
        val localT = if (span < 0.001f) 0f else ((t - kf0.time) / span).coerceIn(0f, 1f)
        val result = mutableListOf<DrawShape>()
        val map0 = kf0.shapes.associateBy { it.id }
        val map1 = kf1.shapes.associateBy { it.id }
        for (id in map0.keys) {
            val s0 = map0[id] ?: continue
            val s1 = map1[id] ?: s0
            result.add(DrawShape(
                id = id, shape = s0.shape,
                x = lerp(s0.x, s1.x, localT),
                y = lerp(s0.y, s1.y, localT),
                w = lerp(s0.w, s1.w, localT),
                h = lerp(s0.h, s1.h, localT),
                rotation = lerp(s0.rotation, s1.rotation, localT),
                color = blendColor(s0.color, s1.color, localT),
                layer = s0.layer
            ))
        }
        return result
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun blendColor(c0: String, c1: String, t: Float): String {
        return try {
            val a = Color.parseColor(c0); val b = Color.parseColor(c1)
            String.format("#%02X%02X%02X",
                (Color.red(a) + (Color.red(b)-Color.red(a))*t).toInt().coerceIn(0,255),
                (Color.green(a) + (Color.green(b)-Color.green(a))*t).toInt().coerceIn(0,255),
                (Color.blue(a) + (Color.blue(b)-Color.blue(a))*t).toInt().coerceIn(0,255))
        } catch (e: Exception) { c0 }
    }

    private fun drawSky(canvas: Canvas, scene: TemporalScene) {
        val p = Paint()
        val top = safeColor(scene.skyTop, Color.parseColor("#4A90E2"))
        val bot = safeColor(scene.skyBottom, Color.parseColor("#B0E0E6"))
        p.shader = LinearGradient(0f,0f,0f,h.toFloat(), top, bot, Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(), p); p.shader = null
    }

    private fun drawBackground(canvas: Canvas, scene: TemporalScene, time: Float) {
        if (scene.time == "night" || scene.weather == "stars") {
            val p = Paint().apply { color = Color.WHITE }
            for ((x,y,r) in stars) {
                p.alpha = (kotlin.math.sin(time*3f+x*0.01f)*127+128).toInt().coerceIn(0,255)
                canvas.drawCircle(x,y,r,p)
            }
            p.alpha = 255
        }
        val sunP = Paint().apply { isAntiAlias = true }
        val sx = w*0.85f; val sy = h*0.18f
        if (scene.time == "night") {
            sunP.shader = RadialGradient(sx,sy,120f, intArrayOf(Color.WHITE, Color.TRANSPARENT), floatArrayOf(0.3f,1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(sx,sy,120f,sunP); sunP.shader = null
            sunP.color = Color.parseColor("#E8E8E8"); canvas.drawCircle(sx,sy,40f,sunP)
        } else {
            sunP.shader = RadialGradient(sx,sy,180f, intArrayOf(Color.parseColor("#FFF8DC"), Color.TRANSPARENT), floatArrayOf(0.3f,1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(sx,sy,180f,sunP); sunP.shader = null
            sunP.color = Color.parseColor("#FFD700"); canvas.drawCircle(sx,sy,45f,sunP)
        }
    }

    private fun drawShape(canvas: Canvas, s: DrawShape) {
        val paint = Paint().apply { isAntiAlias = true; color = safeColor(s.color, Color.WHITE) }
        val cx = s.x/100f*w; val cy = s.y/100f*h
        val sw = s.w/100f*w; val sh = s.h/100f*h
        canvas.save()
        canvas.rotate(s.rotation, cx, cy)
        when (s.shape) {
            "circle" -> canvas.drawCircle(cx, cy, sw/2f, paint)
            "ellipse" -> canvas.drawOval(RectF(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2), paint)
            "rect" -> canvas.drawRect(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2, paint)
            "triangle" -> {
                val path = Path()
                path.moveTo(cx, cy-sh/2); path.lineTo(cx+sw/2, cy+sh/2); path.lineTo(cx-sw/2, cy+sh/2); path.close()
                canvas.drawPath(path, paint)
            }
            "line" -> {
                paint.strokeWidth = maxOf(2f, sw*0.15f); paint.style = Paint.Style.STROKE
                canvas.drawLine(cx-sw/2, cy, cx+sw/2, cy, paint)
            }
            "arc" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = maxOf(2f, sw*0.1f)
                canvas.drawArc(RectF(cx-sw/2, cy-sh/2, cx+sw/2, cy+sh/2), 0f, 180f, false, paint)
            }
        }
        canvas.restore()
    }

    private fun drawWeather(canvas: Canvas, scene: TemporalScene, time: Float) {
        val paint = Paint().apply { isAntiAlias = true }
        when (scene.weather) {
            "rain" -> {
                paint.color = Color.parseColor("#88FFFFFF"); paint.strokeWidth = 2f
                for ((x,y,spd) in rainDrops) {
                    val dy = (y + time*600f*spd/15f) % h
                    canvas.drawLine(x, dy, x-4f, dy+18f, paint)
                }
            }
            "snow" -> {
                paint.color = Color.WHITE
                for ((x,y,_) in rainDrops.take(100)) {
                    val sy = (y + time*40f) % h
                    val sx = x + kotlin.math.sin(time*1.5f+x*0.01f)*10f
                    paint.alpha = (150+kotlin.math.sin(time+x)*100).toInt().coerceIn(0,255)
                    canvas.drawCircle(sx,sy,3f,paint)
                }
                paint.alpha = 255
            }
            "fire" -> {
                for (i in 0..60) {
                    val fx = (i*37f)%w
                    val ph = (time*1.5f+i*0.1f)%1f
                    val fy = h*0.85f - ph*h*0.4f
                    paint.color = if (i%2==0) Color.argb(((1-ph)*200).toInt().coerceIn(0,255),255,100,0)
                                   else Color.argb(((1-ph)*200).toInt().coerceIn(0,255),255,220,0)
                    canvas.drawCircle(fx,fy,8f*(1f-ph),paint)
                }
            }
        }
    }

    private fun drawVignette(canvas: Canvas) {
        val p = Paint().apply {
            shader = RadialGradient(w/2f,h/2f,w*0.9f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.parseColor("#77000000")),
                floatArrayOf(0f,0.55f,1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),p)
    }

    private fun safeColor(s: String, fb: Int): Int = try { Color.parseColor(s) } catch(e:Exception) { fb }
}
