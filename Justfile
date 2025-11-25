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
    ./gradlew runClient --args="--username Dev{{`shuf -i 1000000-9999999 -n 1`}}"

# Clean build
clean:
    ./gradlew clean

# Install git pre-commit hooks (requires uvx/uv)
install-hooks:
    uvx pre-commit install
