import React, { useEffect, useState } from "react";
import { api } from "../services/api.js";
import { useNavigate } from "react-router-dom";
import Header from "../components/Header";
import "./AdminManageAdmins.css";

function sentenceCase(s) {
  if (!s && s !== 0) return "";
  const str = String(s).trim();
  return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

function formatToDDMMYYYY_HHMM(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return "—";

  const p = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kolkata",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).formatToParts(d);

  const mp = {};
  p.forEach((x) => {
    if (x.type !== "literal") mp[x.type] = x.value;
  });

  return `${mp.day}/${mp.month}/${mp.year} ${mp.hour}:${mp.minute}`;
}

export default function AdminManageAdmins() {
  const [admins, setAdmins] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  const [editingUser, setEditingUser] = useState(null);
  const [editEmail, setEditEmail] = useState("");
  const [editPhone, setEditPhone] = useState("");

  const [emailError, setEmailError] = useState("");
  const [phoneError, setPhoneError] = useState("");

  const [savingEdit, setSavingEdit] = useState(false);

  const loggedIn = localStorage.getItem("username");
  const navigate = useNavigate();

  const fetchAdmins = async () => {
    setLoading(true);
    try {
      const res = await api.get("/admin-users");
      setAdmins(res.data || []);
    } catch {
      setMessage("Failed to load admin users.");
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchAdmins();
  }, []);

  const deleteAdmin = async (username) => {
    if (!window.confirm(`Delete admin ${username}?`)) return;
    try {
      const res = await api.delete(`/admin-users/${username}`, {
        headers: { "X-Admin-Username": loggedIn }
      });

      if (res.data.success) {
        setMessage("Admin deleted successfully.");
        fetchAdmins();
      } else {
        setMessage(res.data.message || "Delete failed.");
      }
    } catch {
      setMessage("Delete failed.");
    }

    setTimeout(() => setMessage(""), 3000);
  };

  const validateEmail = (email) => {
    const e = email.trim();
    if (!e) return "Email required.";
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e) ? "" : "Invalid email.";
  };

  const validatePhone = (phone) => {
    const p = phone.trim();
    if (!p) return "Phone required.";
    return /^[0-9]{10}$/.test(p) ? "" : "Must be 10 digits.";
  };

  const openEdit = (admin) => {
    setEditingUser(admin.username);
    setEditEmail(admin.email || "");
    setEditPhone(admin.phone || "");
    setEmailError("");
    setPhoneError("");
  };

  const closeEdit = () => {
    setEditingUser(null);
    setSavingEdit(false);
  };

  const saveEdit = async () => {
    const emailErr = validateEmail(editEmail);
    const phoneErr = validatePhone(editPhone);

    setEmailError(emailErr);
    setPhoneError(phoneErr);
    if (emailErr || phoneErr) return;

    setSavingEdit(true);

    try {
      const res = await api.put(
        `/admin-users/${editingUser}`,
        { email: editEmail, phone: editPhone },
        { headers: { "X-Admin-Username": loggedIn } }
      );

      if (res.data.success) {
        setMessage("Admin updated successfully.");
        fetchAdmins();
        closeEdit();
      } else {
        setMessage(res.data.message || "Update failed.");
      }
    } catch {
      setMessage("Update failed.");
    }

    setSavingEdit(false);
    setTimeout(() => setMessage(""), 3000);
  };

  useEffect(() => {
  document.title = "Manage Admins | AstroNova Bank";
}, []);

  return (
    <div
      className="manage-wrapper"
      style={{
        backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`
      }}
    >
      <Header />

      <div className="manage-card">
        <div className="manage-header">
          <h2 className="manage-title">Manage Admin Users</h2>

          <div className="manage-buttons">
            <button className="gl-btn-blue" onClick={() => navigate("/admin")}>
              Back
            </button>

            <button className="gl-btn-blue" onClick={fetchAdmins}>
              Refresh
            </button>
          </div>
        </div>

        {message && <div className="manage-success">{message}</div>}

        {loading ? (
          <div className="manage-loading">Loading...</div>
        ) : (
          <div className="manage-table-wrapper">
            <table className="manage-table">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Created</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {admins.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="manage-empty">
                      No admin users found.
                    </td>
                  </tr>
                ) : (
                  admins.map((a, i) => {
                    const isSelf = a.username === loggedIn;
                    const isMainAdmin = a.username.toLowerCase() === "admin";

                    return (
                      <tr key={i}>
                        <td>{a.username}</td>
                        <td>{a.email || "-"}</td>
                        <td>{a.phone || "-"}</td>
                        <td>{formatToDDMMYYYY_HHMM(a.createdAt)}</td>
                        <td>{sentenceCase(a.status)}</td>

                        <td className="manage-action-col">
                          <button
                            className="gl-btn-blue small-btn"
                            onClick={() => openEdit(a)}
                          >
                            Edit
                          </button>

                          <button
                            className={`gl-btn-red small-btn ${
                              isSelf || isMainAdmin ? "disabled" : ""
                            }`}
                            disabled={isSelf || isMainAdmin}
                            onClick={() => deleteAdmin(a.username)}
                          >
                            {isSelf ? "You" : isMainAdmin ? "Protected" : "Delete"}
                          </button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* EDIT MODAL */}
      {editingUser && (
        <div className="edit-modal-overlay">
          <div className="edit-modal">
            <div className="edit-modal-header">
              <h3>
                Editing:{" "}
                <span className="modal-username">{editingUser}</span>
              </h3>
              <button className="modal-close" onClick={closeEdit}>
                ✕
              </button>
            </div>

            <div className="edit-modal-body">
              <label className="gl-label">Email</label>
              <input
                className="gl-input"
                value={editEmail}
                onChange={(e) => setEditEmail(e.target.value)}
              />
              {emailError && <p className="field-error">{emailError}</p>}

              <label className="gl-label">Phone</label>
              <input
                className="gl-input"
                value={editPhone}
                onChange={(e) => setEditPhone(e.target.value)}
              />
              {phoneError && <p className="field-error">{phoneError}</p>}
            </div>

            <div className="edit-modal-footer">
              <button className="gl-btn-green" onClick={saveEdit}>
                {savingEdit ? "Saving..." : "Save"}
              </button>

              <button className="gl-btn-blue" onClick={closeEdit}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
