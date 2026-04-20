@echo off
cd /d "%~dp0"
title ORQA Doc Chat - Process PDFs
color 0A

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║         ORQA Doc Chat — PDF Processor    ║
echo  ╚══════════════════════════════════════════╝
echo.
echo  This converts PDFs in docs\ into clean text files.
echo  Better text = better search results = more accurate answers.
echo  Only needs to be done once per document.
echo.

:: Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo  !! Python not found. Please run SETUP.bat first.
    pause
    exit /b 1
)

:: Check docs folder has PDFs
if not exist "docs\" (
    echo  !! docs\ folder not found. Creating it now.
    mkdir docs
)

:: Count PDFs
set count=0
for %%f in (docs\*.pdf) do set /a count+=1
if %count%==0 (
    echo  No PDF files found in docs\
    echo  Drop your PDF manuals into the docs\ folder and run this again.
    pause
    exit /b 0
)

echo  Found %count% PDF(s) in docs\
echo  Processing with Ollama (llama3.2)...
echo.
echo  This may take several minutes for large documents.
echo.

python process_pdf.py docs\ --provider ollama --model llama3.2
echo.
echo  Done! Processed files saved to docs\
echo  Start the chat with START.bat
echo.
pause
