<#
.SYNOPSIS
    Aktualisiert das rollierende Release „latest“ an Ort und Stelle – die Download-Links bleiben gültig.

.DESCRIPTION
    Für GitHub Actions (braucht GH_TOKEN, GITHUB_REPOSITORY und GITHUB_SHA). Früher wurde „latest“ gelöscht und neu
    angelegt: Dann fehlten die Dateien bei jedem Push ein paar Sekunden lang, und scheiterte das Anlegen, war der
    Download ganz weg. Jetzt:
    1. Veröffentlicht wird nur ein Commit, der in main liegt und nicht älter ist als der, auf den „latest“ schon zeigt
       (ein neu gestarteter alter Lauf ändert nichts). Ist main schon weiter und hat dabei Workflows geändert, übernimmt
       der nachfolgende Lauf: GitHub lässt das Actions-Token keine Releases auf Commits setzen, deren Workflows vom
       Stand in main abweichen.
    2. Alle Dateien werden zuerst als „neu-<Name>“ hochgeladen. Scheitert das, bleiben die Dateien unter den festen
       Namen unverändert; die neu-*-Reste räumt der nächste Lauf weg.
    3. Erst dann zeigt der Tag auf den neuen Commit, und jede Datei ersetzt ihre gleichnamige alte (löschen, dann
       umbenennen – dazwischen liegt ein API-Aufruf, etwa eine halbe Sekunde; beides wird bei Fehlern wiederholt).
    4. Titel und Text werden aktualisiert, dann Dateien entfernt, die der neue Build nicht mehr hat (z. B. alte Namen
       mit Versionsnummer).
    Gibt es den Release noch nicht, wird er angelegt.

    Solange es kein Release aus einem Tag (v1.2.3) gibt, ist „latest“ ein normales Release mit dem Vermerk „Latest“:
    GitHub zeigt Vorabversionen nicht in der Seitenleiste des Repos, dort stünde sonst nur „Create a new release“.
    Gibt es ein Release aus einem Tag, wird „latest“ zur Vorabversion, und das Tag-Release behält den Platz.

.EXAMPLE
    ./.github/scripts/release-latest.ps1 -Notes $notes -Title "Latest Build (main) – 0.1.0-ci.9"
#>
param(
    [Parameter(Mandatory)] [string] $Notes,
    [string] $Title = 'Latest Build (main)',
    [string] $Tag = 'latest',
    [string] $Branch = 'main',
    [string] $Dist = 'dist'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false # Exit-Codes von gh werden unten selbst geprüft
$repo = $env:GITHUB_REPOSITORY
$sha = $env:GITHUB_SHA
if (-not $repo -or -not $sha) { throw 'GITHUB_REPOSITORY und GITHUB_SHA müssen gesetzt sein.' }

# gh mit Fehlerprüfung, Ausgabe als ein Text
function Invoke-Gh {
    $out = & gh @args
    if ($LASTEXITCODE -ne 0) { throw "gh $($args -join ' ') ist fehlgeschlagen (Exit $LASTEXITCODE)." }
    $out -join "`n"
}

# Wie Invoke-Gh, aber „gibt es nicht“ (HTTP 404) ist kein Fehler – dann ohne Ausgabe. Alles andere (5xx, Netz,
# Anmeldung) bricht ab, damit ein kurzer Ausfall nicht als „fehlt“ gilt.
function Find-Gh {
    $all = & gh @args 2>&1
    $code = $LASTEXITCODE
    if ($code -eq 0) { return @($all | Where-Object { $_ -isnot [Management.Automation.ErrorRecord] }) -join "`n" }
    $errors = @($all | Where-Object { $_ -is [Management.Automation.ErrorRecord] }) -join "`n"
    if ($errors -notmatch 'HTTP 404') { throw "gh $($args -join ' ') ist fehlgeschlagen (Exit $code): $errors" }
}

# Bis zu drei Versuche für kurze Aufrufe, die man gefahrlos wiederholen kann
function Invoke-Retry([scriptblock] $action) {
    for ($try = 1; ; $try++) {
        try { return & $action }
        catch {
            if ($try -ge 3) { throw }
            Write-Host "Versuch $try fehlgeschlagen ($_), nächster in $(3 * $try) s."
            Start-Sleep -Seconds (3 * $try)
        }
    }
}

function Get-Assets([long] $releaseId) {
    @(Invoke-Gh api "repos/$repo/releases/$releaseId/assets?per_page=100" | ConvertFrom-Json)
}

# Schon weg (404) zählt als erledigt – auch wenn ein früherer Versuch nur die Antwort verloren hat
function Remove-Asset($asset) {
    Invoke-Retry { Find-Gh api -X DELETE "repos/$repo/releases/assets/$($asset.id)" | Out-Null }
}

function Move-Tag {
    if (-not $current) { Invoke-Gh api -X POST "repos/$repo/git/refs" -f "ref=refs/tags/$Tag" -f "sha=$sha" | Out-Null }
    elseif ($current -ne $sha) {
        Invoke-Retry { Invoke-Gh api -X PATCH "repos/$repo/git/refs/tags/$Tag" -f "sha=$sha" -F force=true | Out-Null }
    }
}

function Skip([string] $why) {
    Write-Host "::notice::$why"
    $global:LASTEXITCODE = 0
}

$files = @(Get-ChildItem $Dist -File)
if ($files.Count -eq 0) { throw "Keine Dateien in $Dist." }

# 1. Nur den neuesten Stand von main veröffentlichen. Die Dateiliste des Vergleichs endet bei 300 Dateien; umbenannte
#    Workflows stehen nur unter previous_filename.
$compareJq = '{status: .status, capped: (((.files // []) | length) >= 300), workflows: [.files[]? | select((.filename | ' +
    'startswith(".github/workflows/")) or ((.previous_filename // "") | startswith(".github/workflows/"))) | .filename]}'
$onBranch = Invoke-Retry { Invoke-Gh api "repos/$repo/compare/$sha...$Branch" --jq $compareJq } | ConvertFrom-Json
if ($onBranch.status -notin 'identical', 'ahead') {
    Skip "Commit $sha liegt nicht (mehr) in $Branch – nichts veröffentlicht."
    return
}
if ($onBranch.status -eq 'ahead' -and ($onBranch.capped -or @($onBranch.workflows).Count -gt 0)) {
    $why = if ($onBranch.capped) { 'mit über 300 geänderten Dateien – Workflow-Änderungen nicht prüfbar' } else { "und ändert Workflows ($($onBranch.workflows -join ', '))" }
    Skip "$Branch ist schon weiter $why – der nachfolgende Lauf veröffentlicht."
    return
}
$current = Invoke-Retry { Find-Gh api "repos/$repo/git/ref/tags/$Tag" --jq '.object.sha' }
if ($current -and $current -ne $sha) {
    # base...head: „ahead“ heißt, der Tag ist dem Commit dieses Laufs voraus
    $status = Invoke-Retry { Invoke-Gh api "repos/$repo/compare/$sha...$current" --jq '.status' }
    if ($status -eq 'ahead') {
        Skip "'$Tag' steht schon auf einem neueren Commit ($current) – dieser Lauf ($sha) veröffentlicht nichts."
        return
    }
}

$release = Invoke-Retry { Find-Gh api "repos/$repo/releases/tags/$Tag" } | ConvertFrom-Json

$taggedJq = '[.[] | select(.tag_name != "' + $Tag + '" and (.draft | not) and (.prerelease | not))] | length'
$tagged = [int](Invoke-Retry { Invoke-Gh api "repos/$repo/releases?per_page=100" --jq $taggedJq })
$kind = if ($tagged -gt 0) { @('--prerelease', '--latest=false') } else { @('--prerelease=false', '--latest') }
Write-Host ("'$Tag' wird " + $(if ($tagged -gt 0) { "Vorabversion ($tagged Release(s) aus Tags vorhanden)." } else { "normales Release mit dem Vermerk 'Latest'." }))
if (-not $release) {
    Write-Host "Release '$Tag' gibt es noch nicht – wird angelegt."
    # Steht der Tag noch (Release im Browser gelöscht), übergeht GitHub --target – also erst den Tag setzen
    if ($current) { Move-Tag }
    & gh release create $Tag $files.FullName -R $repo @kind --target $sha --title $Title --notes $Notes
    if ($LASTEXITCODE -ne 0) { throw "Release '$Tag' ließ sich nicht anlegen (Exit $LASTEXITCODE)." }
    return
}

# 2. Alles hochladen – die Dateien unter den festen Namen bleiben dabei unverändert
$staging = Join-Path ([IO.Path]::GetTempPath()) ("latest-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $staging | Out-Null
try {
    foreach ($file in $files) {
        $upload = Join-Path $staging "neu-$($file.Name)"
        Copy-Item $file.FullName $upload
        Invoke-Retry {
            # Rest eines abgebrochenen Laufs oder Versuchs mit demselben Namen
            foreach ($partial in @(Get-Assets $release.id | Where-Object name -eq "neu-$($file.Name)")) { Remove-Asset $partial }
            Invoke-Gh release upload $Tag $upload -R $repo | Out-Null
        }
    }
}
finally {
    Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
}
$assets = Invoke-Retry { Get-Assets $release.id }
$missing = @($files | Where-Object { "neu-$($_.Name)" -notin $assets.name })
if ($missing.Count -gt 0) { throw "Hochgeladen, aber nicht im Release: $($missing.Name -join ', ')" }

# 3. Tag auf den neuen Commit, dann Datei für Datei tauschen
Move-Tag
foreach ($file in $files) {
    $new = $assets | Where-Object name -eq "neu-$($file.Name)" | Select-Object -First 1
    foreach ($old in @($assets | Where-Object name -eq $file.Name)) { Remove-Asset $old }
    Invoke-Retry { Invoke-Gh api -X PATCH "repos/$repo/releases/assets/$($new.id)" -f "name=$($file.Name)" | Out-Null }
    Write-Host "Ersetzt: $($file.Name)"
}

# 4. Titel und Text gleich zu den getauschten Dateien. --target hält das Ziel des Releases beim neuen Commit – sonst
#    prüft GitHub gegen den alten Stand. Danach weg, was der neue Build nicht mehr hat (auch neu-*-Reste früherer Läufe).
Invoke-Retry { Invoke-Gh release edit $Tag -R $repo --title $Title --notes $Notes @kind --target $sha | Out-Null }
$keep = @($files.Name)
foreach ($stale in @(Invoke-Retry { Get-Assets $release.id } | Where-Object { $_.name -notin $keep })) {
    Remove-Asset $stale
    Write-Host "Entfernt: $($stale.name)"
}

# 5. GitHub zeigt beim Release das Datum der Veröffentlichung – beim Aktualisieren an Ort und Stelle bliebe das der
#    allererste Build („vor 8 Stunden“). Kurz Entwurf und gleich wieder veröffentlicht, steht dort dieser Build. Die
#    Dateien sind dazwischen für einen API-Aufruf nicht erreichbar, wie beim Tausch. Bleibt das Veröffentlichen hängen,
#    schlägt der Lauf fehl, statt einen unsichtbaren Entwurf stehen zu lassen.
$makeLatest = if ($tagged -gt 0) { 'false' } else { 'true' }
Invoke-Retry { Invoke-Gh api -X PATCH "repos/$repo/releases/$($release.id)" -F draft=true | Out-Null }
Invoke-Retry { Invoke-Gh api -X PATCH "repos/$repo/releases/$($release.id)" -F draft=false -f make_latest=$makeLatest | Out-Null }
Write-Host "'$Tag' zeigt jetzt auf $sha."
$global:LASTEXITCODE = 0
