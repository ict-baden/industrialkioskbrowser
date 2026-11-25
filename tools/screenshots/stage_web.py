"""Stage the app's web pages (demo, error page) and the QR login example in a realistic state for screenshot rendering.

Usage: python tools/screenshots/stage_web.py <repo> <web-dir>
"""
import os
import sys

repo, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
assets = os.path.join(repo, "app", "src", "main", "assets")

# Demo page after three scans: the events go through the same window event the app dispatches.
demo = open(os.path.join(assets, "demo", "index.html"), encoding="utf-8").read()
staging = """<style>::-webkit-scrollbar { display: none; }</style>
<script>
(function () {
  var times = ['09:40:58', '09:41:05', '09:41:27'], i = 0;
  Date.prototype.toLocaleTimeString = function () { return times[i++] || '09:41:30'; };
  function scan(data, symbology) {
    window.dispatchEvent(new CustomEvent('industrialkioskbrowser:scan', {
      detail: { data: data, symbology: symbology, source: 'Zebra DataWedge' }, cancelable: true }));
  }
  scan('4006381000017', 'EAN13');
  scan('LP-03-12-B', 'CODE128');
  scan('4006381333931', 'EAN13');
  document.getElementById('article').value = '4006381333931';
  document.getElementById('location').focus();
})();
</script>
</body>"""
open(os.path.join(out, "demo.html"), "w", encoding="utf-8").write(demo.replace("</body>", staging))

status = open(os.path.join(assets, "status.html"), encoding="utf-8").read()
for key, value in {
    "title": "Server not found",
    "message": "The address could not be resolved. Check the Wi-Fi connection. (net::ERR_NAME_NOT_RESOLVED)",
    "url": "https://wms.example.com/goods-receipt",
    "color": "#0D47A1",
    "code": "Error code: IKB-0001",
    "retry": "Try again",
    "lang": "en",
}.items():
    status = status.replace("{{" + key + "}}", value)
open(os.path.join(out, "status.html"), "w", encoding="utf-8").write(status)

# QR login (docs/qr-login.md): a sign-in page with examples/qr-login/qr-login.js injected; render_web.mjs scans the code
# (same event as the app), types the PIN and, for "filled", confirms it. Test account only, PIN 482913.
login = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  ::-webkit-scrollbar { display: none; }
  body { margin: 0; font: 17px/1.45 Roboto, system-ui, sans-serif; color: #1c1d21; background: #eef1f6; }
  .top { padding: 34px 22px 30px; background: linear-gradient(135deg, #0d47a1, #1565c0); color: #fff; }
  .top b { display: block; font-size: 25px; }
  .top span { opacity: .85; font-size: 15px; }
  form { margin: -16px 16px 0; padding: 22px 20px 24px; background: #fff; border-radius: 18px; box-shadow: 0 10px 30px rgb(13 71 161 / .15); }
  label { display: block; margin: 0 0 16px; font-size: 13px; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; color: #5f6470; }
  input { box-sizing: border-box; width: 100%; margin-top: 6px; padding: 13px 14px; font: 18px Roboto, sans-serif; border: 2px solid #d7dbe3; border-radius: 12px; }
  button { width: 100%; margin-top: 6px; padding: 15px; font: 600 18px Roboto, sans-serif; color: #fff; background: #0d47a1; border: 0; border-radius: 12px; }
  .hint { display: flex; gap: 12px; align-items: center; margin: 18px 16px; padding: 14px 16px; background: #fff; border-radius: 14px; color: #3b3f48; font-size: 15px; }
  .hint i { flex: none; width: 34px; height: 34px; border-radius: 8px; background:
    conic-gradient(#1c1d21 25%, #fff 0 50%, #1c1d21 0 75%, #fff 0) 0 0 / 11px 11px; border: 3px solid #1c1d21; }
</style></head><body>
<div class="top"><b>Goods receipt</b><span>Warehouse management · sign in</span></div>
<form onsubmit="return false"><label>User name<input name="username" autocomplete="username"></label>
<label>Password<input type="password" name="password"></label><button type="submit">Sign in</button></form>
<p class="hint"><i></i>Scan the QR code on the back of your badge – or type your user name and password.</p>
<script>window.IKB_QR_LOGIN = { submit: false };</script>
<script>""" + open(os.path.join(repo, "examples", "qr-login", "qr-login.js"), encoding="utf-8").read() + """</script>
<script>window.qrLoginScan = function () {
  window.dispatchEvent(new CustomEvent('industrialkioskbrowser:scan', { cancelable: true, detail: { source: 'Zebra DataWedge',
    symbology: 'QRCODE', data: 'IKBL1.AQAB1MDZH_ovOnJBFsrCD76RkrAkmGvdX02uZJp8OjoNuJd9GoO7PRjwQYt5SO5qodTwq0giWBer58os-g3n4tuxKpfAwFEBHgzvFlJ33PY3HuXtm0rbnV7q4uk' } }));
};</script>
</body></html>"""
for name in ("qr-login-pin", "qr-login-filled"):
    open(os.path.join(out, name + ".html"), "w", encoding="utf-8").write(login)
print("staged", out)
