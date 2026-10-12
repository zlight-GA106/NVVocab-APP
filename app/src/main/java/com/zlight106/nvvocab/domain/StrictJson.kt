package com.zlight106.nvvocab.domain

/** org.json accepts some JavaScript syntax; AI output must be actual JSON. */
internal object StrictJson {
    fun validate(text: String) {
        var offset = 0
        fun whitespace() { while (offset < text.length && text[offset] in " \t\r\n") offset++ }
        fun take(char: Char) { whitespace(); require(offset < text.length && text[offset++] == char) { "JSON 格式错误" } }
        fun string(): String {
            whitespace(); val start = offset; take('"')
            while (offset < text.length) {
                val c = text[offset++]
                if (c == '"') return text.substring(start, offset)
                require(c.code >= 32) { "JSON 字符串含控制字符" }
                if (c == '\\') {
                    require(offset < text.length)
                    val escape = text[offset++]
                    require(escape in "\"\\/bfnrtu") { "JSON 转义无效" }
                    if (escape == 'u') repeat(4) { require(offset < text.length && text[offset++].digitToIntOrNull(16) != null) }
                }
            }
            error("JSON 字符串未闭合")
        }
        lateinit var value: () -> Unit
        value = {
            whitespace(); require(offset < text.length) { "JSON 值缺失" }
            when (text[offset]) {
                '"' -> { string(); Unit }
                '{' -> {
                    take('{'); whitespace()
                    val keys = mutableSetOf<String>()
                    if (offset < text.length && text[offset] == '}') take('}') else {
                        while (true) {
                            val key = string()
                            require(keys.add(org.json.JSONArray("[$key]").getString(0))) { "JSON 字段重复" }
                            take(':'); value(); whitespace()
                            if (offset < text.length && text[offset] == '}') { take('}'); break }
                            take(',')
                        }
                    }
                }
                '[' -> {
                    take('['); whitespace()
                    if (offset < text.length && text[offset] == ']') take(']') else {
                        while (true) {
                            value(); whitespace()
                            if (offset < text.length && text[offset] == ']') { take(']'); break }
                            take(',')
                        }
                    }
                }
                else -> {
                    val match = Regex("(?:true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)").find(text, offset)
                    require(match != null && match.range.first == offset) { "JSON 值无效" }
                    offset = match.range.last + 1
                }
            }
        }
        value(); whitespace(); require(offset == text.length) { "JSON 后含额外文字" }
    }
}
