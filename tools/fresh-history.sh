#!/usr/bin/env bash
# fresh-history.sh – build a NEW repository with exactly one commit that holds the complete
# current state of `main` of a local clone. Nothing else is carried over: no history, no tags,
# no other branches, no author e-mail addresses other than the project address.
#
# Usage:
#   tools/fresh-history.sh <local-clone> <target-dir> [<blocklist-file>]
#
#   <local-clone>     an up-to-date clone of the repository, checked out on `main`, clean
#   <target-dir>      does not exist yet or is empty; the new repository is created there
#   <blocklist-file>  optional, kept OUTSIDE the repository (see "Blocklist" below)
#
# The script never touches the source clone (read only) and never talks to a remote. Pushing the
# new repository is a separate, deliberate step described in docs/release-public.md.
#
# How the single commit is built:
#   `git archive main` of the source is unpacked into the target and added with the exact file
#   list of the source (`git ls-files`), so the resulting tree object is byte-for-byte the same as
#   the tree of the source `main`. The script proves that by comparing the tree hashes.
#
# Blocklist (optional third argument):
#   A plain text file with one entry per line. Lines starting with `#` are ignored.
#     path <relative path>          the path must not appear anywhere in the new history
#     word <term>                   case-insensitive; must not appear in any file of HEAD,
#                                   nor in any file name
#     allow <term> <file> [<file>…] files that may contain <term> on purpose
#   Built-in entries (always active): see BUILTIN_RULES below. Keep the project-specific
#   entries outside the repository: a public blocklist would itself disclose what it hides.
#
# Exit code 0 means every check passed. Any failed check aborts with a message on stderr.

set -euo pipefail

AUTHOR_NAME="ICT Baden"
AUTHOR_EMAIL="info@ict-baden.de"
APP_NAME="Industrial Kiosk Browser"

# Built-in rules. Former product names are mentioned on purpose in a few files (changelog, rename
# notes, legacy retry links); everything else must be clean.
BUILTIN_RULES='
word googlemail
word @gmail.
word pforte
allow googlemail tools/fresh-history.sh
allow @gmail. tools/fresh-history.sh
allow pforte tools/fresh-history.sh docs/release-public.md CHANGELOG.md CLAUDE.md README.md docs/deployment.md docs/limitations.md app/src/main/java/de/ictbaden/industrialkioskbrowser/web/StatusPage.kt app/src/test/java/de/ictbaden/industrialkioskbrowser/StoreTextsTest.kt
'

# E-mail addresses that may appear in the tree (regex, case-insensitive).
ALLOWED_MAIL_RE='@ict-baden\.de|noreply|@example\.(com|org|net)|@users\.noreply\.github\.com'

die() { printf 'fresh-history: ERROR: %s\n' "$*" >&2; exit 1; }
note() { printf 'fresh-history: %s\n' "$*"; }
ok() { printf 'fresh-history:   ok  %s\n' "$*"; }

[ $# -ge 2 ] && [ $# -le 3 ] || { sed -n '2,20p' "$0" >&2; exit 2; }

SRC=$1
DEST=$2
BLOCKLIST=${3:-}

# ---------------------------------------------------------------------------------------------
# 1. Check the source clone
# ---------------------------------------------------------------------------------------------
[ -d "$SRC" ] || die "source clone '$SRC' does not exist"
SRC=$(cd "$SRC" && pwd -P)
git -C "$SRC" rev-parse --is-inside-work-tree > /dev/null 2>&1 || die "'$SRC' is not a git work tree"

branch=$(git -C "$SRC" symbolic-ref --short -q HEAD || true)
[ "$branch" = main ] || die "source clone is on '${branch:-detached HEAD}', expected 'main'"

if [ -n "$(git -C "$SRC" status --porcelain --untracked-files=normal)" ]; then
  git -C "$SRC" status --short >&2
  die "source work tree is not clean (commit, stash or remove the files above)"
fi

# The cut must be made from the latest main. If the clone has an origin, it must match.
if git -C "$SRC" rev-parse --verify -q origin/main > /dev/null; then
  if [ "$(git -C "$SRC" rev-parse main)" != "$(git -C "$SRC" rev-parse origin/main)" ]; then
    die "local main differs from origin/main – run 'git pull --ff-only' first (or push your commits)"
  fi
fi

SRC_COMMIT=$(git -C "$SRC" rev-parse main)
SRC_TREE=$(git -C "$SRC" rev-parse 'main^{tree}')
SRC_FILES=$(git -C "$SRC" ls-files | wc -l | tr -d ' ')

# Version from app/build.gradle.kts (the fallback value of versionName), overridable
VERSION=${VERSION:-$(grep -E '^\s*versionName = ' "$SRC/app/build.gradle.kts" \
  | grep -oE '"[0-9]+\.[0-9]+\.[0-9]+"' | tr -d '"' | head -1 || true)}
[ -n "$VERSION" ] || die "could not read the version from app/build.gradle.kts (set VERSION=x.y.z)"

note "source: $SRC"
note "  main = $SRC_COMMIT, tree = $SRC_TREE, $SRC_FILES files, version $VERSION"

# ---------------------------------------------------------------------------------------------
# 2. Read the rules
# ---------------------------------------------------------------------------------------------
BLOCK_PATHS=()
BLOCK_WORDS=()
declare -a ALLOW_RULES=()   # entries "term<TAB>file"

read_rules() {
  local kind rest term files f
  while read -r kind rest; do
    case "$kind" in
      ''|'#'*) continue ;;
      path) BLOCK_PATHS+=("$rest") ;;
      word) BLOCK_WORDS+=("$rest") ;;
      allow)
        term=${rest%% *}; files=${rest#* }
        for f in $files; do ALLOW_RULES+=("$term"$'\t'"$f"); done ;;
      *) die "unknown rule '$kind' in blocklist" ;;
    esac
  done
}
read_rules <<< "$BUILTIN_RULES"
if [ -n "$BLOCKLIST" ]; then
  [ -f "$BLOCKLIST" ] || die "blocklist '$BLOCKLIST' not found"
  case "$(cd "$(dirname "$BLOCKLIST")" && pwd -P)" in
    "$SRC"|"$SRC"/*) die "the blocklist must be kept outside the repository" ;;
  esac
  read_rules < "$BLOCKLIST"
  note "blocklist: $BLOCKLIST (${#BLOCK_PATHS[@]} paths, ${#BLOCK_WORDS[@]} words incl. built-ins)"
else
  note "no blocklist given – only the built-in rules are checked"
fi

is_allowed() {  # is_allowed <term> <file>
  local r
  for r in "${ALLOW_RULES[@]+"${ALLOW_RULES[@]}"}"; do
    [ "$r" = "$1"$'\t'"$2" ] && return 0
  done
  return 1
}

# ---------------------------------------------------------------------------------------------
# 3. Create the new repository with one orphan commit
# ---------------------------------------------------------------------------------------------
if [ -e "$DEST" ]; then
  [ -d "$DEST" ] || die "target '$DEST' exists and is not a directory"
  [ -z "$(ls -A "$DEST")" ] || die "target directory '$DEST' is not empty"
else
  mkdir -p "$DEST"
fi
DEST=$(cd "$DEST" && pwd -P)
case "$DEST" in "$SRC"|"$SRC"/*) die "target must not be inside the source clone" ;; esac

# Local config only: no global hooks, no signing, no line-ending conversion, no template files.
G=(git -C "$DEST" -c core.hooksPath=/dev/null -c core.autocrlf=false -c commit.gpgsign=false -c tag.gpgsign=false)

git -c init.defaultBranch=main init -q --template= "$DEST"
"${G[@]}" config core.autocrlf false
"${G[@]}" config user.name "$AUTHOR_NAME"
"${G[@]}" config user.email "$AUTHOR_EMAIL"

# Unpack the exact content of main (file modes included) …
git -C "$SRC" archive --format=tar main | tar -x -C "$DEST"
# … and stage exactly the files main tracks (update-index ignores .gitignore and keeps the file modes).
git -C "$SRC" ls-files -z | "${G[@]}" update-index --add -z --stdin

# gradlew must stay executable in Git, otherwise the CI fails (CLAUDE.md).
if [ "$(git -C "$SRC" ls-files -s gradlew | cut -d' ' -f1)" = 100755 ]; then
  "${G[@]}" update-index --chmod=+x gradlew
else
  note "WARNING: gradlew is not executable in the source – fix the source first (git update-index --chmod=+x gradlew)"
fi
[ "$("${G[@]}" ls-files -s gradlew | cut -d' ' -f1)" = 100755 ] || die "gradlew is not executable (mode 100755) in the new index"

MSG_FILE=$(mktemp)
trap 'rm -f "$MSG_FILE"' EXIT
cat > "$MSG_FILE" <<EOF
$APP_NAME $VERSION – frischer Verlauf für die Veröffentlichung

Vollständiger Stand von main ($SRC_COMMIT) als einzelner Commit, Baum $SRC_TREE.

Der Entwicklungsverlauf von Oktober 2026 (0.1.0 bis 0.7.0) bleibt im privaten Archiv
von ICT-Baden und ist nicht Teil dieses Repositories.
EOF

export GIT_AUTHOR_NAME="$AUTHOR_NAME" GIT_AUTHOR_EMAIL="$AUTHOR_EMAIL"
export GIT_COMMITTER_NAME="$AUTHOR_NAME" GIT_COMMITTER_EMAIL="$AUTHOR_EMAIL"
"${G[@]}" commit -q --no-verify --no-gpg-sign -F "$MSG_FILE"
unset GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL

NEW_COMMIT=$("${G[@]}" rev-parse HEAD)
note "created $DEST"
note "  main = $NEW_COMMIT"

# ---------------------------------------------------------------------------------------------
# 4. Checks – every one of them must pass
# ---------------------------------------------------------------------------------------------
note "checking the new repository"
fail=0
bad() { printf 'fresh-history:   FAIL %s\n' "$*" >&2; fail=1; }

# 4a. Exactly one commit, exactly one ref, no tags
count=$("${G[@]}" rev-list --all --count)
[ "$count" = 1 ] && ok "exactly one commit" || bad "$count commits, expected 1"

refs=$("${G[@]}" for-each-ref --format='%(refname)')
[ "$refs" = refs/heads/main ] && ok "only refs/heads/main" || bad "unexpected refs: $(echo "$refs" | tr '\n' ' ')"

tags=$("${G[@]}" tag)
[ -z "$tags" ] && ok "no tags" || bad "tags present: $(echo "$tags" | tr '\n' ' ')"

# 4b. Authors and committers
mails=$("${G[@]}" log --all --format='%ae%n%ce' | sort -u)
[ "$mails" = "$AUTHOR_EMAIL" ] && ok "author/committer e-mail is only $AUTHOR_EMAIL" \
  || bad "unexpected author/committer e-mails: $(echo "$mails" | tr '\n' ' ')"
names=$("${G[@]}" log --all --format='%an%n%cn' | sort -u)
[ "$names" = "$AUTHOR_NAME" ] && ok "author/committer name is only '$AUTHOR_NAME'" \
  || bad "unexpected author/committer names: $(echo "$names" | tr '\n' ' ')"

# 4c. Blocked paths never existed
for p in "${BLOCK_PATHS[@]+"${BLOCK_PATHS[@]}"}"; do
  if [ -n "$("${G[@]}" log --all --oneline -- "$p")" ]; then bad "history mentions blocked path $p"
  elif "${G[@]}" cat-file -e "HEAD:$p" 2> /dev/null; then bad "blocked path $p exists in HEAD"
  else ok "no trace of $p"; fi
done

# 4d. Blocked words: file contents (case-insensitive) and file names
for w in "${BLOCK_WORDS[@]+"${BLOCK_WORDS[@]}"}"; do
  hits=$("${G[@]}" grep -il -F -e "$w" HEAD -- . | sed 's/^HEAD://' || true)
  unexpected=""
  for f in $hits; do is_allowed "$w" "$f" || unexpected="$unexpected $f"; done
  if [ -n "$unexpected" ]; then
    bad "'$w' found in:$unexpected"
    "${G[@]}" grep -in -F -e "$w" HEAD -- $unexpected | sed 's/^HEAD://' | head -20 >&2
  else
    ok "'$w' only in allowed files ($(echo "$hits" | wc -w | tr -d ' '))"
  fi
  namehits=$("${G[@]}" ls-files | grep -i -F -e "$w" || true)
  [ -z "$namehits" ] || bad "'$w' in file names: $(echo "$namehits" | tr '\n' ' ')"
done

# 4e. E-mail addresses in the tree: only project addresses, no private ones
mailhits=$("${G[@]}" grep -n -I -E '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' HEAD -- . \
  | sed 's/^HEAD://' | grep -v -E 'this@' \
  | grep -v -i -E "$ALLOWED_MAIL_RE" || true)
if [ -z "$mailhits" ]; then ok "no e-mail addresses besides the project addresses"
else bad "e-mail addresses found:"; printf '%s\n' "$mailhits" | head -20 >&2; fi

# 4f. Integrity, file count, tree identity
if "${G[@]}" fsck --strict --no-dangling > /dev/null 2>&1; then ok "git fsck"; else bad "git fsck reports problems"; "${G[@]}" fsck --strict >&2 || true; fi

NEW_FILES=$("${G[@]}" ls-files | wc -l | tr -d ' ')
[ "$NEW_FILES" = "$SRC_FILES" ] && ok "file count $NEW_FILES = $SRC_FILES" || bad "file count $NEW_FILES, source has $SRC_FILES"

NEW_TREE=$("${G[@]}" rev-parse 'HEAD^{tree}')
[ "$NEW_TREE" = "$SRC_TREE" ] && ok "tree hash identical ($NEW_TREE)" || bad "tree hash $NEW_TREE differs from source $SRC_TREE"

[ -z "$("${G[@]}" status --porcelain)" ] && ok "work tree clean" || bad "work tree of the new repository is not clean"

if [ "$fail" != 0 ]; then
  die "checks failed – do not push $DEST"
fi

cat <<EOF

fresh-history: all checks passed.

  New repository: $DEST
  Commit:         $NEW_COMMIT  ($APP_NAME $VERSION)
  Tree:           $NEW_TREE  (identical to $SRC main $SRC_COMMIT)

Next steps are described in docs/release-public.md. Nothing has been pushed.
EOF
