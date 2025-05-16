package com.schwanitz.swan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_paths")
data class LibraryPathEntity(
    @PrimaryKey val uri: String,
    val displayName: String
)