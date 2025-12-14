# 🏦 AstroNova Banking Simulator

A full-stack **Banking Management System** that simulates real-world banking operations with strong security, role-based access, OTP authentication, account lifecycle management, loan workflows, transaction tracking, and PDF reporting.

This project is designed to demonstrate **enterprise-grade backend logic using Spring Boot** and a **modern React frontend**.

---

## 📌 Key Features

### 🔐 Authentication & Security
- OTP-based login for **Admin and Customer**
- Passwords & PINs encrypted using **BCrypt**
- Role-based access control (ADMIN / CUSTOMER)
- Account lock after multiple failed login attempts
- Transaction PIN security layer
- Audit logging for sensitive actions

### 👤 Customer Features
- Open bank account (via Admin)
- View dashboard with:
  - Account number
  - IFSC code
  - Branch name
  - Balance & status
- Deposit, withdraw, and transfer money
- Category-based transactions
- Transaction history & mini statements (PDF)
- Request account deletion (soft delete workflow)
- Loan request with:
  - Govt ID upload
  - EMI plan selection
  - Interest calculation
- View loan details & repayment status
- OTP-based PIN reset & transaction PIN generation
- Email & SMS notifications

### 👨‍💼 Admin Features
- Admin dashboard
- Create & manage customer accounts
- Manage other admins
- Lock / unlock accounts
- View & rollback transactions
- Review account deletion requests
- Review loan requests (approve / reject with comments)
- Generate reports:
  - Accounts report (PDF)
  - Transactions report (PDF)
  - Mini statements
- Branch-wise report filtering
- View audit logs

### 📄 Reports & Documents
- Accounts PDF report
- Transactions PDF report
- Customer mini-statement PDF
- Secure access control
- Branch & IFSC aware reporting

---

## 🛠️ Tech Stack

### ⚙ Backend
- Java 17
- Spring Boot
- Spring JDBC (JdbcTemplate)
- MySQL
- BCrypt (password & PIN hashing)
- iText PDF
- Java Mail / Notification Service
- RESTful APIs

### 💻 Frontend
- React.js
- React Router
- Axios
- Tailwind CSS
- OTP Modals
- Role-based Protected Routes

---

## 🗂️ Project Structure

```
banking-simulator/
├── backend/
│ ├── src/main/java/com/bankingsim/
│ │ ├── controller/
│ │ ├── dao/
│ │ ├── model/
│ │ ├── service/
│ │ ├── util/
│ │ └── config/
│ │ └── DatabaseInitializer.java
│ └── src/main/resources/
│ └── application.properties
│
├── frontend/
│ ├── public/
│ │ └── galaxy-bg.png
│ ├── src/
│ │ ├── pages/
│ │ ├── components/
│ │ ├── services/
│ │ └── App.js
│ └── package.json
│
└── README.md
```

---

## 🗃️ Database Tables (Auto-Initialized)

The backend automatically creates & updates all required tables on startup.

### Core Tables
- `accounts`
- `users`
- `transactions`
- `otp_verification`
- `audit_log`

### Advanced Workflow Tables
- `deletion_requests`
- `loan_requests`

### Important Flags & Columns
- `is_locked`
- `failed_attempts`
- `deletion_req`
- `is_deleted`
- `taken_loan`
- `loan_amount`
- `loan_interest_rate`
- `loan_total_due`
- `branch_name`
- `ifsc_code`
- `transaction_pin`

---

## 🚀 Getting Started

### 🔙 Backend Setup

1. Navigate to backend directory
   ```bash
   cd backend
    ```

2. **Create a MySQL database named `banking_simulator`**  
   Update the following credentials in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/banking_simulator
   spring.datasource.username=root
   spring.datasource.password=your_password
   ```

3. **Run the Spring Boot application**  
   Either using an IDE (like IntelliJ) or command line:
   ```bash
   ./mvnw spring-boot:run
   ```

✅ The backend will run at: `http://localhost:8080`
⭐ A default admin is auto-created:
    Username: admin
    Password: admin123

### 🌐 Frontend Setup

1. **Navigate to the frontend directory**  
   ```bash
   cd request-manager-frontend
   ```

2. **Install frontend dependencies**  
   ```bash
   npm install
   ```

3. **Start the frontend server**  
   ```bash
   npm start
   ```
✅ The frontend will run at: `http://localhost:3000`

---

## 👤 User Roles

### 👩‍💼 Customer

- Login via Account Number + PIN + OTP
- Perform transactions
- View statements & reports
- Request account deletion
- Request loans
- Manage transaction PIN

### 👨‍💻 Admin
- Login via Username + Password + OTP
- Manage customers & admins
- Review deletion & loan requests
- Generate reports
- Lock/unlock accounts
- Rollback transactions

---

## 🔄 Account Deletion Workflow

- Customer submits deletion request
- deletion_req = 1 in DB
- Admin reviews request
- If approved:
-- is_deleted = 1
-- Account soft-deleted
-- Email sent to customer
- If rejected:
-- Comment sent to customer
-- Request reset

---

## 🏦 Loan Workflow

-Customer submits loan request
-Govt ID upload & EMI plan selection
-Admin reviews transaction history & balance
-Loan approved / rejected with comment
-Approved loans update:
    --taken_loan
    --loan_total_due
--loan_interest_rate
-Loan reflected in customer dashboard

---

## 🔐 Security Highlights

- BCrypt hashing for passwords and transaction PINs
- OTP verification for critical and sensitive actions
- Transaction-level locking to avoid inconsistent updates
- Audit trail maintained for all admin activities
- Soft delete mechanism (no permanent data removal)

---

## 🏗️ Architecture Overview

```
             +----------------------+
             |   React Frontend     |
             +----------+-----------+
                        |
                        | REST / Axios APIs
                        |
             +----------v-----------+
             | Spring Boot Backend  |
             +----------+-----------+
                        |
                 +------v------+
                 |  MySQL DB   |
                 +-------------+
```

---

## 🔄 Data Flow

- User interacts with the React-based interface
- Requests are sent using Axios as REST API calls
- Spring Boot handles validation, security, and business logic
- MySQL database stores and retrieves persistent data
- Processed responses are returned to the frontend

---

## 🧪 Sample Credentials

| Role     | Identifier  | Password / PIN |
|----------|-------------|----------------|
| Admin    | admin       | admin123       |
| Customer | Account No  | User-defined   |

---

## ✨ Project Highlights

- Real-world banking system simulation
- Enterprise-grade backend logic
- Clean separation of frontend, backend, and database layers
- Auto-initialized and auto-migrated database schema
- Secure, scalable, and maintainable architecture

---

## 📬 Contact

Feel free to raise issues or suggestions.  
This project is built for learning, demonstration, and portfolio use.
