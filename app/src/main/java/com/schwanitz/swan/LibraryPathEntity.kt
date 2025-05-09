package com.schwanitz.swan

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_paths")
data class LibraryPathEntity(
    @PrimaryKey val uri: String,
    val displayName: String
)