@echo off
cd /d "%~dp0"
java -jar "target\hiworks-attendance-1.0-SNAPSHOT-jar-with-dependencies.jar" --now
pause
