import React, { useEffect, useState } from "react";
import {
  fetchAllAccounts,
  unlockAccount,
  getAccount,
  updateContact,
} from "../services/accounts";
import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import "./AdminAccounts.css";

function sentenceCase(s) {
  if (!s && s !== 0) return "";
  const str = String(s).trim();
  const lower = str.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
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

/* ============================================================
   Account Table Row Component
============================================================ */
function AccountRow({ acc, onOpen }) {
  return (
    <tr>
      <td>{acc.accountNumber}</td>
      <td>{acc.ifscCode}</td>
      <td>{acc.holderName}</td>
      <td>₹{acc.balance}</td>
      <td>{sentenceCase(acc.accountType)}</td>
      <td>{acc.email ?? "-"}</td>
      <td>{acc.phoneNumber ?? acc.phone ?? "-"}</td>
      <td>{acc.locked ? "Locked" : "Active"}</td>

      <td>
        <button className="btn-blue" onClick={() => onOpen(acc)}>
          Details
        </button>
      </td>
    </tr>
  );
}

/* ============================================================
   MAIN PAGE
============================================================ */
export default function AdminAccounts() {
  const navigate = useNavigate();

  const [accounts, setAccounts] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);

  const [selected, setSelected] = useState(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const [draft, setDraft] = useState({ email: "", phoneNumber: "" });

  useEffect(() => {
    loadAccounts();
  }, []);

  const loadAccounts = async () => {
    setLoading(true);
    setError("");
    try {
      const res = await fetchAllAccounts();
      setAccounts(res.data || []);
      setFiltered(res.data || []);
    } catch {
      setError("Failed to load accounts.");
    }
    setLoading(false);
  };

  useEffect(() => {
    const q = query.toLowerCase();
    setFiltered(
      accounts.filter(
        (a) =>
          a.accountNumber?.toString().includes(q) ||
          a.holderName?.toLowerCase().includes(q) ||
          a.email?.toLowerCase().includes(q)
      )
    );
  }, [query, accounts]);

  const doUnlock = async (accNo) => {
    if (!window.confirm(`Unlock account ${accNo}?`)) return;

    setActionLoading(true);

    try {
      const res = await unlockAccount(accNo);
      if (res.data.success) {
        setMessage("Account unlocked.");
        loadAccounts();
      } else {
        setMessage("Unlock failed.");
      }
    } catch {
      setMessage("Unlock failed.");
    }

    setActionLoading(false);
    setTimeout(() => setMessage(""), 3000);
  };

  const openDetails = async (acc) => {
    setSelected(null);
    setError("");

    try {
      const res = await getAccount(acc.accountNumber);
      const data = res.data || acc;

      setSelected(data);

      setDraft({
        email: data.email || "",
        phoneNumber: data.phoneNumber || "",
      });
    } catch {
      setSelected(acc);
    }
  };

  const saveContact = async () => {
    if (!selected) return;

    const email = draft.email.trim();
    const phone = draft.phoneNumber.trim();

    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!emailPattern.test(email)) {
      setError("Invalid email format.");
      return;
    }

    if (!/^\d{10}$/.test(phone)) {
      setError("Phone number must be exactly 10 digits.");
      return;
    }

    setSaving(true);

    try {
      const res = await updateContact(selected.accountNumber, draft);

      if (res.data.success || res.status === 200) {
        setMessage("Contact updated.");

        setAccounts((prev) =>
          prev.map((a) =>
            a.accountNumber === selected.accountNumber ? { ...a, ...draft } : a
          )
        );

        setFiltered((prev) =>
          prev.map((a) =>
            a.accountNumber === selected.accountNumber ? { ...a, ...draft } : a
          )
        );

        setSelected(null);
      } else {
        setError("Update failed.");
      }
    } catch {
      setError("Server error.");
    }

    setSaving(false);
    setTimeout(() => setMessage(""), 3000);
  };
     
  useEffect(() => {
  document.title = "View All Accounts | AstroNova Bank";
}, []);


  return (
    <div
      className="admin-accounts-wrapper"
      style={{
        backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`,
      }}
    >
      <Header />

      <div className="admin-accounts-container">
        {/* ======================= TOP BAR ======================= */}
        <div className="acc-topbar">
          <h2>All Accounts</h2>

          <div className="acc-controls">
            <button className="btn-blue" onClick={() => navigate("/admin")}>
              Back
            </button>

            <input
              className="acc-search"
              placeholder="Search account / name / email"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />

            <button className="btn-blue" onClick={loadAccounts}>
              Refresh
            </button>
          </div>
        </div>

        {message && <div className="acc-msg">{message}</div>}

        {/* ======================= TABLE ======================= */}
        <div className="acc-table-wrapper">
          {loading ? (
            <div className="acc-empty">Loading accounts...</div>
          ) : (
            <table className="acc-table">
              <thead>
                <tr>
                  <th>Account No</th>
                  <th>IFSC Code</th>
                  <th>Holder</th>
                  <th>Balance</th>
                  <th>Type</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan="9" className="acc-empty">
                      No accounts found.
                    </td>
                  </tr>
                ) : (
                  filtered.map((a) => (
                    <AccountRow key={a.accountNumber} acc={a} onOpen={openDetails} />
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>

      </div>
      {/* ======================= DETAILS MODAL ======================= */}
        {selected && (
          <div className="acc-modal-overlay">
            <div className="acc-modal">
              <h3 className="acc-modal-title">Account Details</h3>

              <div className="acc-modal-body">
                <div><strong>Account:</strong> {selected.accountNumber}</div>
                <div><strong>IFSC Code:</strong> {selected.ifscCode}</div>
                <div><strong>Holder:</strong> {selected.holderName}</div>

                <div>
                  <strong>DOB:</strong>{" "}
                  {selected.dob
                    ? new Date(selected.dob).toLocaleDateString("en-GB")
                    : "—"}
                </div>

                <div><strong>Age:</strong> {selected.age ?? "—"}</div>

                <div>
                  <strong>Email:</strong>
                  <input
                    className="acc-input"
                    value={draft.email}
                    onChange={(e) =>
                      setDraft({ ...draft, email: e.target.value })
                    }
                  />
                </div>

                <div>
                  <strong>Phone:</strong>
                  <input
                    className="acc-input"
                    value={draft.phoneNumber}
                    onChange={(e) =>
                      setDraft({ ...draft, phoneNumber: e.target.value })
                    }
                  />
                </div>

                <div><strong>Balance:</strong> ₹{selected.balance}</div>
                <div><strong>Type:</strong> {sentenceCase(selected.accountType)}</div>
                <div><strong>Status:</strong> {selected.locked ? "Locked" : "Active"}</div>
                <div>
                  <strong>Last Activity:</strong>{" "}
                  {formatToDDMMYYYY_HHMM(selected.lastActivity || selected.createdAt)}
                </div>
              </div>

              {error && <div className="acc-error">{error}</div>}

              <div className="acc-modal-footer">
                <button className="btn-blue" onClick={saveContact}>
                  {saving ? "Saving..." : "Save"}
                </button>

                {selected.locked && (
                  <button
                    className="btn-green"
                    onClick={() => doUnlock(selected.accountNumber)}
                  >
                    {actionLoading ? "Working..." : "Unlock"}
                  </button>
                )}

                <button className="btn-cancel" onClick={() => setSelected(null)}>
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
    </div>
  );
}
