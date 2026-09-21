package `in`.cashlessconsumer.zovoice.update

import org.json.JSONArray
import org.json.JSONObject

/** A GitHub release with an installable APK asset. */
data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val notesHead: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** In-app updater state machine. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Ready(val apkPath: String) : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Pure update logic — JVM-testable, no Android framework classes. */
object UpdateLogic {

    /**
     * True when [latest] is strictly newer than [current]. Dotted numeric versions with an
     * optional leading "v"; segments are compared numerically and shorter versions are
     * zero-padded. Non-numeric segments make the comparison conservative (returns false).
     */
    fun isNewer(current: String, latest: String): Boolean {
        val c = parseSegments(current) ?: return false
        val l = parseSegments(latest) ?: return false
        for (i in 0 until maxOf(c.size, l.size)) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    /**
     * Parse the body of `GET /repos/{owner}/{repo}/releases/latest`. Returns null when the
     * payload has no tag or no .apk asset (drafts/prereleases filtered by the endpoint itself).
     */
    fun parseLatestRelease(body: String): ReleaseInfo? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val tag = root.optString("tag_name").orEmpty()
        if (tag.isBlank()) return null
        val assets = root.optJSONArray("assets") ?: return null
        val apk = pickApkAsset(assets) ?: return null
        val name = root.optString("name").orEmpty().ifBlank { tag }
        val notesHead = root.optString("body").orEmpty()
        return ReleaseInfo(tagName = tag, name = name, notesHead = notesHead, apkUrl = apk.first, apkSize = apk.second)
    }

    /** Prefer `*-debug.apk` (our release convention), else the first `.apk`. */
    fun pickApkAsset(assets: JSONArray): Pair<String, Long>? {
        var fallback: Pair<String, Long>? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val apkName = a.optString("name").orEmpty()
            if (!apkName.endsWith(".apk", ignoreCase = true)) continue
            val url = a.optString("browser_download_url").orEmpty()
            if (url.isBlank()) continue
            val size = a.optLong("size", 0L)
            val lower = apkName.lowercase()
            if (lower.endsWith("-debug.apk")) return url to size
            if (fallback == null) fallback = url to size
        }
        return fallback
    }

    private fun parseSegments(v: String): List<Int>? {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isEmpty()) return null
        val out = ArrayList<Int>()
        for (seg in cleaned.split('.')) {
            val digits = seg.takeWhile { it.isDigit() }
            if (digits.isEmpty() || digits.length != seg.length) return null
            out.add(digits.toInt())
        }
        return out
    }
}
