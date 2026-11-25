#!/usr/bin/env python3
"""Test server for the scan queue (scanner.queue): a goods-receipt form that takes a while to save.

    python3 tools/scan-queue/server.py [--host 0.0.0.0] [--port 8767]

Pages (all take ?delay=<ms>, the time the server needs to save, default 1500):

  /?mode=form   classic form: Enter posts it, the server waits, then redirects to the next page
  /?mode=js     JavaScript saves with fetch(), then loads the next page; &busy=1 reports
                scanner.busy()/ready() through the JavaScript API (needs the default port 80 or 443,
                the API is not available on other ports)
  /?mode=spa    JavaScript saves with fetch() and stays on the page (single-page app); &busy=1 as above
  &focus=<ms>   the field gets the focus only this long after the page loaded (tests "no field yet")

  /log          what the server saved, in order, with a check of SCAN0001, SCAN0002, … (plain text)
  /reset        clears the log

Only for testing: no authentication, keeps everything in memory. See docs/testing-scan-queue.md.
"""
import argparse
import html
import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlencode, urlparse

LOCK = threading.Lock()
SAVED = []  # (time, value, mode)
SEQ = re.compile(r"^SCAN(\d+)$")


def summary():
    with LOCK:
        values = [v for _, v, _ in SAVED]
    numbers = [int(m.group(1)) for m in map(SEQ.match, values) if m]
    seen = set()
    dup = sum(1 for n in numbers if n in seen or seen.add(n))
    ooo = sum(1 for a, b in zip(numbers, numbers[1:]) if b < a)
    top = max(numbers, default=0)
    missing = [n for n in range(1, top + 1) if n not in seen]
    other = [v for v in values if not SEQ.match(v)]
    return {"saved": len(values), "highest": top, "missing": missing, "duplicates": dup,
            "outOfOrder": ooo, "other": other, "values": values}


PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<style>
  body {{ font-family: system-ui, sans-serif; margin: 16px; color: #111; background: #fff; }}
  input {{ width: 100%; font-size: 26px; padding: 8px; box-sizing: border-box; }}
  .state {{ margin: 8px 0; padding: 8px; border-radius: 6px; background: #e8f5e9; }}
  .saving {{ background: #fff3e0; }}
  ol {{ font-family: monospace; font-size: 14px; padding-left: 28px; max-height: 40vh; overflow: auto; }}
  .bad {{ color: #b71c1c; font-weight: bold; }}
  small {{ color: #555; }}
</style>
</head>
<body>
<h3>Goods receipt – page {page}</h3>
<small>mode={mode} · save {delay} ms · busy={busy} · focus after {focus} ms</small>
<form id="form" method="post" action="/save?{query}">
  <input id="item" name="item" autocomplete="off" placeholder="scan the next item">
</form>
<div id="state" class="state">ready</div>
<p>Saved by the server: <b>{saved}</b> · missing: <span class="{missing_class}">{missing}</span> ·
duplicates: <span class="{dup_class}">{dup}</span> · out of order: <span class="{ooo_class}">{ooo}</span>{other}</p>
<ol reversed>{items}</ol>
<script>
(function () {{
  var mode = {mode_js}, delay = {delay}, useBusy = {busy_js}, focusAfter = {focus};
  var form = document.getElementById('form'), item = document.getElementById('item');
  var state = document.getElementById('state');
  var api = window.IndustrialKioskBrowser;
  if (useBusy && !api) state.textContent = 'busy=1, but the JavaScript API is not available on this page (port?)';
  window.addEventListener('industrialkioskbrowser:scan', function (e) {{
    if (e.detail.data === 'RESET') {{ e.preventDefault(); fetch('/reset').then(function () {{ location.reload(); }}); }}
  }});
  if (mode !== 'form') {{
    form.addEventListener('submit', function (e) {{
      e.preventDefault();
      var value = item.value;
      state.textContent = 'saving ' + value + ' …';
      state.className = 'state saving';
      if (useBusy && api) api.scanner.busy();
      fetch('/api/save?' + {query_js}, {{ method: 'POST', body: value }}).then(function () {{
        if (mode === 'js') {{ location.href = '/?' + {query_js} + '&page=' + ({page} + 1); return; }}
        // spa: stay, empty the field for the next scan
        var li = document.createElement('li'); li.textContent = value;
        document.querySelector('ol').prepend(li);
        item.value = ''; item.focus();
        state.textContent = 'ready'; state.className = 'state';
        if (useBusy && api) api.scanner.ready();
      }});
    }});
  }}
  setTimeout(function () {{ item.focus(); }}, focusAfter);
}})();
</script>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args))

    def params(self):
        q = parse_qs(urlparse(self.path).query)
        mode = q.get("mode", ["form"])[0]
        if mode not in ("form", "js", "spa"):
            mode = "form"
        num = lambda key, default: max(0, min(60000, int(q.get(key, [default])[0] or default)))
        return {"mode": mode, "delay": num("delay", 1500), "busy": 1 if q.get("busy", ["0"])[0] == "1" else 0,
                "focus": num("focus", 0), "page": num("page", 1)}

    def send(self, code, body, ctype="text/html; charset=utf-8", headers=None):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length).decode("utf-8", "replace") if length else ""

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/log":
            s = summary()
            text = ("saved=%(saved)d highest=%(highest)d missing=%(m)s duplicates=%(duplicates)d "
                    "outOfOrder=%(outOfOrder)d other=%(o)s\n") % dict(s, m=",".join(map(str, s["missing"])) or "none",
                                                                     o=json.dumps(s["other"]))
            return self.send(200, text + "\n".join(s["values"]) + "\n", "text/plain; charset=utf-8")
        if path == "/reset":
            with LOCK:
                SAVED.clear()
            return self.send(200, "reset\n", "text/plain; charset=utf-8")
        if path != "/":
            return self.send(404, "not found\n", "text/plain; charset=utf-8")
        p = self.params()
        s = summary()
        query = urlencode({"mode": p["mode"], "delay": p["delay"], "busy": p["busy"], "focus": p["focus"]})
        title = "IKB-Q saved=%d miss=%d dup=%d ooo=%d other=%d" % (
            s["saved"], len(s["missing"]), s["duplicates"], s["outOfOrder"], len(s["other"]))
        items = "".join("<li>%s</li>" % html.escape(v) for v in reversed(s["values"][-200:]))
        other = (" · <span class=\"bad\">unexpected values: %s</span>" % html.escape(", ".join(s["other"][-5:]))) if s["other"] else ""
        self.send(200, PAGE.format(
            title=title, page=p["page"], mode=p["mode"], delay=p["delay"], busy=p["busy"], focus=p["focus"],
            query=html.escape(query + "&page=%d" % p["page"]), saved=s["saved"],
            missing=",".join(map(str, s["missing"][:30])) or "none", missing_class="bad" if s["missing"] else "",
            dup=s["duplicates"], dup_class="bad" if s["duplicates"] else "",
            ooo=s["outOfOrder"], ooo_class="bad" if s["outOfOrder"] else "", other=other, items=items,
            mode_js=json.dumps(p["mode"]), busy_js="true" if p["busy"] else "false", query_js=json.dumps(query),
        ))

    def do_POST(self):
        path = urlparse(self.path).path
        p = self.params()
        if path == "/save":  # classic form post
            value = parse_qs(self.body()).get("item", [""])[0]
        elif path == "/api/save":  # fetch() from mode js/spa
            value = self.body()
        else:
            return self.send(404, "not found\n", "text/plain; charset=utf-8")
        time.sleep(p["delay"] / 1000)
        with LOCK:
            SAVED.append((time.time(), value, p["mode"]))
        if path == "/api/save":
            return self.send(200, "{}", "application/json")
        query = urlencode({"mode": p["mode"], "delay": p["delay"], "busy": p["busy"], "focus": p["focus"], "page": p["page"] + 1})
        self.send(303, "", headers={"Location": "/?" + query})


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="0.0.0.0", help="address to listen on (default: all)")
    ap.add_argument("--port", type=int, default=8767)
    a = ap.parse_args()
    print("scan queue test server on http://%s:%d/  (log: /log, reset: /reset)" % (a.host, a.port))
    ThreadingHTTPServer((a.host, a.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
