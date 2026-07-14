@echo off
cd /d "%~dp0"

rem Java 17 경로 탐색 (JAVA_HOME > 일반 설치 경로 > 시스템 java 순)
if defined JAVA_HOME (
    set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set JAVA_CMD="C:\Program Files\Java\jdk-17\bin\java.exe"
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe" (
    set JAVA_CMD="C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe"
) else (
    set JAVA_CMD=java
)

%JAVA_CMD% -jar "target\hiworks-attendance-1.0-SNAPSHOT-jar-with-dependencies.jar" --now
pause
