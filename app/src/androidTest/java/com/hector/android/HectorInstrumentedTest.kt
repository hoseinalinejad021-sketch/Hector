package com.hector.android

import android.content.Context
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureMemoryStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = SecureMemoryStore(context)
    private val prefs = context.getSharedPreferences("hector_secure_memory_v1", Context.MODE_PRIVATE)

    @After
    fun cleanup() {
        listOf("instrumented_roundtrip", "instrumented_tamper", "instrumented_aad_a", "instrumented_aad_b")
            .forEach(store::delete)
    }

    @Test
    fun encryptedValueRoundTripsAndCanBeDeleted() {
        val name = "instrumented_roundtrip"
        val secret = "hector-test-secret-π".toByteArray(Charsets.UTF_8)
        store.write(name, secret)

        val stored = prefs.getString(name, null)
        assertNotNull("Ciphertext should be persisted", stored)
        assertFalse("Plaintext must not be stored in preferences", stored!!.contains(String(secret, Charsets.UTF_8)))
        assertArrayEquals(secret, store.read(name))

        store.delete(name)
        assertNull(store.read(name))
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val name = "instrumented_tamper"
        store.write(name, "sensitive-value".toByteArray(Charsets.UTF_8))
        val encoded = requireNotNull(prefs.getString(name, null))
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        packed[packed.lastIndex] = (packed.last().toInt() xor 0x01).toByte()
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()

        assertNull("AES-GCM must reject modified ciphertext", store.read(name))
        packed.fill(0)
    }

    @Test
    fun ciphertextCannotBeMovedToAnotherPreferenceName() {
        store.write("instrumented_aad_a", "bound-to-name".toByteArray(Charsets.UTF_8))
        val encoded = requireNotNull(prefs.getString("instrumented_aad_a", null))
        prefs.edit().putString("instrumented_aad_b", encoded).commit()

        assertNull("Authenticated data binds ciphertext to its preference name", store.read("instrumented_aad_b"))
    }
}

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @Test
    fun homeScreenLaunchesAndShowsPrimaryActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                assertTrue("Activity should render a non-empty home screen", content.childCount > 0)
                assertTrue("Code Studio action should be visible", containsText(content, "محیط کدنویسی و تولید کد با هوش مصنوعی"))
                assertTrue("Live voice action should be visible", containsText(content, "گفت‌وگوی زنده GPT-Live"))
                assertTrue("Emergency stop action should be visible", containsText(content, "توقف اضطراری HIC-STOP-01"))
            }
        }
    }

    private fun containsText(view: View, expected: String): Boolean {
        if (view is TextView && view.text?.toString()?.contains(expected) == true) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsText(view.getChildAt(index), expected)) return true
            }
        }
        return false
    }
}

@RunWith(AndroidJUnit4::class)
class CodeStudioInstrumentedTest {
    @Test
    fun workspaceCanSaveAndReopenAFile() {
        ActivityScenario.launch(CodeStudioActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val fileName = findEditText(content, "نام فایل")
                val editor = findEditText(content, "کد یا متن")
                val save = findButton(content, "ذخیره")
                val open = findButton(content, "باز کردن")
                findButton(content, "اجرای فرمان در Termux")
                val testName = "instrumented-workspace-test.kt"
                val testContents = "fun hectorTest() = \"workspace-ok\""

                fileName.setText(testName)
                editor.setText(testContents)
                save.performClick()
                assertTrue("Save status should confirm success", containsText(content, "ذخیره شد: " + testName))

                editor.setText("")
                open.performClick()
                assertEquals("Opening a saved workspace file should restore its content",
                    testContents, editor.text.toString())
                activity.filesDir.resolve("code-workspace/$testName").delete()
            }
        }
    }

    private fun findEditText(view: View, hintPart: String): android.widget.EditText {
        if (view is android.widget.EditText && view.hint?.toString()?.contains(hintPart) == true) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = runCatching { findEditText(view.getChildAt(index), hintPart) }.getOrNull()
                if (found != null) return found
            }
        }
        throw AssertionError("EditText with hint containing '$hintPart' was not found")
    }

    private fun findButton(view: View, text: String): android.widget.Button {
        if (view is android.widget.Button && view.text?.toString() == text) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = runCatching { findButton(view.getChildAt(index), text) }.getOrNull()
                if (found != null) return found
            }
        }
        throw AssertionError("Button '$text' was not found")
    }

    private fun containsText(view: View, expected: String): Boolean {
        if (view is TextView && view.text?.toString()?.contains(expected) == true) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsText(view.getChildAt(index), expected)) return true
            }
        }
        return false
    }
}


@RunWith(AndroidJUnit4::class)
class TerminalInstrumentedTest {
    @Test
    fun internalTerminalLaunchesAndShowsSafetyNotice() {
        ActivityScenario.launch(TerminalActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                assertTrue("Terminal screen should render", content.childCount > 0)
                assertTrue("Terminal must explain its restricted Android sandbox",
                    containsTerminalText(content, "بدون دسترسی root"))
                assertTrue("Terminal must require confirmation before running commands",
                    containsTerminalText(content, "بررسی و اجرای فرمان"))
            }
        }
    }

    private fun containsTerminalText(view: View, expected: String): Boolean {
        if (view is TextView && view.text?.toString()?.contains(expected) == true) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsTerminalText(view.getChildAt(index), expected)) return true
            }
        }
        return false
    }
}
