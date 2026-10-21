// Injected at document start into allow-listed origins. Wraps the native message channel
// PforteNative in a promise-based API: window.Pforte.
(function () {
  'use strict';
  if (window.Pforte || typeof PforteNative === 'undefined') return;

  var channel = PforteNative;
  var nextId = 0;
  var pending = {};

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

  window.Pforte = Object.freeze({
    beep: function (kind) { return call('beep', { kind: kind === 'error' ? 'error' : 'ok' }); },
    vibrate: function (ms) { return call('vibrate', { ms: ms || 200 }); },
    toast: function (text) { return call('toast', { text: String(text) }); },
    deviceInfo: function () { return call('deviceInfo'); },
    scanner: Object.freeze({
      enable: function () { return call('scanner.enable'); },
      disable: function () { return call('scanner.disable'); },
      trigger: function (start) { return call('scanner.trigger', { start: start === undefined ? null : !!start }); },
      status: function () { return call('scanner.status'); }
    }),
    // handler(scan, event); call event.preventDefault() to stop insertion into the focused field.
    onScan: function (handler) {
      window.addEventListener('pforte:scan', function (event) { handler(event.detail, event); });
    }
  });
})();
