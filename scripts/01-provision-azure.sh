#!/usr/bin/env bash
# PayPink 2.0 - Step 1: create the NSG and VM in Azure.
# Run from your LAPTOP or Azure Cloud Shell after `az login`.
#
# Example:
#   export RESOURCE_GROUP="rg-azuser8406_mml.local-722QP"
#   export DNS_LABEL="paypink-demo-ph"
#   export TEAM_IPS="203.0.113.10/32 198.51.100.7/32"   # one /32 per teammate (curl ifconfig.me)
#   ./scripts/01-provision-azure.sh
set -euo pipefail

RESOURCE_GROUP="${RESOURCE_GROUP:?Set RESOURCE_GROUP (use your assigned resource group)}"
DNS_LABEL="${DNS_LABEL:?Set DNS_LABEL (lowercase, globally unique, e.g. paypink-demo-ph)}"
TEAM_IPS="${TEAM_IPS:?Set TEAM_IPS to space-separated CIDRs, e.g. \"203.0.113.10/32 198.51.100.7/32\"}"
VM_NAME="${VM_NAME:-vm-paypink-test}"
VM_SIZE="${VM_SIZE:-Standard_D4s_v3}"
ADMIN_USER="${ADMIN_USER:-azureuser}"
SHUTDOWN_UTC="${SHUTDOWN_UTC:-1100}"   # 11:00 UTC = 7:00 PM Philippine time
NSG_NAME="${VM_NAME}-nsg"

read -r -a TEAM_IP_ARR <<< "$TEAM_IPS"

# Use the existing resource group (and its region) if there is one.
if az group show -n "$RESOURCE_GROUP" >/dev/null 2>&1; then
  LOCATION="${LOCATION:-$(az group show -n "$RESOURCE_GROUP" --query location -o tsv)}"
else
  LOCATION="${LOCATION:-southeastasia}"
  az group create -n "$RESOURCE_GROUP" -l "$LOCATION" >/dev/null
fi
echo "Using resource group '$RESOURCE_GROUP' in '$LOCATION'"

echo "Creating NSG..."
az network nsg create -g "$RESOURCE_GROUP" -n "$NSG_NAME" -l "$LOCATION" >/dev/null

# SSH only from the team
az network nsg rule create -g "$RESOURCE_GROUP" --nsg-name "$NSG_NAME" \
  -n Allow-SSH-Team --priority 100 --direction Inbound --access Allow --protocol Tcp \
  --destination-port-ranges 22 --source-address-prefixes "${TEAM_IP_ARR[@]}" >/dev/null

# Web SPA (Nginx) and API gateway
az network nsg rule create -g "$RESOURCE_GROUP" --nsg-name "$NSG_NAME" \
  -n Allow-HTTP-SPA --priority 110 --direction Inbound --access Allow --protocol Tcp \
  --destination-port-ranges 80 >/dev/null

az network nsg rule create -g "$RESOURCE_GROUP" --nsg-name "$NSG_NAME" \
  -n Allow-API-Gateway --priority 120 --direction Inbound --access Allow --protocol Tcp \
  --destination-port-ranges 8080 >/dev/null
# Everything else is blocked by Azure's built-in DenyAllInBound rule.

echo "Creating VM (this takes a few minutes)..."
az vm create -g "$RESOURCE_GROUP" -n "$VM_NAME" -l "$LOCATION" \
  --image "Canonical:ubuntu-24_04-lts:server:latest" \
  --size "$VM_SIZE" \
  --admin-username "$ADMIN_USER" \
  --generate-ssh-keys \
  --nsg "$NSG_NAME" \
  --public-ip-sku Standard \
  --public-ip-address-dns-name "$DNS_LABEL" \
  --os-disk-size-gb 64 \
  --storage-sku StandardSSD_LRS >/dev/null

echo "Setting daily auto-shutdown at ${SHUTDOWN_UTC} UTC (7:00 PM PHT)..."
az vm auto-shutdown -g "$RESOURCE_GROUP" -n "$VM_NAME" --time "$SHUTDOWN_UTC" >/dev/null

FQDN=$(az network public-ip list -g "$RESOURCE_GROUP" \
  --query "[?contains(name,'${VM_NAME}')].dnsSettings.fqdn | [0]" -o tsv)

cat << MSG

Done.
  VM host name : ${FQDN}
  SSH          : ssh ${ADMIN_USER}@${FQDN}
  Next         : copy 02-bootstrap-vm.sh to the VM and run it:
                 scp scripts/02-bootstrap-vm.sh ${ADMIN_USER}@${FQDN}:~ && ssh ${ADMIN_USER}@${FQDN} ./02-bootstrap-vm.sh

Cost control (do these now):
  * Set a budget alert in the Azure portal (Cost Management > Budgets).
  * Stop billing with:  az vm deallocate -g ${RESOURCE_GROUP} -n ${VM_NAME}
    (stopping inside Linux does NOT stop compute billing)
MSG
