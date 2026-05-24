#!/bin/bash
set -e

# Stop the bank simulator when this script exits (Ctrl+C or error)
trap 'echo "Stopping bank simulator..."; docker-compose down' EXIT

echo "Starting bank simulator..."
docker-compose up -d bank_simulator

echo "Waiting for bank simulator to be ready..."
until curl -s http://localhost:8080/payments > /dev/null 2>&1; do
  sleep 1
done
echo "Bank simulator is ready."

echo "Starting Payment Gateway..."
./gradlew bootRun
