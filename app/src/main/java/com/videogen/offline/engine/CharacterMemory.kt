package com.videogen.offline.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CharacterMemory(context: Context) {
    private val prefs = context.getSharedPreferences("CHARACTER_MEMORY", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(scene: TemporalScene) {
        val hash = scene.prompt.lowercase().trim().hashCode().toString()
        val json = gson.toJson(scene)
        prefs.edit().putString(hash, json).apply()
    }

    fun load(prompt: String): TemporalScene? {
        val hash = prompt.lowercase().trim().hashCode().toString()
        val json = prefs.getString(hash, null) ?: return null
        return try { gson.fromJson(json, TemporalScene::class.java) } catch (e: Exception) { null }
    }

    fun listAll(): List<String> {
        return prefs.all.keys.toList()
    }
}
