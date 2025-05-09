package com.schwanitz.swan

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "filters")
data class FilterEntity(
    @PrimaryKey val criterion: String,
    val displayName: String
)