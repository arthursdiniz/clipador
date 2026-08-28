@echo off
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0infra\local\Start-Clipador.ps1" -OpenBrowser
if errorlevel 1 (
  echo.
  echo Nao foi possivel iniciar o Clipador. Confira a mensagem acima.
  pause
)
