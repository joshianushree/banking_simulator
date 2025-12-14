// src/services/customer.js
import { api } from "./api";

// --------------------- ACCOUNT DATA ---------------------
export const fetchCustomerAccount = (accountNumber) =>
  api.get(`/accounts/${accountNumber}`);

export const updateAccount = (accountNumber, payload) =>
  api.put(`/accounts/${accountNumber}`, payload);

// --------------------- ACCOUNT DELETE (NEW) ---------------------
export const deleteCustomerAccount = (payload) =>
  api.post("/accounts/delete", payload, { withCredentials: true });

// --------------------- TRANSACTIONS ---------------------
export const deposit = (payload) =>
  api.post("/transactions/deposit", payload);

export const withdraw = (payload) =>
  api.post("/transactions/withdraw", payload);

export const transfer = (payload) =>
  api.post("/transactions/transfer", payload);

export const getTransactions = (accNo) =>
  api.get(`/transactions/${accNo}`);

export const downloadMiniStatement = (accNo) =>
  api.get(`/transactions/ministatement/${accNo}`, {
    responseType: "blob",
  });

// --------------------- NEW TX PIN APIs (WRAPPER) ---------------------
export const requestTransactionPinOtp = (accountNumber, contact) =>
  api.post("/auth/customer/transaction-pin/request", {
    accountNumber,
    contact,
  });

export const verifyTransactionPinOtp = (accountNumber, otp) =>
  api.post("/auth/customer/transaction-pin/verify", {
    accountNumber,
    otp,
  });

/* ========================================================================
   ⭐ NEW LOAN FEATURE API ENDPOINTS
   ======================================================================== */

// --------------------- SUBMIT LOAN REQUEST ---------------------
export const submitLoanRequest = (payload) =>
  api.post("/loan/request", payload);

// --------------------- FETCH LOAN STATUS FOR A USER ---------------------
export const fetchLoanStatus = (accountNumber) =>
  api.get(`/loan/status/${accountNumber}`);

// --------------------- EARLY LOAN CLOSURE ---------------------
export const closeLoanEarly = (payload) =>
  api.post("/loan/close", payload, { withCredentials: true });
