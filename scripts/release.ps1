<#
.SYNOPSIS
Builds a signed release APK and publishes it as a GitHub Release, where Obtainium picks it up. Also
builds the signed app bundle (.aab) to upload to Google Play.
The release notes are the "## Unreleased" section of CHANGELOG.md, which becomes "## v<Version> - <date>".

.EXAMPLE
./scripts/release.ps1 0.4.0
#>
param(
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)

if (git status --porcelain) { throw 'Commit or stash your changes first.' }
if ((git branch --show-current) -ne 'main') { throw 'Releases are made from main.' }
if (git tag -l "v$Version") { throw "v$Version already exists." }
if (-not (Test-Path "$HOME/.twodo/keystore.properties")) { throw 'Signing key not found in ~/.twodo/.' }
if (-not $env:JAVA_HOME) {
    # Any JDK 17+ works; Android Studio ships one.
    $env:JAVA_HOME = @(
        (Get-ChildItem 'C:\Program Files\Eclipse Adoptium\jdk-2*' -ErrorAction SilentlyContinue | Select-Object -Last 1).FullName,
        'C:\Program Files\Android\Android Studio\jbr'
    ) | Where-Object { $_ -and (Test-Path "$_\bin\java.exe") } | Select-Object -First 1
    if (-not $env:JAVA_HOME) { throw 'No JDK found; set JAVA_HOME to a JDK 17+.' }
}

# Release notes: everything under "## Unreleased" up to the next version heading.
# Read and written as UTF-8 (no BOM) explicitly: Windows PowerShell 5.1 would otherwise use the ANSI
# code page and mangle the notes. For the same reason this script itself sticks to ASCII.
$utf8 = New-Object System.Text.UTF8Encoding $false
$changelogPath = Join-Path $PWD CHANGELOG.md
$changelog = [IO.File]::ReadAllText($changelogPath, $utf8)
$match = [regex]::Match($changelog, '(?ms)^## Unreleased\s*\r?\n(.*?)(?=^## )')
$notes = $match.Groups[1].Value.Trim()
if (-not $match.Success -or -not $notes) { throw 'Add release notes under "## Unreleased" in CHANGELOG.md first.' }
$date = Get-Date -Format 'yyyy-MM-dd'
$dash = [char]0x2014
$changelog = $changelog.Substring(0, $match.Index) + "## Unreleased`n`n## v$Version $dash $date`n`n$notes`n`n" +
    $changelog.Substring($match.Index + $match.Length)
[IO.File]::WriteAllText($changelogPath, $changelog.TrimEnd() + "`n", $utf8)

# versionName comes from the argument; versionCode just has to keep increasing.
$props = Get-Content gradle.properties
$code = 1 + [int]($props | Select-String '^intack.versionCode=(\d+)').Matches[0].Groups[1].Value
$props = $props -replace '^intack.versionName=.*', "intack.versionName=$Version" `
                -replace '^intack.versionCode=.*', "intack.versionCode=$code"
Set-Content gradle.properties $props

./gradlew testDebugUnitTest assembleRelease bundleRelease
if ($LASTEXITCODE) {
    git checkout -- gradle.properties CHANGELOG.md
    throw 'Build failed; version and changelog left unchanged.'
}

$apk = "app/build/outputs/apk/release/Intack-v$Version.apk"
Copy-Item app/build/outputs/apk/release/app-release.apk $apk -Force
$bundle = "app/build/outputs/bundle/release/Intack-v$Version.aab"
# Keep this version's R8 map: without it, crash reports from this version can't be decoded (scripts/retrace.ps1).
New-Item -ItemType Directory -Force "$HOME/.twodo/mappings" | Out-Null
Copy-Item app/build/outputs/mapping/release/mapping.txt "$HOME/.twodo/mappings/v$Version-mapping.txt" -Force
Copy-Item app/build/outputs/bundle/release/app-release.aab $bundle -Force

git commit -q -am "Release v$Version"
git tag -a "v$Version" -m "Intack v$Version"
git push origin main "v$Version"
if ($LASTEXITCODE) { throw 'Push failed; the release commit and tag are local only.' }

$notesFile = New-TemporaryFile
[IO.File]::WriteAllText($notesFile.FullName, $notes, $utf8)
gh release create "v$Version" $apk --title "Intack v$Version" --notes-file $notesFile
if ($LASTEXITCODE) { Remove-Item $notesFile; throw 'Creating the GitHub release failed; retry with: gh release create ...' }
Write-Host "Released v$Version (versionCode $code)."

# Google Play's internal testing track, for the testers (scripts/play-upload.js). GitHub is done by now,
# so a failure here only needs this step retried.
node scripts/play-upload.js upload $bundle "$code ($Version)" $notesFile.FullName
if ($LASTEXITCODE) { Write-Warning "Google Play upload failed; retry with: node scripts/play-upload.js upload $bundle `"$code ($Version)`" <notes-file>" }
Remove-Item $notesFile
