// src/pages/CustomerTransfer.jsx
import React, { useState, useEffect } from "react";
import { transfer } from "../services/customer";
import { useNavigate } from "react-router-dom";
import SidebarMenu from "../components/SidebarMenu";
import Header from "../components/Header";
import "./CustomerTransfer.css";

export default function CustomerTransfer() {
  const navigate = useNavigate();
  const fromAccount = localStorage.getItem("accountNumber");

  const [toAccount, setToAccount] = useState("");
  const [ifsc, setIfsc] = useState("");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("Rent");
  const [msg, setMsg] = useState("");

  const [showPinModal, setShowPinModal] = useState(false);
  const [transactionPin, setTransactionPin] = useState("");

  const [menuOpen, setMenuOpen] = useState(false);

  // Reset PIN when closing modal
  useEffect(() => {
    if (!showPinModal) {
      setTransactionPin("");
    }
  }, [showPinModal]);

  const submit = (e) => {
    e.preventDefault();
    setMsg("");

    if (!toAccount.trim())
      return setMsg("Receiver account number is required.");
    if (!ifsc.trim()) return setMsg("IFSC code is required.");
    if (!amount || isNaN(amount) || Number(amount) <= 0)
      return setMsg("Enter a valid amount.");
    if (toAccount === fromAccount)
      return setMsg("Cannot transfer to your own account.");

    setShowPinModal(true);
  };

  const confirmTransfer = async () => {
    setShowPinModal(false);
    setMsg("");

    try {
      const res = await transfer({
        fromAccount,
        toAccount,
        ifsc,
        amount,
        category,
        transactionPin,
      });

      if (res.data.success) {
        alert("Transfer successful!");

        // ⭐ FULL WEBPAGE REFRESH (NOT React-only)
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
  document.title = "Transfer Amount | AstroNova Bank";
}, []);


  return (
    <div
      className="transfer-wrapper"
      style={{
        backgroundImage: 'url("/galaxy-bg.png")',
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
      }}
    >
      <Header />

      {/* Sidebar */}
      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      {/* Subheader */}
      <div className="transfer-subheader">
        <h2 className="transfer-title">Transfer Money</h2>
        <button
          className="transfer-back-btn"
          onClick={() => navigate("/customer")}
        >
          Back
        </button>
      </div>

      {/* Form */}
      <div className="transfer-form-container">
        <h2 className="transfer-form-title">Send Money</h2>

        <form onSubmit={submit}>
          <label className="transfer-label">Receiver Account Number</label>
          <input
            className="transfer-input"
            value={toAccount}
            onChange={(e) => setToAccount(e.target.value)}
          />

          <label className="transfer-label">Recipient IFSC Code</label>
          <input
            className="transfer-input"
            value={ifsc}
            onChange={(e) => setIfsc(e.target.value)}
          />

          <label className="transfer-label">Amount</label>
          <input
            className="transfer-input"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />

          <label className="transfer-label">Category</label>
          <select
            className="transfer-select"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <option value="Rent">Rent</option>
            <option value="Family">Family</option>
            <option value="Friend">Friend</option>
            <option value="Business">Business</option>
            <option value="Loan Payment">Loan Payment</option>
          </select>

          {msg && <p className="transfer-msg">{msg}</p>}

          <div className="transfer-btn-row">
            <button
              type="button"
              className="transfer-btn transfer-cancel"
              onClick={() => navigate("/customer")}
            >
              Cancel
            </button>

            <button className="transfer-btn transfer-submit">
              Transfer
            </button>
          </div>
        </form>
      </div>

      {/* PIN Modal */}
      {showPinModal && (
        <div className="transfer-modal-overlay">
          <div className="transfer-modal">
            <h3 className="modal-title">Enter Transaction PIN</h3>

            <input
              type="password"
              maxLength={6}
              placeholder="Enter PIN"
              className="modal-pin-input"
              value={transactionPin}
              onChange={(e) => setTransactionPin(e.target.value)}
            />

            <div className="modal-btn-row">
              <button
                onClick={() => setShowPinModal(false)}
                className="modal-btn modal-cancel"
              >
                Cancel
              </button>

              <button
                onClick={confirmTransfer}
                className="modal-btn modal-confirm"
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
