package com.schwanitz.domain.source

data class SourceConfig(
    val id: String,
    val name: String,
    val type: SourceType,
    val isEnabled: Boolean = true,
    val folderUri: String? = null,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null
)
