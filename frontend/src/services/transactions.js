// src/services/transactions.js
import axios from "axios";

/**
 * Axios instance for backend API calls
 *
 * React:           http://localhost:3000
 * Spring Boot:     http://localhost:8080
 */
const api = axios.create({
  baseURL: "http://localhost:8080",
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 15000,
});

/**
 * Fetch filtered transactions
 */
export function fetchTransactionsFiltered(params = {}) {
  const qp = {};
  Object.keys(params || {}).forEach((key) => {
    const val = params[key];
    if (val !== undefined && val !== null && val !== "") {
      qp[key] = val;
    }
  });

  return api.get("/api/transactions/filter", { params: qp });
}

/**
 * Rollback transaction
 */
export function rollbackTx(txId) {
  if (!txId) {
    return Promise.reject(new Error("txId is required for rollback"));
  }
  return api.patch(`/api/transactions/rollback/${encodeURIComponent(txId)}`);
}

/**
 * Fetch all transactions
 */
export function fetchAllTransactions() {
  return api.get("/api/transactions");
}
