# Default recipe
default: check

# Quick type checking and linting
check:
    ./gradlew check --no-daemon

# Format code
fmt:
    ./gradlew spotlessApply

# Check code formatting
format-check:
    ./gradlew spotlessCheck

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
