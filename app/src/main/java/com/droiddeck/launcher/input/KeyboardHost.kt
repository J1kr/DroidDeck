package com.droiddeck.launcher.input

import android.app.Activity
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.droiddeck.launcher.wayland.CompositorHost
import com.droiddeck.launcher.wayland.WaylandCompositor

interface GuestText {
    fun commit(text: String)
    fun preedit(text: String)
}

object CompositorText : GuestText {
    override fun commit(text: String) {
        if (CompositorHost.isStarted) WaylandCompositor.textInputCommit(text)
    }

    override fun preedit(text: String) {
        if (CompositorHost.isStarted) WaylandCompositor.textInputPreedit(text, text.codePointCount(0, text.length))
    }
}

class KeyboardHost(
    context: Context,
    private val guestText: GuestText,
    private val sendKeyEvent: (KeyEvent) -> Boolean,
) : View(context) {
    constructor(context: Context, sendKeyEvent: (KeyEvent) -> Boolean) : this(context, CompositorText, sendKeyEvent)
    constructor(activity: Activity) : this(activity, { activity.dispatchKeyEvent(it) })

    private val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val map: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    var shown = false
        private set

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        visibility = VISIBLE
        layoutParams = android.widget.FrameLayout.LayoutParams(1, 1)
    }

    override fun onCheckIsTextEditor(): Boolean = shown

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!shown) return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return KeyConnection()
    }

    fun toggle() = if (shown && imeVisible()) hide() else show()

    private fun imeVisible() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
        rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != false

    fun show() {
        shown = true
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        imm.restartInput(this)
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide() {
        shown = false
        imm.hideSoftInputFromWindow(windowToken, 0)
        isFocusable = false
        isFocusableInTouchMode = false
        clearFocus()
    }

    private inner class KeyConnection : BaseInputConnection(this, false) {
        private var composing = ""

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val s = text?.toString().orEmpty()
            val composed = composing.isNotEmpty()
            composing = ""
            val events = if (s.isEmpty()) null else map.getEvents(s.toCharArray())
            if (events != null || s.isEmpty()) {
                if (composed) guestText.preedit("")
                events?.forEach { sendKeyEvent(it) }
                return true
            }
            if (composed && typable(s[0])) guestText.preedit("")
            val run = StringBuilder()
            for (ch in s) {
                if (!typable(ch)) {
                    run.append(ch)
                    continue
                }
                if (run.isNotEmpty()) guestText.commit(run.toString())
                run.clear()
                typeChar(ch)
            }
            if (run.isNotEmpty()) guestText.commit(run.toString())
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val s = text?.toString().orEmpty()
            if (s == composing) return true
            composing = s
            guestText.preedit(s)
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composing.isNotEmpty()) commitText(composing, 1)
            return true
        }

        private fun typable(ch: Char): Boolean {
            if (map.getEvents(charArrayOf(ch)) != null) return true
            val plain = EvdevKeys.unshiftedChar(ch.code)
            return EvdevKeys.keycodeForChar(if (plain != 0) plain else ch.code) != 0
        }

        private fun typeChar(ch: Char) {
            val events = map.getEvents(charArrayOf(ch))
            if (events != null) {
                events.forEach { sendKeyEvent(it) }
                return
            }
            val t = System.currentTimeMillis()
            val down = KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, 0)
            sendKeyEvent(CharKeyEvent(down, ch))
            sendKeyEvent(CharKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP), ch))
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength) { tap(KeyEvent.KEYCODE_DEL) }
            repeat(afterLength) { tap(KeyEvent.KEYCODE_FORWARD_DEL) }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean = this@KeyboardHost.sendKeyEvent(event)

        override fun performEditorAction(actionCode: Int): Boolean {
            tap(KeyEvent.KEYCODE_ENTER)
            return true
        }

        private fun tap(keyCode: Int) {
            val t = System.currentTimeMillis()
            sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
            sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
    }

    private class CharKeyEvent(origin: KeyEvent, private val ch: Char) : KeyEvent(origin) {
        override fun getUnicodeChar(): Int = ch.code
        override fun getUnicodeChar(metaState: Int): Int = ch.code
    }
}
