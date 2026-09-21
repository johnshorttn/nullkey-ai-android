package com.nullverse.nullkeyai.clipboard

import androidx.test.core.app.ApplicationProvider
import com.nullverse.nullkeyai.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipSwipePreferencesTest {

    @Test
    fun parseFallsBackForBlankOrUnknown() {
        assertEquals(ClipSwipeAction.DELETE, ClipSwipeAction.parse(null, ClipSwipeAction.DELETE))
        assertEquals(ClipSwipeAction.PIN, ClipSwipeAction.parse("", ClipSwipeAction.PIN))
        assertEquals(ClipSwipeAction.TAG, ClipSwipeAction.parse("NOPE", ClipSwipeAction.TAG))
        assertEquals(ClipSwipeAction.PROTECT, ClipSwipeAction.parse("PROTECT", ClipSwipeAction.DELETE))
    }

    @Test
    fun defaultsAreDeleteLeftAndPinRight() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("nullkey_vault", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(ClipSwipeAction.DELETE, ClipSwipePreferences.left(context))
        assertEquals(ClipSwipeAction.PIN, ClipSwipePreferences.right(context))
    }

    @Test
    fun persistsConfiguredActions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("nullkey_vault", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        ClipSwipePreferences.setLeft(context, ClipSwipeAction.TAG)
        ClipSwipePreferences.setRight(context, ClipSwipeAction.PROTECT)
        assertEquals(ClipSwipeAction.TAG, ClipSwipePreferences.left(context))
        assertEquals(ClipSwipeAction.PROTECT, ClipSwipePreferences.right(context))
    }

    @Test
    fun settingsSummaryUsesLocalizedActionNames() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val summary = context.getString(
            R.string.swipe_action_label,
            context.getString(R.string.swipe_direction_left),
            context.getString(ClipSwipeAction.DELETE.labelRes),
        )
        assertEquals("Left swipe: Delete", summary)
        assertEquals("Pin", context.getString(ClipSwipeAction.PIN.labelRes))
        assertEquals("Protect", context.getString(ClipSwipeAction.PROTECT.labelRes))
        assertEquals("Tag", context.getString(ClipSwipeAction.TAG.labelRes))
    }
}
