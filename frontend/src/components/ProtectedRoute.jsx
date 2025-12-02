import { Navigate } from "react-router-dom";

export default function ProtectedRoute({ role, children }) {
  const storedRole = localStorage.getItem("role");

  // No role? Not allowed
  if (!storedRole) {
    return <Navigate to="/" replace />;
  }

  // Prevent role mismatch
  if (storedRole.toLowerCase() !== role.toLowerCase()) {
    return <Navigate to="/" replace />;
  }

  return children;
}
