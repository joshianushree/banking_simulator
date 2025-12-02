// src/components/OtpModal.jsx
import React, { useState, useEffect } from "react";
import { verifyOtp } from "../services/auth";
import { useNavigate } from "react-router-dom";
import "./OtpModal.css";

export default function OtpModal({ open, onClose, identifier, role }) {
  const navigate = useNavigate();
  const [otp, setOtp] = useState("");
  const [msg, setMsg] = useState("");
  const [loading, setLoading] = useState(false);

  // 🚀 RESET ALL STATE WHEN MODAL CLOSES
  useEffect(() => {
    if (!open) {
      setOtp("");
      setMsg("");
      setLoading(false);
    }
  }, [open]);

  if (!open) return null;

  const submitOtp = async () => {
    setMsg("");
    setLoading(true);

    try {
      const res = await verifyOtp(identifier, otp);
      const data = res.data;

      if (!data.success) {
        setMsg(data.message || "OTP incorrect");
        setLoading(false);
        return;
      }

      // ⭐ STORE ROLE & IDENTIFIER ON CORRECT LOGIN
      if (role === "admin") {
        localStorage.setItem("role", "admin");
        localStorage.setItem("username", identifier);
        navigate("/admin", { replace: true });
      } else {
        localStorage.setItem("role", "customer");
        localStorage.setItem("accountNumber", identifier);
        navigate("/customer", { replace: true });
      }

      onClose(); // close modal after navigation
    } catch (err) {
      console.error(err);
      setMsg("Server error");
    }

    setLoading(false);
  };

  return (
    <div className="otp-overlay">
      <div className="otp-modal">

        {/* Header */}
        <div className="otp-header">
          <h2>Enter OTP</h2>
          <button className="otp-close" onClick={onClose}>✕</button>
        </div>

        {/* OTP Input */}
        <input
          className="otp-input"
          value={otp}
          onChange={(e) => setOtp(e.target.value)}
          placeholder="Enter OTP"
        />

        {/* Error Message */}
        {msg && <p className="otp-error">{msg}</p>}

        {/* Buttons */}
        <button
          onClick={submitOtp}
          disabled={loading}
          className="otp-btn"
        >
          {loading ? "Verifying..." : "Verify OTP"}
        </button>

        <button onClick={onClose} className="otp-cancel-btn">
          Cancel
        </button>

      </div>
    </div>
  );
}
