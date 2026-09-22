package com.nullverse.nullkeyai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source contract for release R8. The minified dex is checked by
 * scripts/check-r8-mapping.sh after assembleRelease; these assertions keep the
 * Gradle flag, keep rules, and docs from drifting without a release build.
 */
class R8ReleaseRulesTest {

    private fun repoRoot(): File {
        val cwd = File(".").canonicalFile
        val candidates = listOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()
        return candidates.firstOrNull { File(it, "app/proguard-rules.pro").isFile }
            ?: error("Could not locate repo root from $cwd")
    }

    @Test
    fun releaseMinifyIsOnByDefaultAndDebugStaysOff() {
        val gradle = File(repoRoot(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("isMinifyEnabled = minifyRelease"))
        assertTrue(gradle.contains("isShrinkResources = minifyRelease"))
        assertTrue(gradle.contains("NULLKEY_RELEASE_MINIFY"))
        assertTrue(gradle.contains("nullkey.releaseMinify"))
        assertTrue(gradle.contains("isMinifyEnabled = false"))
        assertTrue(gradle.contains("isShrinkResources = false"))
        assertFalse(gradle.contains("isMinifyEnabled = false\n            proguardFiles"))
    }

    @Test
    fun proguardRulesKeepImeRoomAndPersistedEnums() {
        val rules = File(repoRoot(), "app/proguard-rules.pro").readText()
        listOf(
            "com.nullverse.nullkeyai.ime.NullKeyImeService",
            "com.nullverse.nullkeyai.clipboard.ClipboardMonitorService",
            "com.nullverse.nullkeyai.ui.MainActivity",
            "com.nullverse.nullkeyai.ime.engine.NullKeyKeyboardView",
            "com.nullverse.nullkeyai.db.NullKeyDatabase_Impl",
            "androidx.room.RoomDatabase",
            "@androidx.room.Entity",
            "@androidx.room.Dao",
            "-keep enum com.nullverse.nullkeyai.**"
        ).forEach { token ->
            assertTrue("missing keep rule token: $token", rules.contains(token))
        }
    }

    @Test
    fun releaseDocsDescribeR8AndTheOptOut() {
        val root = repoRoot()
        val release = File(root, "docs/RELEASE_AAB.md").readText()
        assertTrue(release.contains("nullkey.releaseMinify"))
        assertTrue(release.contains("mapping.txt"))
        assertTrue(release.contains("NullKeyDatabase_Impl"))
        assertFalse(release.contains("R8/minify stays"))

        val play = File(root, "docs/PLAY_STORE.md").readText()
        assertTrue(play.contains("minify + resource shrink"))
        assertTrue(play.contains("mapping.txt"))

        val smoke = File(root, "scripts/smoke-signed-aab.sh").readText()
        assertTrue(smoke.contains("check-r8-mapping.sh"))
        assertTrue(smoke.contains("assembleRelease"))
        assertTrue(smoke.contains("bundleRelease"))
        assertTrue(File(root, "scripts/check-r8-mapping.sh").isFile)
    }

    @Test
    fun bundledOcrStaysLinkedWithoutTheClearcutUploader() {
        val root = repoRoot()
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("com.google.mlkit:text-recognition:16.0.1"))
        assertTrue(gradle.contains("transport-backend-cct"))
        assertTrue(gradle.contains("noCompress"))
        listOf("binarypb", "fb", "bincfg", "conv_model", "lstm_model").forEach { ext ->
            assertTrue("model files .$ext must be stored uncompressed", gradle.contains("\"$ext\""))
        }

        val rules = File(root, "app/proguard-rules.pro").readText()
        listOf(
            "com.google.android.gms.dynamite.descriptors.com.google.mlkit.dynamite.text.latin.ModuleDescriptor",
            "MODULE_ID",
            "MODULE_VERSION",
            "BundledTextRecognizerCreator",
            "com.google.android.datatransport.cct.CCTDestination"
        ).forEach { token ->
            assertTrue("missing OCR keep token: $token", rules.contains(token))
        }

        val stub = File(
            root,
            "app/src/main/java/com/google/android/datatransport/cct/CCTDestination.java"
        ).readText()
        assertTrue(stub.contains("implements EncodedDestination"))
        assertTrue(stub.contains("Encoding.of(\"proto\")"))
        assertTrue(stub.contains("Encoding.of(\"json\")"))
        assertFalse(stub.contains("CctTransportBackend"))
        assertFalse(stub.contains("HttpURLConnection"))

        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("tools:node=\"remove\""))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))

        val checker = File(root, "scripts/check-r8-mapping.sh").readText()
        assertTrue(checker.contains("libmlkit_google_ocr_pipeline.so"))
        assertTrue(checker.contains("CctTransportBackend"))
        assertTrue(checker.contains("mlkit-google-ocr-models"))
    }
}
