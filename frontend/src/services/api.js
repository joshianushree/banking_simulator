// src/services/api.js
import axios from "axios";

export const api = axios.create({
  baseURL: "http://localhost:8080/api",
  headers: {
    "Content-Type": "application/json"
  },
  // IMPORTANT: allow sending cookies (session cookie) with requests
  withCredentials: true
});

// Optional: attach Authorization header if you store a JWT (keeps compatibility)
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers["Authorization"] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Helper to update admin contact (convenience)
export const updateAdminUser = (username, data, loggedInUsername) => {
  return api.put(`/admin-users/${username}`, data, {
    headers: { "X-Admin-Username": loggedInUsername }
  });
};

export default api;
