# PayPink 2.0 — Azure Cloud & CI/CD Deployment Guide
**Omnichannel Remittance, Real-Time Fraud Screening & Core Retail Ledger**
*(Test & Evaluation Environment: West US 2 / Standard_D4s_v3)*

> [!NOTE]
> **Environment Context & Architecture Mapping:**
> This guide describes the active **Test & Sandbox Environment** deployed in Azure region `westus2` on an optimized `Standard_D4s_v3` VM (4 vCPU, 16 GiB RAM) to comply with the strict **$10 cloud budget limit**. The enterprise production specification described in general project architecture targets `Standard_D8s_v5` (8 vCPU, 32 GiB RAM) in `eastasia` / `southeastasia` for high-throughput multi-region resilience.

> [!WARNING]
> **Security & Access Control:**
> The live IP (`20.69.157.88`) and FQDN in this guide are operational references for internal evaluation only. Keep this repository strictly private. Do NOT expose live IP addresses or public hostnames in external slide decks or public demonstrations.

---

## 1. Cloud Architecture & Pipeline Stage Topology

### Infrastructure Topology
PayPink 2.0 is hosted on Microsoft Azure as a containerized microservice matrix running on an Ubuntu 24.04 LTS Virtual Machine (`vm-paypink`: `Standard_D4s_v3`, 4 vCPU / 16 GiB RAM) orchestrated by Docker Compose.

```mermaid
flowchart TD
    subgraph Clients["Clients & Ingress"]
        Browser["Admin & Customer Web SPA\n(HTTP :80)"]
        Mobile["Flutter Mobile App\n(HTTP :3002)"]
        Devs["Engineering Team\n(SSH :22)"]
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
    Devs -->|"SSH :22, team IPs"| VM

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

### Memory Footprint & Container Sizing
* **Active Stack on `Standard_D4s_v3` (16 GiB RAM):** Runs the 26 core operational containers (edge gateways, 12 backend microservices, datastores, Kafka, and telemetry collectors) utilizing JVM heap limits (`-XX:MaxRAMPercentage=60`), SQL Server buffer cap (`3072 MB`), and a 4 GiB swap safety partition.
* **Full Enterprise 31-Container Stack:** The un-trimmed enterprise stack (with multi-replica service instances, dedicated chaos simulation workers, and multi-node telemetry clusters) requires **24–26 GiB RAM**, which corresponds to `Standard_D8s_v5`.

---

## 2. CI/CD Pipeline Stages Architecture

PayPink 2.0 uses a 3-stage CI/CD pipeline. **Dev and Test stages use free GitHub cloud runners (zero Azure compute cost)**; deployments to the Azure VM only trigger on the `main` branch after approval.

```mermaid
flowchart TD
    Devs["Developers\n(Local Compose Dev)"] --> Repo["GitHub Repo\n(Pull requests, main)"]
    Repo --> S1

    subgraph S1["1. Dev Stage (GitHub-Hosted Runner - $0 Cost)"]
        S1_Build["Parallel Java Unit Tests\n(Maven test on 11 microservices)"]
        S1_Pytest["Risk Engine Tests\n(Python 3.12 pytest)"]
        S1_Trivy["Security & Config Scan\n(Trivy CRITICAL audit, report-only)"]
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
        S3_Compile["Host Maven Build\n(Package 11 Java service JARs)"]
        S3_Deploy["Deploy Stack via Docker Compose\n(docker compose --env-file /opt/paypink/.env up -d --build)"]
        S3_Health["Gateway & Web SPA Health Checks\n(Up to 180s probe)"]
        S3_Prune["Clean Up Dangling Images\n(docker image prune -f)"]
        S3_Gate --> S3_Disk --> S3_Compile --> S3_Deploy --> S3_Health --> S3_Prune
    end

    S3_Health --> AzureVM["Azure VM (Test Live Host: vm-paypink)\n(NSG: Ports 80, 3002 & 8080 open)"]

    classDef auto fill:#d1fae5,stroke:#059669,stroke-width:2px,color:#065f46;
    classDef manual fill:#fef3c7,stroke:#d97706,stroke-width:2px,color:#92400e;
    classDef prod fill:#ede9fe,stroke:#7c3aed,stroke-width:2px,color:#5b21b6;

    class S1_Build,S1_Pytest,S1_Trivy,S1_Unit_Done,S2_Up,S2_Health,S2_Contract,S2_Teardown auto;
    class S3_Gate manual;
    class S3_Disk,S3_Compile,S3_Deploy,S3_Health,S3_Prune,AzureVM prod;
```

### Architectural Trade-off: Host Maven Build vs. Multi-Stage Dockerfiles (Task 3 Grading)
* **Design Decision:** The production deployment step on the self-hosted runner executes `mvn -B clean package -DskipTests` on the VM host prior to `docker compose up -d --build`, with Dockerfiles utilizing single-stage runtime images (`eclipse-temurin:17-jre-alpine`).
* **Engineering Rationale:**
  1. *Resource Constraints on 16 GiB VM:* Performing 11 individual multi-stage Docker builds sequentially or concurrently would spin up 11 heavyweight Maven build containers inside Docker, downloading redundant dependencies or triggering extreme disk layer thrashing and Docker daemon locks on a single VM.
  2. *Build Acceleration & Cache Sharing:* Running Maven on the host leverages the shared host `~/.m2/repository` cache across all 11 services. Builds complete in ~1.5 minutes (versus 15+ minutes in Docker) with zero Docker storage bloat.
  3. *Production Parity:* The runtime containers remain lean Alpine JRE images with no compilers, Maven binaries, or build tools included in the deployed artifacts.

### Security Scan Policy: Report-Only Audit Scan
* The **Trivy security scan** step runs with `exit-code: '0'` and `continue-on-error: true`.
* **Purpose:** It operates as an **audit and transparency report** rather than a hard blocking gate. Critical CVEs and configuration warnings are published to the GitHub Actions workflow summary for team visibility without blocking PR merges during sprint iterations.

---

## 3. Azure Budget & Network Governance

### Budget Reality ($10 Cloud Budget Strategy)
* **Compute:** `Standard_D4s_v3` costs **~$0.192 per hour**.
* **Storage & Static IP:** 64 GB SSD and Standard IP cost **~$0.25 to $0.30 per day** even while deallocated.
* **Cost Protocol:**
  1. **Always Deallocate when Inactive (Stopping inside Linux does NOT pause compute billing):**
     ```bash
     az vm deallocate --resource-group "RG-PAYPINK-WESTUS2" --name "vm-paypink"
     ```
  2. **Daily Auto-Shutdown Schedule (7:00 PM PHT / 11:00 UTC):**
     ```bash
     az vm auto-shutdown --resource-group "RG-PAYPINK-WESTUS2" --name "vm-paypink" --time "1100"
     ```

### NSG Rule Management & Dynamic Cloud Shell IPs
* Azure Cloud Shell outbound IPs change dynamically every session. To connect via SSH from Cloud Shell, port 22 in the NSG must allow the connection.
* **Inspect Current Rules:**
  ```bash
  az network nsg rule list -g RG-PAYPINK-WESTUS2 --nsg-name vm-paypink-nsg -o table
  ```
* **Restrict Access:** Replace `TEAM_IPS` placeholders with exact `/32` CIDRs for team members (`curl ifconfig.me`). Never leave port 22 open to `*` or `Any` in production.

### SSH Key Recovery Warning
* Using `az vm user update --ssh-key-value ~/.ssh/id_rsa.pub` updates the public key for `azureuser`, which **replaces** the active `~/.ssh/authorized_keys` file.
* **Teammate Access:** If teammates previously had their SSH keys authorized, their keys will be overwritten. Always re-append teammates' public keys after running a key reset:
  ```bash
  echo "<TEAMMATE_PUBLIC_KEY>" >> ~/.ssh/authorized_keys
  ```

### Performance & Latency Context (Philippines vs. West US 2)
* `West US 2` has an unavoidable physical network RTT of **~150–200 ms** from Manila, Philippines over public transit.
* **SLA Validation:** Do not measure internal banking transaction latency (e.g. target `< 50 ms`) from a laptop browser in Manila. Measure latency **server-side** using OpenTelemetry trace spans in Grafana/Tempo or run load tests from a client VM within the same Azure region.

---

## 4. Step-by-Step Deployment Walkthrough

### Step 1: Provision Azure Infrastructure via CLI
Run [`scripts/01-provision-azure.sh`](file:///scripts/01-provision-azure.sh) from your laptop or Cloud Shell after `az login`:

```bash
export RESOURCE_GROUP="RG-PAYPINK-WESTUS2"
export DNS_LABEL="paypink-levi-westus2"
export TEAM_IPS="<TEAMMATE_1_IP>/32 <TEAMMATE_2_IP>/32"   # curl ifconfig.me
export VM_NAME="vm-paypink"
export LOCATION="westus2"

./scripts/01-provision-azure.sh
```

**Key Security Configurations Applied:**
* **SSH Port 22:** Whitelisted exclusively to `${TEAM_IPS}`.
* **HTTP Port 80, Mobile Port 3002 & API Gateway Port 8080:** Open for client/customer ingress.
* **All Other Ports:** Blocked by Azure's default `DenyAllInBound` rule.

---

### Step 2: Bootstrap the Ubuntu 24.04 VM Host
Copy [`scripts/02-bootstrap-vm.sh`](file:///scripts/02-bootstrap-vm.sh) to the VM and execute:

```bash
# 1. Copy script to VM:
scp scripts/02-bootstrap-vm.sh azureuser@20.69.157.88:~

# 2. SSH into VM:
ssh azureuser@20.69.157.88

# 3. Install Maven and OpenJDK 17 on the host for runner artifact packaging:
sudo apt-get update -y && sudo apt-get install -y maven openjdk-17-jdk

# 4. Execute host bootstrap:
./02-bootstrap-vm.sh
```

> [!TIP]
> **Ephemeral Cloud Shell SSH Recovery:** If Cloud Shell reconnects in ephemeral mode and loses its keypair:
> ```bash
> ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N ""
> az vm user update --resource-group RG-PAYPINK-WESTUS2 --name vm-paypink --username azureuser --ssh-key-value ~/.ssh/id_rsa.pub
> ssh azureuser@20.69.157.88
> ```

---

### Step 3: Install GitHub Self-Hosted Runner on VM
1. Go to your GitHub Repository $\rightarrow$ **Settings** $\rightarrow$ **Actions** $\rightarrow$ **Runners** $\rightarrow$ **New self-hosted runner** (Linux / x64).
2. Execute the configuration inside the VM (`azureuser@vm-paypink:~$`):
   ```bash
   mkdir -p ~/actions-runner && cd ~/actions-runner

   # Download & unpack runner (version 2.337.0)
   curl -o actions-runner-linux-x64-2.337.0.tar.gz -L https://github.com/actions/runner/releases/download/v2.337.0/actions-runner-linux-x64-2.337.0.tar.gz
   tar xzf ./actions-runner-linux-x64-2.337.0.tar.gz

   # Register with mandatory labels
   ./config.sh --url https://github.com/TechStart-Integration-Capstone/Integrated-Capstone \
     --token <RUNNER_TOKEN_FROM_GITHUB> \
     --labels "self-hosted,azure-vm" --unattended

   # Install & start systemd background service
   sudo ./svc.sh install azureuser
   sudo ./svc.sh start
   ```

   Verify runner service:
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
* Ports **80**, **3002**, and **8080** report `OPEN`.
* Port **22** reports `OPEN` only if your IP is in the NSG whitelist.
* All internal ports (`1433`, `2181`, `3000`, `3100`, `3200`, `4317`, `5432`, `6379`, `8081-8099`, `9089`, `9090`, `9092`) report `closed` or `filtered`.

---

## 5. Live Services, Network Protocols & Known Technical Constraints

| Component | Port & Scope | Target Access URL |
| :--- | :--- | :--- |
| **Web SPA (Nginx)** | `80` (Public) | `http://20.69.157.88` / `http://paypink-levi-westus2.westus2.cloudapp.azure.com` |
| **Mobile App (Flutter PWA)** | `3002` (Public) | `http://20.69.157.88:3002` |
| **API Gateway Health** | `8080` (Public) | `http://20.69.157.88:8080/actuator/health` |
| **T24 Mock Core Sidecar** | `127.0.0.1:9089` (Loopback) | `http://127.0.0.1:9089` (Internal container network) |
| **Grafana Observability** | `127.0.0.1:3000` (Loopback) | `ssh -L 3000:localhost:3000 azureuser@20.69.157.88` $\rightarrow$ `http://localhost:3000` |

### Technical Considerations & Known Limitations:
1. **Cross-Origin Resource Sharing (CORS):**
   * The Web SPA (`:80`) and Mobile App (`:3002`) both consume the API Gateway (`:8080`), creating cross-origin requests.
   * Spring Cloud Gateway is configured in `api-gateway/src/main/resources/application.yml` with `allowedOriginPatterns: "*"` and `allowCredentials: true`, supporting both origins simultaneously.
2. **Plain HTTP vs. PWA Installability:**
   * Browsers strictly require HTTPS for Service Worker registration and PWA installation prompts ("Add to Home Screen"). Over plain HTTP on a public IP, the Flutter app functions seamlessly as a responsive mobile web application for evaluation.
3. **Database Schema Initialization vs. Existing Volume Migrations:**
   * **Fresh VM / Rebuilds:** Docker Compose mounts `microservices/audit-service/.../schema-postgres.sql` directly into `/docker-entrypoint-initdb.d/01_schema.sql`, so all tables (including `RECONCILIATION_LOG`, `RISK_DECISION`, and interest tables) are created automatically.
   * **Existing Pre-Seeded Volumes:** Run `scripts/migrate_reconciliation_fix.sql` and `scripts/migrate_interest_postgres.sql` inside the `postgres-immutable-audit` container to apply additive changes without data loss.
