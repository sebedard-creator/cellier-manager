$ErrorActionPreference = 'Stop'
$companionRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$python = Join-Path $companionRoot '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    py -3.11 -m venv (Join-Path $companionRoot '.venv')
    & $python -m pip install -e "$companionRoot[test]"
}

& $python -m cellier_companion.main
