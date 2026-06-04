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
echo [2/3] Installing PyInstaller and dependencies...
pip install -r requirements.txt

echo.
echo [3/3] Building Standalone EXE...
echo This may take a minute...

:: --onefile: Single EXE
:: --noconsole: No terminal window
:: --uac-admin: Request Admin on launch
:: --name: Output name
:: --clean: Clean cache before build
venv\Scripts\pyinstaller --noconsole --onefile --uac-admin --name "LaptopProxy" --clean --hidden-import=darkdetect --hidden-import=customtkinter --collect-data customtkinter main.py

echo.
echo ========================================
echo   BUILD COMPLETE!
echo   Your software is in the "dist" folder.
echo ========================================
pause
