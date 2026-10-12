package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.*
import org.json.JSONArray
import org.json.JSONObject

object PosCodec {
    fun encode(q: PosExercise): String = JSONObject().apply {
        put("id", q.id); put("mode", q.mode.name); put("sourceWordId", q.sourceWordId ?: JSONObject.NULL)
        put("sourceTag", q.sourceTag); put("sentence", q.sentence); put("targetWord", q.targetWord); put("lemma", q.lemma)
        put("parts", PartOfSpeech.encode(q.correctParts)); put("grammarClue", q.grammarClue); put("explanation", q.explanation); put("difficulty", q.difficulty)
        put("candidates", JSONArray().apply { q.candidates.forEach { c -> put(JSONObject().apply {
            put("id", c.id); put("word", c.word); put("parts", PartOfSpeech.encode(c.parts)); put("wordId", c.wordId ?: JSONObject.NULL)
        }) } })
        put("blanks", JSONArray().apply { q.blanks.forEach { b -> put(JSONObject().apply {
            put("index", b.index); put("answerId", b.answerId); put("pos", b.correctPos.name)
            put("clue", b.grammarClue); put("explanation", b.explanation)
            put("errors", JSONObject().apply { b.distractorErrors.forEach { (id, error) -> put(id, error.name) } })
        }) } })
    }.toString()

    fun decode(text: String): PosExercise {
        val q = JSONObject(text)
        val candidates = q.getJSONArray("candidates").objects().map { c ->
            PosCandidate(c.getString("id"), c.getString("word"), PartOfSpeech.decode(c.getString("parts")), c.nullable("wordId"))
        }
        val blanks = q.getJSONArray("blanks").objects().map { b ->
            val errors = b.getJSONObject("errors")
            PosBlank(b.getInt("index"), b.getString("answerId"), PartOfSpeech.valueOf(b.getString("pos")),
                b.getString("clue"), b.getString("explanation"), errors.keys().asSequence().associateWith { PosErrorType.valueOf(errors.getString(it)) })
        }
        val mode = PosMode.valueOf(q.getString("mode"))
        if (mode == PosMode.CET4) PosPractice.validateCet4(candidates, blanks, q.getString("sentence"))
        return PosExercise(q.getString("id"), mode, q.nullable("sourceWordId"), q.getString("sourceTag"), q.getString("sentence"),
            q.getString("targetWord"), q.getString("lemma"), if (mode == PosMode.CET4) emptySet() else PartOfSpeech.decode(q.getString("parts")),
            q.getString("grammarClue"), q.getString("explanation"), q.getString("difficulty"), candidates, blanks)
    }

    // TitleList already synchronizes and exports QuizQuestion. Keep the richer exercise as
    // an optional source reference so older clients can still read its ordinary choices.
    fun toQuiz(q: PosExercise, index: Int) = ParsedQuizQuestion(
        originalIndex = index, score = 10, text = q.sentence,
        options = if (q.mode == PosMode.CET4) q.candidates.map { QuizOption(it.id, it.word) }
            else PartOfSpeech.core.map { QuizOption(it.name, "${it.shortLabel} ${it.label}") },
        answers = if (q.mode == PosMode.CET4) setOf(q.blanks.first().answerId) else q.correctParts.map { it.name }.toSet(),
        explanation = q.explanation, category = q.mode.attemptMode.name, sourceReference = "POS_JSON:" + encode(q),
    )
    fun fromQuiz(q: QuizQuestion): PosExercise? = q.sourceReference?.takeIf { it.startsWith("POS_JSON:") }
        ?.let { runCatching { decode(it.removePrefix("POS_JSON:")) }.getOrNull() }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
    private fun JSONObject.nullable(name: String): String? = if (isNull(name)) null else getString(name)
}
