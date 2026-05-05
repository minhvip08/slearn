# Dashboard startup script for Apache Thrift Understand-Anything visualization
# PowerShell version for Windows

param(
    [string]$ProjectPath = (Get-Location).Path
)

# Configuration
$pluginRoot = "C:\Users\simi\.vscode\agent-plugins\github.com\Lum1104\Understand-Anything\understand-anything-plugin"
$dashboardDir = Join-Path $pluginRoot "packages\dashboard"
$graphFile = Join-Path $ProjectPath ".understand-anything\knowledge-graph.json"


# Step 1: Verify knowledge graph exists
if (-not (Test-Path $graphFile)) {
    Write-Host "ERROR: Knowledge graph not found at:"
    Write-Host "  $graphFile"
    Write-Host ""
    Write-Host "Run the /understand skill first to generate the knowledge graph."
    Write-Host ""
    exit 1
}
Write-Host "[OK] Knowledge graph found"
Write-Host "     Location: $graphFile"
Write-Host ""

# Step 2: Verify plugin installation
if (-not (Test-Path $dashboardDir)) {
    Write-Host "ERROR: Dashboard not found at:"
    Write-Host "  $dashboardDir"
    Write-Host ""
    Write-Host "Please verify the Understand-Anything plugin is installed."
    Write-Host ""
    exit 1
}
Write-Host "[OK] Dashboard code found"
Write-Host "     Location: $dashboardDir"
Write-Host ""

# Step 3: Install dependencies if needed
Write-Host "Installing dependencies..."
Push-Location $dashboardDir
$output = pnpm install --frozen-lockfile 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Retrying pnpm install (less strict)..."
    $output = pnpm install 2>&1
}
Pop-Location
Write-Host "[OK] Dependencies ready"
Write-Host ""

# Step 4: Build core package
Write-Host "Building core package..."
Push-Location $pluginRoot
$output = pnpm --filter @understand-anything/core build 2>&1
Pop-Location
Write-Host "[OK] Core package built"
Write-Host ""

# Step 5: Start the dashboard
Write-Host "====================================================================="
Write-Host "Starting Vite development server..."
Write-Host "====================================================================="
Write-Host ""
Write-Host "Project Directory: $ProjectPath"
Write-Host "Knowledge Graph:   $graphFile"
Write-Host ""
Write-Host "The dashboard will start momentarily. Look for the URL with token."
Write-Host ""
Write-Host "Example: http://127.0.0.1:5173?token=abc123def456..."
Write-Host ""
Write-Host "IMPORTANT: Copy and paste the FULL URL including ?token= parameter"
Write-Host ""
Write-Host "Press Ctrl+C to stop the dashboard."
Write-Host "====================================================================="
Write-Host ""

# Start Vite server
Push-Location $dashboardDir
$env:GRAPH_DIR = $ProjectPath
& npx vite --host 127.0.0.1
Pop-Location

Write-Host ""
Write-Host "Dashboard has stopped."
