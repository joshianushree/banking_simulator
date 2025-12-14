// src/pages/CustomerLoanRequest.jsx
import React, { useEffect, useState } from "react";
import axios from "axios";
import Header from "../components/Header";
import SidebarMenu from "../components/SidebarMenu";
import "./CustomerLoanRequest.css";

export default function CustomerLoanRequest() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  

  // form fields
  const [loanType, setLoanType] = useState("Personal Loan");
  const [emiPlan, setEmiPlan] = useState("MONTHLY");
  const [amount, setAmount] = useState("");
  const [govtIdNumber, setGovtIdNumber] = useState("");
  const [govtIdProof, setGovtIdProof] = useState(null);
  const [transactionPin, setTransactionPin] = useState("");
  const [accepted, setAccepted] = useState(false);

  // helper preview
  const [interestRate, setInterestRate] = useState(11.0);
  const [months, setMonths] = useState(12);
  const [emiPreview, setEmiPreview] = useState({
    interestRate: 11,
    totalPayable: 0,
    installment: 0,
    months: 12,
  });

  const accountNumber = localStorage.getItem("accountNumber") || "";
  const [accountBalance, setAccountBalance] = useState(null);
  const [loanStatus, setLoanStatus] = useState(null);

  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);

  const TERMS = {
    "Personal Loan": `Personal Loan — Rate: 11% p.a.
• Unsecured personal credit for urgent expenses.
• Max tenure usually short (1–3 years).
• Pre-closure allowed; partial prepayment may attract charges.
• Income verification required.`,
    "Home Loan": `Home Loan — Rate: 8% p.a.
• Secured loan against property.
• Longer tenures (up to 20 years).
• Requires property valuation and KYC.`,
    "Education Loan": `Education Loan — Rate: 6.5% p.a.
• For tuition and course-related expenses.
• Collateral may be relaxed for small amounts.
• Proof of admission required.`,
  };

  const rateForLoanType = (t) =>
    t === "Home Loan" ? 8.0 : t === "Education Loan" ? 6.5 : 11.0;

  const monthsForPlan = (p) =>
    p === "MONTHLY" ? 12 : p === "QUARTERLY" ? 4 : 1;

  const computeEmiPreview = (amt, rate, m) => {
    const a = Number(amt) || 0;
    if (a <= 0) return { interestRate: rate, totalPayable: 0, installment: 0, months: m };

    const interest = (a * rate) / 100;
    const total = a + interest;

    return {
      interestRate: rate,
      totalPayable: total,
      installment: total / m,
      months: m,
    };
  };

  useEffect(() => {
    const r = rateForLoanType(loanType);
    const m = monthsForPlan(emiPlan);
    setInterestRate(r);
    setMonths(m);
    setEmiPreview(computeEmiPreview(amount, r, m));
  }, [loanType, emiPlan, amount]);

  useEffect(() => {
    if (!accountNumber) return;

    axios
      .get(`http://localhost:8080/api/accounts/${accountNumber}`, { withCredentials: true })
      .then((res) => {
        const bal = res.data.balance ?? null;
        setAccountBalance(Number(bal));
      })
      .catch(() => {});
  }, [accountNumber]);

  useEffect(() => {
    axios
      .get(`http://localhost:8080/api/loan/status/${accountNumber}`, { withCredentials: true })
      .then((res) => {
        if (res.data.success) setLoanStatus(res.data);
      })
      .catch(() => setLoanStatus(null));
  }, []);

  const requiredBalance = () => {
    const a = Number(amount) || 0;
    return a / 4.0;
  };

  const resetForm = () => {
  setLoanType("Personal Loan");
  setEmiPlan("MONTHLY");
  setAmount("");
  setGovtIdNumber("");
  setGovtIdProof(null);
  setTransactionPin("");
  setAccepted(false);

  setMessage(null);

  // Reset EMI preview
  const r = rateForLoanType("Personal Loan");
  const m = monthsForPlan("MONTHLY");
  setInterestRate(r);
  setMonths(m);
  setEmiPreview(computeEmiPreview(0, r, m));
};


  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage(null);

    if (!govtIdProof)
      return setMessage({ type: "error", text: "Upload Govt ID proof." });

    if (!accepted)
      return setMessage({ type: "error", text: "Accept Terms & Conditions." });

    setSubmitting(true);

    try {
      const fd = new FormData();
      fd.append("accountNumber", accountNumber);
      fd.append("amount", amount);
      fd.append("interestRate", interestRate);
      fd.append("loanType", loanType);
      fd.append("emiPlan", emiPlan);
      fd.append("govtIdNumber", govtIdNumber);
      fd.append("govtIdProof", govtIdProof);
      fd.append("transactionPin", transactionPin);

      const res = await axios.post("http://localhost:8080/api/loan/request", fd, {
        withCredentials: true,
        headers: { "Content-Type": "multipart/form-data" },
      });

      if (res.data.success)
        setMessage({ type: "success", text: res.data.message });
      else setMessage({ type: "error", text: res.data.message });
    } catch (e) {
      setMessage({ type: "error", text: "Server error" });
    }

    setSubmitting(false);
  };

  const f = (n) =>
    n === null || n === undefined
      ? "-"
      : Number(n).toLocaleString("en-IN", { maximumFractionDigits: 2 });

      useEffect(() => {
  document.title = "Loan Application | AstroNova Bank";
}, []);


  return (
    <div
      className="loan-wrapper"
      style={{ backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)` }}
    >
      <button className="menu-btn-global" onClick={() => setSidebarOpen(true)}>
        ☰
      </button>

      <Header />
      <SidebarMenu isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div className="loan-container">
        {/* TITLE BAR */}
        {/* ⭐ Subheader (Same style as Generate PIN page) */}
        <div className="loan-subheader">
          <h2 className="loan-subheader-title">Loan Application</h2>

          <button className="loan-back-btn" onClick={() => window.history.back()}>
            Back
          </button>
        </div>

        {/* GRID LAYOUT */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* LEFT FORM */}
          <div className="loan-card-block">
            <form onSubmit={handleSubmit}>
              <label className="gl-label">Loan Type</label>
              <select className="gl-input" value={loanType} onChange={(e) => setLoanType(e.target.value)}>
                <option>Personal Loan</option>
                <option>Home Loan</option>
                <option>Education Loan</option>
              </select>

              <label className="gl-label">EMI Plan</label>
              <select className="gl-input" value={emiPlan} onChange={(e) => setEmiPlan(e.target.value)}>
                <option value="MONTHLY">Monthly</option>
                <option value="QUARTERLY">Quarterly</option>
                <option value="YEARLY">Yearly</option>
              </select>

              <label className="gl-label">Loan Amount (₹)</label>
              <input className="gl-input" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />

              <label className="gl-label">Government ID Number</label>
              <input className="gl-input" type="text" value={govtIdNumber} onChange={(e) => setGovtIdNumber(e.target.value)} />

              <label className="gl-label">Upload Govt ID Proof</label>
              <input className="gl-input" type="file" onChange={(e) => setGovtIdProof(e.target.files[0])} />

              <label className="gl-label">Transaction PIN</label>
              <input
                className="gl-input"
                type="password"
                maxLength={4}
                value={transactionPin}
                onChange={(e) => setTransactionPin(e.target.value.replace(/\D/g, ""))}
              />

              <label className="checkbox-row">
                <input type="checkbox" checked={accepted} onChange={(e) => setAccepted(e.target.checked)} />
                Accept Terms & Conditions
              </label>
                      {message && (
              <div
                className={`alert-box ${
                  message.type === "success" ? "alert-success" : "alert-error"
                      }`}
                    >
                      {message.text}
                    </div>
                  )}
              <div className="loan-btn-row">
                <button
                  type="submit"
                  disabled={!accepted || submitting}
                  className={`gl-btn-submit ${!accepted || submitting ? "disabled" : ""}`}
                >
                  {submitting ? "Submitting..." : "Send Loan Request"}
                </button>

               <button
                type="button"
                className="gl-btn-reset"
                onClick={resetForm}
              >
                Reset
              </button>

              </div>

              <div className="required-info">
                <p>Required Balance: <strong>₹{f(requiredBalance())}</strong></p>
                <p>Your Balance: <strong>{accountBalance !== null ? f(accountBalance) : "Loading..."}</strong></p>
              </div>
            </form>
          </div>

          {/* RIGHT SIDE INFO */}
          <div className="space-y-6">

            <div className="loan-info-block">
              <h3 className="loan-info-title">Terms & Conditions — {loanType}</h3>
              <p className="terms-text whitespace-pre-line">{TERMS[loanType]}</p>
            </div>

            <div className="loan-info-block">
              <h3 className="loan-info-title">EMI Preview</h3>
              <p><strong>Interest Rate:</strong> {interestRate}%</p>
              <p><strong>Installments:</strong> {months}</p>
              <p><strong>Total Payable:</strong> ₹{f(emiPreview.totalPayable)}</p>
              <p><strong>Each Installment:</strong> ₹{f(emiPreview.installment)}</p>
            </div>

            <div className="loan-info-block">
              <h3 className="loan-info-title">Current Loan Status</h3>
              {!loanStatus ? (
                <p>No active loan.</p>
              ) : (
                <div>
                  <p><strong>Taken Loan:</strong> {loanStatus.takenLoan ? "Yes" : "No"}</p>
                  <p><strong>Principal:</strong> ₹{loanStatus.loanAmount}</p>
                  <p><strong>Interest Rate:</strong> {loanStatus.loanInterestRate}%</p>
                  <p><strong>Total Due:</strong> ₹{loanStatus.loanTotalDue}</p>
                </div>
              )}
            </div>

          </div>
        </div>
      </div>
    </div>
  );
}
