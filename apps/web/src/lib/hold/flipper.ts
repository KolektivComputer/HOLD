import type { RadioProtocol } from "./types.ts";

export type ParsedRadio = {
  protocol: RadioProtocol;
  uid: string;
  frequencyHz: number | null;
  filetype: string;
  raw: string;
  title: string;
};

function protocolFrom(filetype: string, keyType: string): RadioProtocol {
  const blob = `${filetype} ${keyType}`.toLowerCase();
  if (blob.includes("nfc")) return "nfc";
  if (blob.includes("rfid") || blob.includes("em4100") || blob.includes("hid")) return "rfid";
  if (blob.includes("subghz") || blob.includes("sub-ghz")) return "subghz";
  if (blob.includes("infrared") || blob.includes("ir")) return "ir";
  if (blob.includes("ibutton") || blob.includes("ibtn")) return "ibutton";
  return "other";
}

export function parseFlipper(text: string): ParsedRadio {
  const raw = text.replace(/^\uFEFF/, "").trim();
  const fields = new Map<string, string>();
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const idx = trimmed.indexOf(":");
    if (idx <= 0) continue;
    const key = trimmed.slice(0, idx).trim().toLowerCase();
    const value = trimmed.slice(idx + 1).trim();
    if (!fields.has(key)) fields.set(key, value);
  }
  const filetype = fields.get("filetype") ?? "";
  const keyType = fields.get("key type") ?? fields.get("device type") ?? fields.get("protocol") ?? "";
  const protocol = protocolFrom(filetype, keyType);
  const uid = fields.get("uid") ?? fields.get("data") ?? "";
  const freqRaw = fields.get("frequency");
  const frequencyHz = freqRaw && Number.isFinite(Number(freqRaw)) ? Number(freqRaw) : null;
  const label = keyType || filetype || "Radio dump";
  const title = uid ? `${label} · ${uid}` : label;
  return { protocol, uid, frequencyHz, filetype, raw, title };
}
