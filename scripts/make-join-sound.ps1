<#
.SYNOPSIS
Makes app/src/main/res/raw/joined.wav, the "someone joined" notification sound: three soft bell notes
rising (G5, C6, E6), like a doorbell's welcome. Rerun after changing the notes below.
#>
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)

$rate = 22050
$seconds = 1.3
# Frequency (Hz) and start time (s) of each note.
$notes = @(@(783.99, 0.0), @(1046.50, 0.13), @(1318.51, 0.26))
$count = [int]($rate * $seconds)
$samples = New-Object double[] $count
foreach ($note in $notes) {
    $freq = $note[0]; $start = [int]($note[1] * $rate)
    for ($i = $start; $i -lt $count; $i++) {
        $t = ($i - $start) / $rate
        # A quick 5 ms attack, then a bell-like decay; a little of the octave above for shimmer.
        $env = [Math]::Min(1.0, $t / 0.005) * [Math]::Exp(-$t * 4.5)
        $samples[$i] += $env * ([Math]::Sin(2 * [Math]::PI * $freq * $t) + 0.25 * [Math]::Sin(4 * [Math]::PI * $freq * $t) * [Math]::Exp(-$t * 6))
    }
}
$peak = ($samples | ForEach-Object { [Math]::Abs($_) } | Measure-Object -Maximum).Maximum
# Fade out the last 50 ms so it ends without a click.
$fade = [int]($rate * 0.05)
for ($i = 0; $i -lt $fade; $i++) { $samples[$count - 1 - $i] *= $i / $fade }

$path = Join-Path $PWD 'app/src/main/res/raw/joined.wav'
New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
$stream = [IO.File]::Create($path)
$w = New-Object IO.BinaryWriter $stream
$w.Write([Text.Encoding]::ASCII.GetBytes('RIFF')); $w.Write([int](36 + $count * 2))
$w.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt ')); $w.Write([int]16)
$w.Write([int16]1); $w.Write([int16]1); $w.Write([int]$rate); $w.Write([int]($rate * 2)); $w.Write([int16]2); $w.Write([int16]16)
$w.Write([Text.Encoding]::ASCII.GetBytes('data')); $w.Write([int]($count * 2))
foreach ($s in $samples) { $w.Write([int16][Math]::Round($s / $peak * 0.6 * 32767)) }
$w.Close()
Write-Output "Wrote $path"
