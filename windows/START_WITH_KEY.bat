@echo off
cd /d "%~dp0"
title ORQA Doc Chat - Claude API
color 0B

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║       ORQA Doc Chat — Claude API         ║
echo  ╚══════════════════════════════════════════╝
echo.
echo  This mode uses Claude (Anthropic) for higher quality answers.
echo  Requires an internet connection and an Anthropic API key.
echo  Your key is not saved to disk.
echo.

:: Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo  !! Python not found. Please run SETUP.bat first.
    pause
    exit /b 1
)

:: Check docs folder
if not exist "docs\" mkdir docs

set /p APIKEY="  Enter your Anthropic API key (sk-ant-...): "
echo.

if "%APIKEY%"=="" (
    echo  !! No key entered. Exiting.
    pause
    exit /b 1
)

echo  Starting server with Claude API...
echo  Open your browser and go to: http://localhost:5000
echo.
echo  Leave this window open while using the chat.
echo  Press Ctrl+C to stop.
echo.

python server.py --key "%APIKEY%"
pause
