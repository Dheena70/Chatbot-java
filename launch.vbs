Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "C:\Users\ELCOT\Desktop\COMPLETED\chatbot-java"
WshShell.Run "cmd.exe /c launch.bat", 0, False
