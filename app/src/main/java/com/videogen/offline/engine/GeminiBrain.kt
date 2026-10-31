package com.videogen.offline.engine

import com.google.ai.client.generativeai.GenerativeModel
import org.json.JSONObject

data class ParsedScene(
    val subject: String,
    val action: String,
    val colorHex: String,
    val bodyType: String,      // humanoid, quadruped, vehicle, bird, abstract
    val features: List<String>, // wings, antenna, tail, flame, etc
    val environment: String,    // sky, ocean, space, forest, city, sunset
    val weather: String,        // rain, snow, stars, fire, clear
    val timeOfDay: String       // day, night, sunset
)

class GeminiBrain(apiKey: String) {
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzePrompt(prompt: String): ParsedScene {
        val systemInstruction = """
            Anda adalah mesin penerjemah bahasa ke parameter rendering visual.
            Baca input user dan tentukan parameter berikut:
            
            1. subject: nama objek utama
            2. action: gerakan (berjalan, berlari, terbang, berenang, melayang, menari)
            3. color: hex warna utama objek
            4. body_type: "humanoid", "quadruped", "vehicle", "bird", atau "abstract"
            5. features: array fitur (["wings", "antenna", "tail", "horns", "cape", "sword"])
            6. environment: "sky", "ocean", "space", "forest", "city", "desert", "mountain"
            7. weather: "rain", "snow", "stars", "fire", "clear"
            8. time_of_day: "day", "night", "sunset", "dawn"
            
            Balas HANYA dengan JSON valid tanpa markdown:
            {"subject":"alien","action":"terbang","color":"#00FF00","body_type":"humanoid","features":["wings","antenna"],"environment":"space","weather":"stars","time_of_day":"night"}
        """.trimIndent()

        val response = model.generateContent("$systemInstruction\n\nInput: $prompt")
        val jsonText = response.text ?: throw Exception("AI tidak merespons")

        val cleanJson = jsonText.replace("```json", "").replace("```", "").trim()
        val json = JSONObject(cleanJson)
        
        val featuresArray = json.optJSONArray("features")
        val featuresList = mutableListOf<String>()
        if (featuresArray != null) {
            for (i in 0 until featuresArray.length()) {
                featuresList.add(featuresArray.getString(i))
            }
        }

        return ParsedScene(
            subject = json.optString("subject", "makhluk"),
            action = json.optString("action", "bergerak"),
            colorHex = json.optString("color", "#FFFFFF"),
            bodyType = json.optString("body_type", "abstract"),
            features = featuresList,
            environment = json.optString("environment", "sky"),
            weather = json.optString("weather", "clear"),
            timeOfDay = json.optString("time_of_day", "day")
        )
    }
}
