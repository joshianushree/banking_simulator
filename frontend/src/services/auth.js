// src/services/auth.js
import { api } from "./api";

// --------------------- LOGIN OTP FLOW ---------------------
export const verifyOtp = (identifier, otp) =>
  api.post("/auth/otp/verify", { identifier, otp });

// --------------------- NEW TX PIN FLOWS ---------------------
// Request OTP to generate new transaction PIN
export const requestTxPinOtp = (accountNumber, contact) =>
  api.post("/auth/customer/transaction-pin/request", {
    accountNumber,
    contact,
  });

// Verify OTP and receive NEW TRANSACTION PIN
export const verifyTxPinOtp = (accountNumber, otp) =>
  api.post("/auth/customer/transaction-pin/verify", {
    accountNumber,
    otp,
  });

// --------------------- LOGIN & FORGOT PIN ---------------------
export const customerLogin = (accountNumber, pin) =>
  api.post("/auth/customer/login", { accountNumber, pin });

export const adminLogin = (username, password) =>
  api.post("/auth/admin/login", { username, password });

export const forgotRequest = (payload) =>
  api.post("/auth/forgot/request", payload);

export const forgotVerify = (payload) =>
  api.post("/auth/forgot/verify", payload);

export const forgotReset = (payload) =>
  api.post("/auth/forgot/reset", payload);
