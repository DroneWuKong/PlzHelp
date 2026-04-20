@echo off
cd /d "%~dp0"
title ORQA Doc Chat - Setup
color 0A

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║       ORQA Doc Chat — First Time Setup   ║
echo  ╚══════════════════════════════════════════╝
echo.
echo  This will install everything needed to run the chat.
echo  It may take a few minutes depending on your internet speed.
echo.
pause

:: ── Check Python ─────────────────────────────────────────────────
echo.
echo  [1/4] Checking Python...
python --version >nul 2>&1
if errorlevel 1 (
    echo.
    echo  !! Python not found.
    echo.
    echo  Please install Python first:
    echo    1. Open the Microsoft Store and search "Python"
    echo    2. Install Python 3.12 or newer
    echo    3. IMPORTANT: Check "Add Python to PATH" during install
    echo    4. Close this window and run SETUP.bat again
    echo.
    pause
    exit /b 1
)
python --version
echo  Python OK.

:: ── Install Python packages ──────────────────────────────────────
echo.
echo  [2/4] Installing Python packages...
pip install -r requirements.txt
if errorlevel 1 (
    echo.
    echo  !! Failed to install packages. Check your internet connection.
    pause
    exit /b 1
)
echo  Packages installed OK.

:: ── Download and install Ollama ──────────────────────────────────
echo.
echo  [3/4] Checking Ollama...
ollama --version >nul 2>&1
if not errorlevel 1 (
    echo  Ollama already installed. Skipping download.
    goto :pull_model
)

echo  Ollama not found. Downloading installer...
echo  (about 120MB - this may take a minute)
echo.

:: Use curl which is built into Windows 10/11
curl -L -o "%TEMP%\OllamaSetup.exe" "https://ollama.com/download/OllamaSetup.exe"
if errorlevel 1 (
    echo.
    echo  !! Download failed. Check your internet connection.
    echo  You can also download manually from: https://ollama.com/download
    pause
    exit /b 1
)

echo.
echo  Installing Ollama...
echo  (A window may appear - follow any prompts)
"%TEMP%\OllamaSetup.exe" /S
timeout /t 5 /nobreak >nul

:: Refresh PATH so ollama command is found
set "PATH=%PATH%;%LOCALAPPDATA%\Programs\Ollama"

ollama --version >nul 2>&1
if errorlevel 1 (
    echo.
    echo  !! Ollama installed but not found in PATH yet.
    echo  Please close this window and run SETUP.bat again.
    pause
    exit /b 1
)
echo  Ollama installed OK.

:: ── Pull AI model ────────────────────────────────────────────────
:pull_model
echo.
echo  [4/4] Downloading AI model (llama3.2 - about 2GB)...
echo  This is the part that takes the longest. Grab a coffee.
echo.
ollama pull llama3.2
if errorlevel 1 (
    echo.
    echo  !! Model download failed. Check your internet connection.
    echo  You can try again by running: ollama pull llama3.2
    pause
    exit /b 1
)

:: ── Done ─────────────────────────────────────────────────────────
echo.
echo  ╔══════════════════════════════════════════╗
echo  ║            Setup complete!               ║
echo  ║                                          ║
echo  ║  Next steps:                             ║
echo  ║   1. Drop your docs into the docs\ folder║
echo  ║   2. Double-click START.bat              ║
echo  ║   3. Open http://localhost:5000          ║
echo  ╚══════════════════════════════════════════╝
echo.
pause
