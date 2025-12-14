// src/pages/CustomerDashboard.jsx
import React, { useEffect, useState } from "react";
import { getAccount, updateAccount } from "../services/accounts";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import SidebarMenu from "../components/SidebarMenu";

import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import { format } from "date-fns";

import Header from "../components/Header";
import "./CustomerDashboard.css";
import { getLoanStatus } from "../services/loan";

export default function CustomerDashboard() {
  const navigate = useNavigate();
  const accountNumber = localStorage.getItem("accountNumber");

  const [menuOpen, setMenuOpen] = useState(false);
  const [account, setAccount] = useState(null);
  const [loan, setLoan] = useState(null); // ⭐ LOAN STATUS
  const [loading, setLoading] = useState(true);
  const [loanLoading, setLoanLoading] = useState(true);

  const [error, setError] = useState("");
  const [editMode, setEditMode] = useState(false);
  const [form, setForm] = useState({});
  const [saving, setSaving] = useState(false);
  const [dobError, setDobError] = useState("");

  // EARLY LOAN CLOSURE MODAL
  const [showModal, setShowModal] = useState(false);
  const [txnPin, setTxnPin] = useState("");
  const [closing, setClosing] = useState(false);
  const [closeError, setCloseError] = useState("");
  const [closeSuccess, setCloseSuccess] = useState("");

  // AUTO-REPAYMENT MODAL
  const [showAutoModal, setShowAutoModal] = useState(false);
  const [autoPin, setAutoPin] = useState("");
  const [autoLoading, setAutoLoading] = useState(false);
  const [autoError, setAutoError] = useState("");
  const [autoSuccess, setAutoSuccess] = useState("");

  const isoToDate = (iso) => {
    if (!iso) return null;
    const parts = iso.split("-");
    return new Date(parts[0], parts[1] - 1, parts[2]);
  };

  const dateToIso = (d) => (d ? format(d, "yyyy-MM-dd") : "");
  const dateToDisplay = (d) => (d ? format(d, "dd/MM/yyyy") : "");

  // --------------------------------------------------
  // Helper: load loan status (reused after actions)
  // --------------------------------------------------
  const loadLoanStatus = () => {
    if (!accountNumber) return;

    setLoanLoading(true);

    getLoanStatus(accountNumber)
      .then((res) => {
        console.log("LOAN STATUS RESPONSE:", res.data);

        if (!res.data?.success) {
          setLoan(null);
          return;
        }

        const d = res.data;

        setLoan({
          takenLoan: d.takenLoan ?? false,
          loanAmount: d.loanAmount ?? 0,
          loanInterestRate: d.loanInterestRate ?? 0,
          loanTotalDue: d.loanTotalDue ?? 0,
          loanLastPaid: d.loanLastPaid ?? null,
          autoRepaymentEnabled:
            d.autoRepaymentEnabled ?? d.auto_repayment_enabled ?? false,
          loanType: d.loanType ?? d.loan_type ?? "",
          emiPlan: d.emiPlan ?? d.emi_plan ?? "",
        });
      })
      .catch(() => setLoan(null))
      .finally(() => setLoanLoading(false));
  };

  // LOAD ACCOUNT INFO
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

  // LOAD LOAN STATUS
   useEffect(() => {
      document.title = "Customer Dashboard | AstroNova Bank";
    }, []);
  useEffect(() => {
    loadLoanStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountNumber]);

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

  // --------------------------------------------------
  // HANDLE EARLY LOAN CLOSURE
  // --------------------------------------------------
  const attemptEarlyClose = () => {
    setCloseError("");
    setCloseSuccess("");

    if (!txnPin || txnPin.length !== 4) {
      setCloseError("Transaction PIN must be 4 digits.");
      return;
    }

    setClosing(true);

    axios
      .post(
        "http://localhost:8080/api/loan/early-close",
        {
          accountNumber,
          transactionPin: txnPin,
        },
        { withCredentials: true }
      )
      .then((res) => {
        if (res.data.success) {
          setCloseSuccess("Loan closed successfully!");

          // ✅ Update balance from backend response if provided
          if (res.data.newBalance !== undefined && res.data.newBalance !== null) {
            setAccount((prev) => ({
              ...prev,
              balance: res.data.newBalance,
            }));
          }

          // ✅ Refresh loan status (should now reflect no active loan)
          loadLoanStatus();

          setTimeout(() => {
            setShowModal(false);
            setTxnPin("");
            setCloseError("");
            setCloseSuccess("");
          }, 1500);
        } else {
          setCloseError(res.data.message || "Failed to close loan.");
        }
      })
      .catch(() => setCloseError("Server error."))
      .finally(() => setClosing(false));
  };

  // --------------------------------------------------
  // HANDLE AUTO-REPAYMENT TOGGLE
  // --------------------------------------------------
  const openAutoModal = () => {
    setAutoPin("");
    setAutoError("");
    setAutoSuccess("");
    setShowAutoModal(true);
  };

  const handleToggleAutoRepayment = () => {
    setAutoError("");
    setAutoSuccess("");

    if (!autoPin || autoPin.length !== 4) {
      setAutoError("Transaction PIN must be 4 digits.");
      return;
    }

    if (!loan) {
      setAutoError("No active loan found.");
      return;
    }

    const targetEnabled = !loan.autoRepaymentEnabled;

    setAutoLoading(true);

    axios
      .post(
        "http://localhost:8080/api/loan/auto-repayment",
        {
          accountNumber,
          transactionPin: autoPin,
          enabled: targetEnabled,
        },
        { withCredentials: true }
      )
      .then((res) => {
        if (res.data.success) {
          setAutoSuccess(res.data.message || "Updated successfully.");

          // ✅ Toggle flag in local state
          setLoan((prev) =>
            prev
              ? {
                  ...prev,
                  autoRepaymentEnabled: targetEnabled,
                }
              : prev
          );

          setTimeout(() => {
            setShowAutoModal(false);
            setAutoPin("");
            setAutoError("");
            setAutoSuccess("");
          }, 1500);
        } else {
          setAutoError(res.data.message || "Failed to update auto repayment.");
        }
      })
      .catch(() => setAutoError("Server error."))
      .finally(() => setAutoLoading(false));
  };

  if (loading || loanLoading)
    return <div className="center-loading">Loading...</div>;

  if (!account)
    return (
      <div className="center-error">
        {error || "Account not found"}
      </div>
    );

  // ⭐ Early closure eligibility:
  const eligibleForEarlyClose =
    loan?.takenLoan &&
    Number(account.balance) >= Number(loan.loanTotalDue) + 1000;

   

  return (
    <div
      className="customer-wrapper"
      style={{ backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)` }}
    >
      <Header />

      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>

      <div className="customer-subheader">
        <h2 className="customer-subheader-title">Customer Dashboard</h2>

        <button className="customer-subheader-logout" onClick={logout}>
          Logout
        </button>
      </div>

      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      <div className="customer-container">
        <div className="customer-card">
          {/* =================== ACCOUNT SUMMARY =================== */}
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

          {/* =================== INFO GRID =================== */}
          <div className="info-grid">
            <div>
              <strong>Account No:</strong> {account.accountNumber}
            </div>
            <div>
              <strong>IFSC:</strong> {account.ifscCode}
            </div>
            <div>
              <strong>Email:</strong> {account.email}
            </div>
            <div>
              <strong>Phone:</strong> {account.phoneNumber}
            </div>

            {/* Editable fields */}
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
                      today.setHours(0, 0, 0, 0);

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

            <div>
              <strong>Status:</strong> {account.locked ? "Locked" : "Active"}
            </div>
            <div>
              <strong>Balance:</strong> ₹{account.balance}
            </div>
            <div>
              <strong>Last Activity:</strong>{" "}
              {account.lastActivity
                ? new Date(account.lastActivity).toLocaleString("en-GB")
                : "-"}
            </div>
          </div>

          {/* =================== LOAN STATUS SECTION =================== */}
          {loan?.takenLoan ? (
            <div className="loan-status-section">
              <h3 className="loan-title">Loan Details</h3>

              <div className="loan-grid">
                <div>
                  <strong>Loan Type:</strong> {loan.loanType || "-"}
                </div>
                <div>
                  <strong>EMI Plan:</strong> {loan.emiPlan || "-"}
                </div>
                <div>
                  <strong>Loan Amount:</strong> ₹{loan.loanAmount}
                </div>
                <div>
                  <strong>Interest Rate:</strong> {loan.loanInterestRate}%
                </div>
                <div>
                  <strong>Total Due:</strong> ₹{loan.loanTotalDue}
                </div>
                <div>
                  <strong>Last Paid:</strong> {loan.loanLastPaid || "-"}
                </div>
                <div>
                  <strong>Auto Repayment:</strong>{" "}
                  {loan.autoRepaymentEnabled ? "Enabled" : "Disabled"}
                </div>
              </div>

              <div className="loan-actions-row">
                {/* ⭐ EARLY CLOSE BUTTON */}
                {eligibleForEarlyClose && (
                  <button
                    className="early-close-btn"
                    onClick={() => setShowModal(true)}
                  >
                    Pay Now & Close Loan (₹{loan.loanTotalDue})
                  </button>
                )}

                {/* ⭐ AUTO-REPAYMENT TOGGLE BUTTON */}
                {/* <button
                  className="early-close-btn"
                  style={{ marginLeft: "10px" }}
                  onClick={openAutoModal}
                >
                  {loan.autoRepaymentEnabled
                    ? "Disable Auto Repayment"
                    : "Enable Auto Repayment"}
                </button> */}
              </div>
            </div>
          ) : (
            <div className="loan-status-section">
              <h3 className="loan-title-dash">No Active Loan</h3>
            </div>
          )}
        </div>
      </div>

      {/* =================== MODAL FOR EARLY LOAN CLOSURE =================== */}
      {showModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>Early Loan Closure</h3>

            <p>
              Amount to be paid: <strong>₹{loan.loanTotalDue}</strong>
            </p>

            <input
              type="password"
              maxLength="4"
              value={txnPin}
              onChange={(e) => setTxnPin(e.target.value.replace(/\D/g, ""))}
              className="modal-input"
              placeholder="Enter 4-digit Transaction PIN"
            />

            {closeError && <p className="modal-error">{closeError}</p>}
            {closeSuccess && <p className="modal-success">{closeSuccess}</p>}

            <div className="modal-actions">
              <button
                onClick={() => setShowModal(false)}
                className="modal-cancel"
              >
                Cancel
              </button>

              <button
                onClick={attemptEarlyClose}
                className="modal-confirm"
                disabled={closing}
              >
                {closing ? "Processing..." : "Confirm Payment"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =================== MODAL FOR AUTO-REPAYMENT TOGGLE =================== */}
      {/* {showAutoModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3>
              {loan?.autoRepaymentEnabled
                ? "Disable Auto Repayment"
                : "Enable Auto Repayment"}
            </h3>

            <p>
              Enter your 4-digit Transaction PIN to{" "}
              {loan?.autoRepaymentEnabled ? "disable" : "enable"} auto
              repayment.
            </p>

            <input
              type="password"
              maxLength="4"
              value={autoPin}
              onChange={(e) => setAutoPin(e.target.value.replace(/\D/g, ""))}
              className="modal-input"
              placeholder="Enter 4-digit Transaction PIN"
            />

            {autoError && <p className="modal-error">{autoError}</p>}
            {autoSuccess && <p className="modal-success">{autoSuccess}</p>}

            <div className="modal-actions">
              <button
                onClick={() => setShowAutoModal(false)}
                className="modal-cancel"
                disabled={autoLoading}
              >
                Cancel
              </button>

              <button
                onClick={handleToggleAutoRepayment}
                className="modal-confirm"
                disabled={autoLoading}
              >
                {autoLoading ? "Processing..." : "Confirm"}
              </button>
            </div>
          </div>
        </div>
      )} */}
    </div>
  );
}
