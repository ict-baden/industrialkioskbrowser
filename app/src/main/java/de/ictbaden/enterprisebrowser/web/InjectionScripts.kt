package de.ictbaden.enterprisebrowser.web

import de.ictbaden.enterprisebrowser.config.AppConfig
import de.ictbaden.enterprisebrowser.config.Injection
import de.ictbaden.enterprisebrowser.config.InjectionTiming
import de.ictbaden.enterprisebrowser.config.MatchType
import de.ictbaden.enterprisebrowser.config.ViewportMode
import de.ictbaden.enterprisebrowser.config.ViewportSettings
import org.json.JSONObject

/**
 * Builds the JavaScript that applies configured injections and viewport overrides. Every script
 * checks the URL (and optionally page source) itself, so the same script can be registered once
 * as a document-start script and also be evaluated after a page load.
 */
object InjectionScripts {

    /**
     * Script for [injections] of the given [timing]; `null` when there is nothing to inject.
     * Contents are resolved through [config] (`res:<name>`).
     */
    fun build(injections: List<Injection>, timing: InjectionTiming, config: AppConfig): String? {
        val blocks = injections.filter { it.timing == timing }.mapNotNull { block(it, config) }
        if (blocks.isEmpty()) return null
        return "(function () {\n${HELPERS}\n${blocks.joinToString("\n")}\n})();"
    }

    private fun block(injection: Injection, config: AppConfig): String? {
        val actions = buildList {
            config.content(injection.css)?.let { add("eb.css(${q(it)});") }
            config.content(injection.html)?.let { add("eb.html(${q(it)}, ${q(injection.htmlPosition.key)});") }
            config.content(injection.js)?.let { add("eb.js(${q(it)});") }
        }
        if (actions.isEmpty()) return null
        val frameCheck = if (injection.allFrames) "true" else "window === window.top"
        val urlCheck = when (injection.matchType) {
            MatchType.ALL -> "true"
            MatchType.CONTAINS -> "location.href.indexOf(${q(injection.urlMatch)}) >= 0"
            MatchType.PREFIX -> "location.href.indexOf(${q(injection.urlMatch)}) === 0"
            MatchType.REGEX -> "new RegExp(${q(injection.urlMatch)}).test(location.href)"
        }
        val sourceCheck = injection.sourceContains?.let { "eb.source(${q(it)})" } ?: "true"
        return """if ($frameCheck && $urlCheck) eb.whenReady(${injection.sourceContains != null}, function () {
  if (!($sourceCheck)) return;
  ${actions.joinToString("\n  ")}
});"""
    }

    /**
     * Script that rewrites the page's viewport meta tag, or `null` when the WebView settings alone
     * implement [viewport].
     */
    fun viewport(viewport: ViewportSettings): String? {
        val content = viewport.metaOverride ?: when (viewport.mode) {
            ViewportMode.FIXED -> "width=${viewport.width}"
            else -> return null
        }
        return """(function (content) {
  if (window !== window.top) return;
  function apply() {
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'viewport';
      (document.head || document.documentElement).appendChild(meta);
    }
    if (meta.content !== content) meta.content = content;
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})(${q(content)});"""
    }

    private fun q(text: String) = JSONObject.quote(text)

    private const val HELPERS = """var eb = {
  whenReady: function (needsDom, fn) {
    if (!needsDom || document.readyState !== 'loading') fn();
    else document.addEventListener('DOMContentLoaded', fn);
  },
  source: function (text) { return document.documentElement.outerHTML.indexOf(text) >= 0; },
  css: function (text) {
    var style = document.createElement('style');
    style.setAttribute('data-enterprisebrowser', '');
    style.textContent = text;
    (document.head || document.documentElement).appendChild(style);
  },
  js: function (text) {
    try { (0, eval)(text); } catch (e) { console.error('EnterpriseBrowser injection failed', e); }
  },
  html: function (text, position) {
    eb.whenReady(true, function () {
      var template = document.createElement('template');
      template.innerHTML = text;
      var fragment = template.content;
      fragment.querySelectorAll('script').forEach(function (old) {
        var script = document.createElement('script');
        script.textContent = old.textContent;
        old.replaceWith(script);
      });
      if (position === 'head') document.head.appendChild(fragment);
      else if (position === 'bodyStart') document.body.insertBefore(fragment, document.body.firstChild);
      else document.body.appendChild(fragment);
    });
  }
};"""
}
