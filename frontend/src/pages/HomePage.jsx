// src/pages/HomePage.jsx
import React from "react";
import { useNavigate } from "react-router-dom";
import "./HomePage.css";
import logo from "../assets/logo.png";

export default function HomePage() {
  const navigate = useNavigate();

  return (
    <div
      className="homepage-bg"
      style={{
        backgroundImage: `url(/galaxy-bg.png)`
      }}
    >
      <div className="nebula-layer"></div>
      <div className="stars-layer stars1"></div>
      <div className="stars-layer stars2"></div>

      <div className="homepage-content">
        <h1 className="homepage-title">
          <img src={logo} alt="logo" className="homepage-logo" />
          AstroNova Bank Portal
        </h1>

        <p className="homepage-subtitle">Where Banking Meets the Universe</p>

        <div className="homepage-buttons">
          {/* ⭐ Passing role + source to lock form */}
          <button
            className="btn-open"
            onClick={() =>
              navigate("/create-account", {
                state: { role: "CUSTOMER", source: "public" }
              })
            }
          >
            Open New Account
          </button>

          <button className="btn-login" onClick={() => navigate("/login")}>
            Login
          </button>
        </div>
      </div>
    </div>
  );
}
