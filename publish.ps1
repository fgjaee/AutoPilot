# Run this script from the unzipped AutoPilot project directory.
# Prerequisites: Git and GitHub CLI (gh), logged in as fgjaee.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git is required: https://git-scm.com/downloads' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI is required: https://cli.github.com/' }
gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Sign in once with: gh auth login' }
if (-not (Test-Path .git)) { git init -b main }
git add .
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) { git commit -m 'Initial AutoPilot Android automation engine' }
$exists = $false
gh repo view fgjaee/AutoPilot *> $null
if ($LASTEXITCODE -eq 0) { $exists = $true }
if (-not $exists) {
    gh repo create fgjaee/AutoPilot --private --source . --remote origin --push
    if ($LASTEXITCODE -ne 0) { throw 'GitHub repo creation/push failed' }
} else {
    $remote = git remote get-url origin 2>$null
    if ($LASTEXITCODE -ne 0) { git remote add origin https://github.com/fgjaee/AutoPilot.git }
    git push -u origin main
    if ($LASTEXITCODE -ne 0) { throw 'Push failed' }
}
Write-Host 'Repository: https://github.com/fgjaee/AutoPilot'
Write-Host 'Build workflow: https://github.com/fgjaee/AutoPilot/actions/workflows/android.yml'
