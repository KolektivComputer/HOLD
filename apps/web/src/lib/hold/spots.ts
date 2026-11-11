import { newId, type ContactHandle, type Entry, type SpotKind } from "./types.ts";

export type Spot = { kind: SpotKind; value: string };

const RULES: { kind: SpotKind; pattern: RegExp; pick: number }[] = [
  { kind: "email", pattern: /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, pick: 0 },
  { kind: "discord", pattern: /\b[A-Za-z0-9._]{2,32}#\d{4}\b/g, pick: 0 },
  { kind: "discord", pattern: /\bdiscord(?:\s+account)?[:\s]+@?([A-Za-z0-9._]{2,32})/gi, pick: 1 },
  { kind: "fluxer", pattern: /\bfluxer[:\s]+@?([A-Za-z0-9._]{2,32})/gi, pick: 1 },
  { kind: "x", pattern: /\b(?:twitter|x(?:\.com)?)[:\s]+@?([A-Za-z0-9_]{1,15})\b/gi, pick: 1 },
  {
    kind: "phone",
    pattern: /(?<!\d)(?:\+\d{1,3}[\s.-]?)?(?:\(?\d{3}\)?[\s.-]?)\d{3}[\s.-]?\d{4}(?!\d)/g,
    pick: 0,
  },
  {
    kind: "address",
    pattern:
      /\b\d{1,6}\s+[A-Za-z0-9.'-]{2,}(?:\s+[A-Za-z0-9.'-]{2,}){0,4}\s+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Way|Court|Ct)\b\.?/gi,
    pick: 0,
  },
];

export function canon(kind: SpotKind, value: string): string {
  const trimmed = value.trim().toLowerCase();
  if (kind === "phone") return trimmed.replace(/\D/g, "");
  if (kind === "email") return trimmed;
  return trimmed.replace(/^@/, "");
}

export function spotText(text: string): Spot[] {
  const found: { kind: SpotKind; value: string; start: number; end: number }[] = [];
  for (const rule of RULES) {
    rule.pattern.lastIndex = 0;
    for (const match of text.matchAll(rule.pattern)) {
      const raw = rule.pick === 0 ? match[0] : match[1];
      if (!raw || match.index == null) continue;
      const value = raw.trim().replace(/[.,;:!?]+$/g, "");
      if (rule.kind === "phone" && value.replace(/\D/g, "").length < 7) continue;
      const start = match.index + (rule.pick === 0 ? 0 : match[0].indexOf(raw));
      found.push({ kind: rule.kind, value, start, end: start + value.length });
    }
  }
  found.sort((a, b) => a.start - b.start || b.end - b.start - (a.end - a.start));
  const kept: Spot[] = [];
  let cursor = -1;
  const seen = new Set<string>();
  for (const spot of found) {
    if (spot.start < cursor) continue;
    const key = `${spot.kind}:${canon(spot.kind, spot.value)}`;
    cursor = spot.end;
    if (seen.has(key)) continue;
    seen.add(key);
    kept.push({ kind: spot.kind, value: spot.value });
  }
  return kept;
}

export function filedSpot(kind: SpotKind, value: string, deviceId: string, title = ""): Entry {
  const now = Date.now();
  const name = title.trim();
  const base = {
    id: newId(),
    title: name && name.toLowerCase() !== value.trim().toLowerCase() ? name : value.trim(),
    body: "",
    createdAt: now,
    updatedAt: now,
    categoryId: null,
    tagIds: [] as string[],
    pinned: false,
    deletedAt: null,
    dirty: true,
    syncedAt: null,
    deviceId,
  };
  if (kind === "phone") {
    return { ...base, kind: "number", payload: { kind: "number", phone: value.trim() } };
  }
  const handle: ContactHandle = { kind, value: value.trim() };
  return { ...base, kind: "contact", payload: { kind: "contact", handles: [handle] } };
}

export type SpotHint = {
  spot: Spot;
  contacts: { id: string; title: string }[];
  already: string | null;
};

export function spotHints(entries: Entry[], text: string): SpotHint[] {
  const spots = spotText(text);
  const live = entries.filter((entry) => !entry.deletedAt);
  const named = live.filter(
    (entry) => entry.payload.kind === "contact" && entry.title.trim().length >= 3 && text.toLowerCase().includes(entry.title.trim().toLowerCase()),
  );
  return spots.map((spot) => {
    const key = canon(spot.kind, spot.value);
    const contacts = live
      .filter((entry) => entry.payload.kind === "contact")
      .filter((entry) => {
        const handles = entry.payload.kind === "contact" ? entry.payload.handles : [];
        const same = handles.some((handle) => handle.kind === spot.kind && canon(handle.kind, handle.value) === key);
        const mentioned = named.some((item) => item.id === entry.id);
        return same || mentioned;
      })
      .map((entry) => ({ id: entry.id, title: entry.title || "Contact" }));
    const number = live.find(
      (entry) => entry.payload.kind === "number" && spot.kind === "phone" && canon("phone", entry.payload.phone) === key,
    );
    return { spot, contacts, already: number ? number.title || number.payload.kind : null };
  });
}

export function phraseEntry(kind: string, text: string, deviceId: string): Entry | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  if (kind === "phone" || kind === "number") return filedSpot("phone", spotText(trimmed).find((spot) => spot.kind === "phone")?.value ?? trimmed, deviceId);
  if (kind === "discord" || kind === "email" || kind === "x" || kind === "fluxer" || kind === "address") {
    const spot = spotText(trimmed).find((item) => item.kind === kind) ?? { kind, value: trimmed.replace(new RegExp(`^${kind}\\s*`, "i"), "").trim() || trimmed };
    return filedSpot(kind, spot.value, deviceId);
  }
  const now = Date.now();
  if (kind === "voice") {
    return {
      id: newId(),
      kind: "voice",
      title: trimmed.slice(0, 80),
      body: "",
      createdAt: now,
      updatedAt: now,
      categoryId: null,
      tagIds: [],
      pinned: false,
      deletedAt: null,
      dirty: true,
      syncedAt: null,
      deviceId,
      payload: { kind: "voice", durationMs: 0, transcript: trimmed, hasAudio: false, mime: "" },
    };
  }
  return {
    id: newId(),
    kind: "note",
    title: trimmed.slice(0, 80),
    body: trimmed,
    createdAt: now,
    updatedAt: now,
    categoryId: null,
    tagIds: [],
    pinned: false,
    deletedAt: null,
    dirty: true,
    syncedAt: null,
    deviceId,
    payload: { kind: "note" },
  };
}
