import React, { useEffect, useState } from "react";
import axios from "axios";
import Header from "../components/Header";
import SidebarMenu from "../components/SidebarMenu";
import { useNavigate } from "react-router-dom";
import "./AdminDeletionRequests.css";

export default function AdminDeletionRequests() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [requests, setRequests] = useState([]);
  const [selected, setSelected] = useState(null);
  const [comment, setComment] = useState("");
  const [approveloading, setApproveLoading] = useState(false);
  const [rejectloading, setRejectLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const navigate = useNavigate();
  const adminName = sessionStorage.getItem("username") || "ADMIN";

  const loadRequests = async () => {
    try {
      const res = await axios.get("http://localhost:8080/api/deletion/pending");
      setRequests(res.data);
    } catch (err) {
      console.log("Error loading requests:", err);
    }
  };

  useEffect(() => {
    loadRequests();
  }, []);

  const openDetails = async (id) => {
    try {
      const res = await axios.get(`http://localhost:8080/api/deletion/${id}`);
      setSelected(res.data);
      setComment("");
      setMessage("");
    } catch (err) {
      console.log("Error:", err);
    }
  };

  const closeModal = () => {
    setSelected(null);
    setComment("");
    setMessage("");
  };

  const approve = async () => {
    if (!selected) return;

    if (selected.hasLoan) {
      setMessage({
        type: "error",
        text: "Cannot approve deletion. Loan is active.",
      });
      return;
    }

    setApproveLoading(true);
    try {
      const res = await axios.post(
        `http://localhost:8080/api/deletion/${selected.id}/approve`,
        {},
        {
          params: { admin: adminName, comment },
          withCredentials: true,
        }
      );

      setMessage({
        type: res.data.success ? "success" : "error",
        text: res.data.message,
      });

      if (res.data.success) {
        setTimeout(() => {
          closeModal();
          loadRequests();
        }, 1200);
      }
    } catch {
      setMessage({ type: "error", text: "Server error" });
    }
    setApproveLoading(false);
  };

  const reject = async () => {
    if (!selected) return;

    if (!comment.trim()) {
      setMessage({
        type: "error",
        text: "Comment is required to reject a request.",
      });
      return;
    }

    setRejectLoading(true);
    try {
      const res = await axios.post(
        `http://localhost:8080/api/deletion/${selected.id}/reject`,
        {},
        {
          params: { admin: adminName, comment },
          withCredentials: true,
        }
      );

      setMessage({
        type: res.data.success ? "success" : "error",
        text: res.data.message,
      });

      if (res.data.success) {
        setTimeout(() => {
          closeModal();
          loadRequests();
        }, 1200);
      }
    } catch {
      setMessage({ type: "error", text: "Server error" });
    }
    setRejectLoading(false);
  };

  useEffect(() => {
  document.title = "Account Deletion Requests | AstroNova Bank";
}, []);

  return (
    <div
      className="admin-del-wrapper"
      style={{ backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)` }}
    >
      <Header onMenuClick={() => setMenuOpen(true)} />
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      <div className="admin-del-container">
        {/* Top Bar */}
        <div className="del-topbar">
          <h2>Account Deletion Requests</h2>

          <div className="del-controls">
            <button className="btn-blue" onClick={() => navigate("/admin")}>Back</button>
            <button className="btn-blue" onClick={loadRequests}>Refresh</button>
          </div>
        </div>

        {/* Table List */}
        <div className="del-table-wrapper">
          <h3 className="del-section-title">Pending ({requests.length})</h3>

          <table className="del-table">
            <thead>
              <tr>
                <th>Holder Name</th>
                <th>Account Number</th>
                <th>Loan Status</th>
                <th>Action</th>
              </tr>
            </thead>

            <tbody>
              {requests.length === 0 ? (
                <tr>
                  <td colSpan="4" className="del-empty">No pending requests</td>
                </tr>
              ) : (
                requests.map((r) => (
                  <tr key={r.id}>
                    <td>{r.holderName}</td>
                    <td>{r.accountNumber}</td>
                    <td>
                      <span className={r.hasLoan ? "badge-red" : "badge-green"}>
                        {r.hasLoan ? "Loan Active" : "No Loan"}
                      </span>
                    </td>
                    <td>
                      <button className="btn-blue" onClick={() => openDetails(r.id)}>
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
            <div className="modal-overlay" onClick={closeModal}></div>

            <div className="modal-box">
              <h2 className="modal-title">Deletion Request Details</h2>

              {message && (
                <div
                  className={`admin-alert ${
                    message.type === "success" ? "admin-alert-success" : "admin-alert-error"
                  }`}
                >
                  {message.text}
                </div>
              )}

              <p><strong>Account:</strong> {selected.accountNumber}</p>
              <p><strong>Holder:</strong> {selected.holderName}</p>
              <p><strong>Email:</strong> {selected.email}</p>
              <p><strong>Phone:</strong> {selected.phone}</p>

              <div className="reason-box">
                <strong>Reason:</strong> {selected.reason}
              </div>

              <h3>Loan Information</h3>

              {selected.hasLoan ? (
                <div className="loan-warning-box">
                  <p><strong>Status:</strong> Loan ACTIVE ❌</p>
                </div>
              ) : (
                <div className="loan-clear-box">No active loan ✔</div>
              )}

              <label className="modal-label">Admin Comment</label>
              <textarea
                className="modal-input"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Add comment..."
              />

              <div className="acc-modal-footer">
                <button className="btn-green" onClick={approve} disabled={loading || selected.hasLoan}>
                  {approveloading ? "Processing..." : "Approve"}
                </button>

                <button className="btn-cancel" onClick={reject} disabled={loading}>
                  {rejectloading ? "Processing..." : "Reject"}
                </button>

                <button className="btn-cancel" onClick={closeModal}>Close</button>
              </div>

              {selected.hasLoan && (
                <p className="modal-block-msg">
                  ❌ Cannot approve deletion because this customer has an active loan.
                </p>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
