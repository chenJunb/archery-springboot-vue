@echo off
REM This is a wrapper script to run build.bat from PowerShell
REM Usage: Simply run .\run-build.bat in PowerShell

cd /d "%~dp0"
call build.bat
