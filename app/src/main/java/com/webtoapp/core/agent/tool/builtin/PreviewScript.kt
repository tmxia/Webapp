package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonParser






internal object PreviewScript {
    const val MAX_SCRIPT_CHARS = 12_000
    const val MAX_RESULT_CHARS = 16_000
    const val MAX_WAIT_MS = 1_500

    fun acceptableHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }

    fun wrap(body: String): String = """
        (function(){
          try {
            var value = (function(){
              $body
            })();
            return JSON.stringify({ok:true, value: value === undefined ? null : value});
          } catch (e) {
            return JSON.stringify({ok:false, error: String(e && e.message || e)});
          }
        })()
    """.trimIndent()

    



    fun snapshot(): String = """
        (function(){
          var nodes = [];
          var list = document.querySelectorAll('a,button,input,textarea,select,summary,h1,h2,h3,h4,[role="button"],[role="link"],[role="tab"],[role="checkbox"],[role="textbox"],[onclick]');
          var n = 0;
          for (var i = 0; i < list.length && n < 60; i++) {
            var el = list[i];
            var r = el.getBoundingClientRect();
            if (r.width < 2 || r.height < 2) continue;
            el.setAttribute('data-wta-n', String(n));
            var name = (el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.innerText || el.value || '').replace(/\s+/g, ' ').trim().slice(0, 80);
            nodes.push({n:n, tag:el.tagName.toLowerCase(), role:el.getAttribute('role') || '', name:name, id:el.id || '', type:el.getAttribute('type') || ''});
            n++;
          }
          return JSON.stringify({url:location.href, title:document.title, nodes:nodes});
        })()
    """.trimIndent()

    
    fun unwrap(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return try {
            val element = JsonParser.parseString(raw)
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element.asString else raw
        } catch (_: Exception) {
            raw
        }
    }

    fun truncate(text: String): String {
        if (text.length <= MAX_RESULT_CHARS) return text
        return text.take(MAX_RESULT_CHARS) + "\n…[truncated]"
    }
}
