@echo off
setlocal
cd /d "%~dp0"

:: 1. Check if server is already running on port 5000
curl.exe -s -m 1 http://localhost:5000/api/health >nul 2>&1
if %ERRORLEVEL% equ 0 (
    goto open_app
)

:: 2. Compile classes if needed
if not exist out\chatbot\ChatbotServer.class (
    if not exist out mkdir out
    javac -d out src\main\java\chatbot\*.java
)

:: 3. Launch Java silently in the background (no console window)
start "" javaw -cp out chatbot.ChatbotServer

:: 4. Wait until the server is ready (up to 3 seconds)
for /l %%i in (1,1,6) do (
    curl.exe -s -m 1 http://localhost:5000/api/health >nul 2>&1
    if %ERRORLEVEL% equ 0 goto open_app
    timeout /t 1 /nobreak >nul 2>&1
)

:open_app
:: 5. Open as a standalone desktop app window if Chrome is installed, else default browser
if exist "C:\Program Files\Google\Chrome\Application\chrome.exe" (
    start "" "C:\Program Files\Google\Chrome\Application\chrome.exe" --app=http://localhost:5000
) else if exist "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" (
    start "" "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" --app=http://localhost:5000
) else (
    start "" http://localhost:5000
)
