package com.videogen.offline.engine

data class DrawShape(
    val shape: String,           // circle, ellipse, rect, triangle, line, polygon, arc
    val x: Float,                // 0-100 (center)
    val y: Float,                // 0-100 (center)
    val w: Float,                // 0-100
    val h: Float,                // 0-100
    val color: String,           // #RRGGBB
    val layer: Int,              // draw order (low = back)
    val animation: String,       // static, flap, sway, breathe, walk, float, rotate, blink
    val points: List<Pair<Float, Float>> = emptyList()
)

data class UniversalScene(
    val environment: String,
    val weather: String,
    val time: String,
    val skyTop: String,
    val skyBottom: String,
    val shapes: List<DrawShape>
)
