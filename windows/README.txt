╔══════════════════════════════════════════════════════════════════╗
║               ORQA DOC CHAT — SETUP GUIDE                       ║
╚══════════════════════════════════════════════════════════════════╝

What this is:
  A local AI chatbox that reads your ORQA manuals and documents
  and answers questions about them. Runs on your computer.
  No internet required after setup.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 BEFORE YOU START — You need Python installed
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 1. Open the Microsoft Store (search it in the Start menu)
 2. Search for "Python" and install the latest version (3.12 or newer)

    OR go to: https://www.python.org/downloads/
    Download and run the installer.

 !! IMPORTANT: During install, check the box that says:
    [✓] Add Python to PATH
    It is UNCHECKED by default. If you miss this, nothing will work.

 3. After installing Python, close any open CMD windows and reopen them.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STEP 1 — Run setup (do this once)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 Double-click:  SETUP.bat

 This will automatically:
   - Download and install Ollama (the local AI engine, ~120MB)
   - Download the AI model (llama3.2, about 2GB) — this takes a few minutes
   - Install all required Python packages

 You only need to do this once.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STEP 2 — Add your documents
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 Drop any of these into the  docs\  folder:
   - PDF files  (manuals, datasheets, specs)
   - Text files (.txt, .md)
   - JSON files

 The more documents you add, the better the answers.
 You can add or remove documents at any time — even while running.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STEP 3 — (Optional but recommended) Process your PDFs
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 Double-click:  PROCESS_PDFS.bat

 This converts your PDFs into clean text files that give much
 better search results and more accurate answers.
 Only needs to be done once per document.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STEP 4 — Start the chat
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 Double-click:  START.bat

 Then open your browser and go to:
   http://localhost:5000

 Leave the black terminal window open while using the chat.
 Close it when you're done.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 OPTIONAL — Use Claude API instead of local Ollama
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 If you have an Anthropic API key and want higher quality answers,
 double-click:  START_WITH_KEY.bat

 It will ask for your API key and use Claude instead of Ollama.
 Requires internet. Your key is never saved to disk.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 TROUBLESHOOTING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

 "python not found"
   → Python isn't installed or "Add to PATH" was not checked.
     Reinstall Python and check that box.

 "ollama not found"
   → Run SETUP.bat again.

 Page won't load in browser
   → Make sure the black terminal window is still open.
     Try http://localhost:5000 again.
     If port 5000 is in use, edit START.bat and change 5000 to 8080,
     then go to http://localhost:8080 instead.

 Slow answers
   → The local Ollama model is CPU-based if you don't have a supported
     GPU. Answers may take 10-30 seconds. This is normal.
     For faster answers, use START_WITH_KEY.bat with a Claude API key.

 The chat says "no documents found"
   → Make sure you have files in the docs\ folder.
     Supported types: PDF, TXT, MD, JSON.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 FILE OVERVIEW
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  docs\              ← Drop your manuals and documents here
  SETUP.bat          ← Run once to install everything
  START.bat          ← Start the chat (offline, uses Ollama)
  START_WITH_KEY.bat ← Start the chat (online, uses Claude API)
  PROCESS_PDFS.bat   ← Convert PDFs to better search format
  README.txt         ← This file
  server.py          ← The server (don't need to touch this)
  process_pdf.py     ← The PDF processor (don't need to touch this)
  index.html         ← The chat interface (don't need to touch this)
