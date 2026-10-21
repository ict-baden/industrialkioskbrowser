package de.ictbaden.pforte.web

import de.ictbaden.pforte.scanner.ScanResult
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.net.URLEncoder

/** JavaScript snippets evaluated in the page and helpers for their results. */
object ScanScripts {

    const val SCAN_EVENT = "pforte:scan"

    /** Collects scanner-related meta tags as a JSON array of `[name, content]` pairs. */
    const val COLLECT_META_TAGS = """(function () {
  var out = [];
  document.querySelectorAll('meta[http-equiv], meta[name]').forEach(function (m) {
    var name = (m.getAttribute('http-equiv') || m.getAttribute('name') || '').toLowerCase();
    if (name === 'scanner' || name === 'scannernavigate' || name === 'scannerterminator') {
      out.push([name, m.getAttribute('content') || '']);
    }
  });
  return JSON.stringify(out);
})()"""

    fun scanPayload(scan: ScanResult): JSONObject = JSONObject().apply {
        put("data", scan.data)
        put("symbology", scan.symbology ?: JSONObject.NULL)
        put("source", scan.source)
        put("timestamp", scan.timestamp)
    }

    /**
     * Dispatches the cancelable `pforte:scan` event on `window`. Unless a listener calls
     * `preventDefault()`, the data is inserted into the focused text field the way a keyboard wedge
     * would. Evaluates to `"handled"`, `"inserted"`, `"event"` or `"nofield"`.
     */
    fun deliver(scan: ScanResult, injectIntoFocusedField: Boolean): String = """(function (scan, inject) {
  var event = new CustomEvent('$SCAN_EVENT', { detail: scan, cancelable: true });
  if (!window.dispatchEvent(event)) return 'handled';
  if (!inject) return 'event';
  var el = document.activeElement;
  for (;;) {
    if (el && el.shadowRoot && el.shadowRoot.activeElement) { el = el.shadowRoot.activeElement; continue; }
    if (el && el.tagName === 'IFRAME') {
      try { el = el.contentDocument.activeElement; continue; } catch (e) { return 'nofield'; }
    }
    break;
  }
  if (!el) return 'nofield';
  var view = el.ownerDocument.defaultView;
  var textTypes = /^(text|search|url|tel|email|password|number)?$/i;
  if (el.tagName === 'TEXTAREA' || (el.tagName === 'INPUT' && textTypes.test(el.getAttribute('type') || ''))) {
    if (el.readOnly || el.disabled) return 'nofield';
    var proto = el.tagName === 'TEXTAREA' ? view.HTMLTextAreaElement.prototype : view.HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    var start = null, end = null;
    try { start = el.selectionStart; end = el.selectionEnd; } catch (e) {}
    var value = el.value;
    var next = start === null ? value + scan.data : value.slice(0, start) + scan.data + value.slice(end);
    setter.call(el, next);
    if (start !== null) { try { el.setSelectionRange(start + scan.data.length, start + scan.data.length); } catch (e) {} }
    el.dispatchEvent(new view.Event('input', { bubbles: true }));
    el.dispatchEvent(new view.Event('change', { bubbles: true }));
    return 'inserted';
  }
  if (el.isContentEditable) {
    el.ownerDocument.execCommand('insertText', false, scan.data);
    return 'inserted';
  }
  return 'nofield';
})(${scanPayload(scan)}, $injectIntoFocusedField)"""

    /** Fills `%s` in a ScannerNavigate template: JS string literal for `javascript:`, URL-encoded otherwise. */
    fun applyTemplate(template: String, data: String): String =
        if (template.trimStart().startsWith("javascript:", ignoreCase = true)) {
            template.replace("%s", JSONObject.quote(data))
        } else {
            template.replace("%s", URLEncoder.encode(data, "UTF-8"))
        }

    /** What a filled ScannerNavigate template asks for. */
    sealed interface Target {
        data class Script(val js: String) : Target
        data class Address(val url: String) : Target
    }

    /**
     * Fills a ScannerNavigate template. Leading spaces in the meta tag are ignored, so
     * `" javascript:…"` still runs as script; relative targets (`/item?code=%s`) are resolved
     * against [currentUrl].
     */
    fun navigationTarget(template: String, data: String, currentUrl: String?): Target {
        val filled = applyTemplate(template.trim(), data)
        if (filled.startsWith("javascript:", ignoreCase = true)) return Target.Script(filled.substringAfter(':'))
        return Target.Address(runCatching { URI(currentUrl).resolve(filled).toString() }.getOrDefault(filled))
    }

    /** Decodes the value `evaluateJavascript` reports for a script that returns a string. */
    fun decodeStringResult(result: String?): String? {
        if (result == null || result == "null") return null
        return try {
            JSONTokener(result).nextValue() as? String
        } catch (_: Exception) {
            null
        }
    }

    fun parseMetaTags(json: String?): List<Pair<String, String>> {
        val text = decodeStringResult(json) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                val pair = array.optJSONArray(index) ?: return@mapNotNull null
                pair.optString(0) to pair.optString(1)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
