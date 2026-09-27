package com.zlight106.nvvocab.ui

import android.content.Context
import com.zlight106.nvvocab.data.PracticeSessionRuntime
import com.zlight106.nvvocab.ui.screens.PracticeSessionRequest
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

data class PausedPracticeSnapshot(
    val request: PracticeSessionRequest,
    val runtime: PracticeSessionRuntime,
    val currentQuestionTimeMs: Long,
) : Serializable

/** A private, device-only snapshot written when the user explicitly pauses. */
class PausedPracticeStore(context: Context) {
    private val file = File(context.filesDir, "paused-practice.bin")

    fun restore(): PausedPracticeSnapshot? {
        if (!file.isFile) return null
        return runCatching {
            ObjectInputStream(file.inputStream()).use { it.readObject() as PausedPracticeSnapshot }
        }.getOrElse {
            file.delete()
            null
        }
    }

    fun save(snapshot: PausedPracticeSnapshot) {
        val temporary = File(file.parentFile, "paused-practice.tmp")
        try {
            ObjectOutputStream(temporary.outputStream()).use { it.writeObject(snapshot) }
            check(temporary.renameTo(file)) { "无法保存暂停进度" }
        } finally {
            temporary.delete()
        }
    }

    fun clear() {
        file.delete()
    }
}
