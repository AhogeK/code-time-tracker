#!/usr/bin/env bash
# Verifies the full sync credential flow against a local ctt-server:
# register -> email verification (Mailpit) -> login -> create SYNC-scoped API key -> key accepted by server.
#
# Uses throwaway accounts (timestamp-suffixed email) and the hCaptcha official test
# keys (see test-auth-bootstrap skill). stdout carries the raw key + probe result;
# progress goes to stderr. Env overrides: CTT_API, MAILPIT.
set -euo pipefail

CTT_API="${CTT_API:-http://localhost:8080/ctt-server}"
MAILPIT="${MAILPIT:-http://localhost:8025}"
TS="$(date +%s)"
EMAIL="sync-test-${TS}@example.com"
PASSWORD="SyncTest-Pass-${TS}!"
DEVICE="sync-test-device-${TS}"
CAPTCHA="10000000-aaaa-bbbb-cccc-000000000001"

log() { echo "[sync-flow] $*" >&2; }

log "registering ${EMAIL}"
REG="$(curl -sS -X POST "${CTT_API}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"displayName\":\"SyncTest\",\"deviceId\":\"${DEVICE}\",\"termsVersion\":\"1.0.0\",\"captchaToken\":\"${CAPTCHA}\"}")"
echo "${REG}" | grep -q '"success":true' || { log "register failed: ${REG}"; exit 1; }

log "waiting for the verification email in Mailpit"
TOKEN=""
for _ in $(seq 1 15); do
  MSGS="$(curl -sS "${MAILPIT}/api/v1/messages?limit=10")"
  MID="$(echo "${MSGS}" | python3 -c "
import sys, json
try:
    ms = [m for m in json.load(sys.stdin).get('messages', []) if m.get('To', [{}])[0].get('Address') == '${EMAIL}']
    print(ms[0]['ID'] if ms else '')
except Exception:
    print('')" 2>/dev/null || true)"
  if [ -n "${MID}" ]; then
    BODY="$(curl -sS "${MAILPIT}/api/v1/message/${MID}")"
    TOKEN="$(echo "${BODY}" | python3 -c "
import sys, json, re
try:
    html = json.load(sys.stdin).get('HTML') or ''
    m = re.search(r'[?&]token=([^\"&]+)', html)
    print(m.group(1) if m else '')
except Exception:
    print('')" 2>/dev/null || true)"
    [ -n "${TOKEN}" ] && break
  fi
  sleep 2
done
[ -n "${TOKEN}" ] || { log "verification email/token not found"; exit 1; }

log "verifying email"
curl -sS "${CTT_API}/api/v1/auth/verify-email?token=${TOKEN}" | grep -q '"success":true' \
  || { log "email verification failed"; exit 1; }

log "logging in"
LOGIN="$(curl -sS -X POST "${CTT_API}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"deviceId\":\"${DEVICE}\",\"captchaToken\":\"${CAPTCHA}\"}")"
JWT="$(echo "${LOGIN}" | python3 -c "
import sys, json
print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null || true)"
[ -n "${JWT}" ] || { log "login failed: ${LOGIN}"; exit 1; }

log "creating a SYNC-scoped API key"
KEY="$(curl -sS -X POST "${CTT_API}/api/v1/auth/api-keys" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${JWT}" \
  -d '{"name":"sync-flow-test","scopes":["SYNC"]}')"
RAW="$(echo "${KEY}" | python3 -c "
import sys, json
print(json.load(sys.stdin)['data']['rawKey'])" 2>/dev/null || true)"
[ -n "${RAW}" ] || { log "key creation failed: ${KEY}"; exit 1; }

log "probing the key (expect 403 AUTH_020: valid SYNC key without READ scope)"
PROBE="$(curl -sS -o /dev/null -w "%{http_code}" "${CTT_API}/api/v1/auth/api-keys" -H "Authorization: Bearer ${RAW}")"
echo "rawKey=${RAW}"
echo "probeHttpStatus=${PROBE}"
if [ "${PROBE}" = "403" ]; then
  log "OK: key accepted by the server, SYNC scope only"
else
  log "unexpected probe status: ${PROBE} (key may still be valid)"
fi
