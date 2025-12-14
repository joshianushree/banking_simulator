// src/services/reports.js

const REPORT_BASE = "http://localhost:8080/api/reports";

/**
 * Generic browser-based file downloader
 */
function triggerDownload(url, fallbackFilename = "download.pdf") {
  try {
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", fallbackFilename);
    document.body.appendChild(link);
    link.click();
    link.remove();
  } catch (err) {
    console.error("Download trigger error:", err);
  }
}

/**
 * DOWNLOAD: ACCOUNTS REPORT (PDF)
 */
export const downloadAccountsReport = () => {
  const username = localStorage.getItem("username");
  const url = `${REPORT_BASE}/accounts?username=${encodeURIComponent(username)}`;
  triggerDownload(url, "Accounts_Report.pdf");
};

/**
 * DOWNLOAD: TRANSACTIONS REPORT (PDF)
 */
export const downloadTransactionsReport = () => {
  const username = localStorage.getItem("username");
  const url = `${REPORT_BASE}/transactions?username=${encodeURIComponent(username)}`;
  triggerDownload(url, "Transactions_Report.pdf");
};

/**
 * DOWNLOAD: MINI STATEMENT (PDF)
 */
export const downloadMiniStatement = (accNo) => {
  if (!accNo || !accNo.trim()) {
    alert("Account number is required.");
    return;
  }

  const url = `${REPORT_BASE}/ministatement/${accNo}`;
  triggerDownload(url, `MiniStatement_${accNo}.pdf`);
};

/**
 * DOWNLOAD ACCOUNTS REPORT BY BRANCH
 */
export const downloadAccountsReportByBranch = (branch) => {
  const username = localStorage.getItem("username") || "";
  const url = `${REPORT_BASE}/accounts?username=${encodeURIComponent(
    username
  )}&branch=${encodeURIComponent(branch)}`;

  triggerDownload(url, `Accounts_Report_${branch}.pdf`);
};

/**
 * DOWNLOAD TRANSACTIONS REPORT BY BRANCH
 */
export const downloadTransactionsReportByBranch = (branch) => {
  const username = localStorage.getItem("username") || "";
  const url = `${REPORT_BASE}/transactions?username=${encodeURIComponent(
    username
  )}&branch=${encodeURIComponent(branch)}`;

  triggerDownload(url, `Transactions_Report_${branch}.pdf`);
};
