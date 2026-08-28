@echo off
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0infra\local\Stop-Clipador.ps1"
if errorlevel 1 (
  echo.
  echo Nao foi possivel encerrar todo o Clipador. Confira a mensagem acima.
  pause
)
