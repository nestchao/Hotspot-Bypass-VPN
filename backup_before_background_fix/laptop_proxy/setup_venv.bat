@echo off
echo Creating virtual environment...
python -m venv venv
echo Activating virtual environment...
call venv\Scripts\activate
echo Installing dependencies...
pip install --upgrade pip
if exist requirements.txt (
    pip install -r requirements.txt
)
echo Setup complete.
echo To run the app, use: venv\Scripts\python.exe main.py
pause
