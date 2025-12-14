import React, { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";
import SidebarMenu from "../components/SidebarMenu";
import Header from "../components/Header";
import "./CustomerGeneratePin.css";

export default function CustomerGeneratePin() {
  const navigate = useNavigate();
  const accountNumber = localStorage.getItem("accountNumber");

  const [menuOpen, setMenuOpen] = useState(false);

  const [step, setStep] = useState(1);
  const [contact, setContact] = useState("");
  const [otp, setOtp] = useState("");

  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState("");

  const [transactionPin, setTransactionPin] = useState(null);
  const [pinVisible, setPinVisible] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(10);

  const timerRef = useRef(null);

  useEffect(() => {
    if (!accountNumber) navigate("/");
    return () => clearInterval(timerRef.current);
  }, [accountNumber, navigate]);

  const requestOtp = async () => {
    setMsg("");

    if (!contact.trim()) {
      setMsg("Please enter your registered email or phone number.");
      return;
    }

    setLoading(true);
    try {
      const res = await api.post("/auth/customer/transaction-pin/request", {
        accountNumber,
        contact: contact.trim(),
      });

      if (!res.data.success) {
        setMsg(res.data.message || "Failed to send OTP.");
        return;
      }

      setStep(2);
      setMsg("OTP sent to your registered contact.");
    } catch {
      setMsg("Server error while sending OTP.");
    } finally {
      setLoading(false);
    }
  };

  const verifyOtp = async () => {
    setMsg("");

    if (!otp.trim()) {
      setMsg("Please enter the OTP.");
      return;
    }

    setLoading(true);
    try {
      const res = await api.post("/auth/customer/transaction-pin/verify", {
        accountNumber,
        otp: otp.trim(),
      });

      if (!res.data.success) {
        setMsg(res.data.message || "OTP invalid or expired.");
        return;
      }

      const pin = res.data.transactionPin;
      if (!pin) {
        setMsg("PIN not generated. Contact support.");
        return;
      }

      setTransactionPin(pin);
      setPinVisible(true);
      setStep(3);

      setSecondsLeft(10);
      timerRef.current = setInterval(() => {
        setSecondsLeft((s) => {
          if (s <= 1) {
            clearInterval(timerRef.current);
            setPinVisible(false);

                  
            // ⭐ Auto-redirect after PIN hides
            navigate("/customer");
            
            return 0;
          }
          return s - 1;
        });
      }, 1000);

      setMsg("PIN generated. Visible for 10 seconds.");
    } catch {
      setMsg("Server error verifying OTP.");
    } finally {
      setLoading(false);
    }
  };

  const resendOtp = async () => {
    setMsg("");
    setLoading(true);

    try {
      const res = await api.post("/auth/customer/transaction-pin/request", {
        accountNumber,
        contact: contact.trim(),
      });

      if (!res.data.success) {
        setMsg(res.data.message || "Failed to resend OTP.");
        return;
      }

      setMsg("OTP resent.");
    } catch {
      setMsg("Server error resending OTP.");
    } finally {
      setLoading(false);
    }
  };

  const cancelAndBack = () => {
    clearInterval(timerRef.current);
    navigate("/customer");
  };

  useEffect(() => {
  document.title = "Generate Transaction Pin | AstroNova Bank";
}, []);

  return (
    <div className="pin-wrapper" style={{
    backgroundImage: 'url("/galaxy-bg.png")',
    backgroundSize: "cover",
    backgroundPosition: "center",
    backgroundRepeat: "no-repeat",
  }}>

      {/* ⭐ Global Header */}
      <Header />

      {/* ⭐ Menu Button */}
      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>

      {/* Sidebar */}
      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      {/* ⭐ Subheader */}
      <div className="pin-subheader">
        <h2 className="pin-title">Generate Transaction PIN</h2>

        <button className="pin-back-btn" onClick={() => navigate("/customer")}>
          Back
        </button>
      </div>

      {/* ⭐ Main Card */}
      <div className="pin-card">
        <h2 className="pin-card-title">Secure PIN Generation</h2>

        {msg && <p className="pin-msg">{msg}</p>}

        {/* STEP 1 */}
        {step === 1 && (
          <>
            <p className="pin-text">
              Enter your registered email or phone for account:
              <span className="pin-accent"> {accountNumber}</span>
            </p>

            <label className="pin-label">Email or Phone</label>
            <input
              className="pin-input"
              value={contact}
              onChange={(e) => setContact(e.target.value)}
            />

            <div className="pin-btn-row">
              <button className="pin-btn pin-cancel" onClick={cancelAndBack}>
                Cancel
              </button>
              <button
                onClick={requestOtp}
                disabled={loading}
                className="pin-btn pin-submit"
              >
                {loading ? "Sending..." : "Send OTP"}
              </button>

            
            </div>
          </>
        )}

        {/* STEP 2 */}
        {step === 2 && (
          <>
            <p className="pin-text">Enter the OTP sent to your contact.</p>

            <label className="pin-label">OTP</label>
            <input
              className="pin-input"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
            />

            <div className="pin-btn-row">
              <button
                onClick={verifyOtp}
                disabled={loading}
                className="pin-btn pin-submit"
              >
                {loading ? "Verifying..." : "Verify"}
              </button>

              <button
                onClick={resendOtp}
                disabled={loading}
                className="pin-btn pin-resend"
              >
                Resend OTP
              </button>

              <button className="pin-btn pin-cancel" onClick={cancelAndBack}>
                Cancel
              </button>
            </div>
          </>
        )}

        {/* STEP 3 */}
        {step === 3 && (
          <>
            <p className="pin-text">
              Your new PIN is shown below. It disappears automatically in:
              <span className="pin-accent"> {secondsLeft}s</span>
            </p>

            <div className="pin-display">
              {pinVisible ? transactionPin : "••••"}
            </div>

            <div className="pin-btn-row">
              <button
                className="pin-btn pin-copy"
                disabled={!pinVisible}
                onClick={() =>
                  navigator.clipboard
                    .writeText(transactionPin)
                    .then(() => alert("PIN copied"))
                }
              >
                Copy PIN
              </button>

              <button className="pin-btn pin-cancel" onClick={cancelAndBack}>
                Done
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
