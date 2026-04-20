@echo off
cd /d "%~dp0"
title ORQA Doc Chat - Diagnostics
color 0E

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║       ORQA Doc Chat — Diagnostics        ║
echo  ╚══════════════════════════════════════════╝
echo.

:: Check Python
echo  [1] Python...
python --version
if errorlevel 1 (
    echo  FAIL - Python not found. Run SETUP.bat.
) else (
    echo  OK
)
echo.

:: Check pip packages
echo  [2] Python packages...
python -c "import flask, requests, watchdog; print('  OK')" 2>nul
if errorlevel 1 (
    echo  FAIL - Missing packages. Run SETUP.bat.
) else (
    echo  OK
)
echo.

:: Check pdfplumber separately
echo  [3] PDF support...
python -c "import pdfplumber; print('  OK')" 2>nul
if errorlevel 1 (
    echo  FAIL - pdfplumber missing. Run: pip install pdfplumber
) else (
    echo  OK
)
echo.

:: Check Ollama installed
echo  [4] Ollama installed...
set "PATH=%PATH%;%LOCALAPPDATA%\Programs\Ollama"
ollama --version
if errorlevel 1 (
    echo  FAIL - Ollama not found. Run SETUP.bat.
) else (
    echo  OK
)
echo.

:: Check Ollama is running and responding
echo  [5] Ollama running and responding...
curl -s http://localhost:11434 >nul 2>&1
if errorlevel 1 (
    echo  FAIL - Ollama is not running or not responding on port 11434.
    echo.
    echo  Try: Look for the Ollama icon in your system tray ^(bottom right^).
    echo       If it's not there, search "Ollama" in the Start menu and open it.
) else (
    echo  OK - Ollama is running
)
echo.

:: Check model is available
echo  [6] Checking llama3.2 model is downloaded...
ollama list 2>nul | findstr "llama3.2" >nul
if errorlevel 1 (
    echo  FAIL - llama3.2 model not found.
    echo.
    echo  Run this command to download it:
    echo    ollama pull llama3.2
    echo.
    echo  This downloads about 2GB and may take a few minutes.
) else (
    echo  OK - llama3.2 found
)
echo.

:: Try a quick test query to Ollama
echo  [7] Testing Ollama response (this may take 10-30 seconds)...
python -c "
import requests, sys
try:
    r = requests.post(
        'http://localhost:11434/v1/chat/completions',
        json={'model':'llama3.2','max_tokens':20,'messages':[{'role':'user','content':'Say OK'}]},
        timeout=60
    )
    r.raise_for_status()
    reply = r.json()['choices'][0]['message']['content']
    print('  OK - Ollama replied:', reply[:50])
except requests.exceptions.ConnectionError:
    print('  FAIL - Could not connect to Ollama. Is it running?')
    sys.exit(1)
except requests.exceptions.Timeout:
    print('  FAIL - Ollama timed out after 60 seconds.')
    print('  The model may still be loading. Wait a minute and run this again.')
    sys.exit(1)
except Exception as e:
    print('  FAIL -', str(e))
    sys.exit(1)
"
echo.

echo  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo  Diagnostics complete.
echo  If everything shows OK, run START.bat and open http://localhost:5000
echo  If anything shows FAIL, follow the instructions above.
echo  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.
pause
