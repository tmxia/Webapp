package com.webtoapp.core.update

import com.webtoapp.core.i18n.AppLanguage
import com.webtoapp.core.i18n.Strings
import com.webtoapp.core.logging.AppLogger
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object UpdateChecker {

    private const val TAG = "UpdateChecker"

    private const val OWNER = "shiaho777"
    private const val REPO = "web-to-app"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val ALL_RELEASES_API =
        "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=100"

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 10000

    



    private const val RACE_TIMEOUT_MS = 12000L

    




    private val raceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    






    private const val ZH_MARKER = "<!-- zh-CN -->"

    




    private fun localizeReleaseBody(body: String): String {
        val raw = body.trim()
        val idx = raw.indexOf(ZH_MARKER)
        if (idx < 0) return raw
        val english = raw.substring(0, idx).trim()
        val chinese = raw.substring(idx + ZH_MARKER.length).trim()
        return if (Strings.currentLanguage.value == AppLanguage.CHINESE && chinese.isNotBlank()) {
            chinese
        } else {
            english
        }
    }

    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun toString(): String = "$major.$minor.$patch"

        override fun compareTo(other: Version): Int {
            if (major != other.major) return major - other.major
            if (minor != other.minor) return minor - other.minor
            return patch - other.patch
        }

        companion object {
            fun parse(raw: String): Version? {
                val core = raw.trim().removePrefix("v").removePrefix("V")
                    .substringBefore('-').substringBefore('+')
                val parts = core.split('.')
                val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                return Version(major, minor, patch)
            }
        }
    }

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val sha256: String?,
        val releaseNotes: String
    )

    






    data class ReleaseSummary(
        val tag: String,
        val version: String,
        val name: String,
        val publishedAt: String,
        val body: String,
        val downloadUrl: String?,
        val sizeBytes: Long,
        val sha256: String?
    )

    sealed class Result {
        data class UpdateAvailable(val info: ReleaseInfo, val currentVersion: String) : Result()
        data class UpToDate(val version: String) : Result()
        data class Failed(val message: String, val throwable: Throwable? = null) : Result()
    }

    suspend fun check(currentVersionName: String): Result = withContext(Dispatchers.IO) {
        val current = Version.parse(currentVersionName)
            ?: return@withContext Result.Failed("Cannot parse current version: $currentVersionName")

        try {
            val json = fetchLatestReleaseJson()
                ?: return@withContext Result.Failed("Empty response from release API")

            val release = JSONObject(json)
            val tag = release.optString("tag_name").ifBlank { release.optString("name") }
            val latest = Version.parse(tag)
                ?: return@withContext Result.Failed("Cannot parse release tag: $tag")

            if (latest <= current) {
                return@withContext Result.UpToDate(current.toString())
            }

            val asset = pickBestApkAsset(release)
                ?: return@withContext Result.Failed("No APK asset found in release $tag")

            val rawUrl = asset.optString("browser_download_url")
            if (rawUrl.isBlank()) {
                return@withContext Result.Failed("Release asset has no download URL")
            }

            val info = ReleaseInfo(
                version = latest.toString(),
                
                
                
                downloadUrl = rawUrl,
                sizeBytes = asset.optLong("size", 0L),
                sha256 = asset.optString("digest").takeIf { it.isNotBlank() }
                    ?.substringAfter("sha256:", "")?.takeIf { it.isNotBlank() },
                releaseNotes = localizeReleaseBody(release.optString("body"))
            )
            Result.UpdateAvailable(info, current.toString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Update check failed", e)
            Result.Failed(e.message ?: "Update check failed", e)
        }
    }

    







    suspend fun fetchAllReleases(): List<ReleaseSummary> = withContext(Dispatchers.IO) {
        try {
            
            
            val json = fetchAllReleasesJson()
                ?: throw IllegalStateException("Empty response from release API")
            val arr = org.json.JSONArray(json)
            val out = ArrayList<ReleaseSummary>(arr.length())
            for (i in 0 until arr.length()) {
                val release = arr.optJSONObject(i) ?: continue
                val tag = release.optString("tag_name").ifBlank { release.optString("name") }
                val version = Version.parse(tag)?.toString() ?: tag
                val asset = pickBestApkAsset(release)
                val rawUrl = asset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
                out.add(
                    ReleaseSummary(
                        tag = tag,
                        version = version,
                        name = release.optString("name").trim(),
                        publishedAt = release.optString("published_at").trim(),
                        body = localizeReleaseBody(release.optString("body")),
                        downloadUrl = rawUrl,
                        sizeBytes = asset?.optLong("size", 0L) ?: 0L,
                        sha256 = asset?.optString("digest").takeIf { !it.isNullOrBlank() }
                            ?.substringAfter("sha256:", "")?.takeIf { it.isNotBlank() }
                    )
                )
            }
            
            out.sortedWith(compareByDescending { Version.parse(it.version) ?: Version(0, 0, 0) })
        } catch (e: Exception) {
            AppLogger.e(TAG, "Fetch all releases failed", e)
            throw e
        }
    }

    



    data class RepoSummary(
        val name: String,
        val description: String,
        val stars: Long,
        val forks: Long,
        val language: String?,
        val pushedAt: String,
        val url: String
    )

    private const val AUTHOR_REPOS_API =
        "https://api.github.com/users/$OWNER/repos?per_page=100&type=owner"

    






    private const val AUTHOR_REPOS_RAW =
        "https://raw.githubusercontent.com/$OWNER/$REPO/author-repos-data/repos.json"

    



    suspend fun fetchAuthorRepos(context: android.content.Context): List<RepoSummary> =
        withContext(Dispatchers.IO) {
            try {
                val json = fetchJsonRaced(authorReposCandidates()) { it.trimStart().startsWith("[") }
                    ?: throw IllegalStateException("Empty response from repos API")
                AuthorReposCache.write(context, json)
                parseAuthorRepos(json)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Fetch author repos failed", e)
                throw e
            }
        }

    




    private fun authorReposCandidates(): List<String> {
        val channels = com.webtoapp.core.network.CnMirrorProbe.peekChannels()
        return (channels.map { it.rewrite(AUTHOR_REPOS_RAW) } +
            AUTHOR_REPOS_RAW +
            channels.map { it.rewrite(AUTHOR_REPOS_API) } +
            AUTHOR_REPOS_API).distinct()
    }

    internal fun parseAuthorRepos(json: String): List<RepoSummary> {
        val arr = org.json.JSONArray(json)
        val out = ArrayList<RepoSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val repo = arr.optJSONObject(i) ?: continue
            if (repo.optBoolean("fork", false)) continue
            val name = repo.optString("name").trim()
            if (name.isBlank() || name.equals(REPO, ignoreCase = true)) continue
            
            
            val rawDesc = repo.opt("description")
            val rawLang = repo.opt("language")
            out.add(
                RepoSummary(
                    name = name,
                    description = if (rawDesc == null || rawDesc == JSONObject.NULL) "" else rawDesc.toString().trim(),
                    stars = repo.optLong("stargazers_count", 0L),
                    forks = repo.optLong("forks_count", 0L),
                    language = if (rawLang == null || rawLang == JSONObject.NULL) null else rawLang.toString(),
                    pushedAt = repo.optString("pushed_at").trim(),
                    url = repo.optString("html_url").trim()
                )
            )
        }
        return out
    }

    private fun pickBestApkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        var best: JSONObject? = null
        var bestVersion: Version? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val assetVersion = Version.parse(name.removeSuffix(".APK").removeSuffix(".apk").substringAfterLast('-'))
            if (best == null || (assetVersion != null && bestVersion != null && assetVersion > bestVersion) ||
                (assetVersion != null && bestVersion == null)) {
                best = asset
                bestVersion = assetVersion
            }
        }
        return best
    }

    private suspend fun fetchLatestReleaseJson(): String? =
        
        
        
        fetchJsonRaced(LATEST_RELEASE_API) { it.trimStart().startsWith("{") }

    private suspend fun fetchAllReleasesJson(): String? =
        
        fetchJsonRaced(ALL_RELEASES_API) { it.trimStart().startsWith("[") }

    






    private suspend fun fetchJsonRaced(apiUrl: String, accept: (String) -> Boolean): String? {
        val urls = (com.webtoapp.core.network.CnMirrorProbe.peekChannels()
            .map { it.rewrite(apiUrl) } + apiUrl).distinct()
        return fetchJsonRaced(urls, accept)
    }

    private suspend fun fetchJsonRaced(urls: List<String>, accept: (String) -> Boolean): String? {
        val results = Channel<Pair<String, String?>>(urls.size)
        val jobs = urls.map { endpoint ->
            raceScope.launch {
                val body = runCatching { httpGet(endpoint) }
                    .onFailure {
                        AppLogger.w(TAG, "API request failed via $endpoint: ${it.message}")
                    }
                    .getOrNull()
                results.send(endpoint to body)
            }
        }
        var winner: String? = null
        var remaining = urls.size
        withTimeoutOrNull(RACE_TIMEOUT_MS) {
            while (remaining > 0 && winner == null) {
                remaining--
                val (endpoint, body) = results.receive()
                if (body != null && accept(body)) {
                    AppLogger.d(TAG, "API request won via $endpoint")
                    winner = body
                } else if (body != null) {
                    AppLogger.w(TAG, "API returned non-JSON via $endpoint: ${body.take(80)}")
                }
            }
        }
        jobs.forEach { it.cancel() }
        return winner
    }

    private fun httpGet(endpoint: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "WebToApp-UpdateChecker")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $endpoint")
            }
            return connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            connection?.disconnect()
        }
    }
}
