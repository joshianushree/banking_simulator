// src/pages/AdminCreateAccount.jsx
import React, { useState, useEffect, useRef } from "react";
import {
  createCustomerAccount,
  createAdminAccount
} from "../services/accounts";
import { useNavigate, useLocation } from "react-router-dom";
import { Eye, EyeOff, Copy } from "lucide-react";

import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";

import { format } from "date-fns";
import Header from "../components/Header";
import "./AdminCreateAccount.css";

export default function AdminCreateAccount() {
  const navigate = useNavigate();
  const location = useLocation();

  // ⭐ ADDED: STATIC BRANCH MAP
  const BRANCHES = {
    Mumbai: "ASTN00MUM01",
    Bangalore: "ASTN00BLR02",
    Pune: "ASTN00PUN03",
    Hyderabad: "ASTN00HYD04"
  };

  // -------------------------------
  // Form State
  // -------------------------------
  const [form, setForm] = useState({
    holderName: "",
    email: "",
    phoneNumber: "",
    address: "",
    gender: "",
    role: "CUSTOMER",
    accountType: "SAVINGS",
    balance: "",
    pin: "",
    pinConfirm: "",
    password: "",
    passwordConfirm: "",
    dob: "",
    age: "",

    // ⭐ ADDED Branch fields
    branchName: "",
    ifscCode: "",

    // Govt ID (common)
    govtIdType: "",
    govtIdNumber: "",
    govtIdProof: null
  });

  const [touched, setTouched] = useState({});
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState("");

  const [generatedPin, setGeneratedPin] = useState(null);
  const [pinVisible, setPinVisible] = useState(false);
  const [pinTimer, setPinTimer] = useState(null);

  const [dobError, setDobError] = useState("");

  const [showPin, setShowPin] = useState(false);
  const [showPinConfirm, setShowPinConfirm] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);

  const timerRef = useRef(null);

  // -------------------------------
  // Required Fields (updated: branch + ifsc added)
  // -------------------------------
  const requiredFields = {
    COMMON: [
      "holderName",
      "email",
      "phoneNumber",
      "address",
      "gender",
      "dob",

      // ⭐ ADDED
      "branchName",
      "ifscCode",

      "govtIdType",
      "govtIdNumber",
      "govtIdProof"
    ],
    CUSTOMER: ["pin", "pinConfirm", "balance", "accountType"],
    ADMIN: ["password", "passwordConfirm"]
  };

  // -------------------------------
  // Govt ID Regex
  // -------------------------------
  const AADHAR_REGEX = /^\d{12}$/;
  const PAN_REGEX = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
  const VOTER_REGEX = /^[A-Z]{3}[0-9]{7}$/;
  const DL_REGEX =
    /^([A-Z]{2}[0-9]{2}[0-9]{4}[0-9]{7}|[A-Z]{2}-[0-9]{2}\/[0-9]{4}\/[0-9]{7})$/;

  // -------------------------------
  // Date Helpers
  // -------------------------------
  const dateToIso = (dateObj) =>
    dateObj ? format(dateObj, "yyyy-MM-dd") : "";

  const isoToDate = (iso) => (!iso ? null : new Date(iso));

  const pinField = (label, name, show, setShow, value) => (
  <div className="gl-field">
    <label className="gl-label">{label}</label>
    <div className="gl-input-wrap">
      <input
        type={show ? "text" : "password"}
        name={name}
        value={value}
        maxLength={4}
        pattern="\d{4}"
        onChange={handleChange}
        onBlur={() => handleBlur(name)}
        className={inputClass(name)}
      />
      <span className="gl-eye" onClick={() => setShow((s) => !s)}>
        {show ? <EyeOff size={20} /> : <Eye size={20} />}
      </span>
    </div>
  </div>
);


  const calculateAge = (dobString) => {
    if (!dobString) return "";
    const today = new Date();
    const dob = new Date(dobString);
    if (isNaN(dob)) return "";

    let age = today.getFullYear() - dob.getFullYear();
    if (
      today.getMonth() < dob.getMonth() ||
      (today.getMonth() === dob.getMonth() &&
        today.getDate() < dob.getDate())
    ) {
      age--;
    }
    return age;
  };

  // -------------------------------
  // When opened from public -> force CUSTOMER
  // -------------------------------
  useEffect(() => {
    if (location.state?.source === "public") {
      setForm((f) => ({ ...f, role: "CUSTOMER" }));
    }
  }, [location.state]);

  // -------------------------------
  // Validation Helpers
  // -------------------------------
  const validateField = (name, value) => {
    let v = value ?? "";
    if (typeof v === "string") v = v.trim();

    // Common required
    if (requiredFields.COMMON.includes(name)) {
      if (name === "govtIdProof") {
        if (!form.govtIdProof) return "Government ID proof is required.";
      } else if (!v) {
        return "This field is required.";
      }
    }

    switch (name) {
      case "branchName":
        if (!v) return "Branch is required.";
        return "";

      case "ifscCode":
        if (!v) return "IFSC code is required.";
        return "";

      default:
        break;
    }

    switch (name) {
      
      case "password":
        if (!v) return "Password is required.";
        if (v.length < 6) return "Password must be at least 6 characters.";
        if (!/[A-Za-z]/.test(v)) return "Password must contain at least one letter.";
        if (!/\d/.test(v)) return "Password must contain at least one number.";
        return "";

      case "passwordConfirm":
        if (!v) return "Please confirm your password.";
        if (v !== form.password) return "Passwords do not match.";
        return "";
    }


    // rest unchanged ↓
    switch (name) {
      case "holderName":
        if (v && v.length < 3) return "Name must be at least 3 characters.";
        return "";

      case "email":
        if (v) {
          const mailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
          if (!mailRegex.test(v)) return "Invalid email format.";
        }
        return "";

      case "phoneNumber":
        if (v && !/^\d{10}$/.test(v)) return "Phone number must be 10 digits.";
        return "";

      case "dob": {
        if (!v) return "This field is required.";
        const d = new Date(v);
        if (isNaN(d)) return "Invalid date.";
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        if (d > today) return "DOB cannot be in the future.";
        return "";
      }

      case "govtIdType":
        if (
          v &&
          !["Aadhar", "PAN", "Voter ID", "Driving License"].includes(v)
        )
          return "Invalid Govt ID type.";
        return "";

      case "govtIdNumber": {
        const type = form.govtIdType;
        const num = (v || "").toUpperCase();
        if (!type) return "Select Govt ID type first.";

        if (type === "Aadhar" && !AADHAR_REGEX.test(num))
          return "Aadhar must be 12 digits.";

        if (type === "PAN" && !PAN_REGEX.test(num)) return "Invalid PAN format.";

        if (type === "Voter ID" && !VOTER_REGEX.test(num))
          return "Invalid Voter ID format.";

        if (type === "Driving License" && !DL_REGEX.test(num))
          return "Invalid Driving License format.";

        return "";
      }

      default:
        return "";
    }
  };

  const validateGovtId = () => {
    const type = form.govtIdType;
    const num = (form.govtIdNumber || "").trim().toUpperCase();

    if (!type) return "Select Govt ID type.";
    if (!num) return "Government ID number required.";

    if (type === "Aadhar" && !AADHAR_REGEX.test(num))
      return "Aadhar must be 12 digits.";

    if (type === "PAN" && !PAN_REGEX.test(num)) return "Invalid PAN format.";

    if (type === "Voter ID" && !VOTER_REGEX.test(num))
      return "Invalid Voter ID format.";

    if (type === "Driving License" && !DL_REGEX.test(num))
      return "Invalid Driving License format.";

    return "";
  };

  // -------------------------------
  // Form Validation
  // -------------------------------
  const isFormValid = () => {
    if (dobError) return false;

    for (let f of requiredFields.COMMON) {
      if (!form[f]) return false;
      if (errors[f]) return false;
    }

    if (form.role === "CUSTOMER") {
      for (let f of requiredFields.CUSTOMER) {
        if (!form[f]) return false;
      }
      if (form.pin !== form.pinConfirm) return false;
    }

    if (form.role === "ADMIN") {
      for (let f of requiredFields.ADMIN) {
        if (!form[f]) return false;
      }
      if (form.password !== form.passwordConfirm) return false;
    }

    if (validateGovtId()) return false;

    return true;
  };

  // -------------------------------
  // Handlers
  // -------------------------------
  const handleChange = (e) => {
    const { name, value } = e.target;

    // prevent role change from public form
    if (location.state?.source === "public" && name === "role") return;

    // ⭐ auto-set IFSC based on branch
    if (name === "branchName") {
      setForm((f) => ({
        ...f,
        branchName: value,
        ifscCode: BRANCHES[value] || ""
      }));
    } else {
      setForm((f) => ({ ...f, [name]: value }));
    }

    setTouched((t) => ({ ...t, [name]: true }));
    setErrors((prev) => ({
      ...prev,
      [name]: validateField(name, value)
    }));
  };

  const handleFile = (e) => {
    const file = e.target.files[0];
    setForm((f) => ({ ...f, govtIdProof: file }));
    setTouched((t) => ({ ...t, govtIdProof: true }));
    setErrors((prev) => ({
      ...prev,
      govtIdProof: validateField("govtIdProof", file)
    }));
  };

  const handleBlur = (fieldName) => {
    const value =
      fieldName === "govtIdProof" ? form.govtIdProof : form[fieldName];
    const err = validateField(fieldName, value);

    setTouched((t) => ({ ...t, [fieldName]: true }));
    setErrors((prev) => ({ ...prev, [fieldName]: err }));

    if (fieldName === "dob" && !err) {
      const age = calculateAge(form.dob);
      setForm((f) => ({ ...f, age }));
      if (age < 18) {
        setForm((f) => ({ ...f, accountType: "STUDENT" }));
      }
    }
  };

  const inputClass = (field) =>
    `gl-input ${touched[field] && errors[field] ? "gl-input-error" : ""}`;

  const passwordField = (label, name, show, setShow, value) => (
    <div className="gl-field">
      <label className="gl-label">{label}</label>
      <div className="gl-input-wrap" autoComplete="off">
        <input
          type={show ? "text" : "password"}
          name={name}
          value={value}
          onChange={handleChange}
          onBlur={() => handleBlur(name)}
          className={inputClass(name)}
          autoComplete="new-password"
          onFocus={(e) => e.target.removeAttribute("readonly")}
          readOnly
        />
        <span className="gl-eye" onClick={() => setShow((s) => !s)}>
          {show ? <EyeOff size={20} /> : <Eye size={20} />}
        </span>
      </div>
    </div>
  );

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text).then(() => {
      setMessage("Copied Transaction Pin!");
      setTimeout(() => setMessage(""), 2000);
    });
  };

  // cleanup
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, []);

  // -------------------------------
  // Submit
  // -------------------------------
  const submit = async (e) => {
    e.preventDefault();
    setMessage("");

    let finalErrors = { ...errors };

    for (const f of requiredFields.COMMON) {
      finalErrors[f] = validateField(
        f,
        f === "govtIdProof" ? form.govtIdProof : form[f]
      );
    }

    if (form.role === "CUSTOMER") {
      for (const f of requiredFields.CUSTOMER) {
        finalErrors[f] = validateField(f, form[f]);
      }
    } else {
      for (const f of requiredFields.ADMIN) {
        finalErrors[f] = validateField(f, form[f]);
      }
    }

    const govtErr = validateGovtId();
    if (govtErr) finalErrors.govtIdNumber = govtErr;

    setErrors(finalErrors);

    const newTouched = { ...touched };
    Object.keys(finalErrors).forEach((k) => (newTouched[k] = true));
    setTouched(newTouched);

    if (!isFormValid()) {
      setMessage("Please fix validation errors before submitting.");
      return;
    }

    try {
      let res;

      if (form.role === "ADMIN") {
        // ⭐ ADDED branch+IFSC to admin account
        res = await createAdminAccount({
          holderName: form.holderName,
          email: form.email,
          phoneNumber: form.phoneNumber,
          password: form.password,
          passwordConfirm: form.passwordConfirm,
          dob: form.dob,

          branchName: form.branchName,
          ifscCode: form.ifscCode,

          govtIdType: form.govtIdType,
          govtIdNumber: form.govtIdNumber
        });
      } else {
        // CUSTOMER — multipart/form-data
        const fd = new FormData();
        fd.append("holderName", form.holderName);
        fd.append("email", form.email);
        fd.append("phoneNumber", form.phoneNumber);
        fd.append("address", form.address);
        fd.append("gender", form.gender);
        fd.append("accountType", form.age < 18 ? "STUDENT" : form.accountType);
        fd.append("balance", form.balance);
        fd.append("pin", form.pin);
        fd.append("pinConfirm", form.pinConfirm);
        fd.append("dob", form.dob);
        fd.append("age", form.age);

        // ⭐ ADDED
        fd.append("branchName", form.branchName);
        fd.append("ifscCode", form.ifscCode);

        fd.append("govtIdType", form.govtIdType);
        fd.append("govtIdNumber", form.govtIdNumber);
        if (form.govtIdProof) fd.append("govtIdProof", form.govtIdProof);

        res = await createCustomerAccount(fd);
      }

      if (res?.data?.success) {
        if (form.role === "CUSTOMER" && res.data.generatedPin) {
          setGeneratedPin(res.data.generatedPin);
          setPinVisible(true);
          setPinTimer(10);

          if (timerRef.current) {
            clearInterval(timerRef.current);
            timerRef.current = null;
          }

          timerRef.current = setInterval(() => {
            setPinTimer((prev) => {
              if (prev <= 1) {
                clearInterval(timerRef.current);
                timerRef.current = null;
                setPinVisible(false);
                navigate(location.state?.source === "public" ? "/" : "/admin");
                return 0;
              }
              return prev - 1;
            });
          }, 1000);
        }

        if (form.role === "ADMIN") {
          setTimeout(() => {
            navigate(location.state?.source === "public" ? "/" : "/admin");
          }, 3000);
        }

        setMessage("Created successfully!");
      } else {
        setMessage(res?.data?.message || "Failed to create account.");
      }
    } catch (err) {
      console.error(err);
      setMessage("Server error. Try again.");
    }
  };

  // -------------------------------
  // Render
  // -------------------------------
  useEffect(() => {
  document.title = "Create Account | AstroNova Bank";
}, []);

  return (
    <div
      className="create-wrapper"
      style={{
        backgroundImage: `url(${process.env.PUBLIC_URL}/galaxy-bg.png)`
      }}
    >
      <Header />

      <form className="create-card" onSubmit={submit}>
        <div className="create-title-row">
          <h2 className="create-title">Create Account</h2>

          <button
            type="button"
            className="gl-btn-back"
            onClick={() =>
              navigate(location.state?.source === "public" ? "/" : "/admin")
            }
          >
            Back
          </button>
        </div>

        {/* Holder Name */}
        <label className="gl-label">Holder Name</label>
        <input
          name="holderName"
          value={form.holderName}
          onChange={handleChange}
          onBlur={() => handleBlur("holderName")}
          className={inputClass("holderName")}
        />
        {touched.holderName && errors.holderName && (
          <p className="gl-error">{errors.holderName}</p>
        )}

        {/* Email */}
        <label className="gl-label">Email</label>
        <input
          name="email"
          value={form.email}
          onChange={handleChange}
          onBlur={() => handleBlur("email")}
          className={inputClass("email")}
        />
        {touched.email && errors.email && (
          <p className="gl-error">{errors.email}</p>
        )}

        {/* Phone */}
        <label className="gl-label">Phone Number</label>
        <input
          name="phoneNumber"
          value={form.phoneNumber}
          onChange={handleChange}
          onBlur={() => handleBlur("phoneNumber")}
          className={inputClass("phoneNumber")}
        />
        {touched.phoneNumber && errors.phoneNumber && (
          <p className="gl-error">{errors.phoneNumber}</p>
        )}

        {/* Address */}
        <label className="gl-label">Address</label>
        <input
          name="address"
          value={form.address}
          onChange={handleChange}
          onBlur={() => handleBlur("address")}
          className={inputClass("address")}
        />
        {touched.address && errors.address && (
          <p className="gl-error">{errors.address}</p>
        )}

        {/* Gender */}
        <label className="gl-label">Gender</label>
        <select
          name="gender"
          value={form.gender}
          onChange={handleChange}
          onBlur={() => handleBlur("gender")}
          className={inputClass("gender")}
        >
          <option value="">Select gender</option>
          <option value="MALE">Male</option>
          <option value="FEMALE">Female</option>
          <option value="OTHER">Other</option>
        </select>
        {touched.gender && errors.gender && (
          <p className="gl-error">{errors.gender}</p>
        )}

        {/* ⭐ ADDED Branch */}
        <label className="gl-label">Branch</label>
        <select
          name="branchName"
          value={form.branchName}
          onChange={handleChange}
          onBlur={() => handleBlur("branchName")}
          className={inputClass("branchName")}
        >
          <option value="">Select Branch</option>
          {Object.keys(BRANCHES).map((b) => (
            <option key={b} value={b}>{b}</option>
          ))}
        </select>
        {touched.branchName && errors.branchName && (
          <p className="gl-error">{errors.branchName}</p>
        )}

        {/* ⭐ ADDED IFSC */}
        <label className="gl-label">IFSC Code</label>
        <input
          name="ifscCode"
          value={form.ifscCode}
          readOnly
          className="gl-input readonly"
        />
        {touched.ifscCode && errors.ifscCode && (
          <p className="gl-error">{errors.ifscCode}</p>
        )}

        {/* Role */}
        {location.state?.source !== "public" && (
          <>
            <label className="gl-label">Role</label>
            <select
              name="role"
              value={form.role}
              onChange={handleChange}
              onBlur={() => handleBlur("role")}
              className={inputClass("role")}
            >
              <option value="CUSTOMER">Customer</option>
              <option value="ADMIN">Admin</option>
            </select>
            {touched.role && errors.role && (
              <p className="gl-error">{errors.role}</p>
            )}
          </>
        )}

        {/* DOB + Age */}
        <div className="gl-row">
          <div className="gl-col">
            <label className="gl-label">Date of Birth</label>
            <DatePicker
              selected={form.dob ? isoToDate(form.dob) : null}
              onChange={(dateObj) => {
                if (!dateObj) {
                  setDobError("DOB cannot be empty.");
                  setErrors((prev) => ({ ...prev, dob: "DOB cannot be empty." }));
                  setTouched((t) => ({ ...t, dob: true }));
                  return;
                }

                const today = new Date();
                today.setHours(0, 0, 0, 0);

                if (dateObj > today) {
                  setDobError("DOB cannot be in the future.");
                  setErrors((prev) => ({
                    ...prev,
                    dob: "DOB cannot be in the future."
                  }));
                } else {
                  setDobError("");
                  const iso = dateToIso(dateObj);

                  const calculatedAge = calculateAge(iso);

                  setForm((f) => ({
                    ...f,
                    dob: iso,
                    age: calculatedAge,
                    accountType: calculatedAge < 18 ? "STUDENT" : f.accountType
                  }));

                  setErrors((prev) => ({
                    ...prev,
                    dob: validateField("dob", iso)
                  }));
                }

                setTouched((t) => ({ ...t, dob: true }));
              }}
              dateFormat="dd/MM/yyyy"
              maxDate={new Date()}
              showMonthDropdown
              showYearDropdown
              dropdownMode="select"
              className={inputClass("dob")}
            />

            {touched.dob && errors.dob && (
              <p className="gl-error">{errors.dob}</p>
            )}
          </div>

          <div className="gl-col">
            <label className="gl-label">Age</label>
            <input value={form.age} readOnly className="gl-input readonly" />
          </div>
        </div>

        {/* ADMIN PASSWORD */}
        {form.role === "ADMIN" && location.state?.source !== "public" && (
          <>
            {passwordField("Password", "password", showPassword, setShowPassword, form.password)}
            {touched.password && errors.password && (
              <p className="gl-error">{errors.password}</p>
            )}

            {passwordField("Confirm Password", "passwordConfirm", showPasswordConfirm, setShowPasswordConfirm, form.passwordConfirm)}
            {touched.passwordConfirm && errors.passwordConfirm && (
              <p className="gl-error">{errors.passwordConfirm}</p>
            )}
          </>
        )}

        {/* CUSTOMER PIN + BALANCE */}
        {form.role === "CUSTOMER" && (
          <>
            {/* Account Type */}
            <label className="gl-label">Account Type</label>
            <select
              name="accountType"
              value={form.accountType}
              onChange={handleChange}
              disabled={form.age < 18}
              onBlur={() => handleBlur("accountType")}
              className={inputClass("accountType")}
            >
              <option value="SAVINGS">Savings</option>
              <option value="CURRENT">Current</option>
              <option value="STUDENT">Student</option>
            </select>
            {touched.accountType && errors.accountType && (
              <p className="gl-error">{errors.accountType}</p>
            )}

            {/* Balance */}
            <label className="gl-label">Initial Balance</label>
            <input
              type="number"
              name="balance"
              value={form.balance}
              onChange={handleChange}
              onBlur={() => handleBlur("balance")}
              className={inputClass("balance")}
            />
            {touched.balance && errors.balance && (
              <p className="gl-error">{errors.balance}</p>
            )}

            {/* PIN */}
            {passwordField("PIN", "pin", showPin, setShowPin, form.pin)}
            {touched.pin && errors.pin && (
              <p className="gl-error">{errors.pin}</p>
            )}

            {passwordField(
              "Confirm PIN",
              "pinConfirm",
              showPinConfirm,
              setShowPinConfirm,
              form.pinConfirm
            )}
            {touched.pinConfirm && errors.pinConfirm && (
              <p className="gl-error">{errors.pinConfirm}</p>
            )}
          </>
        )}

        {/* Govt ID Type */}
        <label className="gl-label">Government ID Type</label>
        <select
          name="govtIdType"
          value={form.govtIdType}
          onChange={handleChange}
          onBlur={() => handleBlur("govtIdType")}
          className={inputClass("govtIdType")}
        >
          <option value="">Select ID</option>
          <option value="Aadhar">Aadhar</option>
          <option value="PAN">PAN</option>
          <option value="Voter ID">Voter ID</option>
          <option value="Driving License">Driving License</option>
        </select>
        {touched.govtIdType && errors.govtIdType && (
          <p className="gl-error">{errors.govtIdType}</p>
        )}

        {/* Govt ID Number */}
        <label className="gl-label">Government ID Number</label>
        <input
          name="govtIdNumber"
          value={form.govtIdNumber}
          onChange={handleChange}
          onBlur={() => handleBlur("govtIdNumber")}
          className={inputClass("govtIdNumber")}
        />
        {touched.govtIdNumber && errors.govtIdNumber && (
          <p className="gl-error">{errors.govtIdNumber}</p>
        )}

        {/* Govt ID Proof */}
        <label className="gl-label">Upload Govt ID Proof</label>
        <input
          type="file"
          accept=".jpg,.png,.pdf"
          onChange={handleFile}
          onBlur={() => handleBlur("govtIdProof")}
          className={inputClass("govtIdProof")}
        />
        {touched.govtIdProof && errors.govtIdProof && (
          <p className="gl-error">{errors.govtIdProof}</p>
        )}

        {/* STATUS MESSAGE */}
        {message && <p className="gl-status">{message}</p>}

        {/* Transaction PIN */}
        {generatedPin && pinVisible && (
          <div className="gl-pinbox">
            <strong>Transaction PIN:</strong>
            <span className="gl-pin">{generatedPin}</span>

            <button
              className="gl-copy"
              type="button"
              onClick={() => copyToClipboard(generatedPin)}
            >
              <Copy size={16} />
            </button>

            <p className="gl-hint">Auto-hide in {pinTimer} seconds</p>
          </div>
        )}

        {/* ACTIONS */}
        <div className="gl-actions">
          <button
            type="button"
            onClick={() =>
              navigate(location.state?.source === "public" ? "/" : "/admin")
            }
            className="gl-btn-cancel"
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={!isFormValid()}
            className={!isFormValid() ? "gl-btn disabled" : "gl-btn"}
          >
            Create
          </button>
        </div>
      </form>
    </div>
  );
}
