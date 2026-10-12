package com.zlight106.nvvocab.data

import java.io.Serializable

enum class PartOfSpeech(val shortLabel: String, val label: String) {
    NOUN("N", "名词"), VERB("V", "动词"), ADJECTIVE("Adj", "形容词"),
    ADVERB("Adv", "副词"), OTHER("Other", "其他"), UNKNOWN("?", "未识别");

    companion object {
        val core = listOf(NOUN, VERB, ADJECTIVE, ADVERB)
        fun encode(values: Set<PartOfSpeech>): String = values.sortedBy { it.ordinal }.joinToString(",") { it.name }
        fun decode(value: String?): Set<PartOfSpeech> = value.orEmpty().split(',')
            .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }.toSet()
            .ifEmpty { setOf(UNKNOWN) }
    }
}

enum class PosMode(val label: String, val attemptMode: PracticeAttemptMode) {
    DRILL("词性速判", PracticeAttemptMode.WORD_POS_CLASSIFICATION),
    CONTEXT("语境词性", PracticeAttemptMode.CONTEXT_POS_CLASSIFICATION),
    SLOT("空位词性", PracticeAttemptMode.SLOT_POS_CLASSIFICATION),
    CET4("CET-4 选词填空模拟", PracticeAttemptMode.CET4_WORD_BANK);
}

enum class PosErrorType(val label: String) {
    POS_WRONG("词性判断错误"), MEANING_WRONG("词义判断错误"),
    COLLOCATION_WRONG("搭配错误"), CONTEXT_WRONG("上下文理解错误"),
    MULTI_POS_CONFUSION("一词多性误判"), OTHER("其他");
}

data class PosCandidate(val id: String, val word: String, val parts: Set<PartOfSpeech>, val wordId: String?) : Serializable
data class PosBlank(
    val index: Int, val answerId: String, val correctPos: PartOfSpeech,
    val grammarClue: String, val explanation: String,
    val distractorErrors: Map<String, PosErrorType>,
) : Serializable

data class PosExercise(
    val id: String, val mode: PosMode, val sourceWordId: String?, val sourceTag: String,
    val sentence: String, val targetWord: String = "", val lemma: String = "",
    val correctParts: Set<PartOfSpeech>, val grammarClue: String = "",
    val explanation: String, val difficulty: String = "CET4",
    val candidates: List<PosCandidate> = emptyList(), val blanks: List<PosBlank> = emptyList(),
) : Serializable

data class PosAnnotation(val word: String, val parts: Set<PartOfSpeech>, val confidence: Double, val explanation: String)

data class PosSettings(
    val questionCount: Int = 20, val timeLimitSeconds: Int = 0, val completeMode: Boolean = false,
    val batchSize: Int = 30, val autoExplanation: Boolean = true,
    val mixedEnabled: Boolean = false, val autoUnknown: Boolean = false,
    val annotationPrompt: String = POS_ANNOTATION_PROMPT,
    val contextPrompt: String = POS_CONTEXT_PROMPT,
    val slotPrompt: String = POS_SLOT_PROMPT,
    val cet4Prompt: String = POS_CET4_PROMPT,
)

const val POS_ANNOTATION_PROMPT = """批量识别输入英语词条的所有词性，以词本身、释义和真实常见用法为依据；词缀只能提示，不能单独作为答案。仅输出严格 JSON，不得 Markdown、前言或额外文字。
格式 {"words":[{"word":"原始单词","pos":["NOUN","VERB"],"confidence":0.98,"explanation":"简短中文提示，附常见词组或例句"}]}。
每个输入单词恰好返回一次，禁止添加单词。pos 仅限 NOUN/VERB/ADJECTIVE/ADVERB/OTHER/UNKNOWN；多词性必须全部列出，不确定使用 UNKNOWN。confidence 为 0 到 1。"""

const val POS_CONTEXT_PROMPT = """生成 CET-4 难度的语境词性题，只输出严格 JSON，禁止 Markdown、前言、额外文字。
格式 {"questions":[{"sourceWordId":"输入id","lemma":"输入word","sentence":"自然完整英文句子","targetWord":"句中实际拼写形式","correctPos":"NOUN","possiblePos":["NOUN"],"grammarClue":"中文句法依据","explanation":"中文用法及简短解释"}]}。
每个输入词恰好一题，句中必须含 targetWord，lemma 必须等于输入词。仅考 NOUN/VERB/ADJECTIVE/ADVERB。考查当前语境的真实词性，不是词的所有可能词性。允许变形但必须记录 lemma。避免歧义，possiblePos 必须只有唯一的 correctPos。"""

const val POS_SLOT_PROMPT = """生成 CET-4 难度的空位词性题，只输出严格 JSON，禁止 Markdown、前言、额外文字。
格式 {"questions":[{"sourceWordId":"输入id","lemma":"输入word","sentence":"The rapid ______ of technology changed our lives.","targetWord":"空中应填的实际词形","correctPos":"NOUN","possiblePos":["NOUN"],"grammarClue":"冠词 + ___ + of，需要名词","explanation":"简短中文解释"}]}。
每个输入词恰好一题，每句只能有一个 ______。尽量使用当前词库。correctPos 仅限 NOUN/VERB/ADJECTIVE/ADVERB，答案必须对应输入词的已知词性。必须包含 grammarClue。只有一种明确合理词性；若两种词性都合理，重新设计句子。possiblePos 只能含唯一 correctPos。"""

const val POS_CET4_PROMPT = """生成一套 CET-4 Section A 选词填空，严格只输出 JSON，禁止 Markdown、前言、多余文字。
格式 {"passage":"一篇自然连贯的180到250词英文文章，含 {{1}} 到 {{10}} 各一次",
"candidates":[{"id":"A","word":"候选词实际拼写","sourceWordId":"输入id","pos":["NOUN"]}],
"blanks":[{"index":1,"answerId":"A","correctPos":"NOUN","possiblePos":["NOUN"],"grammarClue":"中文句法依据","explanation":"结合词义、搭配和上下文解释","distractorErrors":{"B":"MEANING_WRONG"}}]}。
恰好15个不同候选词，id A到O；覆盖NOUN/VERB/ADJECTIVE/ADVERB，全部来自输入词库，记录真实词形和 sourceWordId，不能虚构新词。恰好10个空，每个候选词最多使用一次，10个答案不得重复。各空语法及语义答案必须唯一，possiblePos 仅含唯一 correctPos。
每个空的 distractorErrors 必须列出其他14个候选 id，分别为 MEANING_WRONG/COLLOCATION_WRONG/CONTEXT_WRONG/OTHER，作为词性已判断正确时的错误原因。严禁为相同词性的语义错误标 POS_WRONG。候选词允许一词多性。"""
