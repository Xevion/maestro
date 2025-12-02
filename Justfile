# Default recipe
default: check

# Quick type checking and linting
check:
    ./gradlew :common:compileKotlin :common:compileJava --rerun-tasks
    ./gradlew check -q

# Format code
fmt:
    ./gradlew spotlessApply -q

# Check code formatting
format-check:
    ./gradlew spotlessCheck -q

# Run all tests
test:
    ./gradlew test -q

# Build production artifacts
build:
    ./gradlew build -q

# Development server (run Minecraft client)
dev-basic:
    ./gradlew :fabric:runClient

# Run coordinator bot (loads world, opens LAN, runs #coordinator)
# If world="auto" (default), finds most recent world in saves folder
dev world="auto" goal="100":
    #!/usr/bin/env bash
    set -euo pipefail
    WORLD="{{world}}"
    if [ "$WORLD" = "auto" ]; then
        SAVES_DIR="fabric/run/saves"
        if [ ! -d "$SAVES_DIR" ]; then
            echo "Error: Saves directory not found at $SAVES_DIR"
            exit 1
        fi
        # Find most recently modified world folder
        WORLD=$(ls -t "$SAVES_DIR" | head -n 1)
        if [ -z "$WORLD" ]; then
            echo "Error: No worlds found in $SAVES_DIR"
            exit 1
        fi
        echo "Auto-detected world: $WORLD"
    fi
    AUTOSTART_COORDINATOR=true COORDINATOR_GOAL={{goal}} \
    ./gradlew :fabric:runClient --args="--username Dev --quickPlaySingleplayer \"$WORLD\""

# Run worker bot (auto-connects to coordinator)
dev-worker i:
    ./gradlew :fabric:runClient --args="--username Worker{{ i }} --quickPlayMultiplayer localhost:25565"

# Clean build
clean:
    ./gradlew clean

# Install git pre-commit hooks (requires uvx/uv)
install-hooks:
    uvx pre-commit install

# Integration test - verify client starts and mixins load
smoke platform="fabric":
    bun ./scripts/smoke.ts {{platform}}
