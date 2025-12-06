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

# Query Minecraft source JAR
# Usage:
#   just mcjar list net/minecraft/client/renderer/        # List classes in package
#   just mcjar cat net/minecraft/client/Minecraft.java    # Read entire class
#   just mcjar grep shouldEntityAppearGlowing net/minecraft/client/Minecraft.java  # Search in class
#   just mcjar grep-all startUseItem 'net/minecraft/client/*.java'  # Search multiple files
#   just mcjar cat com/mojang/blaze3d/vertex/VertexFormat.java  # Read Blaze3D class
#   just mcjar asset rendertype_lines.vsh  # Read shader/asset file
#   just mcjar asset-list shaders/  # List asset files
mcjar cmd *args:
    #!/usr/bin/env bash
    set -euo pipefail
    SOURCES_JAR=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name '*-sources.jar' -path '*/1.21.4*' | head -1)
    MERGED_JAR=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name 'minecraft-merged-*.jar' -path '*/1.21.4*' -not -name '*-sources.jar' | head -1)

    if [ -z "$SOURCES_JAR" ]; then
        echo "Error: Minecraft sources JAR not found. Run './gradlew build' first."
        exit 1
    fi

    case "{{cmd}}" in
        list)
            # List classes matching pattern: just mcjar list net/minecraft/client/renderer/
            unzip -l "$SOURCES_JAR" | grep "{{ args }}"
            ;;
        cat)
            # Read entire class: just mcjar cat net/minecraft/client/Minecraft.java
            unzip -p "$SOURCES_JAR" "{{ args }}"
            ;;
        grep)
            # Search in specific class: just mcjar grep shouldEntityAppearGlowing net/minecraft/client/Minecraft.java
            PATTERN="{{ args }}"
            PATTERN_PART="${PATTERN%% *}"
            FILE_PART="${PATTERN#* }"
            unzip -p "$SOURCES_JAR" "$FILE_PART" | grep --color=auto -B3 -A8 "$PATTERN_PART"
            ;;
        grep-all)
            # Search multiple files: just mcjar grep-all startUseItem 'net/minecraft/client/*.java'
            PATTERN="{{ args }}"
            PATTERN_PART="${PATTERN%% *}"
            FILE_PART="${PATTERN#* }"
            unzip -p "$SOURCES_JAR" "$FILE_PART" 2>/dev/null | grep --color=auto -B2 -A5 "$PATTERN_PART"
            ;;
        asset)
            # Read asset file: just mcjar asset rendertype_lines.vsh
            if [ -z "$MERGED_JAR" ]; then
                echo "Error: Minecraft merged JAR not found. Run './gradlew build' first."
                exit 1
            fi
            # Try both with and without assets/minecraft/ prefix
            unzip -p "$MERGED_JAR" "assets/minecraft/{{ args }}" 2>/dev/null || \
            unzip -p "$MERGED_JAR" "{{ args }}" 2>/dev/null || \
            (echo "Error: Asset not found. Try 'just mcjar asset-list' to browse." && exit 1)
            ;;
        asset-list)
            # List assets: just mcjar asset-list shaders/
            if [ -z "$MERGED_JAR" ]; then
                echo "Error: Minecraft merged JAR not found. Run './gradlew build' first."
                exit 1
            fi
            unzip -l "$MERGED_JAR" | grep "assets/minecraft/{{ args }}"
            ;;
        *)
            echo "Unknown command: {{cmd}}"
            echo "Usage:"
            echo "  just mcjar list <path>              # List classes"
            echo "  just mcjar cat <file>               # Read class"
            echo "  just mcjar grep <pattern> <file>    # Search in class"
            echo "  just mcjar grep-all <pattern> <glob># Search multiple files"
            echo "  just mcjar asset <file>             # Read shader/asset file"
            echo "  just mcjar asset-list <path>        # List asset files"
            exit 1
            ;;
    esac
