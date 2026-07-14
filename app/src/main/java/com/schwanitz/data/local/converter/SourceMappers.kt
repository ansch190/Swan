package com.schwanitz.data.local.converter

import com.schwanitz.data.local.entity.SourceConfigEntity
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType

fun SourceConfigEntity.toDomain(): SourceConfig = SourceConfig(
    id = id,
    name = name,
    type = try { SourceType.valueOf(type) } catch (e: Exception) { SourceType.LOCAL },
    isEnabled = isEnabled,
    folderUri = folderUri,
    url = url,
    path = path
)

fun SourceConfig.toEntity(): SourceConfigEntity = SourceConfigEntity(
    id = id,
    name = name,
    type = type.name,
    isEnabled = isEnabled,
    folderUri = folderUri,
    url = url,
    path = path
)
