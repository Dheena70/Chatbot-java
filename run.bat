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

echo Starting ChatbotServer...
echo Open your browser at http://localhost:5000
echo Press Ctrl+C to stop the server.
echo.

java -cp out chatbot.ChatbotServer

pause
