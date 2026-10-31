package com.videogen.offline.engine

data class DrawShape(
    val id: String,
    val shape: String,
    val x: Float, val y: Float,
    val w: Float, val h: Float,
    val rotation: Float,
    val color: String,
    val layer: Int
)

data class Keyframe(
    val time: Float,
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
