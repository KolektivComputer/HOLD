import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { HoldApp } from "@/components/hold/hold-app";
import { HoldProvider } from "@/lib/hold/store";
import "./styles.css";

const root = document.getElementById("root");
if (!root) throw new Error("Missing root");

createRoot(root).render(
  <StrictMode>
    <HoldProvider>
      <HoldApp />
    </HoldProvider>
  </StrictMode>,
);
