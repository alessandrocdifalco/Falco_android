package com.alessandro.falco.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.alessandro.falco.audio.PcmExtractor
import com.alessandro.falco.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.FloatBuffer

data class MaestStyle(val label: String, val score: Float)
data class MaestPrediction(val styles: List<MaestStyle>, val genre: String?, val subgenre: String?, val confidence: Int, val elapsedMs: Long)

class MaestEngine(private val context: Context) {
    private val store = MaestModelStore(context)
    suspend fun analyze(track: TrackEntity, authorization: String?): MaestPrediction = withContext(Dispatchers.Default) {
        check(store.state().installed) { "Installa prima il modello MAEST" }
        val started = System.currentTimeMillis()
        val audio = PcmExtractor(context).centerMono16k(track, authorization)
        val input = MaestSpectrogram.create(audio)
        val labels = loadLabels()
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4); setInterOpNumThreads(1); setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT) }
        env.createSession(store.modelFile.absolutePath, options).use { session ->
            val inputName = session.inputNames.first()
            val predictionOutput = session.outputInfo.entries.firstOrNull { (name, node) ->
                val info = node.info as? TensorInfo; info?.shape?.lastOrNull() == labels.size.toLong() && name.contains("13")
            }?.key ?: session.outputInfo.entries.lastOrNull { (_, node) -> (node.info as? TensorInfo)?.shape?.lastOrNull() == labels.size.toLong() }?.key
                ?: error("Output probabilità MAEST non trovato")
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, MaestSpectrogram.FRAMES.toLong(), MaestSpectrogram.BANDS.toLong())).use { tensor ->
                session.run(mapOf(inputName to tensor), setOf(predictionOutput)).use { result ->
                    val raw = result.get(predictionOutput).orElseThrow { IllegalStateException("Risultato MAEST assente") }.value
                    val scores = when (raw) { is Array<*> -> raw.firstOrNull() as? FloatArray; is FloatArray -> raw; else -> null } ?: error("Formato risultato MAEST non supportato")
                    val styles = scores.indices.sortedByDescending { scores[it] }.take(5).map { MaestStyle(labels.getOrElse(it) { "Stile $it" }, scores[it]) }
                    val mapped = mapTaxonomy(styles.firstOrNull()?.label.orEmpty())
                    MaestPrediction(styles, mapped.first, mapped.second, ((styles.firstOrNull()?.score ?: 0f) * 100).toInt().coerceIn(0, 100), System.currentTimeMillis() - started)
                }
            }
        }
    }

    private suspend fun loadLabels(): List<String> { val json = JSONObject(store.ensureMetadata().readText()); val array = json.getJSONArray("classes"); return List(array.length()) { array.getString(it) } }
    private fun mapTaxonomy(label: String): Pair<String?, String?> {
        val style = label.substringAfter("---", label).uppercase().replace('-', '_').replace(' ', '_')
        return when {
            style in setOf("HOUSE","CLUB_HOUSE","CLASSIC_HOUSE","PIANO_HOUSE","VOCAL_HOUSE","SOULFUL_HOUSE","FUNKY_HOUSE","JACKIN_HOUSE","GARAGE_HOUSE") -> "HOUSE" to style
            style.contains("DISCO") || style == "BOOGIE" -> "DISCO_NU_DISCO" to when { style == "NU_DISCO" -> "NU_DISCO"; style.contains("ITALO") -> "ITALO_DISCO"; style == "BOOGIE" -> "BOOGIE"; else -> "CLASSIC_DISCO" }
            style in setOf("DEEP_HOUSE","DOWNTEMPO","AMBIENT","CHILLWAVE","LOUNGE","DUB_TECHNO") -> "DEEP_LOUNGE" to if (style == "AMBIENT") "AMBIENT_HOUSE" else style
            style.contains("AFRO") || style in setOf("LATIN","TRIBAL","TRIBAL_HOUSE","AMAPIANO") -> "AFRO_LATIN" to when { style.contains("AFRO") -> "AFRO_HOUSE"; style.contains("TRIBAL") -> "TRIBAL_HOUSE"; else -> "LATIN_HOUSE" }
            style.contains("TECH") || style in setOf("MINIMAL","PROGRESSIVE_HOUSE","INDIE_DANCE","TECHNO") -> "TECH_CLUB" to when { style == "MINIMAL_TECHNO" -> "MINIMAL"; style == "DEEP_TECHNO" -> "DEEP_TECH"; else -> style }
            style.contains("POP") || style in setOf("REGGAETON","DANCEHALL","EURODANCE") -> "POP_PARTY" to when { style == "REGGAETON" -> "REGGAETON"; style == "DANCEHALL" -> "DANCEHALL"; else -> "POP_DANCE" }
            else -> "ALTRO" to when { style.contains("HIP_HOP") -> "HIP_HOP"; style.contains("R&B") -> "RNB"; style.contains("FUNK") -> "FUNK"; style.contains("SOUL") -> "SOUL"; else -> "ELECTRONIC" }
        }
    }
}
