# PayPink 2.0 — Azure CLI "Cheat Code" Reference

A comprehensive field cheatsheet for every Azure CLI (`az`) command used in the PayPink 2.0 Cloud Architecture, deployment lifecycle, budget controls, and emergency troubleshooting.

---

## Quick Reference Navigation
1. [Authentication & Account Context](#1-authentication--account-context)
2. [Resource Group Operations](#2-resource-group-operations)
3. [Virtual Machine (VM) Lifecycle & Sizing](#3-virtual-machine-vm-lifecycle--sizing)
4. [Budget & Cost Controls ($10 Cap)](#4-budget--cost-controls-10-cap)
5. [Network Security Group (NSG) & Firewall Rules](#5-network-security-group-nsg--firewall-rules)
6. [Public IP & DNS Management](#6-public-ip--dns-management)
7. [Remote Script Execution & SSH Key Management](#7-remote-script-execution--ssh-key-management)
8. [Azure SQL Database & Firewall Rules](#8-azure-sql-database--firewall-rules)
9. ["Cheat Code" Quick Scenarios](#9-cheat-code-quick-scenarios)

---

## 1. Authentication & Account Context

### `az login`
* **What is it:** Logs into Microsoft Azure via browser or device code authentication.
* **Why is it for:** Authenticates your local terminal or workstation session to interact with Azure resources.
* **Where to use:** Run once on your local terminal or developer laptop before running any scripts or cloud commands.
```bash
az login
```
*(On headless or terminal-only environments, run `az login --use-device-code` and enter the code at https://microsoft.com/devicelogin)*

---

### `az account show` / `az account list`
* **What is it:** Displays your currently active Azure subscription, tenant ID, and user account.
* **Why is it for:** Confirms that commands are being executed against the correct subscription (e.g., student credit, capstone credit, or team subscription).
* **Where to use:** Workstation terminal before provisioning resources.
```bash
# View active subscription
az account show -o table

# List all subscriptions you have access to
az account list -o table
```

---

### `az account set`
* **What is it:** Sets your active working subscription ID.
* **Why is it for:** Switches context if your account has multiple Azure subscriptions.
* **Where to use:** Workstation terminal.
```bash
az account set --subscription "<SUBSCRIPTION_ID_OR_NAME>"
```

---

## 2. Resource Group Operations

### `az group show`
* **What is it:** Checks whether a resource group exists and inspects its details (such as provisioning state and region location).
* **Why is it for:** Validates resource group existence before deploying VMs or networking.
* **Where to use:** Used in deployment scripts (e.g., [`scripts/01-provision-azure.sh`](file:///scripts/01-provision-azure.sh)).
```bash
az group show --name "RG-PAYPINK-WESTUS2" --output table
```

---

### `az group create`
* **What is it:** Creates a logical Azure Resource Group container.
* **Why is it for:** Holds all related PayPink cloud assets (VM, Disks, Public IPs, NSG, Virtual Networks) in one management and billing boundary.
* **Where to use:** Infrastructure setup script.
```bash
az group create --name "RG-PAYPINK-WESTUS2" --location "westus2"
```

---

## 3. Virtual Machine (VM) Lifecycle & Sizing

### `az vm create`
* **What is it:** Provisions a complete Linux Virtual Machine, attached OS disk, network interface, and public IP in a single command.
* **Why is it for:** Sets up `vm-paypink` (`Standard_D4s_v3` / 4 vCPU / 16 GiB RAM) to host the 26 microservice containers.
* **Where to use:** [`scripts/01-provision-azure.sh`](file:///scripts/01-provision-azure.sh) during initial infrastructure setup.
```bash
az vm create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink" \
  --location "westus2" \
  --image "Canonical:ubuntu-24_04-lts:server:latest" \
  --size "Standard_D4s_v3" \
  --admin-username "azureuser" \
  --generate-ssh-keys \
  --nsg "vm-paypink-nsg" \
  --public-ip-sku "Standard" \
  --public-ip-address-dns-name "paypink-levi-westus2" \
  --os-disk-size-gb 64 \
  --storage-sku "StandardSSD_LRS"
```

#### Breakdown of Important Flags:
| Flag | Description | Why PayPink Uses It |
| :--- | :--- | :--- |
| `--size Standard_D4s_v3` | 4 vCPU, 16 GiB RAM | Lowest compute size capable of running the 26 JVM & Docker containers |
| `--image Canonical:ubuntu-24_04-lts:server:latest` | Ubuntu 24.04 LTS OS | Official LTS Linux distribution for Docker and GitHub Runner |
| `--storage-sku StandardSSD_LRS` | Standard SSD | Cost-efficient balance between disk I/O performance and budget limits |
| `--public-ip-address-dns-name` | FQDN subdomain | Assigns `paypink-levi-westus2.westus2.cloudapp.azure.com` |

---

### `az vm show` & `az vm list-ip-addresses`
* **What is it:** Retrieves the live power state, OS status, private IP, and public IP of a VM.
* **Why is it for:** Checking if the VM is running, deallocated, or fetching its current IP.
* **Where to use:** Local workstation terminal or Azure Cloud Shell.
```bash
# Check power status (Running / Deallocated)
az vm show -d -g "RG-PAYPINK-WESTUS2" -n "vm-paypink" --query "powerState" -o tsv

# Get Public IP address
az vm list-ip-addresses -g "RG-PAYPINK-WESTUS2" -n "vm-paypink" -o table
```

---

### `az vm start`
* **What is it:** Powers on a deallocated or stopped VM and allocates compute resources in the Azure datacenter.
* **Why is it for:** Starting up the PayPink platform before a live demo, defense, or sprint review.
* **Where to use:** Workstation terminal or Cloud Shell.
```bash
az vm start --resource-group "RG-PAYPINK-WESTUS2" --name "vm-paypink"
```

---

### `az vm restart`
* **What is it:** Reboots the Linux operating system inside the VM.
* **Why is it for:** Applying kernel updates, restarting the Docker daemon cleanly, or clearing host-level deadlocks.
* **Where to use:** Workstation terminal or Cloud Shell.
```bash
az vm restart --resource-group "RG-PAYPINK-WESTUS2" --name "vm-paypink"
```

---

## 4. Budget & Cost Controls ($10 Cap)

> [!CAUTION]
> **CRITICAL COST DISTINCTION: `stop` vs. `deallocate`**
> * Running `sudo shutdown -h now` inside Linux **DOES NOT STOP BILLING**. Azure keeps CPU/RAM reserved for your VM and continues charging compute rates (~$0.192/hour).
> * Only `az vm deallocate` releases datacenter hardware so compute billing **drops to $0.00**.

---

### `az vm deallocate` *(The Cost-Saver)*
* **What is it:** Shuts down the VM and releases its allocated hardware in the Azure datacenter.
* **Why is it for:** Completely stops compute hourly charges. The only cost that remains is disk storage (~$0.25/day).
* **Where to use:** Immediately after every coding session, demo, or evaluation window.
```bash
az vm deallocate --resource-group "RG-PAYPINK-WESTUS2" --name "vm-paypink"
```

---

### `az vm auto-shutdown`
* **What is it:** Configures an automatic daily shutdown schedule for the virtual machine.
* **Why is it for:** Safety net against forgetting to shut down the VM, protecting against draining the $10 credit cap.
* **Where to use:** Initial setup or re-enabling after demonstrations.
```bash
# Configure automatic shutdown at 11:00 UTC (7:00 PM Philippine Time PHT)
az vm auto-shutdown \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink" \
  --time "1100"
```

---

### `az vm auto-shutdown --off`
* **What is it:** Disables the scheduled auto-shutdown mechanism.
* **Why is it for:** Prevents Azure from shutting down the server in the middle of an evening class, presentation, or capstone defense.
* **Where to use:** Run before starting an extended evaluation window.
```bash
az vm auto-shutdown \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink" \
  --off
```

---

## 5. Network Security Group (NSG) & Firewall Rules

The Azure NSG acts as an external hardware firewall in front of your VM. All ports not explicitly whitelisted are dropped by Azure's built-in `DenyAllInBound` rule.

---

### `az network nsg create`
* **What is it:** Creates a Network Security Group resource.
* **Why is it for:** Provides the container for all inbound and outbound firewall rules protecting `vm-paypink`.
* **Where to use:** Infrastructure provisioning script [`scripts/01-provision-azure.sh`](file:///scripts/01-provision-azure.sh).
```bash
az network nsg create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink-nsg" \
  --location "westus2"
```

---

### `az network nsg rule list`
* **What is it:** Displays all configured firewall rules, port ranges, priorities, and access modes in a table.
* **Why is it for:** Verifying which ports are currently exposed to the internet and auditing IP whitelists.
* **Where to use:** Troubleshooting connectivity issues (e.g. `ERR_CONNECTION_REFUSED` or timeout).
```bash
az network nsg rule list \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --nsg-name "vm-paypink-nsg" \
  -o table
```

---

### `az network nsg rule create` (Whitelisting Web & Mobile Traffic)
* **What is it:** Adds an inbound firewall rule allowing public traffic on specific application ports.
* **Why is it for:**
  * **Port 80:** Public Web Banking SPA (Nginx)
  * **Port 3002:** Flutter Mobile App (Web/PWA)
  * **Port 8080:** API Gateway REST Endpoints
* **Where to use:** Provisioning or when adding new public ingress endpoints.
```bash
# 1. Allow Public Web SPA (Port 80)
az network nsg rule create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --nsg-name "vm-paypink-nsg" \
  --name "Allow-HTTP-SPA" \
  --priority 110 \
  --direction Inbound \
  --access Allow \
  --protocol Tcp \
  --destination-port-ranges 80

# 2. Allow Mobile PWA (Port 3002)
az network nsg rule create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --nsg-name "vm-paypink-nsg" \
  --name "Allow-Mobile-PWA" \
  --priority 115 \
  --direction Inbound \
  --access Allow \
  --protocol Tcp \
  --destination-port-ranges 3002

# 3. Allow API Gateway (Port 8080)
az network nsg rule create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --nsg-name "vm-paypink-nsg" \
  --name "Allow-API-Gateway" \
  --priority 120 \
  --direction Inbound \
  --access Allow \
  --protocol Tcp \
  --destination-port-ranges 8080
```

---

### `az network nsg rule create` (Whitelisting Teammate SSH Access)
* **What is it:** Whitelists a teammate's specific home/office IP (`/32`) on Port 22.
* **Why is it for:** Grants secure SSH access without leaving Port 22 open to the entire internet (brute-force defense).
* **Where to use:** When onboarding a teammate or when your ISP IP changes.
```bash
# Find teammate public IP: curl -s ifconfig.me
az network nsg rule create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --nsg-name "vm-paypink-nsg" \
  --name "Allow-SSH-Teammate" \
  --priority 100 \
  --direction Inbound \
  --access Allow \
  --protocol Tcp \
  --source-address-prefixes "<TEAMMATE_IP>/32" \
  --destination-port-ranges 22
```

---

### `az network nsg rule delete`
* **What is it:** Removes a firewall rule by name.
* **Why is it for:** Revoking access from an outdated IP address or closing a temporary test port.
* **Where to use:** Security maintenance.
```bash
az network nsg rule delete \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --nsg-name "vm-paypink-nsg" \
  --name "Allow-SSH-OldIP"
```

---

## 6. Public IP & DNS Management

### `az network public-ip list` / `show`
* **What is it:** Inspects the public IP resource attached to the VM.
* **Why is it for:** Retrieves the assigned IPv4 address and Fully Qualified Domain Name (FQDN).
* **Where to use:** Used in deployment validation and scripts.
```bash
# Retrieve FQDN and IP
az network public-ip list \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --query "[].{IP:ipAddress, FQDN:dnsSettings.fqdn}" \
  -o table
```

---

### `az network public-ip update` (Setting DNS Domain Name)
* **What is it:** Configures the custom subdomain label for Azure's `cloudapp.azure.com` domain.
* **Why is it for:** Gives your VM a friendly, persistent hostname so you do not have to memorize numerical IP addresses.
* **Where to use:** When provisioning or renaming the DNS label.
```bash
az network public-ip update \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink-ip" \
  --dns-name "paypink-levi-westus2"
```
*(Results in: `paypink-levi-westus2.westus2.cloudapp.azure.com`)*

---

## 7. Remote Script Execution & SSH Key Management

### `az vm run-command invoke` *(The Emergency Backdoor)*
* **What is it:** Executes a bash shell script or command inside the VM directly as `root` via the Azure Guest Agent, completely bypassing SSH and the NSG firewall.
* **Why is it for:**
  * Running maintenance when Port 22 is blocked or your IP is not whitelisted.
  * Fixing broken configurations without opening an interactive SSH session.
  * Executing volume migration scripts like [`scripts/04-migrate-compose-project.sh`](file:///scripts/04-migrate-compose-project.sh).
* **Where to use:** Workstation terminal or Azure Cloud Shell.
```bash
# Example 1: Check Docker containers on the VM without SSH
az vm run-command invoke \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink" \
  --command-id RunShellScript \
  --scripts "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"

# Example 2: Append a teammate's SSH key without needing to log in
az vm run-command invoke \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink" \
  --command-id RunShellScript \
  --scripts "echo '<TEAMMATE_PUBLIC_KEY>' >> /home/azureuser/.ssh/authorized_keys"

# Example 3: Execute a script file stored on your laptop directly on the VM
az vm run-command invoke \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink" \
  --command-id RunShellScript \
  --scripts @scripts/04-migrate-compose-project.sh
```

---

### `az vm user update` *(Emergency SSH Key Reset)*
* **What is it:** Updates the Linux credentials and resets the public SSH key for an administrative user (`azureuser`).
* **Why is it for:** Regains access if Azure Cloud Shell drops its ephemeral keys or a developer loses their private SSH key.
* **Where to use:** Emergency access recovery.
```bash
az vm user update \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --name "vm-paypink" \
  --username "azureuser" \
  --ssh-key-value ~/.ssh/id_rsa.pub
```
> [!WARNING]
> Running `az vm user update` **overwrites** the existing `~/.ssh/authorized_keys` file. If multiple teammates share `azureuser`, use `az vm run-command invoke` with `echo ... >> authorized_keys` instead so you do not lock out your teammates!

---

## 8. Azure SQL Database & Firewall Rules

PayPink uses hosted Azure Cloud SQL PaaS (`paypink-sql.database.windows.net`) for persistent banking records and T24 ledger state.

### `az sql server firewall-rule list`
* **What is it:** Lists all IP whitelist rules configured on the Azure SQL database server.
* **Why is it for:** Verifying whether your local laptop IP or the Azure VM's outbound IP is permitted to connect to port 1433.
* **Where to use:** Cloud Shell or developer workstation.
```bash
az sql server firewall-rule list \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --server "paypink-sql" \
  -o table
```

---

### `az sql server firewall-rule create`
* **What is it:** Adds a client IP or range to the Azure SQL Server firewall whitelist.
* **Why is it for:** Allows local tools (like DBeaver, Azure Data Studio, or local Spring Boot dev runs) or the Azure VM to connect to the cloud database.
* **Where to use:** Database setup or when connecting from a new network.
```bash
# Whitelist developer laptop IP
az sql server firewall-rule create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --server "paypink-sql" \
  --name "Allow-Dev-Laptop" \
  --start-ip-address "<YOUR_LOCAL_PUBLIC_IP>" \
  --end-ip-address "<YOUR_LOCAL_PUBLIC_IP>"

# Whitelist Azure internal services / VMs
az sql server firewall-rule create \
  --resource-group "RG-PAYPINK-WESTUS2" \
  --server "paypink-sql" \
  --name "AllowAllWindowsAzureIps" \
  --start-ip-address "0.0.0.0" \
  --end-ip-address "0.0.0.0"
```

---

## 9. "Cheat Code" Quick Scenarios

### Scenario A: "Class / Defense is over — Stop spending money NOW!"
```bash
az vm deallocate -g RG-PAYPINK-WESTUS2 -n vm-paypink
```
*Verification:* Run `az vm show -d -g RG-PAYPINK-WESTUS2 -n vm-paypink --query "powerState" -o tsv` $\rightarrow$ should return `VM deallocated`.

---

### Scenario B: "Class / Defense is starting — Power on the system!"
```bash
# 1. Start VM
az vm start -g RG-PAYPINK-WESTUS2 -n vm-paypink

# 2. Disable auto-shutdown so it doesn't shut down during the demo
az vm auto-shutdown -g RG-PAYPINK-WESTUS2 -n vm-paypink --off
```

---

### Scenario C: "A teammate got a 'Connection Timed Out' error trying to SSH"
Their IP is not in the NSG whitelist.
```bash
# Ask teammate to check their IP: curl -s ifconfig.me
az network nsg rule create \
  -g RG-PAYPINK-WESTUS2 \
  --nsg-name vm-paypink-nsg \
  -n "Allow-SSH-Teammate" \
  --priority 102 \
  --access Allow \
  --protocol Tcp \
  --destination-port-ranges 22 \
  --source-address-prefixes "<TEAMMATE_IP>/32"
```

---

### Scenario D: "I need to inspect the running Docker containers right now without SSH"
```bash
az vm run-command invoke \
  -g RG-PAYPINK-WESTUS2 \
  -n vm-paypink \
  --command-id RunShellScript \
  --scripts "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
```

---

### Scenario E: "What is our live public URL and IP again?"
```bash
az network public-ip list \
  -g RG-PAYPINK-WESTUS2 \
  --query "[].{IP:ipAddress, FQDN:dnsSettings.fqdn}" \
  -o table
```
* **Web Banking Portal:** `http://<FQDN>:80`
* **Mobile Banking Web/PWA:** `http://<FQDN>:3002`
* **API Gateway Health:** `http://<FQDN>:8080/actuator/health`
