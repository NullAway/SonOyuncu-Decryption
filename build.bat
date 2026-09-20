@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set OUT=build
set JAR=So-Decrypt.jar
set MAIN=com.deluxe.sonoyuncu.Main
set CP=libs\asm.jar;libs\asm-tree.jar

if exist "%ProgramFiles%\Java\jdk-22\bin\javac.exe" (
    set "JAVA_HOME=%ProgramFiles%\Java\jdk-22"
) else if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    rem use existing JAVA_HOME
) else (
    for /d %%D in ("%ProgramFiles%\Java\jdk-*") do (
        if exist "%%D\bin\javac.exe" set "JAVA_HOME=%%D"
    )
)

if not defined JAVA_HOME (
    echo [!] JDK bulunamadi. PATH'e ekleyin veya C:\Program Files\Java altina kurun.
    exit /b 1
)

set "JAVAC=!JAVA_HOME!\bin\javac.exe"
set "JARCMD=!JAVA_HOME!\bin\jar.exe"

echo [*] Cleaning %OUT% ...
if exist %OUT% rmdir /s /q %OUT%
mkdir %OUT%

echo [*] Compiling ...
(for /f "delims=" %%F in ('dir /s /b src\*.java') do (
    set "F=%%F"
    echo "!F:\=/!"
)) > "%TEMP%\so_srcs.txt"
"%JAVAC%" -encoding UTF-8 -cp "%CP%" -d %OUT% @"%TEMP%\so_srcs.txt"
if errorlevel 1 (
    echo [!] Compile failed.
    exit /b 1
)

echo [*] Bundling ASM into jar ...
pushd %OUT%
"%JARCMD%" xf ..\libs\asm.jar
"%JARCMD%" xf ..\libs\asm-tree.jar
if exist META-INF rmdir /s /q META-INF
popd

echo [*] Building %JAR% ...
"%JARCMD%" cfe %JAR% %MAIN% -C %OUT% .
if errorlevel 1 (
    echo [!] Jar build failed.
    exit /b 1
)

rmdir /s /q %OUT%

echo [+] Done: %JAR%
endlocal
