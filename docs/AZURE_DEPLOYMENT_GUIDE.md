# PayPink 2.0 — Azure Cloud & CI/CD Deployment Guide
**Omnichannel Remittance, Real-Time Fraud Screening & Core Retail Ledger**

---

## 1. Cloud Architecture & Pipeline Stage Topology

### Infrastructure Topology
PayPink 2.0 is hosted on Microsoft Azure as a containerized microservice matrix running on an Ubuntu 24.04 LTS Virtual Machine (`Standard_D4s_v3`, 4 vCPU / 16GB RAM) orchestrated by Docker Compose.

```mermaid
flowchart TD
    subgraph Clients["Clients & Ingress"]
        Browser["Admin & Customer Web SPA\n(HTTP :80)"]
        Mobile["Flutter Mobile App\n(REST :8080)"]
        Devs["Engineering Team\n(SSH :22 Whitelisted)"]
    end

    subgraph AzureRG["Azure Resource Group (RG-PAYPINK-WESTUS2)"]
        subgraph NSG["Network Security Group (NSG)"]
            FWRules["Inbound Rules:\n• Port 80 (HTTP / Nginx Web SPA)\n• Port 3002 (Flutter Mobile PWA)\n• Port 8080 (API Gateway)\n• Port 22 (SSH - Team IPs Whitelisted)\n• Azure Default Deny All Else"]
        end

        subgraph VM["Ubuntu 24.04 LTS VM (vm-paypink: Standard_D4s_v3 - 4 vCPU / 16GB RAM)"]
            subgraph Edge["Edge Layer"]
                Nginx["Web SPA (Nginx :80)"]
                MobileEdge["Mobile PWA (Nginx :3002)"]
                Gateway["API Gateway (Spring Cloud :8080)"]
            end

            subgraph CoreServices["Internal Microservices (ledger-net)"]
                Orchestrator["remittance-orchestrator (:8083)"]
                Risk["risk-engine (Python FastAPI :8000)"]
                T24["t24-adapter + mock-core (127.0.0.1:9089)"]
                Auth["auth-service (:8081)"]
                Account["account-service (:8082)"]
                Loan["loan-service (:8091)"]
                Notif["notification-service (:8084)"]
                Audit["audit-service (:8085)"]
                Recon["reconciliation-service (:8086)"]
                Outbox["outbox-publisher (:8087)"]
                Analytics["analytics-service (:8088)"]
            end

            subgraph Datastores["Datastores & Streaming"]
                SQL["Azure SQL / MSSQL (Master OLTP :1433)"]
                Postgres["PostgreSQL 15 (Immutable Audit :5432)"]
                Redis["Redis 7 (Idempotency & Rate Limit :6379)"]
                Kafka["Apache Kafka + Zookeeper (:9092)"]
            end

            subgraph Telemetry["Observability Stack (Loopback)"]
                OTel["OpenTelemetry Collector (:4317/:4318)"]
                Prom["Prometheus (:9090)"]
                Loki["Loki (:3100)"]
                Tempo["Tempo (:3200)"]
                Grafana["Grafana (127.0.0.1:3000)"]
            end

            Runner["GitHub Self-Hosted Runner\n(v2.337.0 - Labels: self-hosted, azure-vm)"]
        end
    end

    Browser -->|HTTP :80| Nginx
    Nginx -->|Proxy /api/| Gateway
    Mobile -->|HTTP :3002 / REST :8080| Gateway
    Devs -->|SSH :22 (Team IPs)| VM

    Gateway --> CoreServices
    CoreServices --> Datastores
    CoreServices -.->|OTLP Metrics/Traces| OTel
    OTel --> Prom
    OTel --> Loki
    OTel --> Tempo
    Prom --> Grafana
    Loki --> Grafana
    Tempo --> Grafana
```

---

## 2. CI/CD Pipeline Stages Architecture

PayPink 2.0 uses a 3-stage CI/CD pipeline. **Dev and Test stages use free GitHub cloud runners (zero Azure compute cost)**; deployments to the Azure VM only trigger on the `main` branch after approval.

```mermaid
flowchart TD
    Devs["Developers\n(Local Compose Dev)"] --> Repo["GitHub Repo\n(Pull requests, main)"]
    Repo --> S1

    subgraph S1["1. Dev Stage (GitHub-Hosted Runner - $0 Cost)"]
        S1_Build["Parallel Java Builds & Unit Tests\n(Maven verify on 11 services)"]
        S1_Pytest["Risk Engine Tests\n(Python 3.12 pytest)"]
        S1_Trivy["Security & Config Scan\n(Trivy CRITICAL exit code 0)"]
        S1_Build --> S1_Unit_Done["Dev Checks Passed"]
        S1_Pytest --> S1_Unit_Done
        S1_Trivy --> S1_Unit_Done
    end

    S1 --> S2

    subgraph S2["2. Test Stage (Temporary Stack, Torn Down After)"]
        S2_Up["Start Essential CI Services\n(docker compose -p paypink-ci up -d)"]
        S2_Health["Wait for Gateway Health\n(http://localhost:8080/actuator/health)"]
        S2_Contract["Newman API Contract Tests\n(Postman Collection)"]
        S2_Teardown["Tear Down CI Stack & Upload Reports\n(docker compose down -v)"]
        S2_Up --> S2_Health --> S2_Contract --> S2_Teardown
    end

    S2 --> S3

    subgraph S3["3. Prod Stage (Self-Hosted Runner on Azure VM)"]
        S3_Gate["Approval Gate\n(GitHub Environment: production)"]
        S3_Disk["Disk Space Verification\n(Minimum 5 GB free)"]
        S3_Compile["Compile Java Service JARs\n(mvn clean package -DskipTests)"]
        S3_Deploy["Deploy Stack via Docker Compose\n(docker compose --env-file /opt/paypink/.env up -d --build)"]
        S3_Health["Gateway & Web SPA Health Checks\n(Up to 180s probe)"]
        S3_Prune["Clean Up Dangling Images\n(docker image prune -f)"]
        S3_Gate --> S3_Disk --> S3_Compile --> S3_Deploy --> S3_Health --> S3_Prune
    end

    S3_Health --> AzureVM["Azure VM (Prod Live Host: vm-paypink)\n(NSG: Ports 80, 3002 & 8080 open)"]

    classDef auto fill:#d1fae5,stroke:#059669,stroke-width:2px,color:#065f46;
    classDef manual fill:#fef3c7,stroke:#d97706,stroke-width:2px,color:#92400e;
    classDef prod fill:#ede9fe,stroke:#7c3aed,stroke-width:2px,color:#5b21b6;

    class S1_Build,S1_Pytest,S1_Trivy,S1_Unit_Done,S2_Up,S2_Health,S2_Contract,S2_Teardown auto;
    class S3_Gate manual;
    class S3_Disk,S3_Compile,S3_Deploy,S3_Health,S3_Prune,AzureVM prod;
```

---

## 3. Azure Budget Optimization ($10 Cloud Budget Strategy)

### Budget Reality & Calculations
* **Compute:** `Standard_D4s_v3` (4 vCPU, 16 GiB RAM) costs **~$0.192 per hour**.
* **Storage & Static IP:** A 64 GB Standard SSD and Standard Public IP incur background charges even when the VM is deallocated (**~$0.25 to $0.30 per day**).
* **Net Available Runtime:** Over a typical 9–10 day capstone sprint, static costs consume ~$2.50. The remaining ~$7.50 provides **~40 hours of active VM runtime**.

### Mandatory Cost Control Protocols:
1. **Always Deallocate when Inactive (Stopping inside Linux does NOT pause compute billing):**
   ```bash
   az vm deallocate --resource-group "RG-PAYPINK-WESTUS2" --name "vm-paypink"
   ```
2. **Auto-Shutdown Schedule (Every day at 7:00 PM PHT / 11:00 UTC):**
   Configured in `01-provision-azure.sh` or via Azure CLI:
   ```bash
   az vm auto-shutdown \
     --resource-group "RG-PAYPINK-WESTUS2" \
     --name "vm-paypink" \
     --time "1100"
   ```
3. **Configure DNS Label (Preserves FQDN across deallocations):**
   ```bash
   az network public-ip update \
     --resource-group "RG-PAYPINK-WESTUS2" \
     --name "vm-paypinkPublicIP" \
     --dns-name "paypink-levi-westus2"
   ```
   **Active FQDN:** `paypink-levi-westus2.westus2.cloudapp.azure.com` (Public IP: `20.69.157.88`).

---

## 4. Step-by-Step Deployment Walkthrough

### Step 1: Provision Azure Infrastructure via CLI
Run [`scripts/01-provision-azure.sh`](file:///scripts/01-provision-azure.sh) from your laptop or Cloud Shell after `az login`:

```bash
export RESOURCE_GROUP="RG-PAYPINK-WESTUS2"
export DNS_LABEL="paypink-levi-westus2"
export TEAM_IPS="<YOUR_IP>/32"   # Replace with teammates' public IPs (curl ifconfig.me)
export VM_NAME="vm-paypink"
export LOCATION="westus2"

./scripts/01-provision-azure.sh
```

**Key Security Configurations Applied:**
* **SSH Port 22:** Whitelisted exclusively to `${TEAM_IPS}`.
* **HTTP Port 80, Mobile Port 3002 & API Gateway Port 8080:** Open for customer/mobile ingress.
* **All Other Ports:** Blocked by Azure's default `DenyAllInBound` rule.

---

### Step 2: Bootstrap the Ubuntu 24.04 VM Host
SSH into the VM as `azureuser` and run the bootstrap script:

```bash
ssh azureuser@20.69.157.88
```

> [!TIP]
> **Cloud Shell SSH Key Recovery:** If Cloud Shell restarts in ephemeral mode and reports `Permission denied (publickey)`, generate a fresh key in Cloud Shell and push it directly using Azure CLI:
> ```bash
> ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N ""
> az vm user update --resource-group RG-PAYPINK-WESTUS2 --name vm-paypink --username azureuser --ssh-key-value ~/.ssh/id_rsa.pub
> ssh azureuser@20.69.157.88
> ```

**Install Build Tools & Bootstrap:**
```bash
# 1. Install Java 17 and Maven for artifact packaging:
sudo apt-get update -y && sudo apt-get install -y maven openjdk-17-jdk

# 2. Run bootstrap script:
./02-bootstrap-vm.sh
```

**What this script configures:**
1. **4 GB Swap Space:** Safety net for bursts.
2. **Memory Limit Tuning:** Creates `/opt/paypink/.env` with `MSSQL_MEMORY_LIMIT_MB=3072` and `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60` so ~20+ containers run smoothly within 16 GB RAM.
3. **Hardened Docker Daemon:** Enables log rotation (`max-size: 10m`, `max-file: 3`) to prevent disk exhaustion.
4. **Secure User Permissions:** Adds `azureuser` to the `docker` group (`usermod -aG docker $USER`).

---

### Step 3: Install GitHub Self-Hosted Runner on VM
1. Go to your GitHub Repository $\rightarrow$ **Settings** $\rightarrow$ **Actions** $\rightarrow$ **Runners** $\rightarrow$ **New self-hosted runner** (Linux / x64).
2. Run the configuration on the VM (`azureuser@vm-paypink:~$`):
   ```bash
   mkdir -p ~/actions-runner && cd ~/actions-runner

   # Download & extract runner package
   curl -o actions-runner-linux-x64-2.337.0.tar.gz -L https://github.com/actions/runner/releases/download/v2.337.0/actions-runner-linux-x64-2.337.0.tar.gz
   tar xzf ./actions-runner-linux-x64-2.337.0.tar.gz

   # Register with required labels
   ./config.sh --url https://github.com/TechStart-Integration-Capstone/Integrated-Capstone \
     --token <RUNNER_TOKEN_FROM_GITHUB> \
     --labels "self-hosted,azure-vm" --unattended

   # Install & start systemd background service
   sudo ./svc.sh install azureuser
   sudo ./svc.sh start
   ```

   Check service status:
   ```bash
   sudo systemctl status actions.runner.*
   ```

---

### Step 4: Validate External Port Hardening (Chaos 3 Evidence)
Run [`scripts/03-verify-ports.sh`](file:///scripts/03-verify-ports.sh) from outside the VM:

```bash
./scripts/03-verify-ports.sh paypink-levi-westus2.westus2.cloudapp.azure.com
```

**Expected Result:**
* Ports **80**, **3002**, and **8080** are `OPEN`.
* Port **22** is `OPEN` only if your IP is in the NSG whitelist.
* All internal ports (`1433`, `3000`, `5432`, `6379`, `8081-8091`, `9089`, `9092`) report `closed` or `filtered`.

---

## 5. Live Services & Verification URLs

| Component | Port & Scope | Target Access URL |
| :--- | :--- | :--- |
| **Web SPA (Nginx)** | `80` (Public) | `http://20.69.157.88` / `http://paypink-levi-westus2.westus2.cloudapp.azure.com` |
| **Mobile PWA App** | `3002` (Public) | `http://20.69.157.88:3002` |
| **API Gateway Health** | `8080` (Public) | `http://20.69.157.88:8080/actuator/health` |
| **T24 Mock Core Sidecar** | `127.0.0.1:9089` (Loopback) | `http://127.0.0.1:9089` (Internal container network) |
| **Grafana Observability** | `127.0.0.1:3000` (Loopback) | `ssh -L 3000:localhost:3000 azureuser@20.69.157.88` $\rightarrow$ `http://localhost:3000` |

### Security & Seed Accounts
* Seed accounts configured in database scripts should load secure demo passwords from `/opt/paypink/.env`.
* Keep the deployment repository private to prevent leaking environment configurations and host addresses.
* Run `scripts/migrate_reconciliation_fix.sql` and `scripts/migrate_interest_postgres.sql` inside the `postgres-immutable-audit` container to ensure database schema parity.
