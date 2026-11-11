import { plusCode } from "./geo.ts";
import { sanitizeInk } from "./ink.ts";
import { applyRemote, outbound } from "./merge.ts";
import {
  KINDS,
  RADIO_PROTOCOLS,
  SPOT_KINDS,
  type Category,
  type ContactHandle,
  type Entry,
  type Kind,
  type Payload,
  type RadioProtocol,
  type SavedView,
  type Snapshot,
  type SpotKind,
  type SyncConfig,
  type Tag,
} from "./types.ts";

type Wire<T> = Omit<T, "dirty" | "syncedAt">;

export type SyncResult = {
  summary: string;
  snapshot: Pick<Snapshot, "entries" | "categories" | "tags" | "views" | "sync">;
};

function toWire<T extends { dirty: boolean; syncedAt: number | null }>(item: T): Wire<T> {
  const { dirty: _dirty, syncedAt: _syncedAt, ...wire } = item;
  void _dirty;
  void _syncedAt;
  return wire;
}

export function syncEndpoint(baseUrl: string): string {
  const url = new URL(baseUrl.trim());
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("Use an http or https address.");
  }
  const path = url.pathname.replace(/\/+$/, "");
  if (path.endsWith("/v1/sync")) return `${url.origin}${path}`;
  if (path.endsWith("/v1")) return `${url.origin}${path}/sync`;
  return `${url.origin}${path}/v1/sync`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function stampFrom(value: Record<string, unknown>, now: number) {
  return {
    id: String(value.id),
    updatedAt: typeof value.updatedAt === "number" ? value.updatedAt : now,
    deletedAt: typeof value.deletedAt === "number" ? value.deletedAt : null,
    dirty: false,
    syncedAt: now,
  };
}

function isKind(value: unknown): value is Kind {
  return typeof value === "string" && (KINDS as readonly string[]).includes(value);
}

function isProtocol(value: unknown): value is RadioProtocol {
  return typeof value === "string" && (RADIO_PROTOCOLS as readonly string[]).includes(value);
}

function isSpot(value: unknown): value is SpotKind {
  return typeof value === "string" && (SPOT_KINDS as readonly string[]).includes(value);
}

function handlesFrom(value: unknown): ContactHandle[] {
  if (!Array.isArray(value)) return [];
  const handles: ContactHandle[] = [];
  for (const item of value) {
    if (!isRecord(item) || !isSpot(item.kind) || typeof item.value !== "string") continue;
    const trimmed = item.value.trim();
    if (!trimmed) continue;
    handles.push({ kind: item.kind, value: trimmed.slice(0, 200) });
    if (handles.length >= 24) break;
  }
  return handles;
}

function finite(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function payloadFrom(kind: Kind, value: unknown): Payload | null {
  const row = isRecord(value) ? value : {};
  switch (kind) {
    case "note":
      return {
        kind: "note",
        ink: sanitizeInk(row.ink),
        ...(typeof row.shotId === "string" && row.shotId ? { shotId: row.shotId } : {}),
      };
    case "number":
      return { kind: "number", phone: typeof row.phone === "string" ? row.phone : "" };
    case "voice":
      return {
        kind: "voice",
        durationMs: typeof row.durationMs === "number" ? row.durationMs : 0,
        transcript: typeof row.transcript === "string" ? row.transcript : "",
        hasAudio: false,
        mime: typeof row.mime === "string" ? row.mime : "",
      };
    case "radio":
      return {
        kind: "radio",
        protocol: isProtocol(row.protocol) ? row.protocol : "other",
        uid: typeof row.uid === "string" ? row.uid : "",
        frequencyHz: finite(row.frequencyHz),
        filetype: typeof row.filetype === "string" ? row.filetype : "",
        raw: typeof row.raw === "string" ? row.raw : "",
      };
    case "health": {
      const numeric = typeof row.value === "number" ? row.value : Number(row.value);
      if (!Number.isFinite(numeric)) return null;
      return {
        kind: "health",
        metric: typeof row.metric === "string" && row.metric ? row.metric : "Reading",
        value: numeric,
        unit: typeof row.unit === "string" ? row.unit : "",
        source: row.source === "import" ? "import" : "manual",
        recordedAt: typeof row.recordedAt === "number" ? row.recordedAt : Date.now(),
      };
    }
    case "place": {
      const lat = finite(row.lat);
      const lon = finite(row.lon);
      return {
        kind: "place",
        lat,
        lon,
        accuracyM: finite(row.accuracyM),
        plus: lat != null && lon != null ? (typeof row.plus === "string" && row.plus ? row.plus : plusCode(lat, lon)) : "",
        address: typeof row.address === "string" ? row.address : "",
        w3w: typeof row.w3w === "string" ? row.w3w : "",
      };
    }
    case "contact":
      return { kind: "contact", handles: handlesFrom(row.handles) };
  }
}

function entryFrom(value: unknown, now: number, previous?: Entry): Entry | null {
  if (!isRecord(value) || typeof value.id !== "string" || !isKind(value.kind)) return null;
  const payload = payloadFrom(value.kind, value.payload);
  if (!payload) return null;
  if (payload.kind === "voice" && previous?.payload.kind === "voice" && previous.payload.hasAudio) {
    payload.hasAudio = true;
    payload.mime = previous.payload.mime || payload.mime;
  }
  if (payload.kind === "note" && previous?.payload.kind === "note" && previous.payload.shotId && !payload.shotId) {
    payload.shotId = previous.payload.shotId;
  }
  return {
    ...stampFrom(value, now),
    kind: value.kind,
    title: typeof value.title === "string" ? value.title : "",
    body: typeof value.body === "string" ? value.body : "",
    createdAt: typeof value.createdAt === "number" ? value.createdAt : now,
    categoryId: typeof value.categoryId === "string" ? value.categoryId : null,
    tagIds: Array.isArray(value.tagIds) ? value.tagIds.filter((id) => typeof id === "string") : [],
    pinned: value.pinned === true,
    deviceId: typeof value.deviceId === "string" ? value.deviceId : previous?.deviceId ?? "",
    payload,
  };
}

function namedFrom(value: unknown, now: number): Category | null {
  if (!isRecord(value) || typeof value.id !== "string" || typeof value.name !== "string") return null;
  return { ...stampFrom(value, now), name: value.name };
}

function viewFrom(value: unknown, now: number): SavedView | null {
  const named = namedFrom(value, now);
  if (!named || !isRecord(value)) return null;
  const kinds = Array.isArray(value.kinds) ? value.kinds.filter(isKind) : [];
  const tagIds = Array.isArray(value.tagIds) ? value.tagIds.filter((id) => typeof id === "string") : [];
  return {
    ...named,
    kinds,
    tagIds,
    categoryId: typeof value.categoryId === "string" ? value.categoryId : null,
    query: typeof value.query === "string" ? value.query : "",
  };
}

function ids(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((id) => typeof id === "string");
}

function settle<T extends { id: string; dirty: boolean; syncedAt: number | null; deletedAt: number | null; updatedAt: number }>(
  local: T[],
  sent: ReturnType<typeof outbound<T>>,
  incoming: T[],
  deletedIds: string[],
  now: number,
): T[] {
  const kept = local.filter((item) => !sent.dropLocal.includes(item.id));
  const merged = applyRemote(kept, incoming, [...deletedIds, ...sent.deleted]).next;
  const liveIds = new Set(sent.live.map((item) => item.id));
  return merged.map((item) => (liveIds.has(item.id) && item.dirty ? { ...item, dirty: false, syncedAt: now } : item));
}

export async function pushSync(snapshot: Snapshot): Promise<SyncResult> {
  const { sync } = snapshot;
  if (!sync.baseUrl.trim()) throw new Error("Set a server address first.");
  const endpoint = syncEndpoint(sync.baseUrl);
  const now = Date.now();
  const entries = outbound(snapshot.entries);
  const categories = outbound(snapshot.categories);
  const tags = outbound(snapshot.tags);
  const views = outbound(snapshot.views);
  let response: Response;
  try {
    response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(sync.token.trim() ? { Authorization: `Bearer ${sync.token.trim()}` } : {}),
      },
      body: JSON.stringify({
        deviceId: sync.deviceId,
        deviceName: sync.deviceName,
        since: sync.lastCursor,
        categories: categories.live.map(toWire),
        tags: tags.live.map(toWire),
        views: views.live.map(toWire),
        upserts: entries.live.map(toWire),
        deleted: [...entries.deleted, ...categories.deleted, ...tags.deleted, ...views.deleted],
      }),
    });
  } catch {
    throw new Error("Could not reach the server.");
  }
  if (!response.ok) {
    throw new Error(response.status === 401 || response.status === 403 ? "The server refused the token." : `The server answered ${response.status}.`);
  }
  const json: unknown = await response.json();
  if (!isRecord(json)) throw new Error("The server sent something this pocket doesn't understand.");
  const byId = new Map(snapshot.entries.map((entry) => [entry.id, entry]));
  const incomingEntries = Array.isArray(json.upserts)
    ? json.upserts
        .map((item) => entryFrom(item, now, isRecord(item) && typeof item.id === "string" ? byId.get(item.id) : undefined))
        .filter((item): item is Entry => item !== null)
    : [];
  const incomingCategories = Array.isArray(json.categories)
    ? json.categories.map((item) => namedFrom(item, now)).filter((item): item is Category => item !== null)
    : [];
  const incomingTags = Array.isArray(json.tags) ? json.tags.map((item) => namedFrom(item, now)).filter((item): item is Tag => item !== null) : [];
  const incomingViews = Array.isArray(json.views)
    ? json.views.map((item) => viewFrom(item, now)).filter((item): item is SavedView => item !== null)
    : [];
  const deleted = ids(json.deleted);
  const cursor = typeof json.cursor === "string" ? json.cursor : sync.lastCursor;
  const nextSync: SyncConfig = { ...sync, lastCursor: cursor, lastSyncAt: now, lastError: null };
  const arrived = incomingEntries.length + deleted.length;
  return {
    summary: arrived > 0 ? `Synced. ${arrived} from the server.` : "Synced. The server had nothing new.",
    snapshot: {
      entries: settle(snapshot.entries, entries, incomingEntries, deleted, now),
      categories: settle(snapshot.categories, categories, incomingCategories, deleted, now),
      tags: settle(snapshot.tags, tags, incomingTags, deleted, now),
      views: settle(snapshot.views, views, incomingViews, deleted, now),
      sync: nextSync,
    },
  };
}
