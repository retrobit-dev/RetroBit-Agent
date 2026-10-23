package dev.retrobit.agent.preview

import dev.retrobit.agent.data.DesignSystem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Injects the RetroBit runtime into generated pages:
 *  - design tokens as a final stylesheet (so the Design tab wins over page CSS)
 *  - window.RetroBitDB backed by the on-device SQLite database
 *  - window.__RETROBIT_ENV__ with secrets the user explicitly exposed
 *  - a lightweight toast helper
 */
object PreviewInjector {

    fun inject(html: String, design: DesignSystem, exposed: Map<String, String>): String {
        val runtime = "<script>${runtimeJs(design, exposed)}</script>"
        val style = "<style id=\"rb-design-fallback\">${escapeCss(design.cssVariables())}</style>"
        var output = html
        output = insertAfterHead(output, runtime)
        output = insertBeforeBodyEnd(output, style)
        return output
    }

    fun runtimePatch(design: DesignSystem, exposed: Map<String, String>): String =
        "(function(){try{${runtimeJs(design, exposed)}}catch(e){console.error(e)}})();"

    private fun runtimeJs(design: DesignSystem, exposed: Map<String, String>): String {
        val css = jsonString(design.cssVariables())
        val env = envJson(exposed)
            .replace("<", "\\u003c")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        val bg = jsonString(design.background)
        val text = jsonString(design.textColor)
        val font = jsonString("${design.font}, system-ui, sans-serif")
        return """
            (function(){
              // Some WebView configurations refuse file:// storage. Fall back to an
              // in-memory store so generated apps that use localStorage keep working.
              function rbInstallStorage(name){
                try {
                  var probe = window[name];
                  probe.setItem('__rb_probe', '1');
                  probe.removeItem('__rb_probe');
                  return;
                } catch (e) { }
                try {
                  var data = {};
                  var api = {
                    getItem: function(k){ return Object.prototype.hasOwnProperty.call(data, String(k)) ? data[String(k)] : null; },
                    setItem: function(k, v){ data[String(k)] = String(v); },
                    removeItem: function(k){ delete data[String(k)]; },
                    clear: function(){ data = {}; },
                    key: function(i){ var keys = Object.keys(data); return i >= 0 && i < keys.length ? keys[i] : null; }
                  };
                  Object.defineProperty(api, 'length', { get: function(){ return Object.keys(data).length; } });
                  Object.defineProperty(window, name, { configurable: true, value: api });
                } catch (e) { }
              }
              rbInstallStorage('localStorage');
              rbInstallStorage('sessionStorage');
              var css = $css;
              var style = document.getElementById('rb-live-design');
              if (!style) { style = document.createElement('style'); style.id = 'rb-live-design'; }
              style.textContent = css;
              if (document.body) { document.body.appendChild(style); }
              else if (document.documentElement) { document.documentElement.appendChild(style); }
              window.__RETROBIT_ENV__ = $env;
              window.__RETROBIT_NATIVE__ = typeof RetroBitNative !== 'undefined';
              var rbBg = $bg, rbText = $text, rbFont = $font;
              window.RetroBitDB = {
                query: function(sql, params){
                  return new Promise(function(resolve, reject){
                    try {
                      var raw = RetroBitNative.query(String(sql), JSON.stringify(params || []));
                      resolve(JSON.parse(raw));
                    } catch(e){ reject(e); }
                  });
                },
                exec: function(sql, params){ return window.RetroBitDB.query(sql, params); },
                env: function(){ return window.__RETROBIT_ENV__ || {}; }
              };
              window.RetroBitToast = function(message){
                var el = document.getElementById('__rb_toast');
                if (!el) {
                  el = document.createElement('div');
                  el.id = '__rb_toast';
                  el.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translate(-50%,16px);font-size:13px;font-weight:600;padding:10px 16px;border-radius:2px;border:1px solid rgba(255,255,255,.28);box-shadow:0 12px 30px -16px #000;letter-spacing:.02em;z-index:2147483647;opacity:0;transition:.3s;pointer-events:none;max-width:86vw;text-align:center;';
                  el.style.background = rbBg;
                  el.style.color = rbText;
                  el.style.fontFamily = rbFont;
                  (document.body || document.documentElement).appendChild(el);
                }
                el.textContent = String(message);
                requestAnimationFrame(function(){ el.style.opacity = '1'; el.style.transform = 'translate(-50%,0)'; });
                clearTimeout(window.__rbToastTimer);
                window.__rbToastTimer = setTimeout(function(){
                  el.style.opacity = '0'; el.style.transform = 'translate(-50%,16px)';
                }, 2200);
              };
            })();
        """.trimIndent()
    }

    private fun envJson(exposed: Map<String, String>): String = buildJsonObject {
        exposed.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }.toString()

    private fun jsonString(value: String): String =
        JsonPrimitive(value).toString()
            .replace("<", "\\u003c")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

    private fun escapeCss(css: String): String = css.replace("</style", "<\\/style")

    private fun insertAfterHead(html: String, snippet: String): String {
        val match = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html) ?: return "$snippet\n$html"
        val index = match.range.last + 1
        return html.substring(0, index) + "\n" + snippet + "\n" + html.substring(index)
    }

    private fun insertBeforeBodyEnd(html: String, snippet: String): String {
        val index = html.lastIndexOf("</body>", ignoreCase = true)
        return if (index >= 0) html.substring(0, index) + snippet + "\n" + html.substring(index)
        else html + "\n" + snippet
    }
}
