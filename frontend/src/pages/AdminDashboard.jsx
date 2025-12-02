// src/pages/AdminDashboard.jsx
import React from "react";
import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import "./AdminDashboard.css";

export default function AdminDashboard() {
  const navigate = useNavigate();

  const logout = () => {
    localStorage.clear();
    navigate("/");
  };

  return (
    <div
      className="admin-wrapper"
      style={{
        backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`
      }}
    >
      <Header />

      <div className="admin-container">
        {/* Dashboard Header with Logout */}
        <div className="admin-topbar">
          <h1 className="admin-title">Admin Dashboard</h1>

          <button className="logout-btn" onClick={logout}>
            Logout
          </button>
        </div>

        {/* Grid Boxes */}
        <div className="admin-grid">

          <div
            className="admin-card"
            onClick={() => navigate("/admin/accounts")}
          >
            <h2>View Accounts</h2>
            <p>List all accounts</p>
          </div>

          <div
            className="admin-card"
            onClick={() => navigate("/admin/create-account")}
          >
            <h2>Create Account</h2>
            <p>Add a new customer</p>
          </div>

          <div
            className="admin-card"
            onClick={() => navigate("/admin/locked-accounts")}
          >
            <h2>Locked Accounts</h2>
            <p>Unlock user accounts</p>
          </div>

          <div
            className="admin-card"
            onClick={() => navigate("/admin/rollback")}
          >
            <h2>Transaction Rollback</h2>
            <p>Reverse incorrect transactions</p>
          </div>

          <div
            className="admin-card"
            onClick={() => navigate("/admin/reports")}
          >
            <h2>Reports</h2>
            <p>Download PDF reports</p>
          </div>

          <div
            className="admin-card"
            onClick={() => navigate("/admin/manage-admins")}
          >
            <h2>Manage Admin Users</h2>
            <p>View & remove admin accounts</p>
          </div>

        </div>
      </div>
    </div>
  );
}
