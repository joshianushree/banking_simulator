import React, { useEffect, useState } from "react";
import { getAccount, updateAccount } from "../services/accounts";
import { useNavigate } from "react-router-dom";
import SidebarMenu from "../components/SidebarMenu";

import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import { format } from "date-fns";

import Header from "../components/Header";
import "./CustomerDashboard.css";

export default function CustomerDashboard() {
  const navigate = useNavigate();
  const accountNumber = localStorage.getItem("accountNumber");

  const [menuOpen, setMenuOpen] = useState(false);
  const [account, setAccount] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [editMode, setEditMode] = useState(false);
  const [form, setForm] = useState({});
  const [saving, setSaving] = useState(false);
  const [dobError, setDobError] = useState("");

  const isoToDate = (iso) => {
    if (!iso) return null;
    const parts = iso.split("-");
    return new Date(parts[0], parts[1] - 1, parts[2]);
  };

  const dateToIso = (d) => (d ? format(d, "yyyy-MM-dd") : "");
  const dateToDisplay = (d) => (d ? format(d, "dd/MM/yyyy") : "");

  useEffect(() => {
    if (!accountNumber) {
      navigate("/");
      return;
    }

    getAccount(accountNumber)
      .then((res) => {
        const data = res.data || {};
        const normalized = {
          ...data,
          dob: data.dob?.substring(0, 10),
        };

        setAccount(normalized);
        setForm(normalized);
        setLoading(false);
      })
      .catch(() => {
        setError("Failed to load account.");
        setLoading(false);
      });
  }, [accountNumber, navigate]);

  const logout = () => {
    localStorage.clear();
    navigate("/");
  };

  const handleChange = (e) =>
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const saveChanges = () => {
    if (dobError) return alert("Fix DOB error first.");

    setSaving(true);
    updateAccount(accountNumber, form)
      .then(() => {
        setAccount(form);
        setEditMode(false);
        setSaving(false);
        alert("Updated successfully");
      })
      .catch(() => {
        alert("Failed to save changes");
        setSaving(false);
      });
  };

  if (loading)
    return <div className="center-loading">Loading...</div>;

  if (!account)
    return (
      <div className="center-error">
        {error || "Account not found"}
      </div>
    );

  return (
    <div
      className="customer-wrapper"
      style={{ backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)` }}
    >
      {/* GLOBAL HEADER */}
      <Header />

      {/* ⭐ HAMBURGER INSIDE GLOBAL HEADER TOP-LEFT */}
      <button
        className="menu-btn-global"
        onClick={() => setMenuOpen(true)}
      >
        ☰
      </button>

      {/* CLEAN SUBHEADER BELOW HEADER */}
      <div className="customer-subheader">
        <h2 className="customer-subheader-title">Customer Dashboard</h2>

        <button className="customer-subheader-logout" onClick={logout}>
          Logout
        </button>
      </div>

      {/* SIDEBAR ABOVE EVERYTHING */}
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      <div className="customer-container">
        <div className="customer-card">
          <div className="card-top-row">
            <h2 className="customer-section-title">Account Summary</h2>

            {!editMode ? (
              <button
                onClick={() => setEditMode(true)}
                className="customer-btn customer-btn-edit"
              >
                Edit Details
              </button>
            ) : (
              <div className="edit-actions">
                <button
                  onClick={saveChanges}
                  disabled={saving}
                  className="customer-btn customer-btn-save"
                >
                  {saving ? "Saving..." : "Save"}
                </button>

                <button
                  onClick={() => {
                    setEditMode(false);
                    setForm(account);
                    setDobError("");
                  }}
                  className="customer-btn customer-btn-cancel"
                >
                  Cancel
                </button>
              </div>
            )}
          </div>

          <div className="info-grid">
            <div><strong>Account No:</strong> {account.accountNumber}</div>
            <div><strong>IFSC:</strong> {account.ifscCode}</div>
            <div><strong>Email:</strong> {account.email}</div>
            <div><strong>Phone:</strong> {account.phoneNumber}</div>

            <div>
              <strong>Name: </strong>
              {editMode ? (
                <input
                  name="holderName"
                  value={form.holderName || ""}
                  onChange={handleChange}
                  className="customer-input"
                />
              ) : (
                account.holderName
              )}
            </div>

            <div>
              <strong>Address: </strong>
              {editMode ? (
                <input
                  name="address"
                  value={form.address || ""}
                  onChange={handleChange}
                  className="customer-input"
                />
              ) : (
                account.address
              )}
            </div>

            <div>
              <strong>Date Of Birth:</strong>
              {editMode ? (
                <div>
                  <DatePicker
                    selected={isoToDate(form.dob)}
                    onChange={(date) => {
                      if (!date) return setDobError("Invalid DOB");

                      const today = new Date();
                      today.setHours(0,0,0,0);

                      if (date > today) {
                        return setDobError("DOB cannot be in future");
                      }

                      setDobError("");
                      setForm((f) => ({ ...f, dob: dateToIso(date) }));
                    }}
                    className="customer-input"
                    dateFormat="dd/MM/yyyy"
                    showMonthDropdown
                    showYearDropdown
                    dropdownMode="select"
                  />
                  {dobError && <div className="dob-error">{dobError}</div>}
                </div>
              ) : (
                dateToDisplay(isoToDate(form.dob))
              )}
            </div>

            <div><strong>Status:</strong> {account.locked ? "Locked" : "Active"}</div>
            <div><strong>Balance:</strong> ₹{account.balance}</div>
            <div><strong>Last Activity:</strong> {account.lastActivity ? new Date(account.lastActivity).toLocaleString('en-GB') : "-"}</div>
          </div>

        </div>
      </div>
    </div>
  );
}
