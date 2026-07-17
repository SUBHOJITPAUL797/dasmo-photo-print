package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private val client = OkHttpClient()

    private const val PREF_GITHUB_OWNER = "update_github_owner"
    private const val PREF_GITHUB_REPO = "update_github_repo"
    private const val DEFAULT_OWNER = "SUBHOJITPAUL797"
    private const val DEFAULT_REPO = "dasmo-photo-print"

    fun getGithubOwner(context: Context): String {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREF_GITHUB_OWNER, DEFAULT_OWNER) ?: DEFAULT_OWNER
    }

    fun getGithubRepo(context: Context): String {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREF_GITHUB_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
    }

    fun saveGithubConfig(context: Context, owner: String, repo: String) {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_GITHUB_OWNER, owner.trim())
            .putString(PREF_GITHUB_REPO, repo.trim())
            .apply()
    }

    class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val browserUrl: String
    )

    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val owner = getGithubOwner(context)
        val repo = getGithubRepo(context)
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"

        val currentVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "DASMO-Update-Checker")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch update info: ${response.code}")
                    return@withContext UpdateInfo(false, currentVersion, currentVersion, "", "", "")
                }

                val bodyString = response.body?.string() ?: ""
                if (bodyString.isEmpty()) {
                    return@withContext UpdateInfo(false, currentVersion, currentVersion, "", "", "")
                }

                val json = JSONObject(bodyString)
                val tagName = json.optString("tag_name", "").trim()
                val browserUrl = json.optString("html_url", "")
                val body = json.optString("body", "No release notes provided.")

                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (downloadUrl.isEmpty()) {
                    downloadUrl = browserUrl
                }

                val cleanLatest = sanitizeVersion(tagName)
                val cleanCurrent = sanitizeVersion(currentVersion)

                val hasUpdate = isLatestVersionNewer(cleanCurrent, cleanLatest)

                Log.d(TAG, "Current: $cleanCurrent, Latest: $cleanLatest, Has Update: $hasUpdate")

                return@withContext UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    currentVersion = currentVersion,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    browserUrl = browserUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext UpdateInfo(false, currentVersion, currentVersion, "", "", "")
        }
    }

    private fun sanitizeVersion(version: String): String {
        return version.lowercase().removePrefix("v").trim()
    }

    private fun isLatestVersionNewer(current: String, latest: String): Boolean {
        if (current == latest) return false
        
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currentVal = currentParts.getOrNull(i) ?: 0
            val latestVal = latestParts.getOrNull(i) ?: 0
            if (latestVal > currentVal) return true
            if (latestVal < currentVal) return false
        }

        return false
    }

    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $url", e)
        }
    }
}
