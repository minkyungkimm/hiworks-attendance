@echo off
cd /d "%~dp0"

set "JAVA_CMD=java"
if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\Java\jdk-17\bin\java.exe"
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe"
)

"%JAVA_CMD%" -jar "target\hiworks-attendance-1.0-SNAPSHOT-jar-with-dependencies.jar" --vacation-check
pause
