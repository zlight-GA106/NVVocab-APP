package com.zlight106.nvvocab.domain

import android.util.Xml
import com.zlight106.nvvocab.data.PracticeAttempt
import java.io.OutputStream
import java.time.Instant

object SessionTelemetryXmlWriter {
    fun write(
        sessionId: String,
        attempts: List<PracticeAttempt>,
        output: OutputStream,
        includeTiming: Boolean,
    ) {
        val sessionAttempts = attempts
            .filter { it.sessionId == sessionId }
            .sortedBy(PracticeAttempt::sequenceIndex)
        val serializer = Xml.newSerializer()
        serializer.setOutput(output, Charsets.UTF_8.name())
        serializer.startDocument(Charsets.UTF_8.name(), true)
        serializer.startTag(null, "nvvocab_telemetry")
        serializer.attribute(null, "version", "1")
        serializer.startTag(null, "session")
        serializer.attribute(null, "id", sessionId)
        serializer.attribute(null, "started_at", sessionAttempts.minOfOrNull(PracticeAttempt::timestamp).toIso())
        serializer.attribute(null, "ended_at", sessionAttempts.maxOfOrNull(PracticeAttempt::timestamp).toIso())
        serializer.attribute(null, "attempt_count", sessionAttempts.size.toString())
        serializer.attribute(null, "correct_count", sessionAttempts.count(PracticeAttempt::correct).toString())
        serializer.attribute(null, "wrong_count", sessionAttempts.count { !it.correct }.toString())
        serializer.endTag(null, "session")
        serializer.startTag(null, "sources")
        sessionAttempts.groupBy { it.sourceId.orEmpty() }.forEach { (sourceId, sourceAttempts) ->
            serializer.startTag(null, "source")
            serializer.attribute(null, "id", sourceId)
            serializer.attribute(null, "item_count", sourceAttempts.size.toString())
            serializer.attribute(
                null,
                "wrong_count",
                sourceAttempts.count { !it.correct }.toString(),
            )
            sourceAttempts.map { it.mode.name }.distinct().sorted().forEach { mode ->
                serializer.element("mode", mode)
            }
            serializer.endTag(null, "source")
        }
        serializer.endTag(null, "sources")
        serializer.startTag(null, "items")
        sessionAttempts.forEach { attempt -> serializer.writeAttempt(attempt, includeTiming) }
        serializer.endTag(null, "items")
        serializer.startTag(null, "wrong_items")
        sessionAttempts.filterNot(PracticeAttempt::correct).forEach { attempt ->
            serializer.writeAttempt(attempt, includeTiming)
        }
        serializer.endTag(null, "wrong_items")
        serializer.endTag(null, "nvvocab_telemetry")
        serializer.endDocument()
        output.flush()
    }

    private fun org.xmlpull.v1.XmlSerializer.writeAttempt(
        attempt: PracticeAttempt,
        includeTiming: Boolean,
    ) {
        startTag(null, "item")
        attribute(null, "sequence", (attempt.sequenceIndex + 1).toString())
        attribute(null, "correct", attempt.correct.toString())
        element("item_id", attempt.itemId)
        element("source_id", attempt.sourceId.orEmpty())
        element("mode", attempt.mode.name)
        element("question", attempt.question)
        if (attempt.options.isNotEmpty()) {
            startTag(null, "options")
            attempt.options.forEach { option ->
                startTag(null, "option")
                attribute(null, "id", option.id)
                text(option.text)
                endTag(null, "option")
            }
            endTag(null, "options")
        }
        element("first_answer", attempt.firstAnswer)
        element("final_answer", attempt.finalAnswer)
        element("reference_answer", attempt.referenceAnswer)
        startTag(null, "accepted_answers")
        attempt.acceptedAnswers.sorted().forEach { answer -> element("answer", answer) }
        endTag(null, "accepted_answers")
        element("explanation", attempt.explanation.orEmpty())
        if (includeTiming) element("active_time_ms", attempt.activeTimeMs.toString())
        element("hint_used", attempt.hintUsed.toString())
        element("timestamp", Instant.ofEpochMilli(attempt.timestamp).toString())
        endTag(null, "item")
    }

    private fun org.xmlpull.v1.XmlSerializer.element(name: String, value: String) {
        startTag(null, name)
        text(value)
        endTag(null, name)
    }

    private fun Long?.toIso(): String = this?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()
}
