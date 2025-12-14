// src/components/ForgotModal.jsx
import React, { useState, useEffect } from "react";
import { Eye, EyeOff } from "lucide-react";
import { forgotRequest, forgotVerify, forgotReset } from "../services/auth";
import "./ForgotModal.css";

export default function ForgotModal({ open, onClose, role }) {
  const [step, setStep] = useState(1);
  const [identifier, setIdentifier] = useState("");
  const [contact, setContact] = useState("");
  const [otp, setOtp] = useState("");

  const [newValue, setNewValue] = useState("");
  const [retypeValue, setRetypeValue] = useState("");

  const [showNew, setShowNew] = useState(false);
  const [showRetype, setShowRetype] = useState(false);

  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const isCustomer = role === "customer";

  // 🚀 RESET ALL STATE WHEN MODAL CLOSES
  useEffect(() => {
    if (!open) {
      setStep(1);
      setIdentifier("");
      setContact("");
      setOtp("");
      setNewValue("");
      setRetypeValue("");
      setShowNew(false);
      setShowRetype(false);
      setMessage("");
      setLoading(false);
    }
  }, [open]);

  if (!open) return null;

  const handleRequestOtp = async () => {
    setMessage("");

    if (!identifier || !contact) {
      setMessage("Please enter all details.");
      return;
    }

    setLoading(true);
    try {
      const res = await forgotRequest({ role, identifier, contact });
      const data = res.data;

      if (data.success) {
        setMessage("OTP sent successfully.");
        setStep(2);
      } else {
        setMessage(data.message);
      }
    } catch {
      setMessage("Server error");
    }
    setLoading(false);
  };

  const handleVerifyOtp = async () => {
    setMessage("");

    if (!otp) {
      setMessage("Enter OTP.");
      return;
    }

    setLoading(true);
    try {
      const res = await forgotVerify({ identifier, otp });
      const data = res.data;

      if (data.success) {
        setMessage("OTP verified.");
        setStep(3);
      } else {
        setMessage(data.message);
      }
    } catch {
      setMessage("Server error");
    }
    setLoading(false);
  };

  const handleReset = async () => {
    setMessage("");

    if (newValue !== retypeValue) {
      setMessage("Both fields must match.");
      return;
    }

    if (isCustomer && !/^\d{4}$/.test(newValue)) {
      setMessage("PIN must be exactly 4 digits.");
      return;
    }

    if (!isCustomer && newValue.length < 6) {
      setMessage("Password must be at least 6 characters.");
      return;
    }

    setLoading(true);

    try {
      const res = await forgotReset({
        role,
        identifier,
        newPin: newValue
      });

      const data = res.data;

      if (data.success) {
        setMessage("Updated successfully!");
        setTimeout(() => onClose(), 800);
      } else {
        setMessage(data.message);
      }
    } catch {
      setMessage("Server error");
    }

    setLoading(false);
  };

  return (
    <div className="fm-overlay">
      <div className="fm-modal">
        <div className="fm-header">
          <h2>Forgot {isCustomer ? "PIN" : "Password"}</h2>
          <button className="fm-close" onClick={onClose}>✕</button>
        </div>

        {message && <p className="fm-error">{message}</p>}

        {/* STEP 1 */}
        {step === 1 && (
          <div className="fm-section">
            <label className="fm-label">
              {isCustomer ? "Account Number" : "Username"}
            </label>
            <input
              className="fm-input"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
            />

            <label className="fm-label">Email or Phone</label>
            <input
              className="fm-input"
              value={contact}
              onChange={(e) => setContact(e.target.value)}
            />

            <button 
              onClick={handleRequestOtp}
              disabled={loading}
              className="fm-btn"
            >
              {loading ? "Sending..." : "Send OTP"}
            </button>
          </div>
        )}

        {/* STEP 2 */}
        {step === 2 && (
          <div className="fm-section">
            <label className="fm-label">Enter OTP</label>
            <input
              className="fm-input"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
            />

            <button
              onClick={handleVerifyOtp}
              disabled={loading}
              className="fm-btn"
            >
              {loading ? "Verifying..." : "Verify OTP"}
            </button>
          </div>
        )}

        {/* STEP 3 */}
        {step === 3 && (
          <div className="fm-section">
            <label className="fm-label">
              {isCustomer ? "New PIN (4 digits)" : "New Password"}
            </label>

            <div className="fm-input-wrap">
              <input
                type={showNew ? "text" : "password"}
                className="fm-input"
                value={newValue}
                onChange={(e) => setNewValue(e.target.value)}
              />
              <span className="fm-eye" onClick={() => setShowNew(!showNew)}>
                {showNew ? <EyeOff size={18}/> : <Eye size={18}/>}
              </span>
            </div>

            <label className="fm-label">
              Re-enter {isCustomer ? "PIN" : "Password"}
            </label>

            <div className="fm-input-wrap">
              <input
                type={showRetype ? "text" : "password"}
                className="fm-input"
                value={retypeValue}
                onChange={(e) => setRetypeValue(e.target.value)}
              />
              <span className="fm-eye" onClick={() => setShowRetype(!showRetype)}>
                {showRetype ? <EyeOff size={18}/> : <Eye size={18}/>}
              </span>
            </div>

            <button
              onClick={handleReset}
              disabled={loading}
              className="fm-btn"
            >
              {loading ? "Saving..." : "Reset"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
