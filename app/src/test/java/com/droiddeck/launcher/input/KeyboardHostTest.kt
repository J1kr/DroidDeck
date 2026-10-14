package com.droiddeck.launcher.input

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KeyboardHostTest {
    private lateinit var imm: InputMethodManager
    private lateinit var host: KeyboardHost

    @Before fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        host = KeyboardHost(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(host) })
        idle()
    }

    @Config(sdk = [33])
    @Test fun toggleShowsAKeyboardTheSystemDismissed() {
        host.show()
        idle()
        imm.hideSoftInputFromWindow(host.windowToken, 0)
        idle()
        host.toggle()
        idle()
        assertTrue(shadowOf(imm).isSoftInputVisible)
        assertTrue(host.shown)
    }

    @Config(sdk = [28])
    @Test fun toggleHidesAKeyboardThatIsShowing() {
        host.show()
        idle()
        host.toggle()
        idle()
        assertFalse(shadowOf(imm).isSoftInputVisible)
        assertFalse(host.shown)
    }

    @Test fun committedHangulGoesToTheGuestAsText() {
        val (ic, log) = connection()
        ic.commitText("한글", 1)
        assertEquals(listOf("commit:한글"), log)
    }

    @Test fun committedAsciiStaysKeyPresses() {
        val (ic, log) = connection()
        ic.commitText("hi", 1)
        assertEquals(listOf("key:H", "key:I"), log)
    }

    @Test fun mixedTextKeepsItsOrder() {
        val (ic, log) = connection()
        ic.commitText("a한 b", 1)
        assertEquals(listOf("key:A", "commit:한", "key:SPACE", "key:B"), log)
    }

    @Test fun hangulCompositionIsShownAsPreeditThenCommitted() {
        val (ic, log) = connection()
        ic.setComposingText("ㅎ", 1)
        ic.setComposingText("하", 1)
        ic.setComposingText("한", 1)
        ic.commitText("한", 1)
        ic.setComposingText("ㄱ", 1)
        ic.finishComposingText()
        assertEquals(listOf("preedit:ㅎ", "preedit:하", "preedit:한", "commit:한", "preedit:ㄱ", "commit:ㄱ"), log)
    }

    @Test fun asciiCommitClearsAPendingPreedit() {
        val (ic, log) = connection()
        ic.setComposingText("ㅎ", 1)
        ic.commitText("1", 1)
        assertEquals(listOf("preedit:ㅎ", "preedit:", "key:1"), log)
    }

    private fun connection(): Pair<InputConnection, MutableList<String>> {
        val log = mutableListOf<String>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val text = object : GuestText {
            override fun commit(text: String) { log += "commit:$text" }
            override fun preedit(text: String) { log += "preedit:$text" }
        }
        val host = KeyboardHost(activity, text) { event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_SHIFT_LEFT)
                log += "key:" + KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
            true
        }
        activity.setContentView(FrameLayout(activity).apply { addView(host) })
        host.show()
        idle()
        return host.onCreateInputConnection(EditorInfo())!! to log
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
