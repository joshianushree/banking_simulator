import React, { useEffect, useState } from "react";
import { fetchTransactionsFiltered, rollbackTx } from "../services/transactions";
import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import "./AdminRollback.css";

function sentenceCase(str) {
  if (!str) return "";
  const s = String(str).toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function formatToDDMMYYYY_HHMM(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";

  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(d);

  const map = {};
  parts.forEach((p) => {
    if (p.type !== "literal") map[p.type] = p.value;
  });

  return `${map.day}/${map.month}/${map.year} ${map.hour}:${map.minute}`;
}

export default function AdminRollback() {
  const navigate = useNavigate();

  const [txs, setTxs] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const [search, setSearch] = useState("");

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetchTransactionsFiltered({ limit: 200 });
      const data = (res.data || []).filter((t) => t.txType === "TRANSFER");
      setTxs(data);
      setFiltered(data);
    } catch (err) {
      console.error(err);
    }
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    let list = [...txs];
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(
        (t) =>
          t.txId?.toLowerCase().includes(q) ||
          t.fromAccount?.toLowerCase().includes(q) ||
          t.toAccount?.toLowerCase().includes(q)
      );
    }
    setFiltered(list);
  }, [search, txs]);

  const rollback = async (id, type, status) => {
    if (status === "REVERSED") return;
    if (!window.confirm("Rollback this transaction?")) return;

    try {
      await rollbackTx(id);
      setMessage("Transaction reversed.");
      load();
      setTimeout(() => setMessage(""), 3000);
    } catch (e) {
      console.error(e);
      setMessage("Rollback failed.");
    }
  };

  useEffect(() => {
  document.title = "Transaction Rollback | AstroNova Bank";
}, []);

  return (
    <div className="rollback-wrapper">
      <Header />

      <div className="rollback-card">
        <div className="rollback-header">
          <h2 className="rollback-title">Transaction Rollback</h2>

          <div className="rollback-buttons">
            <button onClick={() => navigate("/admin")} className="gl-btn-blue">
              Back
            </button>

            <button onClick={load} className="gl-btn-blue">
              Refresh
            </button>
          </div>
        </div>

        <div className="rollback-filters">
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search txId / from / to"
            className="gl-input"
          />
        </div>

        {message && <div className="rollback-success">{message}</div>}

        {loading ? (
          <div className="rollback-loading">Loading transactions...</div>
        ) : (
          <div className="rollback-table-wrapper">
            <table className="rollback-table">
              <thead>
                <tr>
                  <th>Transaction ID</th>
                  <th>Date/Time</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="rollback-empty">
                      No transactions found.
                    </td>
                  </tr>
                ) : (
                  filtered.map((t) => (
                    <tr key={t.txId}>
                      <td>{t.txId}</td>
                      <td>{formatToDDMMYYYY_HHMM(t.timestamp)}</td>
                      <td>{t.fromAccount || "-"}</td>
                      <td>{t.toAccount || "-"}</td>
                      <td>₹{t.amount}</td>
                      <td
                        className={
                          t.status === "REVERSED"
                            ? "rollback-status-green"
                            : "rollback-status-normal"
                        }
                      >
                        {sentenceCase(t.status)}
                      </td>

                      <td>
                        {t.status === "REVERSED" ? (
                          <button disabled className="gl-btn-gray">
                            Reversed
                          </button>
                        ) : (
                          <button
                            onClick={() => rollback(t.txId, t.txType, t.status)}
                            className="gl-btn-red"
                          >
                            Rollback
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
