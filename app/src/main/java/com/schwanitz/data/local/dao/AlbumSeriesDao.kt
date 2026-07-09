package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.schwanitz.data.local.entity.AlbumSeriesEntity
import com.schwanitz.data.local.entity.AlbumSeriesMappingEntity
import com.schwanitz.data.source.SeriesDetector.SeriesResult
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumSeriesDao {

    @Query("SELECT * FROM album_series ORDER BY name ASC")
    fun getAllSeries(): Flow<List<AlbumSeriesEntity>>

    @Query("SELECT * FROM album_series WHERE id = :seriesId")
    fun getSeriesById(seriesId: Long): Flow<AlbumSeriesEntity?>

    @Query("SELECT * FROM album_series WHERE name = :name LIMIT 1")
    suspend fun getSeriesByName(name: String): AlbumSeriesEntity?

    @Query("""
        SELECT ase.* FROM album_series ase
        INNER JOIN album_series_mapping asm ON ase.id = asm.seriesId
        WHERE asm.albumName = :albumName LIMIT 1
    """)
    fun getSeriesByAlbumName(albumName: String): Flow<AlbumSeriesEntity?>

    @Query("SELECT * FROM album_series_mapping WHERE seriesId = :seriesId ORDER BY volumeNumber ASC")
    fun getMappingsForSeries(seriesId: Long): Flow<List<AlbumSeriesMappingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: AlbumSeriesEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMappings(mappings: List<AlbumSeriesMappingEntity>)

    @Query("DELETE FROM album_series")
    suspend fun deleteAllSeries()

    @Transaction
    suspend fun replaceAllSeries(seriesList: List<SeriesResult>) {
        deleteAllSeries()
        for (series in seriesList) {
            val id = insertSeries(AlbumSeriesEntity(name = series.seriesName))
            insertMappings(series.volumes.map { v ->
                AlbumSeriesMappingEntity(
                    seriesId = id,
                    albumName = v.albumName,
                    volumeNumber = v.volumeNumber
                )
            })
        }
    }
}
