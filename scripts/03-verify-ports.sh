#!/usr/bin/env bash
# PayPink 2.0 - Step 3: port check from OUTSIDE the VM (Chaos 3 evidence).
# Usage: ./03-verify-ports.sh <vm-dns-name-or-ip>
# Expected: 80, 3002 (mobile PWA, if deployed) and 8080 open; every other port filtered/closed.
# (22 is open only if your IP is in the NSG SSH rule.)
set -uo pipefail
HOST="${1:?Usage: $0 <vm-dns-name-or-ip>}"
PORTS="22,80,1433,2181,3000,3002,3100,3200,4317,5432,6379,8080-8099,9089,9090,9092"

if command -v nmap >/dev/null 2>&1; then
  nmap -Pn -p "$PORTS" "$HOST" | tee "port-scan-$(date +%Y%m%d-%H%M).txt"
else
  echo "nmap not found - using a basic TCP check (install nmap for better evidence)."
  for p in 22 80 1433 2181 3000 3002 3100 3200 4317 5432 6379 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091 9089 9090 9092; do
    if timeout 3 bash -c "</dev/tcp/$HOST/$p" 2>/dev/null; then echo "$p OPEN"; else echo "$p closed/filtered"; fi
  done
fi
