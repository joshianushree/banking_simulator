import React, { useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";

import Header from "../components/Header";
import SidebarMenu from "../components/SidebarMenu";

import "./CustomerDeleteAccount.css";

export default function CustomerDeleteAccount() {
  const navigate = useNavigate();

  const [menuOpen, setMenuOpen] = useState(false);

  const [holderName, setHolderName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [ifsc, setIfsc] = useState("");
  const [contact, setContact] = useState("");
  const [pin, setPin] = useState("");
  const [reason, setReason] = useState("");

  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const isValid =
    holderName &&
    accountNumber &&
    ifsc &&
    contact &&
    pin &&
    reason &&
    pin.length === 4;

  const handleDelete = async (e) => {
    e.preventDefault();

    if (!window.confirm("Delete your account permanently?")) return;

    setLoading(true);
    setMessage("");

    try {
      const res = await axios.post(
        "http://localhost:8080/api/accounts/delete",
        {
          holderName,
          accountNumber,
          ifsc,
          contact,
          pin,
          reason,
        },
        { withCredentials: true }
      );

      if (res.data.success) {
        setMessage({ type: "success", text: res.data.message });

        setTimeout(() => {
          navigate("/login");
        }, 1600);
      } else {
        setMessage({ type: "error", text: res.data.message });
      }
    } catch (err) {
      setMessage({ type: "error", text: "Server error. Try again." });
    }

    setLoading(false);
  };

  return (
    <div
      className="delete-wrapper"
      style={{
        backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`,
      }}
    >
      {/* global header */}
      <Header />

      {/* hamburger */}
      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>

      {/* sidebar */}
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      <form className="delete-card" onSubmit={handleDelete}>
        <div className="delete-title-row">
          <h2 className="delete-title">Delete My Account</h2>

          {/* Back button */}
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

        <label className="gl-label">Holder Name</label>
        <input
          type="text"
          className="gl-input"
          value={holderName}
          onChange={(e) => setHolderName(e.target.value)}
        />

        <label className="gl-label">Account Number</label>
        <input
          type="text"
          className="gl-input"
          value={accountNumber}
          onChange={(e) => setAccountNumber(e.target.value)}
        />

        <label className="gl-label">IFSC Code</label>
        <input
          type="text"
          className="gl-input"
          value={ifsc}
          onChange={(e) => setIfsc(e.target.value)}
        />

        <label className="gl-label">Email / Phone</label>
        <input
          type="text"
          className="gl-input"
          value={contact}
          onChange={(e) => setContact(e.target.value)}
        />

        <label className="gl-label">Login PIN (4 digits)</label>
        <input
          type="password"
          maxLength="4"
          className="gl-input"
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
        />

        <label className="gl-label">Reason for Deletion</label>
        <textarea
          className="gl-input"
          rows="3"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        ></textarea>

        <div className="delete-actions-row">
          {/* Cancel FIRST */}
          <button
            type="button"
            className="gl-btn-cancel"
            onClick={() => navigate("/customer")}
          >
            Cancel
          </button>

          {/* Delete SECOND */}
          <button
            type="submit"
            disabled={!isValid || loading}
            className={`gl-btn-delete ${!isValid || loading ? "disabled" : ""}`}
          >
            {loading ? "Processing..." : "Delete My Account"}
          </button>
        </div>
      </form>
    </div>
  );
}
