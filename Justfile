# Default recipe
default: check

# Quick type checking and linting
check:
    ./gradlew check -q --no-daemon

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
dev:
    ./gradlew runClient

# Clean build
clean:
    ./gradlew clean

# Install git pre-commit hooks (requires uvx/uv)
install-hooks:
    uvx pre-commit install
