package com.webtoapp.util






object JsStrings {

    
    fun escape(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when (ch) {
                '"', '\\', '\'' -> sb.append('\\').append(ch)
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    val code = ch.code
                    when {
                        code == 8 -> sb.append("\\b")   
                        code == 12 -> sb.append("\\f")  
                        code < 32 -> sb.append("\\u").append(String.format("%04x", code))
                        else -> sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    
    fun quote(s: String?): String = "\"" + escape(s) + "\""
}
