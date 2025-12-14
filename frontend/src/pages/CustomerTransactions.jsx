import React, { useEffect, useState } from "react";
import { getTransactions } from "../services/customer";
import { downloadMiniStatement } from "../services/reports";
import { useNavigate } from "react-router-dom";
import SidebarMenu from "../components/SidebarMenu";
import Header from "../components/Header";
import "./CustomerTransactions.css";

export default function CustomerTransactions() {
  const navigate = useNavigate();
  const accNo = localStorage.getItem("accountNumber");

  const [txs, setTxs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    try {
      const res = await getTransactions(accNo);
      setTxs(res.data || []);
    } catch (err) {
      console.error("Failed to fetch transactions", err);
    } finally {
      setLoading(false);
    }
  };

  const sentenceCase = (str) => {
  if (!str) return "-";
  return str
    .toLowerCase()
    .replace(/_/g, " ")          // Replace underscores with spaces
    .replace(/\b\w/g, (c) => c.toUpperCase()); // Capitalize each word
};

  const formatDate = (dt) => {
    if (!dt) return "-";
    return new Date(dt).toLocaleString("en-GB");
  };

  const downloadStatement = async () => {
    try {
      await downloadMiniStatement(accNo);

      alert(
        "📄 Mini-statement PDF is downloading.\n\n" +
          "🔐 Password format:\n" +
          " • First 3 letters of your username\n" +
          " • Last 4 digits of your phone number"
      );
    } catch (err) {
      console.error("Mini-statement download error:", err);
      alert(err.message || "Failed to download mini-statement");
    }
  };

  // ⭐ Full webpage refresh (not just React refresh)
  const refreshPage = () => {
    window.location.reload();
  };

useEffect(() => {
  document.title = "Transaction History | AstroNova Bank";
}, []);


  return (
    <div
      className="txn-wrapper"
      style={{
        backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`,
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
      <div className="txn-subheader">
        <h2 className="txn-title">Transaction History</h2>

        <div className="txn-controls">

          {/* ⭐ New Refresh Button
          <button className="txn-refresh" onClick={refreshPage}>
            Refresh Page
          </button> */}

          <button className="txn-download" onClick={downloadStatement}>
            Download Statement
          </button>

          <button className="txn-back" onClick={() => navigate("/customer")}>
            Back
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="txn-table-card">
        <table className="txn-table">
          <thead>
            <tr>
              <th>Transaction ID</th>
              <th>Date</th>
              <th>Type</th>
              <th>Amount</th>
              <th>From</th>
              <th>To</th>
              <th>Category</th>
              <th>Status</th>
            </tr>
          </thead>

          <tbody>
            {loading ? (
              <tr>
                <td colSpan="8" className="txn-loading">
                  Loading...
                </td>
              </tr>
            ) : txs.length === 0 ? (
              <tr>
                <td colSpan="8" className="txn-empty">
                  No transactions found.
                </td>
              </tr>
            ) : (
              txs.map((t) => (
                <tr key={t.txId}>
                  <td>{t.txId}</td>
                  <td>{formatDate(t.createdAt || t.timestamp)}</td>
                  <td>{sentenceCase(t.txType)}</td>
                  <td>₹{t.amount}</td>
                  <td>{t.fromAccount || "-"}</td>
                  <td>{t.toAccount || "-"}</td>
                  <td>{t.category}</td>
                  <td>{sentenceCase(t.status)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
