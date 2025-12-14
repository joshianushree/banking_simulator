import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8080",
  withCredentials: true,
});

// -------------------------
// CUSTOMER: Loan Request
// -------------------------
export const submitLoanRequest = (formData) => {
  return api.post("/api/loan/request", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

// -------------------------
// CUSTOMER: Loan Status
// -------------------------
export const getLoanStatus = (accNo) => {
  return api.get(`/api/loan/status/${accNo}`);
};

// -------------------------
// ADMIN: Fetch all requests
// -------------------------
export const fetchLoanRequests = () => {
  return api.get("/api/loan/admin/requests");
};

// -------------------------
// ADMIN: Review request
// -------------------------
export const reviewLoan = (accNo) => {
  return api.get(`/api/loan/admin/review/${accNo}`);
};

// -------------------------
// ADMIN: Approve Loan
// -------------------------
export const approveLoan = (reqId, comment = "") => {
  return api.post(`/api/loan/admin/approve/${reqId}`, { comment });
};

// -------------------------
// ADMIN: Reject Loan
// -------------------------
export const rejectLoan = (reqId, comment = "") => {
  return api.post(`/api/loan/admin/reject/${reqId}`, { comment });
};
