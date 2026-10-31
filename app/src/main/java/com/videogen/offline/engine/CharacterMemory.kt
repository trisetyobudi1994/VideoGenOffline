package com.videogen.offline.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CharacterMemory(context: Context) {
    private val prefs = context.getSharedPreferences("CHARACTER_MEMORY", Context.MODE_PRIVATE)

    fun save(scene: TemporalScene) {
        val hash = scene.prompt.lowercase().trim().hashCode().toString()
        prefs.edit().putString(hash, sceneToJson(scene).toString()).apply()
    }

    fun load(prompt: String): TemporalScene? {
        val hash = prompt.lowercase().trim().hashCode().toString()
        val json = prefs.getString(hash, null) ?: return null
        return try { jsonToScene(JSONObject(json)) } catch (e: Exception) { null }
    }

    private fun sceneToJson(scene: TemporalScene): JSONObject {
        val json = JSONObject()
        json.put("prompt", scene.prompt)
        json.put("environment", scene.environment)
        json.put("weather", scene.weather)
        json.put("time", scene.time)
        json.put("skyTop", scene.skyTop)
        json.put("skyBottom", scene.skyBottom)
        val kfArray = JSONArray()
        for (kf in scene.keyframes) {
            val kfJson = JSONObject()
            kfJson.put("time", kf.time)
            val shapesArray = JSONArray()
            for (s in kf.shapes) {
                val sJson = JSONObject()
                sJson.put("id", s.id)
                sJson.put("shape", s.shape)
                sJson.put("x", s.x); sJson.put("y", s.y)
                sJson.put("w", s.w); sJson.put("h", s.h)
                sJson.put("rotation", s.rotation)
                sJson.put("color", s.color)
                sJson.put("layer", s.layer)
                shapesArray.put(sJson)
            }
            kfJson.put("shapes", shapesArray)
            kfArray.put(kfJson)
        }
        json.put("keyframes", kfArray)
        return json
    }

    private fun jsonToScene(json: JSONObject): TemporalScene {
        val kfArray = json.getJSONArray("keyframes")
        val keyframes = mutableListOf<Keyframe>()
        for (k in 0 until kfArray.length()) {
            val kfJson = kfArray.getJSONObject(k)
            val shapesArray = kfJson.getJSONArray("shapes")
            val shapes = mutableListOf<DrawShape>()
            for (i in 0 until shapesArray.length()) {
                val s = shapesArray.getJSONObject(i)
                shapes.add(DrawShape(
                    id = s.getString("id"),
                    shape = s.getString("shape"),
                    x = s.getDouble("x").toFloat(),
                    y = s.getDouble("y").toFloat(),
                    w = s.getDouble("w").toFloat(),
                    h = s.getDouble("h").toFloat(),
                    rotation = s.getDouble("rotation").toFloat(),
                    color = s.getString("color"),
                    layer = s.getInt("layer")
                ))
            }
            keyframes.add(Keyframe(time = kfJson.getDouble("time").toFloat(), shapes = shapes))
        }
        return TemporalScene(
            prompt = json.getString("prompt"),
            environment = json.getString("environment"),
            weather = json.getString("weather"),
            time = json.getString("time"),
            skyTop = json.getString("skyTop"),
            skyBottom = json.getString("skyBottom"),
            keyframes = keyframes
        )
    }
}
