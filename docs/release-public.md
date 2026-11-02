[← README](../README.md)

# Publishing the repository

How the repository goes from private to public with a **fresh history**: one commit that holds
the complete state of `main`, and nothing else. Written for the repository owner.

> **Warning – destructive, owner only.** The steps below throw away the Git history, tags,
> releases and branches of this repository on GitHub. Only the repository owner does this, once,
> and only after a complete private mirror exists in a safe place:
>
> ```bash
> git clone --mirror https://github.com/ict-baden/industrialkioskbrowser.git industrialkioskbrowser-archiv.git
> ```
>
> Keep that mirror offline (or in a private repository). It is the only complete copy of the
> development history afterwards.

- [Why](#why)
- [What stays, what goes](#what-stays-what-goes)
- [Step 1 – Prepare](#step-1--prepare)
- [Step 2 – Open pull requests](#step-2--open-pull-requests)
- [Step 3 – Build the fresh repository](#step-3--build-the-fresh-repository)
- [Step 4 – Publish the fresh history](#step-4--publish-the-fresh-history)
  - [Way A – new repository (recommended)](#way-a--new-repository-recommended)
  - [Way B – same repository](#way-b--same-repository)
- [Step 5 – First release and `latest`](#step-5--first-release-and-latest)
- [Step 6 – Check, then switch to public](#step-6--check-then-switch-to-public)
- [What a fresh history does not do](#what-a-fresh-history-does-not-do)
- [Checklist](#checklist)

## Why

The development history up to 0.7.0 was written in a private repository and contains things that
must not become public: working notes about third-party products, personal e-mail addresses as
commit authors, and files that were deleted later but are still in old commits. Rewriting the
history in place (`git filter-repo`) is not enough: tags (`v0.3.0`, `v0.3.1`, `v0.5.0`, `latest`),
old branches and the pull request refs (`refs/pull/*`) that GitHub keeps for every pull request
would still hold the old objects, and anyone with read access could fetch them by hash.

Therefore the public repository starts with **one new commit** that contains the current state
of `main` – identical files, identical tree hash – and no history. The old repository stays
private as the archive.

## What stays, what goes

| | Before | After |
|---|---|---|
| Files of `main` | 180 files, tree `c5aeb94…` | the same files, the same tree hash |
| Commits | 41, five author addresses | 1, author and committer `ICT Baden <info@ict-baden.de>` |
| Tags, releases | `v0.3.0`, `v0.3.1`, `v0.5.0`, `latest` | none; `v0.7.0` and `latest` are created again by the CI (step 5) |
| Branches | `main` and old feature branches | `main` |
| Pull requests, issues | 15 pull requests, their text and diffs | Way A: stay in the private archive. Way B: become public with the repository |
| Secrets, variables, settings | in the repository | Way A: created again. Way B: unchanged |

## Step 1 – Prepare

1. **Archive mirror** – see the warning above. Check it: `git -C industrialkioskbrowser-archiv.git log --oneline | wc -l`.
2. **Clone on `main`, clean and current:** `git -C <clone> status` shows nothing, `git -C <clone> pull --ff-only` changes nothing.
3. **Content check of `main`** – the script does it again, but read the hits yourself once:
   ```bash
   git grep -n -E "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[a-z]{2,}" HEAD | grep -v -E "ict-baden.de|noreply|example\."
   git grep -n -i -E "<private e-mail domains|customer names|device names>" HEAD
   git ls-files | grep -i -E "<customer names|device names>"
   ```
   The former product names (*Pforte*, *EnterpriseBrowser*) are mentioned on purpose in the
   changelog, in `CLAUDE.md`, the README, the deployment and limitations pages and in the code
   that still accepts the old retry links. Everything else that you do not want public is fixed
   on `main` **before** the cut, by an ordinary pull request.
4. **Blocklist outside the repository** – a small text file, for example
   `~/ict-baden-privat/fresh-history-blocklist.txt`, with the paths and words that must not exist
   in the new repository (format at the top of [`tools/fresh-history.sh`](../tools/fresh-history.sh)).
   Do not put it into the repository: it would disclose exactly what it hides. Keep it next to the
   archive mirror.
5. **Your own Git identity:** set `git config user.email` in your clones to your GitHub
   `noreply` address and switch on *Settings → Emails → Keep my email addresses private* and
   *Block command line pushes that expose my email* on GitHub, so no private address gets into
   the new history.

## Step 2 – Open pull requests

Every open pull request is based on the old history. There are two ways to carry one over; pick
one per pull request before the cut.

**Merge first (preferred for finished work).** Merge the pull request into `main` as usual. The
cut then contains it, and the merge commit with its author disappears into the single new commit.
If two pull requests touch the same file (for example `.github/workflows/build.yml`), merge one,
let the other rebase, then merge it.

**Carry over as a patch (for unfinished work or bot pull requests).** Before the cut, save the
diff; after the cut, apply it as a new commit on a new branch of the fresh repository:

```bash
gh pr diff <number> > pr-<number>.patch                  # before the cut, from the old repository
git -C <fresh-clone> switch -c <branch>
git -C <fresh-clone> apply --3way pr-<number>.patch       # not `git am`: that would keep the old author
git -C <fresh-clone> commit -a -m "<message>"
git -C <fresh-clone> push -u origin <branch> && gh pr create …
```

Dependabot pull requests need neither: close them. Dependabot opens them again on the fresh
repository on its next run (*Insights → Dependency graph → Dependabot → Check for updates*).

Pull requests that are left open when the repository is published (Way B) are public, including
their description and every comment. Read them before you switch.

## Step 3 – Build the fresh repository

```bash
tools/fresh-history.sh <clone-on-main> <new-directory> ~/ict-baden-privat/fresh-history-blocklist.txt
```

The script only reads the clone and never contacts a remote. It

- refuses a clone that is not on `main`, not clean, or behind `origin/main`;
- creates a new repository in `<new-directory>` with one orphan commit: the complete content of
  `main` (`git archive`, so file modes such as the executable `gradlew` are kept), author and
  committer `ICT Baden <info@ict-baden.de>`, a German commit message that names the version and
  says the development history stays in the private archive;
- checks the result and aborts if anything is off: exactly one commit and one ref
  (`refs/heads/main`), no tags, no other author or committer address, no trace of the blocked
  paths, no blocked word in any file or file name (except the few files that mention the former
  product names on purpose), no e-mail address besides the project addresses, `git fsck` clean,
  same file count, **same tree hash** as `main` of the clone.

Read the output. Only continue when it ends with `all checks passed`. The new repository has no
remote yet; nothing has left your machine.

## Step 4 – Publish the fresh history

### Way A – new repository (recommended)

A new GitHub repository never had the old objects, the old pull request refs or the old issues.
The old repository is renamed, stays private and becomes the archive. The new repository takes
over the name `ict-baden/industrialkioskbrowser`, so every link in README, `SECURITY.md`,
`SUPPORT.md`, the issue templates and the store texts keeps working.

Recommended because: the pull request refs of the old repository can only be removed by GitHub
Support (see [below](#what-a-fresh-history-does-not-do)); the old issues and pull requests,
including their text, would otherwise become public together with the repository; and the result
can be checked completely from outside (`git ls-remote` shows one branch, nothing else). The price
is about half an hour of settings work, listed here.

1. **Rename the old repository** (*Settings → General → Repository name*), for example to
   `industrialkioskbrowser-archiv`, and leave it **private**. Do not archive it yet: archiving
   makes it read-only, which is fine later, but first close or carry over the pull requests.
2. **Create the new repository from the fresh directory and push `main`:**
   ```bash
   gh repo create ict-baden/industrialkioskbrowser --private --source <new-directory> --push \
     --description "Industrial Kiosk Browser – kiosk browser for rugged Android devices (Zebra, Honeywell …): allow list, kiosk mode, DataWedge scanning, MDM configuration. Open source (MIT) by ICT-Baden." \
     --homepage https://ict-baden.de/produkte/industrial-kiosk-browser/
   ```
   The repository stays **private** until step 6.
3. **Secrets** (*Settings → Secrets and variables → Actions*), from the release manager's safe,
   never from a chat or a ticket:
   `ANDROID_KEYSTORE` (Base64 of the keystore), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
   optional `ANDROID_KEY_PASSWORD`; for the Play pipeline `PLAY_SERVICE_ACCOUNT_JSON` and the
   variables `PLAY_RELEASERS`, `PLAY_ROLLOUT` ([Google Play](play-store.md)).
   The first push already started a build of `main` without secrets; it builds and tests but
   publishes nothing (`SIGN` is false). That is expected.
4. **Topics** (`android`, `android-kiosk`, `barcode-scanner`, `datawedge`, `honeywell`,
   `industrial-browser`, `kiosk`, `kiosk-browser`, `kiosk-mode`, `mdm`, `rugged`, `webview`,
   `zebra`): `gh repo edit ict-baden/industrialkioskbrowser --add-topic android,android-kiosk,…`.
5. **Features:** Issues on, Wiki off, Projects off, Discussions off (`SUPPORT.md` points to
   issues); `gh repo edit --enable-wiki=false --enable-projects=false --enable-discussions=false`.
   Merge options as before (squash, merge, rebase; delete branches on merge as you like).
6. **Labels:** a new repository only has GitHub's default labels. Create the project labels again:
   `fehler` and `wunsch` (used by the issue templates), `voll-bauen` (full CI matrix, guideline 04),
   `test` (test finding, not a confirmed bug): `gh label create <name> --description … --color …`.
7. **Security** (*Settings → Code security*): Dependabot alerts and security updates on.
   *Secret scanning*, *push protection* and *private vulnerability reporting* are free for public
   repositories only – switch them on right after step 6, and check that `SECURITY.md`'s link
   `security/advisories/new` works.
8. **Branch protection or ruleset for `main`** – also only available once the repository is
   public (free plan): require a pull request, require the `Build` check, no force pushes, no
   deletions. Do this right after step 6.
9. **Carry over the pull requests** you did not merge (step 2), then close the remaining ones in
   the old repository and archive it (*Settings → Archive this repository*). Keep it private.
10. **Links elsewhere:** `ict-baden-projekte/projekte.json` (`repo`), the project website, the
    privacy page, the Play Console listing (website, source link) – they keep the name, so only
    check them; update any link that pointed at a commit, tag or pull request of the old repository.

### Way B – same repository

The repository keeps its secrets, variables, labels and settings; the history is replaced by a
force push and everything else is deleted by hand. GitHub keeps the pull request refs
(`refs/pull/*`) itself and does not let you delete them – only GitHub Support can, by ticket,
after all other refs are clean ([GitHub: Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository),
section *Fully removing the data from GitHub*). Until then every old commit can still be fetched
by anyone who knows its hash, and the old pull requests and issues become public with the
repository. Only take this way if the new repository is not possible for you.

1. Close or merge every pull request (step 2). Close every issue you do not want public, or
   delete it (*Delete issue* in the issue's sidebar; deletion is final).
2. Delete all releases, with their tags:
   ```bash
   gh release list --limit 100
   gh release delete latest --cleanup-tag --yes
   gh release delete v0.5.0 --cleanup-tag --yes
   gh release delete v0.3.1 --cleanup-tag --yes
   gh release delete v0.3.0 --cleanup-tag --yes
   ```
3. Delete all remaining tags and all branches except `main`:
   ```bash
   git ls-remote --tags origin                   # should be empty now; otherwise:
   git push origin --delete v0.3.0 v0.3.1 v0.5.0 latest
   git ls-remote --heads origin | grep -v refs/heads/main
   git push origin --delete <every other branch>
   ```
   Pull request branches that GitHub protects while the pull request is open can only be deleted
   after the pull request is closed. Temporarily switch off branch protection on `main` if there
   is any.
4. Force-push the fresh `main` – this is the destructive step:
   ```bash
   git -C <new-directory> remote add origin https://github.com/ict-baden/industrialkioskbrowser.git
   git -C <new-directory> push --force origin main
   ```
5. Open a ticket at the [GitHub Support portal](https://support.github.com/) asking to remove
   the cached views and the pull request refs of the old history and to run garbage collection.
   Give the repository name, the number of pull requests (15) and the old `main` hash
   (`f63c5c0`). Do not switch to public before Support has confirmed.
6. Dependabot opens its pull requests again on its next run; you can trigger it under
   *Insights → Dependency graph → Dependabot*.

## Step 5 – First release and `latest`

Both ways end here, with a private repository whose `main` is the single fresh commit and whose
secrets are in place.

1. `CHANGELOG.md`: the *Unreleased* section becomes `## [0.7.0] – <date>` (pull request as
   usual). Replace the sentence in [Limitations → Releases and updates](limitations.md#releases-and-updates)
   that says there is no signed APK yet, once the first signed release exists.
2. Tag the release – the tag is the release decision and is set by a person, never by a tool:
   ```bash
   git -C <clone> switch main && git -C <clone> pull --ff-only
   git -C <clone> tag -a v0.7.0 -m "Industrial Kiosk Browser 0.7.0"
   git -C <clone> push origin v0.7.0
   ```
3. The CI builds, signs and creates the release `v0.7.0` with APK, App Bundle, SBOM and
   checksums ([build.yml](../.github/workflows/build.yml)). Because the secrets are set, the tag
   run also starts a build of `main` that creates the rolling `latest` release – or the next push
   to `main` does. `latest` is a release *and* a tag; the CI moves both.
4. Play pipeline: once the pull request that adds the `play` job and `play-rollout.yml` is on
   `main` and `PLAY_SERVICE_ACCOUNT_JSON` is set, follow [Google Play → Publishing a version](play-store.md#publishing-a-version).
   The very first App Bundle is uploaded by hand.

## Step 6 – Check, then switch to public

Check the remote, not your clone. Every command must show only the new history:

```bash
git ls-remote https://github.com/ict-baden/industrialkioskbrowser.git
#   expected: HEAD and refs/heads/main on the same hash, refs/tags/v0.7.0 and refs/tags/latest
#   after step 5, nothing else – no old branches, no refs/pull/* (Way A)
gh api repos/ict-baden/industrialkioskbrowser/tags --jq '.[].name'          # v0.7.0, latest
gh api 'repos/ict-baden/industrialkioskbrowser/pulls?state=all' --jq length  # Way A: only new ones
gh api 'repos/ict-baden/industrialkioskbrowser/issues?state=all' --jq length
gh api repos/ict-baden/industrialkioskbrowser/commits --jq length            # 1 plus release commits
gh release list -R ict-baden/industrialkioskbrowser                          # v0.7.0, latest
git clone --mirror https://github.com/ict-baden/industrialkioskbrowser.git check.git
git -C check.git log --all --format='%h %ae %s'                              # only ICT Baden commits
git -C check.git count-objects -v                                            # no unexpected packs
```

If everything is as expected: *Settings → General → Danger Zone → Change visibility → Public*.
Right after that: secret scanning, push protection, private vulnerability reporting, branch
protection (Way A, steps 7 and 8). The next CI run on `main` also produces the build provenance
attestation, which only works for public repositories.

## What a fresh history does not do

- **Clones keep the old history.** Everyone who cloned the private repository (both maintainers,
  their worktrees, any machine that ever pulled) still has every old commit. Delete those clones
  or re-clone; do not merge or rebase anything from an old clone into the new repository – it
  would bring the old commits back. The CI does not keep a copy; its checkouts are discarded.
- **GitHub removes unreachable objects late.** After Way B the old objects stay on GitHub's
  servers until garbage collection, and remain reachable through `refs/pull/*` and cached views
  until GitHub Support removes them. With Way A the new repository never had them; the old
  repository keeps them, privately, as intended.
- **The archive is the only history.** Keep the mirror from step 1 as long as the project exists;
  `git blame` and the reasons for old decisions live there and in `CHANGELOG.md`.

## Checklist

- [ ] Private mirror of the old repository made and checked (`git clone --mirror`)
- [ ] Content check of `main` done; hits that must go were fixed on `main` before the cut
- [ ] Blocklist written and stored outside the repository
- [ ] Open pull requests merged, saved as patches or closed
- [ ] `tools/fresh-history.sh` ran and ended with `all checks passed`
- [ ] Way A: old repository renamed, new repository created and pushed (private)
- [ ] Way A: secrets and variables set, topics, features, labels, Dependabot
- [ ] Way B: releases, tags and branches deleted, `main` force-pushed, Support ticket sent
- [ ] Carried-over pull requests opened again on the fresh repository
- [ ] `CHANGELOG.md` has the `[0.7.0]` section; tag `v0.7.0` pushed; release has APK, App Bundle, SBOM
- [ ] `latest` release exists from a build of `main`
- [ ] Remote checked with `git ls-remote`, `gh api … /tags`, `… /pulls?state=all`, mirror clone
- [ ] Switched to public; secret scanning, push protection, vulnerability reporting, branch protection on
- [ ] Old clones on all machines deleted or re-cloned
- [ ] Links in `ict-baden-projekte/projekte.json`, website, Play Console checked
