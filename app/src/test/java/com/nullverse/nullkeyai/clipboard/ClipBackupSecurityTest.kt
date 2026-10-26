package com.nullverse.nullkeyai.clipboard

import com.nullverse.nullkeyai.db.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipBackupSecurityTest {
    @Test
    fun unprotectedJsonRoundTripRemainsImportable() {
        val source = Clip(content = "hello", notes = "note", protected = false)
        val restored = ClipBackup.fromJson(ClipBackup.toJson(listOf(source))).single()
        assertEquals("hello", restored.content)
        assertEquals("note", restored.notes)
        assertFalse(restored.protected)
    }

    @Test
    fun deviceBoundProtectedJsonIsRejected() {
        val source = Clip(
            content = "nkenc:v1:iv:ciphertext",
            notes = "nkenc:v1:iv:ciphertext",
            protected = true
        )
        val json = ClipBackup.toJson(listOf(source))
        val error = assertThrows(DeviceBoundProtectedImportException::class.java) {
            ClipBackup.fromJson(json)
        }
        assert(error.message?.contains("Secure Backup") == true)
        assert(error is IllegalArgumentException)
    }
}
