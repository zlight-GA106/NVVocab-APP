package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object PosPractice {
    val modes = PosMode.entries.map { it.attemptMode }.toSet()
    fun core(parts: Set<PartOfSpeech>) = parts.intersect(PartOfSpeech.core.toSet())
    fun eligible(word: WordEntry) = core(word.partsOfSpeech).isNotEmpty() && !word.posNeedsReview
    fun judge(selected: Set<PartOfSpeech>, actual: Set<PartOfSpeech>, complete: Boolean): Boolean {
        val answer = core(actual)
        return answer.isNotEmpty() && if (complete) selected == answer else selected.size == 1 && selected.all { it in answer }
    }

    fun translationParts(translation: String): Set<PartOfSpeech> {
        val result = linkedSetOf<PartOfSpeech>()
        val markers = mapOf("n" to PartOfSpeech.NOUN, "v" to PartOfSpeech.VERB, "vt" to PartOfSpeech.VERB,
            "vi" to PartOfSpeech.VERB, "adj" to PartOfSpeech.ADJECTIVE, "adv" to PartOfSpeech.ADVERB)
        Regex("(?i)(?<![a-z])(?:n|vt|vi|v|adj|adv)\\.").findAll(translation).forEach {
            markers[it.value.dropLast(1).lowercase()]?.let(result::add)
        }
        return result.ifEmpty { setOf(PartOfSpeech.UNKNOWN) }
    }

    fun drill(word: WordEntry) = PosExercise(
        id = "pos-word:${word.id}", mode = PosMode.DRILL, sourceWordId = word.id,
        sourceTag = word.bookTag, sentence = word.spelling, targetWord = word.spelling, lemma = word.spelling,
        correctParts = core(word.partsOfSpeech), explanation = word.posExplanation.ifBlank { word.translation },
    )

    private fun strictObject(text: String): JSONObject {
        StrictJson.validate(text)
        require(text.trim().startsWith('{') && text.trim().endsWith('}')) { "AI 必须返回 JSON 原文" }
        return JSONObject(text)
    }
    private fun parts(array: JSONArray): Set<PartOfSpeech> {
        require(array.length() in 1..6) { "词性数组为空或无效" }
        val values = (0 until array.length()).map { PartOfSpeech.valueOf(array.getString(it)) }
        require(values.distinct().size == values.size) { "词性重复" }
        require(PartOfSpeech.UNKNOWN !in values || values.size == 1) { "UNKNOWN 不能与已知词性混用" }
        return values.toSet()
    }
    private fun uniquePos(item: JSONObject): PartOfSpeech {
        val pos = PartOfSpeech.valueOf(item.getString("correctPos"))
        require(pos in PartOfSpeech.core && parts(item.getJSONArray("possiblePos")) == setOf(pos)) { "空位或语境词性不唯一" }
        return pos
    }
    fun parseAnnotations(text: String, words: List<WordEntry>): List<PosAnnotation> {
        val input = words.map { it.spelling }.toSet()
        val array = strictObject(text).getJSONArray("words")
        val result = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val word = item.getString("word")
            require(word in input) { "AI 返回了输入范围外的单词：$word" }
            val confidence = item.getDouble("confidence")
            require(item.get("confidence") is Number) { "confidence 必须为数值" }
            require(confidence.isFinite() && confidence in 0.0..1.0) { "confidence 无效" }
            PosAnnotation(word, parts(item.getJSONArray("pos")), confidence, item.optString("explanation"))
        }
        require(result.map { it.word }.toSet() == input && result.size == input.size) { "AI 标注缺失或重复" }
        return result
    }

    fun parseQuestions(text: String, mode: PosMode, words: List<WordEntry>): List<PosExercise> {
        require(mode == PosMode.CONTEXT || mode == PosMode.SLOT)
        val array = strictObject(text).getJSONArray("questions")
        val input = words.associateBy { it.id }
        val questions = (0 until array.length()).map { index ->
            val q = array.getJSONObject(index)
            val source = input[q.getString("sourceWordId")] ?: error("题目使用了范围外的词条")
            require(q.getString("lemma").equals(source.spelling, true)) { "lemma 与输入词不一致" }
            val pos = uniquePos(q)
            require(pos in source.partsOfSpeech) { "答案与词条已确认词性冲突" }
            val sentence = q.getString("sentence").trim()
            val target = q.getString("targetWord").trim()
            require(isWordForm(target, source.spelling)) { "目标词不是输入词的拼写或合法常见变形" }
            require(target.isNotBlank() && sentence.length in 10..1200)
            if (mode == PosMode.CONTEXT) {
                require(Regex("(?i)(?<![a-z])" + Regex.escape(target) + "(?![a-z])").containsMatchIn(sentence)) { "句子不含目标词" }
            } else {
                require(Regex("______").findAll(sentence).count() == 1 && !sentence.contains("{{")) { "每道空位题必须恰好一个空" }
            }
            val clue = q.getString("grammarClue").trim()
            val explanation = q.getString("explanation").trim()
            require(clue.isNotBlank() && explanation.isNotBlank()) { "缺少句法线索或解释" }
            PosExercise(UUID.randomUUID().toString(), mode, source.id, source.bookTag, sentence, target, source.spelling,
                setOf(pos), clue, explanation)
        }
        require(questions.size == input.size && questions.map { it.sourceWordId }.toSet() == input.keys) { "题目缺失或重复" }
        return questions
    }

    fun parseCet4(text: String, words: List<WordEntry>, tag: String): PosExercise {
        val root = strictObject(text)
        val pool = words.associateBy { it.id }
        val array = root.getJSONArray("candidates")
        val candidates = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val source = pool[item.getString("sourceWordId")] ?: error("候选词不在当前词库")
            val candidateParts = parts(item.getJSONArray("pos"))
            require(core(candidateParts).isNotEmpty() && candidateParts.all { it in source.partsOfSpeech }) { "候选词词性与词库冲突" }
            val word = item.getString("word").trim()
            require(isWordForm(word, source.spelling)) { "候选词不是输入词的拼写或合法常见变形" }
            require(word.matches(Regex("[A-Za-z][A-Za-z'’-]*"))) { "候选词拼写无效" }
            PosCandidate(item.getString("id"), word, candidateParts, source.id)
        }
        require(candidates.size == 15 && candidates.map { it.id }.toSet() == ('A'..'O').map { it.toString() }.toSet()) { "需要 A 到 O 共15个候选词" }
        require(candidates.map { it.word.lowercase() }.distinct().size == 15 && candidates.map { it.wordId }.distinct().size == 15) { "候选词重复" }
        require(candidates.flatMap { core(it.parts) }.toSet().containsAll(PartOfSpeech.core)) { "候选词必须覆盖四类词性" }
        val blankArray = root.getJSONArray("blanks")
        val blanks = (0 until blankArray.length()).map { i ->
            val b = blankArray.getJSONObject(i)
            val correctPos = uniquePos(b)
            val answerId = b.getString("answerId")
            require(candidates.any { it.id == answerId && correctPos in it.parts }) { "空位正确答案不存在或词性冲突" }
            val errors = b.getJSONObject("distractorErrors")
            val errorMap = errors.keys().asSequence().associateWith { PosErrorType.valueOf(errors.getString(it)) }
            require(errorMap.keys == candidates.map { it.id }.toSet() - answerId && errorMap.values.none {
                it == PosErrorType.POS_WRONG || it == PosErrorType.MULTI_POS_CONFUSION
            }) { "缺少干扰项原因或错误类型不合法" }
            val clue = b.getString("grammarClue").trim(); val explanation = b.getString("explanation").trim()
            require(clue.isNotBlank() && explanation.isNotBlank())
            PosBlank(b.getInt("index"), answerId, correctPos, clue, explanation, errorMap)
        }
        val passage = root.getString("passage").trim()
        validateCet4(candidates, blanks, passage)
        return PosExercise(UUID.randomUUID().toString(), PosMode.CET4, null, tag, passage,
            correctParts = emptySet(), explanation = "CET-4 Section A", candidates = candidates, blanks = blanks.sortedBy { it.index })
    }

    fun validateCet4(candidates: List<PosCandidate>, blanks: List<PosBlank>, passage: String) {
        require(candidates.size == 15 && candidates.map { it.id }.distinct().size == 15 && candidates.map { it.word.lowercase() }.distinct().size == 15) { "候选词重复或数量错误" }
        require(blanks.size == 10 && blanks.map { it.index }.toSet() == (1..10).toSet()) { "必须恰好10个空" }
        require(blanks.map { it.answerId }.distinct().size == 10) { "同一候选词不能使用两次" }
        require(blanks.all { b -> candidates.any { it.id == b.answerId && b.correctPos in it.parts } }) { "答案不存在" }
        val placeholders = Regex("\\{\\{(\\d+)}}").findAll(passage).map { it.groupValues[1].toInt() }.toList()
        require(placeholders.size == 10 && placeholders.toSet() == (1..10).toSet()) { "文章空位缺失或重复" }
    }

    fun isWordForm(form: String, lemma: String): Boolean {
        val base = lemma.lowercase(); val value = form.lowercase()
        val irregular = mapOf("be" to setOf("is", "are", "was", "were", "been", "being", "am"),
            "go" to setOf("went", "gone"), "have" to setOf("has", "had"), "do" to setOf("does", "did", "done"),
            "make" to setOf("made"), "take" to setOf("took", "taken"), "write" to setOf("wrote", "written"),
            "give" to setOf("gave", "given"), "see" to setOf("saw", "seen"), "rise" to setOf("rose", "risen"),
            "grow" to setOf("grew", "grown"), "lead" to setOf("led"), "find" to setOf("found"), "build" to setOf("built"),
            "buy" to setOf("bought"), "think" to setOf("thought"), "bring" to setOf("brought"), "leave" to setOf("left"),
            "child" to setOf("children"), "person" to setOf("people"), "man" to setOf("men"), "woman" to setOf("women"))
        val forms = mutableSetOf(base, base + "s", base + "es", base + "ed", base + "ing", base + "er", base + "est")
        if (base.endsWith('e')) { forms.add(base + "d"); forms.add(base.dropLast(1) + "ing"); forms.add(base + "r"); forms.add(base + "st") }
        if (base.endsWith('y')) { forms.add(base.dropLast(1) + "ies"); forms.add(base.dropLast(1) + "ied"); forms.add(base.dropLast(1) + "ier"); forms.add(base.dropLast(1) + "iest") }
        if (base.isNotEmpty() && base.last() in "bcdfghjklmnpqrstvwxyz") { forms.add(base + base.last() + "ed"); forms.add(base + base.last() + "ing") }
        return value in forms || value in irregular[base].orEmpty()
    }

    fun assign(answers: Map<Int, String>, slot: Int, candidate: String): Map<Int, String> {
        require(answers.none { (index, value) -> index != slot && value == candidate }) { "该候选词已经使用" }
        return answers + (slot to candidate)
    }
    fun cetError(blank: PosBlank, chosen: PosCandidate?, classified: Set<PartOfSpeech>): PosErrorType? {
        if (chosen?.id == blank.answerId) return null
        if (chosen == null) return PosErrorType.OTHER
        if (blank.correctPos !in chosen.parts || blank.correctPos !in classified) {
            return if (core(chosen.parts).size > 1) PosErrorType.MULTI_POS_CONFUSION else PosErrorType.POS_WRONG
        }
        return blank.distractorErrors[chosen.id] ?: PosErrorType.CONTEXT_WRONG
    }

    fun cetAttempt(q: PosExercise, blank: PosBlank, session: String, selectedId: String?, classified: Set<PartOfSpeech>,
        activeMs: Long, previous: PracticeAttempt? = null): PracticeAttempt {
        val selected = q.candidates.firstOrNull { it.id == selectedId }
        val correctWord = q.candidates.first { it.id == blank.answerId }
        val correct = selected?.id == blank.answerId
        return PracticeAttempt(previous?.id ?: UUID.randomUUID().toString(), previous?.userId, session, "${q.id}:${blank.index}", q.sourceTag,
            PracticeAttemptMode.CET4_WORD_BANK, blank.index - 1, "第${blank.index}空：${q.sentence}", q.candidates.map { QuizOption(it.id, it.word) },
            previous?.firstAnswer ?: selectedId.orEmpty(), selectedId.orEmpty(), blank.answerId, setOf(blank.answerId),
            "正确答案：${correctWord.word}；需要 ${blank.correctPos.shortLabel}；你的预分类：${classified.joinToString { it.shortLabel }}。\n${blank.grammarClue}\n${blank.explanation}",
            correct, previous?.firstAnswerCorrect ?: correct, activeMs, false, System.currentTimeMillis(), true, setOf(blank.correctPos), classified,
            cetError(blank, selected, classified))
    }

    fun attempt(exercise: PosExercise, session: String, index: Int, selected: Set<PartOfSpeech>,
        complete: Boolean, activeMs: Long, previous: PracticeAttempt? = null): PracticeAttempt {
        val correct = judge(selected, exercise.correctParts, complete)
        val answer = PartOfSpeech.encode(selected)
        return PracticeAttempt(previous?.id ?: UUID.randomUUID().toString(), previous?.userId, session,
            exercise.id, exercise.sourceTag, exercise.mode.attemptMode, index, exercise.sentence,
            PartOfSpeech.core.map { QuizOption(it.name, "${it.shortLabel} ${it.label}") },
            previous?.firstAnswer ?: answer, answer, PartOfSpeech.encode(exercise.correctParts),
            exercise.correctParts.map { it.name }.toSet(), exercise.explanation, correct,
            previous?.firstAnswerCorrect ?: correct, activeMs, previous?.hintUsed ?: false,
            System.currentTimeMillis(), true, exercise.correctParts, selected,
            if (correct) null else if (exercise.correctParts.size > 1) PosErrorType.MULTI_POS_CONFUSION else PosErrorType.POS_WRONG)
    }

    fun wrongResult(attempt: PracticeAttempt) = WrongQuestionResult(
        source = WrongQuestionSource.QUIZ, bankId = null, bankName = PosMode.entries.first { it.attemptMode == attempt.mode }.label,
        questionKey = attempt.itemId, questionText = attempt.question, options = attempt.options,
        correctAnswers = attempt.acceptedAnswers, correct = attempt.correct,
        explanation = attempt.explanation, category = attempt.mode.name,
        sourceReference = attempt.sourceId, userAnswer = attempt.finalAnswer, hintUsed = attempt.hintUsed,
    )
}

data class PosRate(val count: Int, val correct: Int, val averageMs: Long, val within3: Double, val within5: Double) {
    val accuracy: Double get() = if (count == 0) 0.0 else correct.toDouble() / count
}
data class PosStatistics(val rates: Map<PartOfSpeech, PosRate>, val confusion: Map<Pair<PartOfSpeech, PartOfSpeech>, Int>, val worstWords: List<Pair<String, Int>>)
object PosAnalytics {
    fun calculate(attempts: List<PracticeAttempt>, since: Long = 0, wordName: (String) -> String = { it }): PosStatistics {
        val rows = attempts.filter { it.mode in PosPractice.modes && it.timestamp >= since }
            .groupBy { it.sessionId to it.sequenceIndex }.values.map { it.maxBy(PracticeAttempt::timestamp) }
        val rates = PartOfSpeech.core.associateWith { pos ->
            val group = rows.filter { pos in it.actualPos }
            fun timed(limit: Long): Double {
                val subset = group.filter { it.activeTimeMs <= limit && !it.hintUsed }
                return if (subset.isEmpty()) 0.0 else subset.count { it.correct }.toDouble() / subset.size
            }
            PosRate(group.size, group.count { it.correct }, if (group.isEmpty()) 0 else group.sumOf { it.activeTimeMs } / group.size, timed(3000), timed(5000))
        }
        val matrix = mutableMapOf<Pair<PartOfSpeech, PartOfSpeech>, Int>()
        rows.filter { !it.correct }.forEach { row ->
            PosPractice.core(row.actualPos).forEach { actual ->
                PosPractice.core(row.selectedPos).filter { it !in row.actualPos }.forEach { selected ->
                    val key = actual to selected; matrix[key] = matrix.getOrDefault(key, 0) + 1
                }
            }
        }
        val worst = rows.filter { !it.correct }.groupingBy { wordName(it.itemId) }.eachCount().entries
            .sortedByDescending { it.value }.take(20).map { it.key to it.value }
        return PosStatistics(rates, matrix, worst)
    }
}
