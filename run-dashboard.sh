#!/bin/bash

# Dashboard startup script for Apache Thrift Understand-Anything visualization
# Bash version for Linux/macOS

PROJECT_DIR="${1:-.}"
PLUGIN_ROOT=""
DASHBOARD_DIR=""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color


# Step 1: Check for knowledge graph
GRAPH_FILE="$PROJECT_DIR/.understand-anything/knowledge-graph.json"
if [ ! -f "$GRAPH_FILE" ]; then
    echo -e "${RED}ERROR: Knowledge graph not found at:${NC}"
    echo "  $GRAPH_FILE"
    echo ""
    echo -e "${YELLOW}Run the /understand skill first to generate the knowledge graph.${NC}"
    echo ""
    exit 1
fi
echo -e "${GREEN}[OK]${NC} Knowledge graph found"
echo "     Location: $GRAPH_FILE"
echo ""

# Step 2: Find the dashboard code
# Try multiple locations where the plugin could be installed
for candidate in \
    "${CLAUDE_PLUGIN_ROOT}" \
    "$HOME/.understand-anything-plugin" \
    "$HOME/.codex/understand-anything/understand-anything-plugin" \
    "$HOME/.opencode/understand-anything/understand-anything-plugin" \
    "$HOME/.pi/understand-anything/understand-anything-plugin" \
    "$HOME/understand-anything/understand-anything-plugin"; do
    
    if [ -n "$candidate" ] && [ -d "$candidate/packages/dashboard" ]; then
        PLUGIN_ROOT="$candidate"
        DASHBOARD_DIR="$candidate/packages/dashboard"
        break
    fi
done

if [ -z "$PLUGIN_ROOT" ]; then
    echo -e "${RED}ERROR: Cannot find the understand-anything plugin root.${NC}"
    echo ""
    echo "Checked:"
    echo "  - CLAUDE_PLUGIN_ROOT=${CLAUDE_PLUGIN_ROOT:-<unset>}"
    echo "  - \$HOME/.understand-anything-plugin"
    echo "  - \$HOME/.codex/understand-anything/understand-anything-plugin"
    echo "  - \$HOME/.opencode/understand-anything/understand-anything-plugin"
    echo "  - \$HOME/.pi/understand-anything/understand-anything-plugin"
    echo "  - \$HOME/understand-anything/understand-anything-plugin"
    echo ""
    echo -e "${YELLOW}Make sure you followed the installation instructions for your platform.${NC}"
    echo ""
    exit 1
fi
echo -e "${GREEN}[OK]${NC} Dashboard code found"
echo "     Location: $DASHBOARD_DIR"
echo ""

# Step 3: Install dependencies
echo "Installing dependencies..."
(
    cd "$DASHBOARD_DIR" || exit 1
    pnpm install --frozen-lockfile 2>/dev/null || pnpm install 2>/dev/null
)
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}WARNING: pnpm install had issues, attempting to continue...${NC}"
fi
echo -e "${GREEN}[OK]${NC} Dependencies ready"
echo ""

# Step 4: Build core package
echo "Building core package..."
(
    cd "$PLUGIN_ROOT" || exit 1
    pnpm --filter @understand-anything/core build 2>/dev/null
)
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}WARNING: Core build had issues, attempting to continue...${NC}"
fi
echo -e "${GREEN}[OK]${NC} Core package built"
echo ""

# Step 5: Start the dashboard
echo "====================================================================="
echo "Starting Vite development server..."
echo "====================================================================="
echo ""
echo "Project Directory: $PROJECT_DIR"
echo "Knowledge Graph:   $GRAPH_FILE"
echo ""
echo -e "${YELLOW}The dashboard will start momentarily. Look for the URL with token.${NC}"
echo ""
echo "Example: http://127.0.0.1:5173?token=abc123def456..."
echo ""
echo -e "${CYAN}IMPORTANT: Copy and paste the FULL URL including ?token= parameter${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop the dashboard.${NC}"
echo "====================================================================="
echo ""

# Start Vite server
cd "$DASHBOARD_DIR" || exit 1
export GRAPH_DIR="$PROJECT_DIR"
npx vite --host 127.0.0.1

echo ""
echo -e "${YELLOW}Dashboard has stopped.${NC}"
