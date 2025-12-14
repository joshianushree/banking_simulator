// src/services/accounts.js
import { api } from "./api";

/**
 * ⭐ CUSTOMER — CREATE USING MULTIPART
 */
export const createCustomerAccount = (formData) =>
  api.post("/accounts/create-customer", formData, {
    headers: {
      "Content-Type": "multipart/form-data"
    }
  });

/**
 * ⭐ ADMIN — SAME AS BEFORE
 */
export const createAdminAccount = (payload) =>
  api.post("/accounts/create-admin", payload);

/**
 * ⭐ GET ALL ACCOUNTS
 */
export const fetchAllAccounts = () => api.get("/accounts");

/**
 * ⭐ GET LOCKED ACCOUNTS
 */
export const fetchLockedAccounts = () => api.get("/accounts/locked");

/**
 * ⭐ UNLOCK ACCOUNT
 */
export const unlockAccount = (accountNumber) =>
  api.patch(`/accounts/${accountNumber}/unlock`);

/**
 * ⭐ GET ACCOUNT DETAILS
 */
export const getAccount = (accNo) => api.get(`/accounts/${accNo}`);

/**
 * ⭐ UPDATE CONTACT INFO
 */
export const updateContact = (accountNumber, payload) =>
  api.put(`/accounts/${accountNumber}/contact`, payload);

/**
 * ⭐ CUSTOMER PROFILE UPDATE
 */
export const updateAccount = (accountNumber, payload) =>
  api.put(`/accounts/${accountNumber}`, payload);

/* ========================================================================
   ⭐ NEW — LOAN MODULE SUPPORT
   ======================================================================== */

/**
 * ⭐ FETCH LOAN STATUS / AMOUNT / INTEREST / TOTAL DUE
 */
export const getLoanInfo = (accountNumber) =>
  api.get(`/loan/status/${accountNumber}`);

/**
 * ⭐ EARLY LOAN CLOSURE — requires transaction PIN
 */
export const requestEarlyLoanClosure = (payload) =>
  api.post("/loan/early-close", payload, { withCredentials: true });
