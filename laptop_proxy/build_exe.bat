@echo off
echo ========================================
echo   Laptop Proxy - EXE Builder
echo ========================================

echo.
echo [1/3] Preparing virtual environment...
if not exist venv (
    python -m venv venv
)
call venv\Scripts\activate

echo.
echo [2/3] Installing dependencies...
pip install -r requirements.txt

echo.
echo [3/3] Building EXE (onedir mode, no UPX)...
echo This may take a minute...

:: Build from spec file (all settings are in LaptopProxy.spec)
:: --clean: Clean cache before build
:: --noconfirm: Overwrite output without asking
venv\Scripts\pyinstaller --clean --noconfirm LaptopProxy.spec

echo.
echo ========================================
echo   BUILD COMPLETE!
echo   Your software is in:
echo   dist\Hotspot_Bypass_VPN_Windows\
echo ========================================
pause
