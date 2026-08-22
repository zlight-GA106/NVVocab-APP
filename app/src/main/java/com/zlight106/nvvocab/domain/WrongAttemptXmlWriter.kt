package com.zlight106.nvvocab.domain

import android.util.Xml
import com.zlight106.nvvocab.data.PracticeAttempt
import com.zlight106.nvvocab.data.PracticeAttemptMode
import java.io.OutputStream
import java.time.Instant

object WrongAttemptXmlWriter {
    fun write(
        sessionId: String,
        attempts: List<PracticeAttempt>,
        output: OutputStream,
        includeTiming: Boolean = true,
    ) {
        val sessionAttempts = attempts
            .filter { it.sessionId == sessionId }
            .sortedBy(PracticeAttempt::sequenceIndex)
        val wrongAttempts = sessionAttempts.filterNot(PracticeAttempt::correct)
        val serializer = Xml.newSerializer()
        serializer.setOutput(output, Charsets.UTF_8.name())
        serializer.startDocument(Charsets.UTF_8.name(), true)
        serializer.startTag(null, "nvvocab_wrong_session")
        serializer.attribute(null, "version", "1")
        serializer.startTag(null, "session")
        serializer.attribute(null, "id", sessionId)
        serializer.attribute(null, "started_at", sessionAttempts.minOfOrNull(PracticeAttempt::timestamp).toIso())
        serializer.attribute(null, "ended_at", sessionAttempts.maxOfOrNull(PracticeAttempt::timestamp).toIso())
        serializer.attribute(null, "attempt_count", sessionAttempts.size.toString())
        serializer.attribute(null, "wrong_count", wrongAttempts.size.toString())
        serializer.endTag(null, "session")
        serializer.startTag(null, "wrong_items")
        wrongAttempts.forEach { attempt ->
            serializer.startTag(null, "wrong_item")
            serializer.attribute(null, "sequence", (attempt.sequenceIndex + 1).toString())
            serializer.element("item_id", attempt.itemId)
            serializer.element("source_id", attempt.sourceId.orEmpty())
            serializer.element("mode", attempt.mode.name)
            serializer.element("question", attempt.question)
            if (attempt.options.isNotEmpty()) {
                serializer.startTag(null, "options")
                attempt.options.forEach { option ->
                    serializer.startTag(null, "option")
                    serializer.attribute(null, "id", option.id)
                    serializer.text(option.text)
                    serializer.endTag(null, "option")
                }
                serializer.endTag(null, "options")
            }
            serializer.element("first_answer", attempt.displayAnswer(attempt.firstAnswer))
            serializer.element("user_answer", attempt.displayAnswer(attempt.finalAnswer))
            serializer.element("reference_answer", attempt.displayAnswer(attempt.referenceAnswer))
            serializer.startTag(null, "accepted_answers")
            attempt.acceptedAnswers.sorted().forEach { answer ->
                serializer.element("answer", attempt.displayAnswer(answer))
            }
            serializer.endTag(null, "accepted_answers")
            serializer.element("explanation", attempt.explanation.orEmpty())
            if (includeTiming) serializer.element("active_time_ms", attempt.activeTimeMs.toString())
            serializer.element("hint_used", attempt.hintUsed.toString())
            serializer.element("answered_at", attempt.timestamp.toIso())
            serializer.endTag(null, "wrong_item")
        }
        serializer.endTag(null, "wrong_items")
        serializer.endTag(null, "nvvocab_wrong_session")
        serializer.endDocument()
        output.flush()
    }

    private fun org.xmlpull.v1.XmlSerializer.element(name: String, value: String) {
        startTag(null, name)
        text(value)
        endTag(null, name)
    }

    private fun PracticeAttempt.displayAnswer(raw: String): String {
        if (raw.isBlank() || options.isEmpty()) return raw
        val optionIds = when (mode) {
            PracticeAttemptMode.CHINESE_TO_ENGLISH,
            PracticeAttemptMode.ENGLISH_TO_CHINESE,
            PracticeAttemptMode.ENGLISH_DEFINITION_TO_ENGLISH,
            -> raw.toIntOrNull()?.let { index -> options.getOrNull(index)?.id?.let(::listOf) }.orEmpty()
            PracticeAttemptMode.QUIZ_CHOICE -> raw.split(',').map(String::trim).filter(String::isNotBlank)
            else -> emptyList()
        }
        if (optionIds.isEmpty()) return raw
        return optionIds.mapNotNull { id ->
            options.firstOrNull { it.id == id }?.let { option -> "${option.id}. ${option.text}" }
        }.joinToString(", ").ifBlank { raw }
    }

    private fun Long?.toIso(): String = this?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()
}
