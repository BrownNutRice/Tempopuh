package me.avinas.tempo.data.drive

import org.junit.Assert.*
import org.junit.Test

class LocalBackupRetentionTest {
    @Test fun `newest random UUID remains and oldest is removed`() {
        val backups = listOf("f", "e", "d", "c", "b", "0").mapIndexed { index, id ->
            LocalBackupRetention.Backup(id, index + 1L)
        }
        assertEquals(listOf("f"), LocalBackupRetention.deletions(backups, "0", 5))
    }

    @Test fun `retry URI remains even when its original creation time is oldest`() {
        val backups = (1L..6L).map { LocalBackupRetention.Backup("id$it", it) }
        assertEquals(listOf("id2"), LocalBackupRetention.deletions(backups, "id1", 5))
    }

    @Test fun `renamed documents use recorded order and undated documents are preserved`() {
        val backups = listOf(
            LocalBackupRetention.Backup("provider-renamed", 9),
            LocalBackupRetention.Backup("legacy", 8),
            LocalBackupRetention.Backup("uuid", 7),
            LocalBackupRetention.Backup("no-date", null),
            LocalBackupRetention.Backup("zero-date", 0)
        )
        assertEquals(listOf("uuid"), LocalBackupRetention.deletions(backups, "provider-renamed", 2))
    }

    @Test fun `legacy dates are parsed strictly and UUIDs never become creation dates`() {
        assertNotNull(LocalBackupRetention.legacyTimestamp("tempo_auto_backup_2026-11-18_12-34-56-123.tempo"))
        assertNotNull(LocalBackupRetention.legacyTimestamp("tempo_auto_backup_2026-11-18_12-34-56-123 (1).tempo"))
        assertNull(LocalBackupRetention.legacyTimestamp("tempo_auto_backup_f123-abcd.tempo"))
        assertNull(LocalBackupRetention.legacyTimestamp("tempo_auto_backup_2026-02-31_12-34-56-123.tempo"))
    }
}
