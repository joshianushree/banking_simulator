// src/pages/AdminReports.jsx
import React, { useEffect, useState} from "react";
import {
  downloadAccountsReport,
  downloadTransactionsReport,
  downloadAccountsReportByBranch,
  downloadTransactionsReportByBranch,
  downloadMiniStatement,
} from "../services/reports";
import { useNavigate } from "react-router-dom";
import "./AdminReports.css";
import Header from "../components/Header";

export default function AdminReports() {
  const [accNo, setAccNo] = useState("");
  const [branch, setBranch] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  // Generic success/error handler
  const run = async (fn, successMsg) => {
    setMessage("");
    setError("");

    try {
      await fn();
      setMessage(successMsg);
    } catch (err) {
      console.error("Report download error:", err);
      setError(err?.message || "Failed to download report.");
    }
  };

  const runMiniStatement = () => {
    setMessage("");
    setError("");

    if (!accNo.trim()) {
      setError("Please enter an account number.");
      return;
    }

    if (!/^\d{11}$/.test(accNo.trim())) {
      setError("Account number must be 11 digits.");
      return;
    }

    run(
      () => downloadMiniStatement(accNo.trim()),
      alert("Downloaded successfully!\nPassword = first 3 characters of the user's username + last 4 digits of the user's phone number.")
    );
  };

  useEffect(() => {
  document.title = "All Reports | AstroNova Bank";
}, []);

  return (
      <div
        className="reports-wrapper"
        style={{
          backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`
        }}
      >
        <Header />

      <div className="reports-card">

        {/* ⭐ Dashboard Style Header */}
        <div className="reports-header">
          <h1 className="reports-title">Admin Reports</h1>

          <button
            onClick={() => navigate("/admin")}
            className="gl-btn-blue header-btn"
          >
            Back
          </button>
        </div>

        <div className="reports-actions">
          {/* BRANCH FILTER */}
          <select
            className="gl-input"
            value={branch}
            onChange={(e) => setBranch(e.target.value)}
          >
            <option value="">-- All Branches --</option>
            <option value="Mumbai">Mumbai</option>
            <option value="Bangalore">Bangalore</option>
            <option value="Pune">Pune</option>
            <option value="Hyderabad">Hyderabad</option>
          </select>

          {/* ACCOUNTS REPORT */}
          <button
            onClick={() =>
              run(
                () =>
                  branch
                    ? downloadAccountsReportByBranch(branch)
                    : downloadAccountsReport(),
                alert("Downloaded successfully!\nPassword = first 3 characters of your username + last 4 digits of your phone number.")
              )
            }
            className="gl-btn-blue"
          >
            Download Accounts Report
          </button>

          {/* TRANSACTIONS REPORT */}
          <button
            onClick={() =>
              run(
                () =>
                  branch
                    ? downloadTransactionsReportByBranch(branch)
                    : downloadTransactionsReport(),
                alert("Downloaded successfully!\nPassword = first 3 characters of your username + last 4 digits of your phone number.")
              )
            }
            className="gl-btn-blue"
          >
            Download Transactions Report
          </button>

          {/* MINI STATEMENT */}
          <input
            placeholder="Account Number"
            value={accNo}
            onChange={(e) => setAccNo(e.target.value)}
            className="gl-input"
          />

          <button
            onClick={runMiniStatement}
            className="gl-btn-blue"
          >
            Download Mini Statement
          </button>

          {/* SUCCESS MESSAGE (MULTI-LINE) */}
          {message && (
            <pre className="gl-success" style={{ whiteSpace: "pre-wrap" }}>
              {message}
            </pre>
          )}

          {/* ERROR MESSAGE */}
          {error && (
            <p className="gl-error">{error}</p>
          )}
        </div>

      </div>
    </div>
  );
}
