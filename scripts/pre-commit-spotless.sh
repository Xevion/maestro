#!/bin/bash
set -e

# Capture staged files before formatting
staged_files=$(git diff --cached --name-only --diff-filter=ACM)

# Run Spotless to format code
./gradlew spotlessApply --quiet

# Re-stage files that were originally staged (in case Spotless modified them)
for file in $staged_files; do
    if [ -f "$file" ]; then
        git add "$file"
    fi
done
