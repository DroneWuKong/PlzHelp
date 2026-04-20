@echo off
cd /d "%~dp0"
title ORQA Doc Chat - Sync Repos
color 0B

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║       ORQA Doc Chat — Repo Sync          ║
echo  ╚══════════════════════════════════════════╝
echo.
echo  This pulls the latest docs from your GitHub repos
echo  into the docs\ folder so the chat stays current.
echo.

:: ── Check git ────────────────────────────────────────────────────
git --version >nul 2>&1
if errorlevel 1 (
    echo  !! Git not found.
    echo.
    echo  Install Git from: https://git-scm.com/download/win
    echo  Then run SYNC.bat again.
    pause
    exit /b 1
)

:: ── Config ───────────────────────────────────────────────────────
:: Set your GitHub username here
set GH_USER=DroneWuKong

:: Temp folder for repo clones (inside the orqa-chat folder)
set REPOS_DIR=%~dp0_repos

:: Docs output folder
set DOCS_DIR=%~dp0docs

:: ── Check for saved token ────────────────────────────────────────
set TOKEN_FILE=%~dp0.gh_token
set GH_TOKEN=

if exist "%TOKEN_FILE%" (
    set /p GH_TOKEN=<"%TOKEN_FILE%"
    echo  Using saved GitHub token.
) else (
    echo  GitHub Personal Access Token needed to access private repos.
    echo.
    echo  To create one:
    echo    1. Go to github.com/settings/tokens
    echo    2. Click "Generate new token (classic)"
    echo    3. Check "repo" scope
    echo    4. Copy the token and paste it below
    echo.
    set /p GH_TOKEN="  Paste your GitHub token here: "
    echo.
    set /p SAVE_TOKEN="  Save token for future syncs? (y/n): "
    if /i "%SAVE_TOKEN%"=="y" (
        echo !GH_TOKEN!>"%TOKEN_FILE%"
        echo  Token saved. Delete .gh_token to remove it.
    )
)

if "%GH_TOKEN%"=="" (
    echo  !! No token provided. Cannot access private repos.
    pause
    exit /b 1
)

:: ── Create folders ───────────────────────────────────────────────
if not exist "%REPOS_DIR%" mkdir "%REPOS_DIR%"
if not exist "%DOCS_DIR%" mkdir "%DOCS_DIR%"

:: ── Helper: clone or pull a repo ─────────────────────────────────
:: Usage: call :sync_repo <repo_name>
goto :main

:sync_repo
set REPO=%~1
set REPO_PATH=%REPOS_DIR%\%REPO%
echo.
echo  ── %REPO% ──────────────────────────────────
if exist "%REPO_PATH%\.git" (
    echo  Pulling latest...
    cd /d "%REPO_PATH%"
    git pull
) else (
    echo  Cloning...
    cd /d "%REPOS_DIR%"
    git clone "https://%GH_TOKEN%@github.com/%GH_USER%/%REPO%.git"
)
cd /d "%~dp0"
goto :eof

:: ── Main sync ────────────────────────────────────────────────────
:main

:: 1. droneclear_Forge
call :sync_repo droneclear_Forge
set FORGE=%REPOS_DIR%\droneclear_Forge\DroneClear Components Visualizer

echo  Copying troubleshooting knowledge base...
if exist "%FORGE%\forge_troubleshooting.json" (
    copy /y "%FORGE%\forge_troubleshooting.json" "%DOCS_DIR%\forge_troubleshooting.json" >nul
    echo  OK - forge_troubleshooting.json
) else (
    echo  !! forge_troubleshooting.json not found at expected path
)

:: Copy wingman.html as reference (strip to text on next server start)
if exist "%FORGE%\wingman.html" (
    copy /y "%FORGE%\wingman.html" "%DOCS_DIR%\wingman.html" >nul
    echo  OK - wingman.html
)

:: 2. drone-integration-handbook
call :sync_repo drone-integration-handbook
set HANDBOOK=%REPOS_DIR%\drone-integration-handbook

echo  Copying handbook markdown files...
if not exist "%DOCS_DIR%\handbook" mkdir "%DOCS_DIR%\handbook"

:: Copy all markdown files recursively
for /r "%HANDBOOK%" %%f in (*.md) do (
    set "REL=%%~pf"
    set "REL=!REL:%HANDBOOK%\=!"
    if not exist "%DOCS_DIR%\handbook\!REL!" mkdir "%DOCS_DIR%\handbook\!REL!" 2>nul
    copy /y "%%f" "%DOCS_DIR%\handbook\%%~nxf" >nul
)

:: Count copied files
set MD_COUNT=0
for /r "%DOCS_DIR%\handbook" %%f in (*.md) do set /a MD_COUNT+=1
echo  OK - %MD_COUNT% markdown files from handbook

:: 3. orqa-h7quadcore-px4 (your PX4 port)
call :sync_repo orqa-h7quadcore-px4
set PX4REPO=%REPOS_DIR%\orqa-h7quadcore-px4

echo  Copying ORQA PX4 context files...
if not exist "%DOCS_DIR%\orqa-px4" mkdir "%DOCS_DIR%\orqa-px4"

if exist "%PX4REPO%\SESSION_CONTEXT.md" (
    copy /y "%PX4REPO%\SESSION_CONTEXT.md" "%DOCS_DIR%\orqa-px4\SESSION_CONTEXT.md" >nul
    echo  OK - SESSION_CONTEXT.md
)

:: Copy board definition files
if exist "%PX4REPO%\boards\orqa" (
    if not exist "%DOCS_DIR%\orqa-px4\boards" mkdir "%DOCS_DIR%\orqa-px4\boards"
    xcopy /s /y /q "%PX4REPO%\boards\orqa" "%DOCS_DIR%\orqa-px4\boards\orqa\" >nul
    echo  OK - boards\orqa (hwdef files)
)

:: 4. Ai-Project (private canonical data)
call :sync_repo Ai-Project
set AIPROJ=%REPOS_DIR%\Ai-Project

echo  Copying Ai-Project context files...
if not exist "%DOCS_DIR%\ai-project" mkdir "%DOCS_DIR%\ai-project"

for %%f in (SESSION_CONTEXT.md CHANGELOG.md TODO.md) do (
    if exist "%AIPROJ%\%%f" (
        copy /y "%AIPROJ%\%%f" "%DOCS_DIR%\ai-project\%%f" >nul
        echo  OK - %%f
    )
)

:: ── Summary ──────────────────────────────────────────────────────
echo.
echo  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo  Sync complete. docs\ folder contents:
echo  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

set TOTAL=0
for /r "%DOCS_DIR%" %%f in (*.md *.txt *.json) do set /a TOTAL+=1
echo  %TOTAL% indexable files in docs\

echo.
echo  If the chat server is already running it will pick up
echo  the new files automatically within a few seconds.
echo.
echo  If it's not running yet, start it with START.bat
echo.
pause

:: ── Strip forge_database.json ──────────────────────────────────────────
echo.
echo  Stripping forge parts database to searchable format...
set FORGE_DB=%REPOS_DIR%\droneclear_Forge\DroneClear Components Visualizer\forge_database.json
if exist "%FORGE_DB%" (
    python strip_forge_db.py "%FORGE_DB%" "%DOCS_DIR%\forge_parts.md"
) else (
    echo  !! forge_database.json not found - skipping
)

:: ── Extract Wingman knowledge base ────────────────────────────────────
echo.
echo  Extracting Wingman knowledge bases...
python extract_wingman.py
