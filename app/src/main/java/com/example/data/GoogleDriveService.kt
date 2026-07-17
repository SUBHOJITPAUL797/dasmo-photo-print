package com.example.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class DriveFolder(
    val id: String,
    val name: String
)

object GoogleDriveService {

    private val httpClient = OkHttpClient()

    val SCOPES = arrayOf(
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/drive.metadata.readonly",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile"
    )

    fun getSignInClient(context: Context): GoogleSignInClient {
        val o = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
        
        for (scope in SCOPES) {
            o.requestScopes(Scope(scope))
        }
        return GoogleSignIn.getClient(context, o.build())
    }

    suspend fun getAccessToken(context: Context, account: Account): String? = withContext(Dispatchers.IO) {
        val scopesString = "oauth2:" + SCOPES.joinToString(" ")
        try {
            GoogleAuthUtil.getToken(context, account, scopesString)
        } catch (e: UserRecoverableAuthException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    suspend fun clearToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun listFolders(accessToken: String, parentId: String = "root"): List<DriveFolder> = withContext(Dispatchers.IO) {
        val qStr = "mimeType = 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(qStr, "UTF-8")}&fields=files(id,name)&pageSize=100"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext emptyList<DriveFolder>()
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files") ?: return@withContext emptyList<DriveFolder>()
                val list = mutableListOf<DriveFolder>()
                for (i in 0 until files.length()) {
                    val item = files.getJSONObject(i)
                    list.add(DriveFolder(
                        id = item.getString("id"),
                        name = item.getString("name")
                    ))
                }
                // Sort folders alphabetically by name
                list.sortBy { it.name.lowercase() }
                list
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createFolder(accessToken: String, name: String, parentId: String = "root"): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files"
        
        val parentsArr = if (parentId == "root") {
            "[\"root\"]"
        } else {
            "[\"$parentId\"]"
        }

        val jsonBody = """
            {
                "name": "$name",
                "mimeType": "application/vnd.google-apps.folder",
                "parents": $parentsArr
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(jsonBody.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                json.optString("id", null)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    suspend fun uploadPdf(accessToken: String, file: File, fileName: String, parentId: String = "root"): Boolean = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

        val parentsArr = if (parentId == "root") {
            "[\"root\"]"
        } else {
            "[\"$parentId\"]"
        }

        val metadata = """
            {
                "name": "$fileName.pdf",
                "parents": $parentsArr
            }
        """.trimIndent()

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaTypeOrNull()!!)
            .addPart(
                metadata.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
            )
            .addPart(
                file.asRequestBody("application/pdf".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }
}
