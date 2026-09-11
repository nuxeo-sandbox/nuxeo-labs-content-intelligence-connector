/*
 * cic-kd-conversation-libs.js
 *
 * Dynamically loads external libraries used by the cic-kd-conversation element:
 *   - marked.js (MIT) -- converts markdown to HTML
 *   - DOMPurify (Apache/MPL) -- sanitizes HTML to prevent XSS
 *
 * This code is in a separate .js file because Nuxeo Studio Designer's
 * HTML validator rejects document.createElement('script') inside
 * a <script> block (it interprets the string as a nested HTML tag).
 *
 * Once loaded, the libraries are available as window.marked and
 * window.DOMPurify. The cic-kd-conversation element checks for their
 * presence at usage time and falls back to plain text if unavailable.
 */
(function() {
  'use strict';

  function loadScript(url, name) {
    if (window[name]) {
      return;
    }
    
    var script = document.createElement('script');
    script.src = url;
    script.onerror = function() {
      console.warn('cic-kd-conversation-libs: Could not load ' + name + ' from ' + url);
    };
    document.head.appendChild(script);
  }

  /*
   * Versions are pinned on purpose. Without a version jsDelivr resolves to "latest", so a major release of
   * either library could break the UI without any change on our side. Keep these URLs in sync with the ones
   * in cic-kd-ask-question-shared.html: loading two different builds of marked would race on window.marked.
   */
  loadScript('https://cdn.jsdelivr.net/npm/marked@15.0.12/marked.min.js', 'marked');
  loadScript('https://cdn.jsdelivr.net/npm/dompurify@3.2.4/dist/purify.min.js', 'DOMPurify');
})();
