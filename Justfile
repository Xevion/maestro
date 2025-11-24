# Default recipe
default: check

# Quick type checking and linting
check:
    ./gradlew spotlessCheck check --no-daemon

# Format code
fmt:
    ./gradlew spotlessApply

# Run all tests
test:
    ./gradlew test

# Build production artifacts
build:
    ./gradlew build

# Development server (run Minecraft client)
dev:
    ./gradlew runClient

# Clean build
clean:
    ./gradlew clean
