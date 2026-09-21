package com.cellier.manager.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {
    @Test
    fun acceptsOnlyKnownFlatEntriesAndUuidPhotos() {
        assertTrue(isAllowedBackupEntry("manifest.json"))
        assertTrue(isAllowedBackupEntry("photos/75486603-6d8c-4ff1-9cc2-860a95ea6c07.jpg"))
        assertFalse(isAllowedBackupEntry("../inventory.json"))
        assertFalse(isAllowedBackupEntry("photos/subdir/75486603-6d8c-4ff1-9cc2-860a95ea6c07.jpg"))
        assertFalse(isAllowedBackupEntry("photos/not-a-uuid.jpg"))
        assertFalse(isAllowedBackupEntry("secrets.json"))
    }
}
