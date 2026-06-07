@echo off
echo ========================================
echo   Laptop Proxy - Installer Builder
echo ========================================

echo.
echo [1/3] Checking for Inno Setup compiler...
where iscc >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Inno Setup compiler iscc.exe not found in PATH.
    echo Please install Inno Setup from https://jrsoftware.org/isdl.php
    pause
    exit /b 1
)
echo       Found.

echo.
echo [2/3] Checking for portable build output...
if not exist "dist\Hotspot_Bypass_VPN_Windows\Hotspot_Bypass_VPN_Windows.exe" (
    echo [ERROR] dist\Hotspot_Bypass_VPN_Windows\Hotspot_Bypass_VPN_Windows.exe not found.
    echo Please run build_exe.bat first to produce the portable build.
    pause
    exit /b 1
)
echo       Found.

echo.
echo [3/3] Compiling Installer...
iscc laptop_proxy.iss

echo.
echo ========================================
echo   BUILD COMPLETE!
echo   Your installer is in the "Output" folder.
echo ========================================
pause
