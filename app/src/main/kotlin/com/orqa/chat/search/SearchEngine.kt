package com.orqa.chat.search

import com.orqa.chat.data.ChunkDao
import com.orqa.chat.data.ChunkEntity
import kotlin.math.ln

data class SearchResult(
    val chunk: ChunkEntity,
    val score: Float,
    val matchedTerms: List<String>
)

// ── Category detection ────────────────────────────────────────────

object CategoryMap {
    val map = mapOf(
        "wiring"     to listOf("wire","solder","uart","pad","tx","rx","connect","pinout","gnd","ground","vcc"),
        "motors"     to listOf("motor","prop","propeller","vibrat","desync","spin","thrust","kv","stator"),
        "escs"       to listOf("esc","blheli","dshot","amp","mosfet","throttle","protocol","besc"),
        "video"      to listOf("vtx","video","fpv","camera","osd","antenna","signal","range","channel","freq"),
        "radio"      to listOf("receiver","bind","elrs","crsf","crossfire","sbus","failsafe","telemetry","rssi"),
        "gps"        to listOf("gps","compass","heading","toilet","position","rtk","glonass","beidou","mag"),
        "battery"    to listOf("battery","lipo","voltage","cell","puffy","charge","sag","mah","capacity"),
        "firmware"   to listOf("betaflight","inav","ardupilot","px4","flash","configurator","cli","target"),
        "pid"        to listOf("pid","tune","oscillat","wobble","filter","gyro","rates","expo","p gain"),
        "orqa"       to listOf("orqa","quadcore","wingcore","3030","h743","h503","osd","fc-1008","fc-1011","fc-1420"),
        "crash"      to listOf("crash","broke","repair","damage","impact","bent","broken","cracked","burnt"),
        "build"      to listOf("build","recommend","best","compare","which","choose","setup","stack","frame"),
        "compliance" to listOf("ndaa","blue uas","itar","export","dod","defense","faa","waiver"),
        "platform"   to listOf("platform","mavic","skydio","parrot","autel","dji","inspire","matrice"),
        "frame"      to listOf("frame","arm","crack","carbon","mount","standoff","prop size"),
    )

    fun detect(query: String): String {
        val q = query.lowercase()
        var best = "" to 0
        for ((cat, keywords) in map) {
            val score = keywords.count { it in q }
            if (score > best.second) best = cat to score
        }
        return if (best.second > 0) best.first else ""
    }
}

// ── BM25 search ───────────────────────────────────────────────────

class SearchEngine(private val chunkDao: ChunkDao) {

    companion object {
        private const val K1   = 1.2f
        private const val B    = 0.75f
        private const val TOP_K = 12
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9_\\-]+")).filter { it.length > 1 }

    suspend fun search(query: String, k: Int = TOP_K): List<SearchResult> {
        val queryTerms = tokenize(query).toSet()
        if (queryTerms.isEmpty()) return emptyList()

        val category = CategoryMap.detect(query)

        // Fetch candidate chunks for each term (Room does simple LIKE)
        val candidateMap = mutableMapOf<Long, ChunkEntity>()
        for (term in queryTerms.take(8)) { // cap at 8 terms to avoid too many queries
            chunkDao.searchByTerm(term).forEach { candidateMap[it.id] = it }
        }

        if (candidateMap.isEmpty()) return emptyList()

        val chunks    = candidateMap.values.toList()
        val n         = chunks.size.toFloat()
        val avgLen    = chunks.map { it.text.split(" ").size }.average().toFloat().coerceAtLeast(1f)

        // Score each chunk
        val scored = chunks.map { chunk ->
            val chunkTerms  = tokenize(chunk.text)
            val chunkLen    = chunkTerms.size.toFloat().coerceAtLeast(1f)
            val termFreqs   = chunkTerms.groupingBy { it }.eachCount()
            val matched     = mutableListOf<String>()

            var score = 0f
            for (term in queryTerms) {
                val tf = termFreqs[term] ?: 0
                if (tf == 0) continue
                matched.add(term)

                // IDF
                val docsWithTerm = chunks.count { tokenize(it.text).contains(term) }.toFloat()
                val idf = ln((n - docsWithTerm + 0.5f) / (docsWithTerm + 0.5f) + 1f)

                // BM25 TF
                val tfScore = (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * chunkLen / avgLen))

                score += idf * tfScore
            }

            // Category boost
            if (category.isNotEmpty()) {
                val src = chunk.source.lowercase() + " " + chunk.folder.lowercase()
                if (category in src) score *= 1.4f
            }

            // Wingman KB boost
            val src = chunk.source.lowercase()
            if ("fallback_kb" in src || "wiring_kb" in src || "orqa_kb" in src) score *= 1.2f

            SearchResult(chunk, score, matched)
        }

        return scored
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(k)
    }
}
