@echo off
echo ========================================
echo   Laptop Proxy - Installer Builder
echo ========================================

echo.
echo [1/2] Checking for Inno Setup compiler...
where iscc >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Inno Setup compiler (iscc.exe) not found in PATH.
    echo Please install Inno Setup from https://jrsoftware.org/isdl.php
    pause
    exit /b 1
)

echo.
echo [2/2] Compiling Installer...
iscc laptop_proxy.iss

echo.
echo ========================================
echo   BUILD COMPLETE!
echo   Your installer is in the "Output" folder.
echo ========================================
pause
