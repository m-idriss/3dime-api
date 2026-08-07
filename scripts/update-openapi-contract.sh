#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

mvn -B -ntp -DskipTests package
mkdir -p contracts
cp target/openapi/openapi.json contracts/openapi-v1.json

echo "Updated contracts/openapi-v1.json"
