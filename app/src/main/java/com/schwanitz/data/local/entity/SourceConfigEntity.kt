package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_configs",
    indices = [
        Index(value = ["folderUri"], unique = true),
        Index(value = ["url"], unique = true)
    ]
)
data class SourceConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val isEnabled: Boolean = true,
    val folderUri: String? = null,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null
)
