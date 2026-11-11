import type { Category, Entry, Kind, LogFilter, RadioProtocol, SpotKind, Tag } from "./types.ts";

export const KIND_LABEL: Record<Kind, string> = {
  note: "Note",
  number: "Number",
  voice: "Voice",
  radio: "Radio",
  health: "Health",
  place: "Place",
  contact: "Contact",
};

export const SPOT_LABEL: Record<SpotKind, string> = {
  phone: "Phone",
  email: "Email",
  discord: "Discord",
  x: "X",
  fluxer: "Fluxer",
  address: "Address",
};

export const PROTOCOL_LABEL: Record<RadioProtocol, string> = {
  nfc: "NFC",
  rfid: "RFID",
  subghz: "Sub-GHz",
  ir: "Infrared",
  ibutton: "iButton",
  other: "Other",
};

function startOfDay(ms: number) {
  const date = new Date(ms);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

export function formatWhen(ms: number, now = Date.now()): string {
  const time = new Date(ms).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  const diff = Math.round((startOfDay(now) - startOfDay(ms)) / 86_400_000);
  if (diff === 0) return time;
  if (diff === 1) return "Yesterday";
  if (diff > 1 && diff < 7) return new Date(ms).toLocaleDateString(undefined, { weekday: "short" });
  return new Date(ms).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function formatHz(hz: number): string {
  if (hz >= 1_000_000) return `${(hz / 1_000_000).toFixed(3)} MHz`;
  if (hz >= 1_000) return `${(hz / 1_000).toFixed(1)} kHz`;
  return `${hz} Hz`;
}

export function entryPreview(entry: Entry): string {
  switch (entry.payload.kind) {
    case "number":
      return entry.payload.phone;
    case "voice":
      return entry.payload.transcript || formatDuration(entry.payload.durationMs);
    case "radio":
      return [PROTOCOL_LABEL[entry.payload.protocol], entry.payload.uid].filter(Boolean).join(" · ");
    case "health":
      return [entry.payload.value, entry.payload.unit].filter((part) => part !== "").join(" ");
    case "place":
      if (entry.payload.plus) return entry.payload.plus;
      if (entry.payload.address) return entry.payload.address;
      if (entry.payload.lat != null && entry.payload.lon != null) {
        return `${entry.payload.lat.toFixed(5)}, ${entry.payload.lon.toFixed(5)}`;
      }
      return entry.body;
    case "contact":
      return entry.payload.handles.map((handle) => handle.value).join(" · ");
    case "note": {
      if (entry.body.trim()) return entry.body;
      const strokes = entry.payload.ink?.length ?? 0;
      if (strokes === 1) return "Ink · 1 stroke";
      if (strokes > 1) return `Ink · ${strokes} strokes`;
      return "";
    }
  }
}

function payloadSearch(entry: Entry): string {
  switch (entry.payload.kind) {
    case "number":
      return entry.payload.phone;
    case "voice":
      return entry.payload.transcript;
    case "radio":
      return `${entry.payload.uid} ${entry.payload.filetype} ${entry.payload.raw}`;
    case "health":
      return `${entry.payload.metric} ${entry.payload.value} ${entry.payload.unit}`;
    case "place":
      return `${entry.payload.plus} ${entry.payload.address} ${entry.payload.w3w} ${entry.payload.lat ?? ""} ${entry.payload.lon ?? ""}`;
    case "contact":
      return entry.payload.handles.map((handle) => `${handle.kind} ${handle.value}`).join(" ");
    case "note":
      return "";
  }
}

export function matches(entry: Entry, filter: LogFilter, tags: Tag[], categories: Category[]): boolean {
  if (entry.deletedAt) return false;
  if (filter.kinds.length > 0 && !filter.kinds.includes(entry.kind)) return false;
  if (filter.categoryId && entry.categoryId !== filter.categoryId) return false;
  if (filter.tagIds.length > 0 && !filter.tagIds.every((id) => entry.tagIds.includes(id))) return false;
  const query = filter.query.trim().toLowerCase();
  if (!query) return true;
  const tagNames = entry.tagIds.map((id) => tags.find((tag) => tag.id === id)?.name ?? "").join(" ");
  const category = categories.find((item) => item.id === entry.categoryId)?.name ?? "";
  const hay = `${entry.title}\n${entry.body}\n${tagNames}\n${category}\n${payloadSearch(entry)}`.toLowerCase();
  return hay.includes(query);
}

export function sortEntries(entries: Entry[]): Entry[] {
  return entries.slice().sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
    return b.updatedAt - a.updatedAt;
  });
}

export function safeFilename(name: string, extension: string) {
  const base = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 40) || "hold";
  return `${base}.${extension}`;
}

export function radioExtension(protocol: RadioProtocol): string {
  switch (protocol) {
    case "nfc":
      return "nfc";
    case "rfid":
      return "rfid";
    case "subghz":
      return "sub";
    case "ir":
      return "ir";
    case "ibutton":
      return "ibtn";
    default:
      return "txt";
  }
}

export function downloadBlob(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function telHref(phone: string): string | null {
  const cleaned = phone.replace(/[^\d+*#]/g, "");
  if (!/\d/.test(cleaned)) return null;
  return `tel:${cleaned}`;
}
