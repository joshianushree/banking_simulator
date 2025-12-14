import React from "react";
import { useNavigate } from "react-router-dom";

export default function SidebarMenu({ isOpen, onClose }) {
  const navigate = useNavigate();

  return (
    <>
      {/* DARK BACKDROP */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black bg-opacity-40"
          style={{ zIndex: 9998 }}
          onClick={onClose}
        ></div>
      )}

      {/* SIDEBAR PANEL */}
      <aside
        className={`fixed top-0 left-0 h-full w-64 bg-blue-800 text-white p-6 shadow-xl
        transform transition-transform duration-300
        ${isOpen ? "translate-x-0" : "-translate-x-full"}`}
        style={{ zIndex: 9999 }}
      >
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold">Menu</h2>

          <button
            onClick={onClose}
            className="text-white text-2xl font-bold hover:text-gray-300 transition"
            style={{ lineHeight: 1 }}
          >
            ✕
          </button>
        </div>

        <nav className="flex flex-col space-y-4">
          <button
            onClick={() => {
              navigate("/customer");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Dashboard
          </button>

          <button
            onClick={() => {
              navigate("/customer/statistics");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Statistics
          </button>

          <button
            onClick={() => {
              navigate("/customer/deposit");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Deposit
          </button>

          <button
            onClick={() => {
              navigate("/customer/withdraw");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Withdraw
          </button>

          <button
            onClick={() => {
              navigate("/customer/transfer");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Transfer
          </button>

          <button
            onClick={() => {
              navigate("/customer/transactions");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Transaction History
          </button>

          <button
            onClick={() => {
              navigate("/customer/transaction-pin");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Generate Transaction PIN
          </button>

          {/* ---------------- NEW: LOAN REQUEST ----------------
          <button
            onClick={() => {
              navigate("/customer/loan-request");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Apply for Loan
          </button> */}

          {/* ---------------- NEW: LOAN REQUEST (with status inside page) ---------------- */}
          <button
            onClick={() => {
              navigate("/customer/loan-request");
              onClose();
            }}
            className="px-3 py-2 text-left rounded hover:bg-blue-600"
          >
            Loans (Apply & Status)
          </button>

          {/* ---------------- NEW: DELETE ACCOUNT ---------------- */}
          <button
            onClick={() => {
              navigate("/customer/delete-account");
              onClose();
            }}
            className="px-3 py-2 text-left rounded bg-red-600 hover:bg-red-700 transition"
          >
            Delete My Account
          </button>
        </nav>
      </aside>
    </>
  );
}
