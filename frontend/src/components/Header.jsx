import React from "react";
import logo from "../assets/logo.png";
import "./Header.css";

export default function Header() {
  return (
    <header className="global-header">
      <div className="header-content">
        <img src={logo} alt="Bank Logo" className="header-logo" />
        <h1 className="header-title">AstroNova Bank Portal</h1>
      </div>
    </header>
  );
}
