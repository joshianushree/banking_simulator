// src/App.js
import { BrowserRouter, Routes, Route } from "react-router-dom";

import HomePage from "./pages/HomePage";
import Login from "./pages/Login";

import AdminDashboard from "./pages/AdminDashboard";
import CustomerDashboard from "./pages/CustomerDashboard";
import ProtectedRoute from "./components/ProtectedRoute";

// Admin Pages
import AdminAccounts from "./pages/AdminAccounts";
import AdminCreateAccount from "./pages/AdminCreateAccount";
import AdminLockedAccounts from "./pages/AdminLockedAccounts";
import AdminRollback from "./pages/AdminRollback";
import AdminReports from "./pages/AdminReports";
import AdminManageAdmins from "./pages/AdminManageAdmins";

// Customer Pages
import CustomerDeposit from "./pages/CustomerDeposit";
import CustomerWithdraw from "./pages/CustomerWithdraw";
import CustomerTransfer from "./pages/CustomerTransfer";
import CustomerTransactions from "./pages/CustomerTransactions";
import CustomerGeneratePin from "./pages/CustomerGeneratePin";
import StatisticsPage from "./pages/StatisticsPage";

// NEW — Customer Delete Account Page
import CustomerDeleteAccount from "./pages/CustomerDeleteAccount";

// NEW FEATURE IMPORTS — corrected filenames
import LoanRequestPage from "./pages/LoanRequestPage";         // ✅ correct
import AdminDeletionRequests from "./pages/AdminDeletionRequests";  // correct
import AdminLoanRequests from "./pages/AdminLoanRequests";         // correct

// OPTIONAL PAGE (only import if file exists)
// import LoanEarlyClosurePage from "./pages/LoanEarlyClosurePage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>

        {/* PUBLIC ROUTES */}
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<Login />} />

        {/* CUSTOMER ACCOUNT CREATION ROUTE */}
        <Route path="/create-account" element={<AdminCreateAccount />} />

        {/* =====================================================================
            ADMIN ROUTES
        ====================================================================== */}
        <Route
          path="/admin"
          element={
            <ProtectedRoute role="admin">
              <AdminDashboard />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/manage-admins"
          element={
            <ProtectedRoute role="admin">
              <AdminManageAdmins />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/accounts"
          element={
            <ProtectedRoute role="admin">
              <AdminAccounts />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/create-account"
          element={
            <ProtectedRoute role="admin">
              <AdminCreateAccount />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/locked-accounts"
          element={
            <ProtectedRoute role="admin">
              <AdminLockedAccounts />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/rollback"
          element={
            <ProtectedRoute role="admin">
              <AdminRollback />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/reports"
          element={
            <ProtectedRoute role="admin">
              <AdminReports />
            </ProtectedRoute>
          }
        />

        {/* NEW ADMIN FEATURE ROUTES */}
        <Route
          path="/admin/deletion-requests"
          element={
            <ProtectedRoute role="admin">
              <AdminDeletionRequests />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/loan-requests"
          element={
            <ProtectedRoute role="admin">
              <AdminLoanRequests />
            </ProtectedRoute>
          }
        />

        {/* =====================================================================
            CUSTOMER ROUTES
        ====================================================================== */}
        <Route
          path="/customer"
          element={
            <ProtectedRoute role="customer">
              <CustomerDashboard />
            </ProtectedRoute>
          }
        />

        <Route
          path="/customer/deposit"
          element={
            <ProtectedRoute role="customer">
              <CustomerDeposit />
            </ProtectedRoute>
          }
        />

        <Route
          path="/customer/withdraw"
          element={
            <ProtectedRoute role="customer">
              <CustomerWithdraw />
            </ProtectedRoute>
          }
        />

        <Route
          path="/customer/transfer"
          element={
            <ProtectedRoute role="customer">
              <CustomerTransfer />
            </ProtectedRoute>
          }
        />

        <Route
          path="/customer/transactions"
          element={
            <ProtectedRoute role="customer">
              <CustomerTransactions />
            </ProtectedRoute>
          }
        />

        <Route
          path="/customer/transaction-pin"
          element={
            <ProtectedRoute role="customer">
              <CustomerGeneratePin />
            </ProtectedRoute>
          }
        />

        <Route
          path="/customer/statistics"
          element={
            <ProtectedRoute role="customer">
              <StatisticsPage />
            </ProtectedRoute>
          }
        />

        {/* NEW CUSTOMER FEATURE ROUTES */}
        <Route
          path="/customer/delete-account"
          element={
            <ProtectedRoute role="customer">
              <CustomerDeleteAccount />
            </ProtectedRoute>
          }
        />

        <Route
          path="/customer/loan-request"
          element={
            <ProtectedRoute role="customer">
              <LoanRequestPage />
            </ProtectedRoute>
          }
        />

        {/* Optional Early Closure Page — uncomment ONLY if file exists */}
        {/* 
        <Route
          path="/customer/loan-close"
          element={
            <ProtectedRoute role="customer">
              <LoanEarlyClosurePage />
            </ProtectedRoute>
          }
        />
        */}

      </Routes>
    </BrowserRouter>
  );
}
