// src/pages/Login.jsx
import React, { useEffect, useState } from "react";
import { adminLogin, customerLogin } from "../services/auth";
import OtpModal from "../components/OtpModal";
import ForgotModal from "../components/ForgotModal";

import Header from "../components/Header";
import "./Login.css";

import { Eye, EyeOff } from "lucide-react";

export default function Login() {
  const [role, setRole] = useState("customer");

  const [identifier, setIdentifier] = useState("");
  const [pin, setPin] = useState("");
  const [password, setPassword] = useState("");

  const [showPwd, setShowPwd] = useState(false);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const [otpOpen, setOtpOpen] = useState(false);
  const [forgotOpen, setForgotOpen] = useState(false);

  const submitLogin = async (e) => {
    e.preventDefault();
    setMessage("");

    if (role === "admin" && (!identifier || !password)) {
      setMessage("Enter username and password");
      return;
    }
    if (role === "customer" && (!identifier || !pin)) {
      setMessage("Enter account number and PIN");
      return;
    }

    setLoading(true);

    try {
      let res =
        role === "admin"
          ? await adminLogin(identifier, password)
          : await customerLogin(identifier, pin);

      const data = res.data;
      console.log("LOGIN RESPONSE:", data);

      if (data.locked) {
        setMessage("Account locked due to 3 failed attempts.");
        setLoading(false);
        return;
      }

      if (!data.success && role === "customer") {
        let triesLeft =
          data.failedAttempts != null ? 3 - data.failedAttempts : null;

        if (triesLeft !== null && triesLeft > 0) {
          setMessage(data.message + ` ${triesLeft} tries left.`);
        } else if (triesLeft === 0) {
          setMessage("Account locked after 3 failed attempts.");
        } else {
          setMessage(data.message);
        }

        setLoading(false);
        return;
      }

      if (!data.success) {
        setMessage(data.message);
        setLoading(false);
        return;
      }

      localStorage.setItem("pendingLoginRole", role);
      localStorage.setItem("identifier", identifier);

      setOtpOpen(true);
      setLoading(false);
    } catch (err) {
      console.error("Login error:", err);
      setMessage("Server error");
      setLoading(false);
    }
  };

  const pwdField = (
    <div className="input-wrapper">
      <input
        type={showPwd ? "text" : "password"}
        className="text-input"
        value={role === "admin" ? password : pin}
        onChange={(e) =>
          role === "admin"
            ? setPassword(e.target.value)
            : setPin(e.target.value)
        }
      />

      <span className="toggle-eye" onClick={() => setShowPwd(!showPwd)}>
        {showPwd ? <EyeOff size={18} /> : <Eye size={18} />}
      </span>
    </div>
  );

useEffect(() => {
  document.title = "Login Page | AstroNova Bank";
}, []);


  return (
    <div
      className="page-wrapper"
      style={{
        backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`
      }}
    >
      <Header />

      <div className="content-container login-box">
        <h2 className="login-title">Login</h2>

        <label className="label">Login As</label>
        <select
          className="text-input"
          value={role}
          onChange={(e) => {
            setRole(e.target.value);
            setMessage("");
            setIdentifier("");
            setPin("");
            setPassword("");
          }}
        >
          <option value="customer">Customer</option>
          <option value="admin">Admin</option>
        </select>

        <form onSubmit={submitLogin}>
          <label className="label">
            {role === "admin" ? "Username" : "Account Number"}
          </label>
          <input
            className="text-input"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
          />

          <label className="label">
            {role === "admin" ? "Password" : "PIN"}
          </label>
          {pwdField}

          {message && <p className="error-msg">{message}</p>}

          <button type="submit" className="btn-submit" disabled={loading}>
            {loading ? "Please wait..." : "Login"}
          </button>

          <p className="forgot-link" onClick={() => setForgotOpen(true)}>
            Forgot Password / PIN?
          </p>
        </form>
      </div>

      <OtpModal
        open={otpOpen}
        onClose={() => setOtpOpen(false)}
        identifier={identifier}
        role={role}
      />

      <ForgotModal
        open={forgotOpen}
        onClose={() => setForgotOpen(false)}
        role={role}
      />
    </div>
  );
}
