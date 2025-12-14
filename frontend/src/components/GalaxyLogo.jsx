import React from "react";

export default function GalaxyLogo({ size = 70 }) {
  return (
    <img
      src="/galaxy-icon.png"
      alt="Galaxy Icon"
      style={{
        width: size,
        height: size,
        filter: "drop-shadow(0 0 12px #a6d9ff)",
        marginBottom: "10px"
      }}
    />
  );
}
