package com.videogen.offline.engine

import com.google.ai.client.generativeai.GenerativeModel
import org.json.JSONObject

class GeminiBrain(apiKey: String) {
    private val model = GenerativeModel(modelName = "gemini-1.5-flash", apiKey = apiKey)

    suspend fun analyzePrompt(prompt: String): TemporalScene {
        val system = """
            Anda adalah ANIMATOR AI AHLI. Tugas: buat 5 KEYFRAME untuk animasi.

            OUTPUT HANYA JSON VALID (tanpa markdown):
            {
              "environment": "space|ocean|forest|city|mountain|desert|cave|sky",
              "weather": "rain|snow|stars|fire|none|sand",
              "time": "day|night|sunset|dawn",
              "sky_top": "#hex", "sky_bottom": "#hex",
              "keyframes": [
                {"time":0.0,"shapes":[
                  {"id":"head","shape":"circle","x":50,"y":35,"w":20,"h":20,"rotation":0,"color":"#FF0000","layer":5},
                  {"id":"body","shape":"ellipse","x":50,"y":60,"w":30,"h":25,"rotation":0,"color":"#FF0000","layer":4}
                ]},
                {"time":0.25,"shapes":[ SAMA ID TAPI POSISI/ROTASI BEDA ]},
                {"time":0.5,"shapes":[...]},
                {"time":0.75,"shapes":[...]},
                {"time":1.0,"shapes":[...]}
              ]
            }

            ATURAN PALING PENTING:
            - SEMUA 5 keyframe HARUS memiliki ID yang SAMA PERSIS (karakter konsisten).
            - Bedakan pose: misal "left_wing" punya rotation -30 di t=0, +30 di t=0.5, -30 di t=1.0.
            - Untuk MATA berkedip: "eye_l" h=8 di t=0, h=1 di t=0.5 (kedip), h=8 di t=1.
            - Untuk MULUT: bentuk arc berubah dari senyum ke buka ke senyum.
            - Untuk BERJALAN: kaki bergantian maju-mundur (walk cycle).
            - Untuk TERBANG: seluruh badan naik-turun (sin wave).
            - Koordinat 0-100. (50,50)=tengah. Tanah=75. Terbang=30-45.
            - WAJIB ada: kepala, badan, dan minimal 2 detail (mata, kaki, sayap, ekor).
            - Bentuk harus jelas: NAGA=badan+sayap besar+ekor panjang+tanduk. 
              ROBOT=kotak+kotak+kotak+antena+lampu mata. 
              BURUNG=oval+2sayap+paruh+ekor. KUCING=badan+4kaki+kepala+2telinga+ekor.
            - MINIMAL 15 bentuk per keyframe, MAKSIMAL 30.
            - JANGAN lupa bentuk dekorasi latar di layer 0-1 (bintang, pohon, gunung).
        """.trimIndent()

        val response = model.generateContent("$system\n\nPermintaan: $prompt")
        val text = response.text ?: throw Exception("AI tidak merespons")
        val clean = text.replace("```json","").replace("```","").trim()
        val json = JSONObject(clean)
        val kfArray = json.getJSONArray("keyframes")
        val keyframes = mutableListOf<Keyframe>()

        for (k in 0 until kfArray.length()) {
            val kfJson = kfArray.getJSONObject(k)
            val shapesJson = kfJson.getJSONArray("shapes")
            val shapes = mutableListOf<DrawShape>()
            for (i in 0 until shapesJson.length()) {
                val s = shapesJson.getJSONObject(i)
                shapes.add(DrawShape(
                    id = s.optString("id", "shape_$i"),
                    shape = s.optString("shape", "circle"),
                    x = s.optDouble("x", 50.0).toFloat(),
                    y = s.optDouble("y", 50.0).toFloat(),
                    w = s.optDouble("w", 10.0).toFloat(),
                    h = s.optDouble("h", 10.0).toFloat(),
                    rotation = s.optDouble("rotation", 0.0).toFloat(),
                    color = s.optString("color", "#FFFFFF"),
                    layer = s.optInt("layer", 5)
                ))
            }
            keyframes.add(Keyframe(time = kfJson.optDouble("time", k/4.0).toFloat(), shapes = shapes))
        }

        keyframes.sortBy { it.time }

        return TemporalScene(
            prompt = prompt,
            environment = json.optString("environment","sky"),
            weather = json.optString("weather","none"),
            time = json.optString("time","day"),
            skyTop = json.optString("sky_top","#4A90E2"),
            skyBottom = json.optString("sky_bottom","#B0E0E6"),
            keyframes = keyframes
        )
    }
}
