import React, { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";

import Header from "../components/Header";
import SidebarMenu from "../components/SidebarMenu";

import "./AdminLoanRequests.css";

export default function AdminLoanRequests() {
  const navigate = useNavigate();

  const [menuOpen, setMenuOpen] = useState(false);
  const [requests, setRequests] = useState([]);
  const [selected, setSelected] = useState(null);
  const [reviewData, setReviewData] = useState(null);

  const [comment, setComment] = useState("");
  const [approveloading, setApproveLoading] = useState(false);
  const [rejectloading, setRejectLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  // Load pending requests
  const loadRequests = async () => {
    try {
      const res = await axios.get(
        "http://localhost:8080/api/loan/admin/requests",
        { withCredentials: true }
      );

      if (res.data.success) {
        setRequests(res.data.requests);
      }
    } catch (err) {
      console.log("Error loading requests:", err);
    }
  };

  useEffect(() => {
    loadRequests();
  }, []);

  const openModal = (req) => {
    setSelected(req);
    setReviewData(null);
    setComment("");
    setMessage("");
  };

  const closeModal = () => {
    setSelected(null);
    setReviewData(null);
    setComment("");
    setMessage("");
  };

  // Review request
  const reviewLoan = async () => {
    if (!selected) return;

    try {
      const res = await axios.get(
        `http://localhost:8080/api/loan/admin/review/${selected.account_number}`,
        { withCredentials: true }
      );

      if (res.data.success) {
        setReviewData(res.data);
      } else {
        setMessage({ type: "error", text: res.data.message });
      }
    } catch (err) {
      setMessage({ type: "error", text: "Failed to review loan request." });
    }
  };

  // Approve Request
  const approveLoan = async () => {
    if (!selected) return;
    const reqId = selected.id;

    try {
      setApproveLoading(true);

      const res = await axios.post(
        `http://localhost:8080/api/loan/admin/approve/${reqId}`,
        { comment },
        { withCredentials: true }
      );

      alert(res.data.message);
      if (res.data.success) {
        closeModal();
        loadRequests();
      }
    } catch (err) {
      alert("Error approving loan");
      console.error(err);
    }

    setApproveLoading(false);
  };

  // Reject Request
  const rejectLoan = async () => {
    if (!selected) return;
    const reqId = selected.id;

    try {
      setRejectLoading(true);

      const res = await axios.post(
        `http://localhost:8080/api/loan/admin/reject/${reqId}`,
        { comment },
        { withCredentials: true }
      );

      alert(res.data.message);
      if (res.data.success) {
        closeModal();
        loadRequests();
      }
    } catch (err) {
      alert("Error rejecting loan");
      console.error(err);
    }

    setRejectLoading(false);
  };

  useEffect(() => {
  document.title = "View Loan Requests | AstroNova Bank";
}, []);

  return (
    <div
      className="admin-loan-wrapper"
      style={{ backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)` }}
    >
      <Header onMenuClick={() => setMenuOpen(true)} />
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      {/* 🔹 Glass Card Container (matches AdminAccounts) */}
      <div className="admin-loan-container">
        {/* Top bar INSIDE the glass card */}
        <div className="loan-topbar">
          <h2>Loan Requests</h2>

          <div className="loan-controls">
            <button className="btn-blue" onClick={() => navigate("/admin")}>
              Back
            </button>

            <button className="btn-blue" onClick={loadRequests}>
              Refresh
            </button>
          </div>
        </div>

        {/* TABLE SECTION */}
        <div className="loan-table-wrapper">
          <h3 className="loan-section-title">Pending ({requests.length})</h3>

          <table className="loan-table">
            <thead>
              <tr>
                <th>Account Number</th>
                <th>Loan Amount</th>
                <th>Action</th>
              </tr>
            </thead>

            <tbody>
              {requests.length === 0 ? (
                <tr>
                  <td colSpan="3" className="loan-empty">
                    No pending loan requests.
                  </td>
                </tr>
              ) : (
                requests.map((r) => (
                  <tr key={r.id}>
                    <td>{r.account_number}</td>
                    <td>₹ {r.requested_amount}</td>
                    <td>
                      <button className="btn-blue" onClick={() => openModal(r)}>
                        View
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* MODAL */}
        {selected && (
          <>
            <div className="acc-modal-overlay" onClick={closeModal}></div>

            <div className="acc-modal">
              <h3 className="acc-modal-title">Loan Request Details</h3>

              <div className="acc-modal-body">
                <div><strong>Account:</strong> {selected.account_number}</div>
                <div><strong>Amount:</strong> ₹{selected.requested_amount}</div>
                <div><strong>Loan Type:</strong> {selected.loan_type}</div>
                <div><strong>EMI Plan:</strong> {selected.emi_plan}</div>
                <div><strong>ID Number:</strong> {selected.govt_id_number}</div>

                {!reviewData && (
                  <button className="btn-blue" onClick={reviewLoan}>
                    Review Request
                  </button>
                )}

                {reviewData && (
                  <div className="review-box">
                    <h4>Review Summary</h4>
                    <p><strong>Avg Balance:</strong> ₹{reviewData.averageBalance}</p>
                    <p><strong>Deposits (6 months):</strong> ₹{reviewData.totalDeposits}</p>
                    <p>
                      <strong>Suggestion:</strong>{" "}
                      <span
                        className={
                          reviewData.suggestion === "APPROVE"
                            ? "text-green"
                            : "text-red"
                        }
                      >
                        {reviewData.suggestion}
                      </span>
                    </p>
                  </div>
                )}

                <label className="modal-label">Admin Comment</label>
                <textarea
                  className="modal-input"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="Optional comment"
                />

                <div className="acc-modal-footer">
                  <button className="btn-green" onClick={approveLoan} disabled={loading}>
                    {approveloading ? "Processing..." : "Approve"}
                  </button>

                  <button className="btn-cancel" onClick={rejectLoan} disabled={loading}>
                    {rejectloading ? "Processing..." : "Reject"}
                  </button>

                  <button className="btn-cancel" onClick={closeModal}>
                    Close
                  </button>
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
