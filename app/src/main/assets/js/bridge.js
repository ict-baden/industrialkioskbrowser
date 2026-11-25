// Injected at document start into allow-listed origins. Wraps the native message channel
// IndustrialKioskBrowserNative in a promise-based API: window.IndustrialKioskBrowser.
(function () {
  'use strict';
  if (window.IndustrialKioskBrowser || typeof IndustrialKioskBrowserNative === 'undefined') return;

  var channel = IndustrialKioskBrowserNative;
  var nextId = 0;
  var pending = {};
  var busy = 0; // number of the last busy() call while busy, 0 after ready()
  var busyCalls = 0;

  // Read by the scan delivery (ScanScripts.deliver): busy() inside a scan handler holds the next scan
  // back even before the native side has the message.
  Object.defineProperty(window, '__industrialKioskBrowserBusy', { get: function () { return busy; } });

  channel.onmessage = function (event) {
    var message;
    try { message = JSON.parse(event.data); } catch (e) { return; }
    var call = pending[message.id];
    if (!call) return;
    delete pending[message.id];
    if (message.error) call.reject(new Error(message.error));
    else call.resolve(message.result);
  };

  function call(cmd, args) {
    return new Promise(function (resolve, reject) {
      var id = ++nextId;
      pending[id] = { resolve: resolve, reject: reject };
      channel.postMessage(JSON.stringify({ id: id, cmd: cmd, args: args || {} }));
    });
  }

  window.IndustrialKioskBrowser = Object.freeze({
    beep: function (kind) { return call('beep', { kind: kind === 'error' ? 'error' : 'ok' }); },
    vibrate: function (ms) { return call('vibrate', { ms: ms || 200 }); },
    toast: function (text) { return call('toast', { text: String(text) }); },
    deviceInfo: function () { return call('deviceInfo'); },
    scanner: Object.freeze({
      enable: function () { return call('scanner.enable'); },
      disable: function () { return call('scanner.disable'); },
      // trigger() toggles, trigger(true/false) starts/stops, trigger({mode: 'text'}) opens text recognition (OCR).
      trigger: function (start) {
        if (start && typeof start === 'object') return call('scanner.trigger', { start: null, mode: String(start.mode || '') });
        return call('scanner.trigger', { start: start === undefined ? null : !!start });
      },
      status: function () { return call('scanner.status'); },
      // busy(): hold further scans back (saving, loading data) until ready(); timeoutMs is a safety limit.
      busy: function (timeoutMs) {
        busy = ++busyCalls;
        return call('scanner.busy', { timeoutMs: typeof timeoutMs === 'number' && timeoutMs > 0 ? timeoutMs : null });
      },
      ready: function () {
        busy = 0;
        return call('scanner.ready');
      }
    }),
    // handler(scan, event); call event.preventDefault() to stop insertion into the focused field.
    onScan: function (handler) {
      window.addEventListener('industrialkioskbrowser:scan', function (event) { handler(event.detail, event); });
    }
  });
})();
