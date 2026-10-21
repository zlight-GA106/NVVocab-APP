package com.zlight106.nvvocab.domain

import java.util.Locale

/** Conservative local teaching rules. Never use this result as the grading answer. */
object PosSlotRuleEngine {
    private val blank = Regex("_{3,}|\\{\\{\\s*(\\d+)\\s*}}")
    private val word = Regex("[a-z]+(?:'[a-z]+)?")
    private val clauseBoundary = Regex("[.!?;:\\n]")
    private val modals = setOf("can", "may", "must", "should", "will", "could", "would", "might", "shall", "cannot", "can't", "couldn't", "shouldn't", "won't", "wouldn't", "mustn't")
    private val perfectAuxiliaries = setOf("have", "has", "had", "haven't", "hasn't", "hadn't")
    private val articles = setOf("a", "an", "the")
    private val possessives = setOf("my", "your", "his", "her", "their", "our", "its")
    private val degrees = setOf("very", "extremely", "highly", "quite", "somewhat")
    private val copulas = setOf("am", "is", "are", "was", "were", "be", "been", "being", "seem", "seems", "seemed", "feel", "feels", "felt", "become", "becomes", "became")
    private val prepositions = setOf("in", "on", "of", "for", "with", "by", "from")
    private val subjectPronouns = setOf("i", "you", "he", "she", "it", "we", "they")
    private val objectPronouns = setOf("me", "you", "him", "her", "it", "us", "them")
    private val complementVerbs = setOf("say", "said", "know", "knew", "think", "thought", "believe", "hope", "suggest", "show", "ensure")

    // Small explicit anchor lists, not a POS dictionary. Unknown neighbors cause a rule to abstain.
    private val adjectiveAnchors = setOf("rapid", "major", "important", "significant", "common", "small", "big", "large", "high", "low", "new", "old", "good", "bad", "long", "short", "recent", "possible", "different", "public", "private", "national", "local", "social", "financial", "economic", "scientific", "educational", "medical", "successful", "serious", "simple", "necessary", "available", "positive", "negative", "frequent", "similar", "related", "quick", "slow", "strong", "weak", "poor", "great", "friendly", "young", "modern", "useful", "beautiful", "helpful", "careful", "clear", "accurate", "complete", "efficient", "natural", "certain", "effective", "interesting", "excellent")
    private val nounAnchors = setOf("technology", "people", "person", "students", "student", "children", "child", "death", "education", "information", "knowledge", "money", "health", "society", "world", "country", "countries", "system", "systems", "development", "government", "governments", "environment", "language", "languages", "problem", "problems", "project", "projects", "report", "reports", "research", "results", "result", "books", "book", "time", "resources", "resource", "skills", "skill", "energy", "food", "life", "lives", "years", "year", "study", "studies", "researchers")
    private val prepositionalToEndings = listOf(
        Regex("(?:look|looks|looked|looking)\\s+forward\\s+to$"),
        Regex("(?:am|is|are|was|were|be|been|being|get|gets|got|getting|become|becomes|became)(?:\\s+\\w+){0,2}\\s+used\\s+to$"),
        Regex("(?:accustomed|opposed|devoted|committed|addicted|dedicated|similar|due|prior)\\s+to$"),
        Regex("(?:object|objects|objected|objecting|contribute|contributes|contributed|contributing|refer|refers|referred|referring|adapt|adapts|adapted|adapting|lead|leads|led)\\s+to$"),
        Regex("(?:go|goes|went|gone|going|come|comes|came|coming|return|returns|returned|returning|travel|travels|traveled|travelled|walk|walks|walked|drive|drives|drove|move|moves|moved|fly|flies|flew|send|sends|sent)\\s+to$"),
        Regex("(?:in\\s+addition|with\\s+a\\s+view|pay\\s+attention|pays\\s+attention|paid\\s+attention|according|thanks|access|key|approach|solution|response|reaction)\\s+to$"),
    )

    /** A sentence with exactly one blank; ambiguous/multiple blanks return null. */
    fun hint(sentence: String): String? {
        val matches = blank.findAll(sentence).toList()
        if (matches.size != 1) return null
        return around(sentence, matches.single(), matches)
    }

    /** CET-4 helper: inspect only the chosen {{index}}, without filling or reading any answer. */
    fun hint(sentence: String, blankIndex: Int): String? {
        val matches = blank.findAll(sentence).toList()
        val target = matches.filter { it.groupValues.getOrNull(1)?.toIntOrNull() == blankIndex }.singleOrNull()
            ?: return if (matches.size == 1 && matches.single().value.startsWith('_')) hint(sentence) else null
        return around(sentence, target, matches)
    }

    private fun around(sentence: String, target: MatchResult, blanks: List<MatchResult>): String? {
        val previousBlankEnd = blanks.lastOrNull { it.range.last < target.range.first }?.range?.last?.plus(1) ?: 0
        val nextBlankStart = blanks.firstOrNull { it.range.first > target.range.last }?.range?.first ?: sentence.length
        val before = sentence.substring(previousBlankEnd, target.range.first).lowercase(Locale.ROOT).replace('’', '\'')
        val after = sentence.substring(target.range.last + 1, nextBlankStart).lowercase(Locale.ROOT).replace('’', '\'')
        val leftClause = clauseBoundary.split(before).last()
        val rightClause = clauseBoundary.split(after).first()
        val left = word.findAll(leftClause).map { it.value }.toList()
        val right = word.findAll(rightClause).map { it.value }.toList()
        val last = left.lastOrNull()
        val next = right.firstOrNull()

        if (left.takeLast(2) == listOf("despite", "these") || left.takeLast(2) == listOf("despite", "those")) {
            if (next in nounAnchors) return adjective("these/those + ___ + 已知名词")
            return "机械规则：Despite these/those + ___ → 复数 N 名词常见。"
        }
        if (last == "lower" && right.take(2) == listOf("of", "death")) {
            return "机械规则：lower + ___ + of death → N 名词，组成“更低的……”。"
        }
        if (last in modals || (last == "not" && left.getOrNull(left.lastIndex - 1) in modals)) {
            return "机械规则：modal verb + ___ → V 动词原形（can/may/must/should/will/could 等之后）。"
        }
        if (last == "to") {
            val text = left.joinToString(" ")
            // Prepositional to is not an infinitive. Abstain rather than call its following word a base verb.
            if (prepositionalToEndings.any { it.containsMatchIn(text) }) return null
            return "机械规则：不定式 to + ___ → V 动词原形；先排除介词 to 的固定搭配。"
        }
        if (last in perfectAuxiliaries || (last == "not" && left.getOrNull(left.lastIndex - 1) in perfectAuxiliaries)) {
            // have + ___ + noun can be possession + adjective or perfect + participle.
            if (next in nounAnchors || next in adjectiveAnchors) return null
            return "机械规则：have/has/had + ___ → 常见过去分词（V 动词形式），先确认是否为完成时。"
        }

        val subject = last in subjectPronouns || (left.size <= 3 && last in nounAnchors && (left.size == 1 || left.firstOrNull() in articles))
        val objectStarts = next in objectPronouns || next in nounAnchors || next in articles || next in possessives || next in setOf("this", "that", "these", "those")
        if (subject && objectStarts) return "机械规则：主语 + ___ + 宾语 → V 动词常见。"

        if (last in degrees) {
            if (next in nounAnchors) return adjective("程度词 + ___ + 已知名词")
            // A degree word can also modify an adverb: “spoke very ___” is deliberately not guessed.
            if (left.size == 1 || left.dropLast(1).takeLast(2).any { it in copulas }) {
                if (next == null || next in prepositions || next in setOf("to", "that")) {
                    return adjective("very/extremely/highly/quite/somewhat + ___（也需留意副词用法）")
                }
            }
            return null
        }

        if (last in adjectiveAnchors) {
            val prior = left.dropLast(1).takeLast(2)
            if (prior.lastOrNull() in articles || (prior.firstOrNull() in articles && prior.lastOrNull() in degrees)) {
                if (next in nounAnchors) return null // Two adjectives or a compound noun would both be possible.
                val structure = if (next == "of") "the/a/an + adjective + ___ + of" else "a/an/the + adjective + ___"
                return "机械规则：$structure → N 名词常见，空位通常是名词短语的中心词。"
            }
        }
        if (last in articles) {
            if (next in nounAnchors) return adjective("a/an/the + ___ + 已知名词")
            return "机械规则：a/an/the + ___ → N 名词常见；仍需检查后面是否还有名词中心词。"
        }
        if (last in possessives) {
            if (next in nounAnchors) return adjective("所有格 + ___ + 已知名词")
            return "机械规则：my/your/his/her/their/our + ___ → N 名词常见。"
        }
        if (last in setOf("these", "those")) {
            if (next in nounAnchors) return adjective("these/those + ___ + 已知名词")
            return "机械规则：these/those + ___ → 复数 N 名词常见。"
        }
        if (last in setOf("this", "that")) {
            if (last == "that" && left.getOrNull(left.lastIndex - 1) in complementVerbs) return null
            if (next in nounAnchors) return adjective("this/that + ___ + 已知名词")
            return "机械规则：this/that + ___ → 单数 N 名词常见（指示词用法）。"
        }
        if (next in nounAnchors) return adjective("___ + 已知名词（仍需排除名词作定语等用法）")
        if (last in prepositions) return "机械规则：介词 in/on/of/for/with/by/from + ___ → N 名词 / V-ing 形式常见。"
        return null
    }

    private fun adjective(structure: String) = "机械规则：$structure → A / Adj 形容词常见。"
}
