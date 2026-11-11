import { StrictMode, useEffect, useRef } from "react";
import { createRoot } from "react-dom/client";
import { HoldApp } from "@/components/hold/hold-app";
import { phraseEntry } from "@/lib/hold/spots";
import { HoldProvider, useHold } from "@/lib/hold/store";
import "./styles.css";

const root = document.getElementById("root");
if (!root) throw new Error("Missing root");

function CaptureSearch() {
  const hold = useHold();
  const once = useRef(false);
  useEffect(() => {
    if (once.current) return;
    const params = new URLSearchParams(window.location.search);
    const text = params.get("text") ?? "";
    const kind = params.get("kind") ?? "note";
    if (!text.trim()) return;
    const mark = `hold-capture:${kind}:${text}`;
    if (sessionStorage.getItem(mark)) return;
    const filed = phraseEntry(kind, text, hold.sync.deviceId);
    if (!filed) return;
    sessionStorage.setItem(mark, "1");
    once.current = true;
    void hold.saveEntry(filed);
  }, [hold]);
  return null;
}

createRoot(root).render(
  <StrictMode>
    <HoldProvider>
      <CaptureSearch />
      <HoldApp />
    </HoldProvider>
  </StrictMode>,
);
