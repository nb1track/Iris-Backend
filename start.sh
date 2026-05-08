#!/bin/bash
set -e

mkdir -p secrets

echo "Fetching secrets from GCP Secret Manager..."

FIREBASE_JSON=$(gcloud secrets versions access latest --secret="firebase-service-account")
echo "$FIREBASE_JSON" > secrets/gcp-service-account.json

export SPRING_APPLICATION_JSON="$FIREBASE_JSON"
export SPRING_DATASOURCE_PASSWORD=$(gcloud secrets versions access latest --secret="iris-db-beta-password")
export GCP_MAPS_API_KEY=$(gcloud secrets versions access latest --secret="google-maps-api-key")

echo "Starting containers..."
docker compose up "$@"
