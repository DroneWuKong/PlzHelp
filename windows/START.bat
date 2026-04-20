@echo off
cd /d "%~dp0"
title ORQA Doc Chat
color 0A

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║            ORQA Doc Chat                 ║
echo  ╚══════════════════════════════════════════╝
echo.

:: Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo  !! Python not found. Please run SETUP.bat first.
    pause
    exit /b 1
)

:: Check Ollama is installed
ollama --version >nul 2>&1
if errorlevel 1 (
    set "PATH=%PATH%;%LOCALAPPDATA%\Programs\Ollama"
    ollama --version >nul 2>&1
    if errorlevel 1 (
        echo  !! Ollama not found. Please run SETUP.bat first.
        pause
        exit /b 1
    )
)

:: Check docs folder
if not exist "docs\" mkdir docs

echo  Drop your manuals and docs into the docs\ folder.
echo.
echo  Starting server...
echo  Open your browser and go to: http://localhost:5000
echo.
echo  Leave this window open while using the chat.
echo  Press Ctrl+C to stop.
echo.

python server.py --offline
pause
