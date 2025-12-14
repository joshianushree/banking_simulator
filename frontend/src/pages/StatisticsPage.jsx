// src/pages/StatisticsPage.jsx
import React, { useEffect, useState } from "react";
import SidebarMenu from "../components/SidebarMenu";
import Header from "../components/Header";
import "./StatisticsPage.css";

import { Bar, Pie, Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  BarElement,
  CategoryScale,
  LinearScale,
  ArcElement,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(
  BarElement,
  CategoryScale,
  LinearScale,
  ArcElement,
  PointElement,
  LineElement,
  Tooltip,
  Legend
);

export default function StatisticsPage() {
  const accountNumber = localStorage.getItem("accountNumber");

  const [menuOpen, setMenuOpen] = useState(false);
  const [tx, setTx] = useState([]);
  const [loading, setLoading] = useState(true);

  const [month, setMonth] = useState(new Date().getMonth() + 1);
  const [year, setYear] = useState(new Date().getFullYear());

  const goBack = () => {
    window.location.href = "/customer";
  };

  // FETCH DATA
  const loadData = () => {
    setLoading(true);

    const lastDay = new Date(year, month, 0).getDate();

    const start = `${year}-${String(month).padStart(2, "0")}-01`;
    const end = `${year}-${String(month).padStart(2, "0")}-${String(lastDay).padStart(2, "0")}`;

    fetch(
      `http://localhost:8080/api/transactions/filter?fromDate=${start}&toDate=${end}&account=${accountNumber}`
    )
      .then((res) => res.json())
      .then((data) => {
        setTx(data || []);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  };
useEffect(() => {
  document.title = "Statistics Page | AstroNova Bank";
}, []);
  useEffect(loadData, [accountNumber]);

  if (loading)
    return <div className="neon-loading">Loading statistics...</div>;

  // PROCESSING
  const withdrawals = [];
  const deposits = [];
  const dateLabels = [];
  const categories = {};

  const lastDay = new Date(year, month, 0).getDate();

  for (let i = 1; i <= lastDay; i++) {
    dateLabels.push(`${String(i).padStart(2, "0")}/${String(month).padStart(2, "0")}`);
    withdrawals.push(0);
    deposits.push(0);
  }

  tx.forEach((t) => {
    const date = new Date(t.timestamp || t.createdAt);
    const day = date.getDate() - 1;
    const amt = Number(t.amount);

    if (
      t.txType === "DEPOSIT" ||
      (t.txType === "TRANSFER" && t.toAccount === accountNumber)
    ) {
      deposits[day] += amt;
    }

    if (
      t.txType === "WITHDRAW" ||
      (t.txType === "TRANSFER" && t.fromAccount === accountNumber)
    ) {
      withdrawals[day] += amt;

      const cat = t.category || "General";
      if (!categories[cat]) categories[cat] = 0;
      categories[cat] += amt;
    }
  });

  const totalDeposits = deposits.reduce((a, b) => a + b, 0);
  const totalWithdrawals = withdrawals.reduce((a, b) => a + b, 0);
  const peakSpend = Math.max(...withdrawals);
  const monthlyNet = totalDeposits - totalWithdrawals;

  // ------------------------------------------------------
  //   SHARED OPTIONS (date overflow fix here)
  // ------------------------------------------------------
  const xAxisFix = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        ticks: {
          maxRotation: 60,
          minRotation: 45,
          autoSkip: true,
          autoSkipPadding: 12,
          font: { size: 14 },
        },
      },
      y: {
        ticks: { font: { size: 14 } },
      },
    },
    layout: {
      padding: { bottom: 30 },
    }
  };



  return (
    <div
        className="stats-wrapper"
        style={{
          backgroundImage: 'url("/galaxy-bg.png")',
          backgroundSize: "cover",
          backgroundPosition: "center",
          backgroundRepeat: "no-repeat",
        }}
      >
      <Header />

      {/* HAMBURGER */}
      <button className="menu-btn-global" onClick={() => setMenuOpen(true)}>
        ☰
      </button>

      <SidebarMenu isOpen={menuOpen} onClose={() => setMenuOpen(false)} />

      {/* SUBHEADER */}
      <div className="stats-subheader">
        <h2 className="stats-title">Financial Statistics</h2>
        <button className="stats-back-btn" onClick={goBack}>Back</button>
      </div>

      {/* MAIN CONTENT */}
      <div className="stats-content">

        {/* FILTER BAR */}
        <div className="stats-filter-bar">
          <select value={month} onChange={(e) => setMonth(e.target.value)} className="stats-select">
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i} value={i + 1}>
                {new Date(0, i).toLocaleString("default", { month: "long" })}
              </option>
            ))}
          </select>

          <select value={year} onChange={(e) => setYear(e.target.value)} className="stats-select">
            {Array.from({ length: 10 }, (_, i) => {
              const y = new Date().getFullYear() - i;
              return (
                <option key={y} value={y}>
                  {y}
                </option>
              );
            })}
          </select>

          <button onClick={loadData} className="stats-apply-btn">
            Apply
          </button>
        </div>

        {/* SUMMARY CARDS */}
        <div className="stats-summary-grid">
          <SummaryCard title="Total Deposits" value={`₹${totalDeposits}`} />
          <SummaryCard title="Total Withdrawals" value={`₹${totalWithdrawals}`} />
          <SummaryCard title="Highest Spend Day" value={`₹${peakSpend}`} />
          <SummaryCard title="Monthly Net" value={`₹${monthlyNet}`} />
        </div>

        {/* CHART GRID */}
        <div className="stats-chart-grid">

          {/* PIE CHART */}
          <div className="stats-chart-block chart-fixed">
            <h2>Spending by Category</h2>
            <Pie
              data={{
                labels: Object.keys(categories),
                datasets: [
                  {
                    data: Object.values(categories),
                    backgroundColor: [
                      "#FF6384", "#36A2EB", "#FFCE56",
                      "#4CAF50", "#9C27B0", "#FFC107",
                      "#795548", "#00BCD4",
                    ],
                  },
                ],
              }}
              options={{ responsive: true, maintainAspectRatio: false }}
            />
          </div>

          {/* LINE CHART */}
          <div className="stats-chart-block chart-fixed">
            <h2>Daily Spending Trend</h2>
            <Line
              data={{
                labels: dateLabels,
                datasets: [
                  {
                    label: "Spending",
                    data: withdrawals,
                    borderColor: "#00eaff",
                    pointRadius: 3,
                    tension: 0.3,
                  },
                ],
              }}
              options={xAxisFix}
            />
          </div>
        </div>

        {/* BAR CHART */}
        <div className="stats-chart-block chart-fixed">
          <h2>Daily Income vs Expenditure</h2>
          <Bar
            data={{
              labels: dateLabels,
              datasets: [
                { label: "Income", data: deposits, backgroundColor: "#00c671" },
                { label: "Expenditure", data: withdrawals, backgroundColor: "#ff4f4f" },
              ],
            }}
            options={xAxisFix}
          />
        </div>
      </div>
    </div>
  );
}

function SummaryCard({ title, value }) {
  return (
    <div className="stats-summary-card">
      <h3>{title}</h3>
      <p>{value}</p>
    </div>
  );
}
