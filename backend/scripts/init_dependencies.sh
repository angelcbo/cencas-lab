#!/bin/bash
# init_dependencies.sh
#
# Starts the services required to run the backend locally (database only).
# Run this before starting the Spring Boot application from your IDE or terminal.
#
# Usage:
#   ./backend/scripts/init_dependencies.sh

set -euo pipefail

# Resolve project root relative to this script's location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker Desktop and try again." >&2
    exit 1
fi

echo "Starting backend dependencies..."
cd "$PROJECT_ROOT"

# --wait blocks until the db healthcheck passes (requires Docker Compose v2.1+)
docker compose up db -d --wait

echo ""
echo "Database is ready at localhost:5432"
echo "  DB name:  cenicast_lis"
echo "  User:     cenicast"
echo "  Password: cenicast"
echo ""
echo "To start the backend:"
echo "  cd backend"
echo "  SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run"
echo ""
echo "To also start pgAdmin (http://localhost:5050):"
echo "  docker compose --profile tools up pgadmin -d"
echo ""
echo "To stop when done:"
echo "  docker compose stop db"
