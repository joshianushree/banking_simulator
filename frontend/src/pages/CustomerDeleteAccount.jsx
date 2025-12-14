// src/pages/CustomerDeleteAccount.jsx
import React, { useState, useEffect } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";

import Header from "../components/Header";
import SidebarMenu from "../components/SidebarMenu";

import "./CustomerDeleteAccount.css";

export default function CustomerDeleteAccount() {
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  // FORM FIELDS
  const [holderName, setHolderName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [ifsc, setIfsc] = useState("");
  const [reason, setReason] = useState("");

  // OTP STATES
  const [otp, setOtp] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [otpVerified, setOtpVerified] = useState(false);

  const [message, setMessage] = useState(null);
  const [loading, setLoading] = useState(false);

  // PREFILL FROM SESSION
  useEffect(() => {
    setHolderName(sessionStorage.getItem("holderName") || "");
    setAccountNumber(sessionStorage.getItem("accountNumber") || "");
    setEmail(sessionStorage.getItem("email") || "");
    setPhone(sessionStorage.getItem("phone") || "");
    setIfsc(sessionStorage.getItem("ifsc") || "");
  }, []);

  const isValid = reason.trim().length > 0 && otpVerified;

  // SEND OTP
  const sendOtp = async () => {
    if (!phone) {
      setMessage({ type: "error", text: "Phone missing. Re-login." });
      return;
    }

    try {
      const res = await axios.post("http://localhost:8080/api/otp/send", {
        contact: phone,
      });

      if (res.data.success) {
        setOtpSent(true);
        setMessage({ type: "success", text: "OTP sent to your phone!" });
      } else {
        setMessage({ type: "error", text: res.data.message });
      }
    } catch {
      setMessage({ type: "error", text: "Unable to send OTP." });
    }
  };

  // VERIFY OTP
  const verifyOtpHandler = async () => {
    try {
      const res = await axios.post("http://localhost:8080/api/otp/verify", {
        contact: phone,
        otp,
      });

      if (res.data.success) {
        setOtpVerified(true);
        setMessage({ type: "success", text: "OTP verified!" });
      } else {
        setMessage({ type: "error", text: "Invalid OTP." });
      }
    } catch {
      setMessage({ type: "error", text: "Failed to verify OTP." });
    }
  };

  // SUBMIT DELETION REQUEST
  const handleDelete = async (e) => {
    e.preventDefault();

    if (!otpVerified) {
      alert("Verify OTP first.");
      return;
    }

    if (!window.confirm("Are you sure you want to request account deletion?")) {
      return;
    }

    setLoading(true);

    try {
      const res = await axios.post(
        "http://localhost:8080/api/deletion/request",
        {
          accountNumber,
          holderName,
          email,
          phone,
          ifsc,
          reason,
        },
        { withCredentials: true }
      );

      if (res.data.success) {
        setMessage({ type: "success", text: res.data.message });
        setTimeout(() => navigate("/customer"), 1500);
      } else {
        setMessage({ type: "error", text: res.data.message });
      }
    } catch {
      setMessage({ type: "error", text: "Server error. Try again later." });
    }

    setLoading(false);
  };

  useEffect(() => {
  document.title = "Account Deletion Form | AstroNova Bank";
}, []);

  return (
    <div
      className="delete-wrapper"
      style={{ backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)` }}
    >
      {/* HAMBURGER BUTTON - same as dashboard */}
      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>

      <Header />

      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      <form className="delete-card" onSubmit={handleDelete}>
        {/* TITLE + BACK BUTTON */}
        <div className="delete-title-row">
          <h2 className="delete-title">Request Account Deletion</h2>

          <button
            type="button"
            className="gl-btn-back"
            onClick={() => navigate("/customer")}
          >
            Back
          </button>
        </div>

        {message && (
          <div
            className={`alert-box ${
              message.type === "success" ? "alert-success" : "alert-error"
            }`}
          >
            {message.text}
          </div>
        )}

        {/* PREFILLED FIELDS */}
        <label className="gl-label">Account Holder Name</label>
        <input className="gl-input" value={holderName} readOnly />

        <label className="gl-label">Account Number</label>
        <input className="gl-input" value={accountNumber} readOnly />

        <label className="gl-label">Email</label>
        <input className="gl-input" value={email} readOnly />

        <label className="gl-label">Phone</label>
        <input className="gl-input" value={phone} readOnly />

        <label className="gl-label">IFSC Code</label>
        <input className="gl-input" value={ifsc} readOnly />

        <label className="gl-label">Reason for Deletion</label>
        <textarea
          className="gl-input"
          rows="3"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />

        {/* OTP FLOW */}
          {!otpSent && (
          <button type="button" className="gl-btn-send-otp" onClick={sendOtp}>
            Send OTP
          </button>
        )}

        {otpSent && !otpVerified && (
          <>
            <label className="gl-label">Enter OTP</label>
            <input
              type="text"
              className="gl-input"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
            />

            <button
              type="button"
              className="gl-btn-verify"
              onClick={verifyOtpHandler}
            >
              Verify OTP
            </button>
          </>
        )}

        {/* BUTTON ROW */}
        <div className="delete-actions-row">
          <button
            type="button"
            className="gl-btn-cancel"
            onClick={() => navigate("/customer")}
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={!isValid || loading}
            className={`gl-btn-delete ${
              !isValid || loading ? "disabled" : ""
            }`}
          >
            {loading ? "Submitting..." : "Submit Deletion Request"}
          </button>
        </div>
      </form>
    </div>
  );
}
