package com.schwanitz.data.local.converter

import com.schwanitz.data.local.entity.SourceConfigEntity
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMapperTest {

    @Test
    fun `entity toDomain maps all fields`() {
        val entity = SourceConfigEntity(
            id = "src-1",
            name = "My NAS",
            type = "WEBDAV",
            isEnabled = true,
            folderUri = "content://some/uri",
            url = "https://nas.local/music",
            path = "/music"
        )
        val domain = entity.toDomain()
        assertEquals("src-1", domain.id)
        assertEquals("My NAS", domain.name)
        assertEquals(SourceType.WEBDAV, domain.type)
        assertTrue(domain.isEnabled)
        assertEquals("content://some/uri", domain.folderUri)
        assertEquals("https://nas.local/music", domain.url)
        assertNull(domain.username)
        assertNull(domain.password)
        assertEquals("/music", domain.path)
    }

    @Test
    fun `entity toDomain falls back to LOCAL for invalid type`() {
        val entity = SourceConfigEntity(id = "x", name = "X", type = "INVALID_TYPE")
        val domain = entity.toDomain()
        assertEquals(SourceType.LOCAL, domain.type)
    }

    @Test
    fun `domain toEntity maps all fields`() {
        val domain = SourceConfig(
            id = "src-2",
            name = "Local Folder",
            type = SourceType.LOCAL,
            isEnabled = false,
            folderUri = "content://docs/123",
            url = null,
            path = "/sdcard/Music"
        )
        val entity = domain.toEntity()
        assertEquals("src-2", entity.id)
        assertEquals("Local Folder", entity.name)
        assertEquals("LOCAL", entity.type)
        assertEquals(false, entity.isEnabled)
        assertEquals("content://docs/123", entity.folderUri)
        assertNull(entity.url)
        assertEquals("/sdcard/Music", entity.path)
    }

    @Test
    fun `roundtrip domain toEntity toDomain drops credentials`() {
        val original = SourceConfig(
            id = "round-1",
            name = "Test Source",
            type = SourceType.WEBDAV,
            isEnabled = true,
            folderUri = "uri",
            url = "https://test.example.com",
            username = "user",
            password = "pass",
            path = "/data"
        )
        val roundtripped = original.toEntity().toDomain()
        assertEquals(original.id, roundtripped.id)
        assertEquals(original.name, roundtripped.name)
        assertEquals(original.type, roundtripped.type)
        assertNull(roundtripped.username)
        assertNull(roundtripped.password)
    }

    @Test
    fun `roundtrip entity toDomain toEntity preserves identity`() {
        val original = SourceConfigEntity(
            id = "round-2",
            name = "Roundtrip",
            type = "LOCAL",
            isEnabled = true,
            folderUri = "content://test",
            url = "https://test",
            path = "/p"
        )
        val roundtripped = original.toDomain().toEntity()
        assertEquals(original, roundtripped)
    }

    @Test
    fun `SMB type maps correctly`() {
        val entity = SourceConfigEntity(id = "smb", name = "SMB", type = "SMB")
        assertEquals(SourceType.SMB, entity.toDomain().type)
        assertEquals("SMB", SourceType.SMB.toEntitycopy(entity).type)
    }

    private fun SourceType.toEntitycopy(base: SourceConfigEntity) =
        base.toDomain().copy(type = this).toEntity()
}
