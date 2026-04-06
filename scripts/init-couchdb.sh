#!/bin/sh
# CouchDB one-time initialization: single-node cluster + CORS for PouchDB sync.
# Idempotent — safe to run on every `docker compose up`.
set -euo pipefail

COUCHDB_USER="${COUCHDB_USER:-admin}"
COUCHDB_PASSWORD="${COUCHDB_PASSWORD:-admin}"
BASE="http://${COUCHDB_USER}:${COUCHDB_PASSWORD}@couchdb:5984"

echo "==> Finishing single-node cluster setup..."
curl -sf -X POST "${BASE}/_cluster_setup" \
  -H "Content-Type: application/json" \
  -d '{"action":"finish_cluster"}' || echo "    (already initialized, skipping)"

echo "==> Enabling CORS for PouchDB sync..."
curl -sf -X PUT "${BASE}/_node/_local/_config/httpd/enable_cors" -d '"true"'
curl -sf -X PUT "${BASE}/_node/_local/_config/cors/origins" -d '"*"'
curl -sf -X PUT "${BASE}/_node/_local/_config/cors/credentials" -d '"true"'
curl -sf -X PUT "${BASE}/_node/_local/_config/cors/methods" \
  -d '"GET, PUT, POST, HEAD, DELETE"'
curl -sf -X PUT "${BASE}/_node/_local/_config/cors/headers" \
  -d '"accept, authorization, content-type, origin, referer, cache-control, x-requested-with"'

echo "==> CouchDB initialization complete."
