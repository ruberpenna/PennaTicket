@echo off
rem PennaTicket launcher. Double-click or run "run.bat" from a command prompt.
rem Server addresses and passwords come from config\ and pgpass.conf (see README).
rem Keep this file ASCII only: non-ASCII text in .bat files breaks cmd parsing.
cd /d "%~dp0"

if not exist config\application.properties (
    echo [ERROR] config\application.properties not found.
    echo         Copy the config-example folder to config and fill in the values.
    pause
    exit /b 1
)

echo Starting PennaTicket... (stop: Ctrl+C)
echo   API docs: http://localhost:8090/swagger-ui.html
echo.
call "%~dp0gradlew.bat" bootRun
pause
