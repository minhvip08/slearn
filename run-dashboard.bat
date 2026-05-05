@echo off
REM Dashboard startup script for Apache Thrift Understand-Anything visualization
REM This script starts the interactive knowledge graph dashboard

setlocal enabledelayedexpansion

REM Configuration
set "PROJECT_DIR=%~dp0"
set "PLUGIN_ROOT=C:\Users\simi\.vscode\agent-plugins\github.com\Lum1104\Understand-Anything\understand-anything-plugin"
set "DASHBOARD_DIR=%PLUGIN_ROOT%\packages\dashboard"
set "GRAPH_FILE=%PROJECT_DIR%.understand-anything\knowledge-graph.json"

REM Colors for output (Windows 10+)
setlocal


REM Step 1: Verify knowledge graph exists
if not exist "%GRAPH_FILE%" (
    echo ERROR: Knowledge graph not found at:
    echo   %GRAPH_FILE%
    echo.
    echo Run the /understand skill first to generate the knowledge graph.
    echo.
    pause
    exit /b 1
)
echo ✓ Knowledge graph found
echo   Location: %GRAPH_FILE%
echo.

REM Step 2: Verify plugin installation
if not exist "%DASHBOARD_DIR%" (
    echo ERROR: Dashboard not found at:
    echo   %DASHBOARD_DIR%
    echo.
    echo Please verify the Understand-Anything plugin is installed.
    echo.
    pause
    exit /b 1
)
echo ✓ Dashboard code found
echo   Location: %DASHBOARD_DIR%
echo.

REM Step 3: Install dependencies if needed
echo Installing dependencies...
cd /d "%DASHBOARD_DIR%"
call pnpm install --frozen-lockfile >nul 2>&1
if !errorlevel! neq 0 (
    echo Retrying pnpm install...
    call pnpm install >nul 2>&1
    if !errorlevel! neq 0 (
        echo WARNING: npm install may have issues, attempting to continue...
    )
)
echo ✓ Dependencies ready
echo.

REM Step 4: Build core package
echo Building core package...
cd /d "%PLUGIN_ROOT%"
call pnpm --filter @understand-anything/core build >nul 2>&1
echo ✓ Core package built
echo.

REM Step 5: Start the dashboard
echo ================================================================================
echo Starting Vite development server...
echo ================================================================================
echo.
echo Project Directory: %PROJECT_DIR%
echo Knowledge Graph: %GRAPH_FILE%
echo.
echo The dashboard will start momentarily. Look for the URL with the access token.
echo.
echo Example URL: http://127.0.0.1:5173?token=XXXXXXXXXXXXXXXX
echo.
echo Press Ctrl+C in this window to stop the dashboard.
echo ================================================================================
echo.

cd /d "%DASHBOARD_DIR%"
set "GRAPH_DIR=%PROJECT_DIR%"
call npx vite --host 127.0.0.1

REM If Vite exits/fails
echo.
echo Dashboard has stopped.
pause
exit /b 0
