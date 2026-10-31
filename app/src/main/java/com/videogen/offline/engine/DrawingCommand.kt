package com.videogen.offline.engine

data class DrawShape(
    val id: String,           // Nama unik: "head", "left_wing", "body", "eye_l"
    val shape: String,        // circle, ellipse, rect, triangle, polygon, line, arc
    val x: Float, val y: Float,
    val w: Float, val h: Float,
    val rotation: Float,      // derajat
    val color: String,
    val layer: Int
)

data class Keyframe(
    val time: Float,          // 0.0 - 1.0
    val shapes: List<DrawShape>
)

data class TemporalScene(
    val prompt: String,
    val environment: String,
    val weather: String,
    val time: String,
    val skyTop: String,
    val skyBottom: String,
    val keyframes: List<Keyframe>
)
