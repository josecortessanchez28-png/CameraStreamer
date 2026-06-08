@echo off
echo Instalando dependencias...
pip install -r requirements.txt
echo.
echo Iniciando CameraStreamer Desktop...
python desktop_streamer.py
pause
