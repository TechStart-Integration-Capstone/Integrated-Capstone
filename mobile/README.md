# PayPink 2.0 Mobile Application (Flutter)

A modern, high-performance Flutter mobile and web client for **PayPink 2.0** — an enterprise digital banking and remittance platform with core banking integration (Temenos T24 simulation), real-time fraud scoring, distributed saga orchestration, and CQRS ledger feeds.

---

## 🏛️ Architecture Overview

The mobile application acts as the edge presentation layer, communicating exclusively with the backend through the **API Gateway** (`api-gateway` on port `8080`). Direct calls to internal microservices are strictly prohibited by network topology.

```
+-----------------------------------------------------------------------+
|                       PayPink Mobile Client                           |
|         (Flutter 3.x / Web PWA / Android / iOS / Desktop)            |
+-----------------------------------------------------------------------+
                                   |
                  HTTPS / REST (JWT Bearer Auth)
                                   v
+-----------------------------------------------------------------------+
|                    API Gateway (:8080)                                |
|  - Rate Limiting (Redis token bucket)                                 |
|  - JWT Authentication & RBAC Filter (X-Auth-Customer-Id injection)    |
|  - CORS Security Allow-list (Localhost, Chrome, Azure Cloud)         |
|  - Dynamic Route Dispatching                                         |
+-----------------------------------------------------------------------+
        |                 |                 |                 |
        v                 v                 v                 v
+---------------+ +---------------+ +---------------+ +---------------+
| auth-service  | |account-service| |transaction-svc| | loan-service  |
|    (:8081)    | |    (:8082)    | |    (:8083)    | |    (:8091)    |
| /auth/banking | | /accounts/me  | | /transactions | | /loans        |
| Login, MPIN,  | | Live balances | | Activity feed | | Applications, |
| Token refresh | | Beneficiaries | | CQRS read-mdl | | Repayments    |
+---------------+ +---------------+ +---------------+ +---------------+
                          |                 |
                          v                 v
                  +-----------------------------------+
                  |        t24-adapter (:8090)        |
                  | Temenos T24 Core Banking Engine   |
                  | Atomic Holds, SoR Double-Entry GL |
                  +-----------------------------------+
```

---

## ✨ Key Features

1. **Authentication & Session Security**
   - Username and password authentication via `/api/v1/auth/banking/login`.
   - Complete registration modal (First Name, Last Name, Phone, Email, Username, Master Password).
   - Dynamic 6-digit MPIN security sheet with persistent session management via `flutter_secure_storage`.
   - Comprehensive RFC-7807 (`application/problem+json`) error handling with user-friendly error banners.

2. **Accounts & Live Core Balances**
   - Real-time balance inquiry connected to Temenos T24 core banking (`/api/v1/accounts/me`).
   - Checking, Savings, and Loan accounts with 3D interactive card deck and quick lock/freeze toggles.

3. **Remittance & Transfers**
   - 4-step distributed Saga transfer orchestration (`/api/v1/remittance/transfer`).
   - Two-layer real-time fraud and AML screening (Rule-based + Isolation Forest ML).
   - Atomic T24 core funds reservation before ledger commitment.
   - Guard against selecting Loan accounts as transfer funding source.

4. **Transaction Activity & Statements**
   - Unified CQRS activity feed (`/api/v1/transactions/activity`) with resilient fallbacks.
   - In-app filters by transaction type (Credit/Debit/Reversals/Checking/Savings/Loans).
   - Downloadable PDF account statements.

5. **Personal Loans**
   - Loan origination, credit evaluation, and repayment tracking.
   - Core banking disbursement and repayment posting.

---

## 🚀 Running the App Locally

### Prerequisites
- [Flutter SDK](https://docs.flutter.dev/get-started/install) (v3.22.0 or higher recommended)
- Google Chrome (for Flutter Web) or an Android / iOS emulator
- Running PayPink backend (either locally in Docker or hosted on Azure Cloud)

### 1. Install Dependencies
```bash
cd mobile
flutter pub get
```

### 2. Connect to the Azure Cloud Backend (Recommended)
The PayPink platform is deployed and active on Azure VM `vm-paypink`:
```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://paypink-levi-westus2.westus2.cloudapp.azure.com:8080/api/v1
```

### 3. Connect to a Local Docker Backend
If running the microservices stack on your local workstation via `docker compose`:
```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080/api/v1
```

### 4. Running on Android Emulator
When running on an Android emulator connecting to a host machine backend, use `10.0.2.2`:
```bash
flutter run -d android --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
```

---

## 👤 Seeded Demo Accounts

You can log in to the application using any of the following pre-seeded customer accounts:

| Username | Password | Full Name | Primary Account | Role |
|:---|:---|:---|:---|:---|
| `lviernes` | `Password123!` | Levi Viernes | `001181233469` (Savings), `001381233467` (Checking) | Customer |
| `arosales` | `Password123!` | Angelica Rosales | `001181233470` (Savings), `001381233468` (Checking) | Customer |
| `glim` | `Password123!` | Gabriel Lim | `001181233471` (Savings), `001381233469` (Checking) | Customer |
| `admin` | `AdminSecret123!` | Admin User | N/A | Administrator |

---

## 🧪 Testing Guide

### 1. Static Code Analysis
Run Flutter analyzer to check for lint issues and contract compliance:
```bash
flutter analyze
```

### 2. Unit and Widget Tests
Execute the automated test suite:
```bash
flutter test
```

### 3. Verification Checklist
- **Login Flow:** Verify that entering valid credentials logs in successfully and retrieves user profile from `/api/v1/accounts/me`.
- **Registration Flow:** Verify that all 7 required fields are validated and registered through `/api/v1/auth/banking/register`.
- **MPIN Setup:** Verify prompt to set up 6-digit MPIN for newly registered users.
- **Card Balance Inquiry:** Verify live balances load without 403 Forbidden errors.
- **Transfer Saga:** Perform a P2P transfer between `lviernes` and `arosales` and observe real-time balance updates.
- **Transactions Feed:** Verify that recent transfers appear under Activity with accurate timestamps and counterparty details.

---

## 🐳 Docker Deployment

The mobile application includes a production-grade multi-stage `Dockerfile` serving compiled Flutter Web artifacts through a lightweight Alpine Nginx server.

### 1. Build the Docker Image
```bash
docker build -t paypink-mobile:latest -f mobile/Dockerfile mobile/
```

### 2. Run the Container Standalone
```bash
docker run -d -p 3002:80 --name paypink-mobile-app paypink-mobile:latest
```
Access the application at `http://localhost:3002`.

### 3. Run via Docker Compose
From the project root:
```bash
docker compose -f docker/docker-compose.yml up -d mobile-app
```
The mobile application will be served at port `3002`, routed internally to `api-gateway:8080`.

---

## 🔧 Troubleshooting & Error Handling

- **502 / 503 Service Unavailable ("PayPink is starting up"):**
  When cloud or local containers are cold-starting, the app will cleanly surface: *"PayPink is starting up. Please wait a moment and try again."* Retry after 15–30 seconds.
- **429 Too Many Requests:**
  API Gateway rate limits client requests using Redis token buckets. Wait for the cooldown window before retrying.
- **CORS Issues on Web:**
  Ensure the API Gateway `application.yml` contains your browser origin (e.g., `http://localhost:[*]`, `http://*.cloudapp.azure.com:[*]`).
- **Profile Loading Fails (403 Forbidden):**
  Ensure the app does not call admin-only endpoints (`/api/v1/accounts/customers`). Regular customers must query `/api/v1/accounts/me`.
