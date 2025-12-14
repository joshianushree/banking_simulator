// src/pages/CustomerDeposit.jsx
import React, { useEffect, useState } from "react";
import { deposit } from "../services/customer";
import { useNavigate } from "react-router-dom";
import SidebarMenu from "../components/SidebarMenu";
import Header from "../components/Header";    // ⭐ SAME GLOBAL HEADER
import "./CustomerDeposit.css";              // ⭐ NEW THEME
import { v4 as uuidv4 } from "uuid";

export default function CustomerDeposit() {
  const navigate = useNavigate();
  const accountNumber = localStorage.getItem("accountNumber");

  const [menuOpen, setMenuOpen] = useState(false);
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("Salary");
  const [msg, setMsg] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (isSubmitting) return;

    setMsg("");

    if (!amount || isNaN(Number(amount)) || Number(amount) <= 0) {
      setMsg("Enter a valid amount.");
      return;
    }

    setIsSubmitting(true);
    try {
      const clientRequestId = uuidv4();

      const res = await deposit({
        accountNumber,
        amount,
        category,
        clientRequestId,
      });

      if (res.data.success) {
        alert("Deposit successful!");
        setTimeout(() => {
          setIsSubmitting(false);
          // navigate("/customer");
          window.location.reload();
        }, 1000);
      } else {
        setMsg(res.data.message || "Deposit failed");
        setIsSubmitting(false);
      }
    } catch (err) {
      console.error("Deposit error:", err);
      setMsg("Server error.");
      setIsSubmitting(false);
    }
  };

  useEffect(() => {
  document.title = "Deposit Amount | AstroNova Bank";
}, []);

  return (
    <div className="deposit-wrapper" style={{
    backgroundImage: 'url("/galaxy-bg.png")',
    backgroundSize: "cover",
    backgroundPosition: "center",
    backgroundRepeat: "no-repeat",
  }}>
      {/* ⭐ GLOBAL HEADER (same as dashboard/stats) */}
      <Header />

      {/* ⭐ MENU BUTTON (fixed top-left) */}
      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>

      {/* ⭐ SIDEBAR */}
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      {/* ⭐ SUBHEADER — same style as Statistics */}
      <div className="deposit-subheader">
        <h2 className="deposit-title">Deposit Money</h2>

        <button
          className="deposit-back-btn"
          onClick={() => navigate("/customer")}
        >
          Back
        </button>
      </div>

      {/* ⭐ FORM BLOCK — neon glass card */}
      <div className="deposit-form-container">
        <h2 className="deposit-form-title">Add Funds</h2>

        <form onSubmit={submit}>
          {/* Amount */}
          <label className="deposit-label">Amount</label>
          <input
            className="deposit-input"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />

          {/* Category */}
          <label className="deposit-label">Category</label>
          <select
            className="deposit-select"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <option value="Salary">Salary</option>
            <option value="Cash Deposit">Cash Deposit</option>
          </select>

          {msg && <p className="deposit-msg">{msg}</p>}

          {/* Buttons */}
          <div className="deposit-btn-row">
            <button
              type="button"
              className="deposit-cancel"
              onClick={() => navigate("/customer")}
              disabled={isSubmitting}
            >
              Cancel
            </button>

            <button
              type="submit"
              className="deposit-submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Processing..." : "Deposit"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
