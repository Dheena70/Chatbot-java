@echo off
setlocal
echo Stopping DevBot Java server...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5000" ^| findstr "LISTENING"') do (
    echo Stopping process PID %%a on port 5000...
    taskkill /f /pid %%a >nul 2>&1
)
echo Chatbot server stopped.
timeout /t 2 /nobreak >nul 2>&1
