@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo   Building and Starting Java Chatbot
echo ========================================

if not exist out mkdir out

echo Compiling Java source files...
javac -d out src\main\java\chatbot\*.java src\test\java\chatbot\*.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

:: Free port 5000 if an orphan Java or background process is holding it
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5000" ^| findstr "LISTENING"') do (
    echo [INFO] Freeing port 5000 (killing old process PID %%a)...
    taskkill /f /pid %%a >nul 2>&1
)

echo Starting ChatbotServer...
echo Server running at: http://localhost:5000
echo Opening your browser automatically...
echo Press Ctrl+C in this window to stop the server.
echo.

:: Automatically open default browser after launching
start "" http://localhost:5000

java -cp out chatbot.ChatbotServer

pause
