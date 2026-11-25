@echo off
rem Builds the game (only what changed) and starts it.
rem Usage:  play            normal game
rem         play --dev      developer start (lots of gold / max level)
rem         play --seed=42  repeatable randomness
rem Set MAPPA_NO_COLOR=1 to turn off terminal colors.
rem Set MAPPA_ASCII=1 to draw boxes with plain ASCII instead of line characters.
setlocal

rem Folder this script lives in (works no matter where it's started from).
set "ROOT=%~dp0"

rem Pick Java: JAVA_HOME (must be JDK 21+), otherwise whatever "java" is on PATH.
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if "%JAVA_EXE%"=="java" echo Warning: JAVA_HOME is not set; using "java" from PATH. The game needs Java 21 or newer.

echo Building...
call "%ROOT%gradlew.bat" -p "%ROOT%." jar -q --console=plain
if errorlevel 1 (
    echo Build failed. See the errors above.
    exit /b 1
)

set "JAR="
for %%f in ("%ROOT%build\libs\*.jar") do set "JAR=%%~ff"
if not defined JAR (
    echo Could not find the game jar in "%ROOT%build\libs".
    exit /b 1
)

set "COLOR_FLAG="
if defined MAPPA_NO_COLOR set "COLOR_FLAG=-Dmappa.noColor=true"
set "ASCII_FLAG="
if defined MAPPA_ASCII set "ASCII_FLAG=-Dmappa.ascii=true"

rem Switch the terminal to UTF-8 so boxes and bars draw correctly; restore it afterwards.
set "OLDCP="
for /f "tokens=2 delims=:." %%c in ('chcp') do set "OLDCP=%%c"
chcp 65001 >nul

rem Saves always go in the project's saves folder, wherever this script is started from.
"%JAVA_EXE%" -Dstdout.encoding=UTF-8 %COLOR_FLAG% %ASCII_FLAG% -jar "%JAR%" %* "--saves=%ROOT%saves"

if defined OLDCP chcp %OLDCP% >nul
endlocal
