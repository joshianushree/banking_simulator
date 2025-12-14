import React, { useEffect, useState } from "react";
import { fetchLockedAccounts, unlockAccount } from "../services/accounts";
import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import "./AdminLockedAccounts.css";

function formatToDDMMYYYY_HHMM(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return "—";

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

export default function AdminLockedAccounts() {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetchLockedAccounts();
      setAccounts(res.data || []);
    } catch (err) {
      console.error(err);
    }
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, []);

  const unlock = async (accNo) => {
    if (!window.confirm(`Unlock account ${accNo}?`)) return;

    try {
      await unlockAccount(accNo);
      setMessage("Account unlocked successfully!");
      load();
      setTimeout(() => setMessage(""), 3000);
    } catch {
      setMessage("Error unlocking account. Try again.");
    }
  };

  const getLockedTime = (acc) =>
    acc.lockedAt ||
    acc.locked_at ||
    acc.lockTime ||
    acc.lock_time ||
    acc.lastLocked ||
    acc.last_locked ||
    null;

    useEffect(() => {
  document.title = "View Locked Account | AstroNova Bank";
}, []);



  return (
    <div className="locked-wrapper">
      <Header />

      <div className="locked-card">
        {/* ---------------------- HEADER ---------------------- */}
        <div className="locked-header">
          <h2 className="locked-title">Locked Accounts</h2>

          <div className="locked-buttons">
            <button className="gl-btn-blue" onClick={() => navigate("/admin")}>
              Back
            </button>
            <button className="gl-btn-blue" onClick={load}>
              Refresh
            </button>
          </div>
        </div>

        {/* Message */}
        {message && <div className="locked-success">{message}</div>}

        {/* ---------------------- TABLE ---------------------- */}
        {loading ? (
          <div className="locked-loading">Loading...</div>
        ) : (
          <div className="locked-table-wrapper">
            <table className="locked-table">
              <thead>
                <tr>
                  <th>Holder</th>
                  <th>Account Number</th>
                  <th>Locked Time</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {accounts.length === 0 ? (
                  <tr>
                    <td className="locked-empty" colSpan="4">
                      No locked accounts found.
                    </td>
                  </tr>
                ) : (
                  accounts.map((acc) => (
                    <tr key={acc.accountNumber}>
                      <td>{acc.holderName}</td>
                      <td>{acc.accountNumber}</td>
                      <td>{formatToDDMMYYYY_HHMM(getLockedTime(acc))}</td>

                      <td>
                        <button
                          onClick={() => unlock(acc.accountNumber)}
                          className="gl-btn-green"
                        >
                          Unlock
                        </button>
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
