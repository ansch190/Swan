package com.schwanitz.data.backup

import org.json.JSONArray
import org.json.JSONObject

data class BackupFile(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<BackupSource>,
    val credentials: Map<String, BackupCredentials>,
    val apiKeys: BackupApiKeys,
    val languageCode: String,
    val artistDataSource: BackupArtistDataSource
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("timestamp", timestamp)
        put("sources", JSONArray(sources.map { it.toJson() }))
        put("credentials", JSONObject(credentials.mapValues { it.value.toJson() }))
        put("apiKeys", apiKeys.toJson())
        put("languageCode", languageCode)
        put("artistDataSource", artistDataSource.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): BackupFile = BackupFile(
            version = json.getInt("version"),
            timestamp = json.getLong("timestamp"),
            sources = json.getJSONArray("sources").let { arr ->
                (0 until arr.length()).map { BackupSource.fromJson(arr.getJSONObject(it)) }
            },
            credentials = json.getJSONObject("credentials").let { obj ->
                obj.keys().asSequence().associateWith { key ->
                    BackupCredentials.fromJson(obj.getJSONObject(key))
                }
            },
            apiKeys = BackupApiKeys.fromJson(json.getJSONObject("apiKeys")),
            languageCode = json.getString("languageCode"),
            artistDataSource = BackupArtistDataSource.fromJson(json.getJSONObject("artistDataSource"))
        )
    }
}

data class BackupSource(
    val id: String,
    val name: String,
    val type: String,
    val isEnabled: Boolean,
    val folderUri: String?,
    val url: String?,
    val path: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("isEnabled", isEnabled)
        put("folderUri", folderUri ?: JSONObject.NULL)
        put("url", url ?: JSONObject.NULL)
        put("path", path ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): BackupSource = BackupSource(
            id = json.getString("id"),
            name = json.getString("name"),
            type = json.getString("type"),
            isEnabled = json.getBoolean("isEnabled"),
            folderUri = json.opt("folderUri")?.let { if (it == JSONObject.NULL) null else it.toString() },
            url = json.opt("url")?.let { if (it == JSONObject.NULL) null else it.toString() },
            path = json.opt("path")?.let { if (it == JSONObject.NULL) null else it.toString() }
        )
    }
}

data class BackupCredentials(
    val username: String?,
    val password: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("username", username ?: JSONObject.NULL)
        put("password", password ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): BackupCredentials = BackupCredentials(
            username = json.opt("username")?.let { if (it == JSONObject.NULL) null else it.toString() },
            password = json.opt("password")?.let { if (it == JSONObject.NULL) null else it.toString() }
        )
    }
}

data class BackupApiKeys(
    val discogsKey: String?,
    val discogsSecret: String?,
    val lastfmKey: String?,
    val geniusToken: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("discogsKey", discogsKey ?: JSONObject.NULL)
        put("discogsSecret", discogsSecret ?: JSONObject.NULL)
        put("lastfmKey", lastfmKey ?: JSONObject.NULL)
        put("geniusToken", geniusToken ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): BackupApiKeys = BackupApiKeys(
            discogsKey = json.opt("discogsKey")?.let { if (it == JSONObject.NULL) null else it.toString() },
            discogsSecret = json.opt("discogsSecret")?.let { if (it == JSONObject.NULL) null else it.toString() },
            lastfmKey = json.opt("lastfmKey")?.let { if (it == JSONObject.NULL) null else it.toString() },
            geniusToken = json.opt("geniusToken")?.let { if (it == JSONObject.NULL) null else it.toString() }
        )
    }
}

data class BackupArtistDataSource(
    val sourceId: String?,
    val basePath: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sourceId", sourceId ?: JSONObject.NULL)
        put("basePath", basePath)
    }

    companion object {
        fun fromJson(json: JSONObject): BackupArtistDataSource = BackupArtistDataSource(
            sourceId = json.opt("sourceId")?.let { if (it == JSONObject.NULL) null else it.toString() },
            basePath = json.getString("basePath")
        )
    }
}
