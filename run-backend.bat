@echo off
rem Starts the Spring Boot backend on http://localhost:8080
cd /d "%~dp0backend"

echo Java on this computer:
java -version 2>&1 | findstr /i "version"
echo (BookNest needs Java 17 or newer)
echo.

where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven was not found.
  echo   Option 1: run winget install Apache.Maven then open a NEW terminal and run this file again.
  echo   Option 2: open the "backend" folder in IntelliJ IDEA and run BookNestApplication.
  pause
  exit /b 1
)

call mvn spring-boot:run
pause
