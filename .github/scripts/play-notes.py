"""Release notes for Google Play from a CHANGELOG section: plain text, at most 500 characters.

    python3 .github/scripts/play-notes.py <changes.md | ->   (writes to stdout)

Play shows the notes as plain text and cuts them off at 500 characters, so Markdown is removed (bold, italics, code,
links become their text, list items become bullets, hard-wrapped list items are joined) and longer notes end with an
ellipsis at a word boundary.
"""
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')
LIMIT = 500

src = sys.stdin if sys.argv[1] == '-' else open(sys.argv[1], encoding='utf-8')
lines = []
for raw in src.read().splitlines():
    line = raw.replace('**', '').replace('`', '')
    line = re.sub(r'(?<![\w*])\*(\S[^*]*?)\*(?![\w*])', r'\1', line)  # *italics*
    line = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', line)
    bullet = re.match(r'^\s*[-*] ', line) is not None
    line = re.sub(r'^\s*[-*] ', '• ', line)
    line = re.sub(r'^#+\s*', '', line).rstrip()
    continuation = not bullet and raw.startswith(' ') and line.strip() != ''
    if continuation and lines and lines[-1]:
        lines[-1] += ' ' + line.strip()
    else:
        lines.append(line.strip())

text = re.sub(r'\n{3,}', '\n\n', '\n'.join(lines)).strip()
if len(text) > LIMIT:
    text = text[:LIMIT - 2].rsplit(' ', 1)[0].rstrip(' ,;:–-') + ' …'
print(text)
