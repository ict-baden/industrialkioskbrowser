"""Stage the app's web pages (demo, error page) in a realistic state for screenshot rendering.

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
    window.dispatchEvent(new CustomEvent('enterprisebrowser:scan', {
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
}.items():
    status = status.replace("{{" + key + "}}", value)
open(os.path.join(out, "status.html"), "w", encoding="utf-8").write(status)
print("staged", out)
