// src/pages/CustomerWithdraw.jsx
import React, { useState, useEffect } from "react";
import { withdraw } from "../services/customer";
import { useNavigate } from "react-router-dom";
import SidebarMenu from "../components/SidebarMenu";
import Header from "../components/Header";
import "./CustomerWithdraw.css";

export default function CustomerWithdraw() {
  const navigate = useNavigate();
  const accountNumber = localStorage.getItem("accountNumber");

  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("ATM Withdrawal");
  const [msg, setMsg] = useState("");

  const [showPinModal, setShowPinModal] = useState(false);
  const [transactionPin, setTransactionPin] = useState("");

  const [menuOpen, setMenuOpen] = useState(false);

  // Reset PIN whenever modal closes
  useEffect(() => {
    if (!showPinModal) {
      setTransactionPin("");
    }
  }, [showPinModal]);

  const submit = (e) => {
    e.preventDefault();
    setMsg("");

    if (!amount || Number(amount) <= 0) {
      setMsg("Enter a valid amount.");
      return;
    }

    setShowPinModal(true);
  };

  const confirmWithdraw = async () => {
    setMsg("");
    setShowPinModal(false);

    try {
      const res = await withdraw({
        accountNumber,
        amount,
        category,
        transactionPin,
      });

      if (res.data.success) {
        alert("Withdrawal successful!");

        // ⭐ FULL WEBPAGE REFRESH
        setTimeout(() => {
          window.location.reload();
        }, 1000);

      } else {
        setMsg(res.data.message);
      }
    } catch {
      setMsg("Server error. Try again.");
    }
  };

useEffect(() => {
  document.title = "Withdraw Amount | AstroNova Bank";
}, []);


  return (
    <div
      className="withdraw-wrapper"
      style={{
        backgroundImage: 'url("/galaxy-bg.png")',
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
      }}
    >
      {/* Header */}
      <Header />

      {/* Menu Button */}
      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>

      {/* Sidebar */}
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      {/* Subheader */}
      <div className="withdraw-subheader">
        <h2 className="withdraw-title">Withdraw Money</h2>

        <button
          className="withdraw-back-btn"
          onClick={() => navigate("/customer")}
        >
          Back
        </button>
      </div>

      {/* Content */}
      <div className="withdraw-content">
        <div className="withdraw-card">
          <h2 className="withdraw-card-title">Withdraw Funds</h2>

          <form onSubmit={submit}>
            {/* Amount */}
            <label className="withdraw-label">Amount</label>
            <input
              className="withdraw-input"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />

            {/* Category */}
            <label className="withdraw-label">Category</label>
            <select
              className="withdraw-select"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              <option value="ATM Withdrawal">ATM Withdrawal</option>
              <option value="Bill Payment">Bill Payment</option>
              <option value="Shopping">Shopping</option>
              <option value="UPI Payment">UPI Payment</option>
              <option value="Cash Withdrawal">Cash Withdrawal</option>
            </select>

            {msg && <p className="withdraw-msg">{msg}</p>}

            <div className="withdraw-btn-row">
              <button
                type="button"
                className="withdraw-cancel"
                onClick={() => navigate("/customer")}
              >
                Cancel
              </button>

              <button type="submit" className="withdraw-submit">
                Withdraw
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* PIN Modal */}
      {showPinModal && (
        <div className="withdraw-pin-overlay">
          <div className="withdraw-pin-modal">
            <h3 className="withdraw-pin-title">Enter Transaction PIN</h3>

            <input
              type="password"
              maxLength={6}
              placeholder="Enter PIN"
              className="withdraw-pin-input"
              value={transactionPin}
              onChange={(e) => setTransactionPin(e.target.value)}
            />

            <div className="withdraw-pin-btn-row">
              <button
                onClick={() => setShowPinModal(false)}
                className="withdraw-pin-cancel"
              >
                Cancel
              </button>

              <button
                onClick={confirmWithdraw}
                className="withdraw-pin-confirm"
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
