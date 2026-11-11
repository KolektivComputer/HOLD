import { applyRemote, outbound } from "./merge.ts";
import {
  KINDS,
  RADIO_PROTOCOLS,
  type Category,
  type Entry,
  type Kind,
  type Payload,
  type RadioProtocol,
  type SavedView,
  type Snapshot,
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

function payloadFrom(kind: Kind, value: unknown): Payload | null {
  const row = isRecord(value) ? value : {};
  switch (kind) {
    case "note":
      return { kind: "note" };
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
        frequencyHz: typeof row.frequencyHz === "number" ? row.frequencyHz : null,
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
    case "place":
      return {
        kind: "place",
        lat: typeof row.lat === "number" ? row.lat : null,
        lon: typeof row.lon === "number" ? row.lon : null,
      };
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

export async function pushSync(snapshot: Snapshot): Promise<SyncResult> {
  const { sync } = snapshot;
  if (!sync.baseUrl.trim()) throw new Error("Set a server address first.");
  const endpoint = syncEndpoint(sync.baseUrl);
  const now = Date.now();
  const sentEntries = outbound(snapshot.entries);
  const sentCategories = outbound(snapshot.categories);
  const sentTags = outbound(snapshot.tags);
  const sentViews = outbound(snapshot.views);

  const body = {
    deviceId: sync.deviceId,
    deviceName: sync.deviceName,
    since: sync.lastCursor,
    categories: sentCategories.live.map(toWire),
    tags: sentTags.live.map(toWire),
    views: sentViews.live.map(toWire),
    upserts: sentEntries.live.map(toWire),
    deleted: [...sentEntries.deleted, ...sentCategories.deleted, ...sentTags.deleted, ...sentViews.deleted],
  };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15_000);
  let response: Response;
  try {
    response = await fetch(endpoint, {
      method: "POST",
      headers: {
        accept: "application/json",
        "content-type": "application/json",
        ...(sync.token.trim() ? { authorization: `Bearer ${sync.token.trim()}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
  } catch {
    throw new Error("Could not reach the server. Stay offline — the log is still here.");
  } finally {
    clearTimeout(timer);
  }
  if (!response.ok) throw new Error(`The server answered ${response.status}.`);
  const data = (await response.json()) as unknown;
  if (!isRecord(data) || typeof data.cursor !== "string" || !data.cursor) {
    throw new Error("The server reply had no cursor.");
  }

  const skipped = { n: 0 };
  const take = <T>(value: unknown, map: (item: unknown) => T | null): T[] => {
    if (!Array.isArray(value)) return [];
    const out: T[] = [];
    for (const item of value) {
      const parsed = map(item);
      if (parsed) out.push(parsed);
      else skipped.n += 1;
    }
    return out;
  };

  const localEntries = new Map(snapshot.entries.map((entry) => [entry.id, entry]));
  const incomingEntries = take(data.upserts, (item) => {
    const id = isRecord(item) && typeof item.id === "string" ? item.id : "";
    return entryFrom(item, now, localEntries.get(id));
  });
  const incomingCategories = take(data.categories, (item) => namedFrom(item, now));
  const incomingTags = take(data.tags, (item) => namedFrom(item, now));
  const incomingViews = take(data.views, (item) => viewFrom(item, now));
  const deleted = ids(data.deleted);

  const entriesMerged = applyRemote(snapshot.entries, incomingEntries, [
    ...deleted,
    ...sentEntries.deleted,
    ...sentEntries.dropLocal,
  ]);
  const categoriesMerged = applyRemote(snapshot.categories, incomingCategories, [
    ...deleted,
    ...sentCategories.deleted,
    ...sentCategories.dropLocal,
  ]);
  const tagsMerged = applyRemote(snapshot.tags, incomingTags, [...deleted, ...sentTags.deleted, ...sentTags.dropLocal]);
  const viewsMerged = applyRemote(snapshot.views, incomingViews, [
    ...deleted,
    ...sentViews.deleted,
    ...sentViews.dropLocal,
  ]);

  const sentAt = new Map(sentEntries.live.map((entry) => [entry.id, entry.updatedAt]));
  const entries = entriesMerged.next.map((entry) => {
    const pushedAt = sentAt.get(entry.id);
    if (pushedAt !== undefined && entry.updatedAt === pushedAt) {
      return { ...entry, dirty: false, syncedAt: now };
    }
    return entry;
  });
  const markClean = <T extends { id: string; updatedAt: number; dirty: boolean; syncedAt: number | null }>(
    items: T[],
    sent: { id: string; updatedAt: number }[],
  ) => {
    const times = new Map(sent.map((item) => [item.id, item.updatedAt]));
    return items.map((item) => {
      const pushedAt = times.get(item.id);
      if (pushedAt !== undefined && item.updatedAt === pushedAt) {
        return { ...item, dirty: false, syncedAt: now };
      }
      return item;
    });
  };

  const nextSync: SyncConfig = {
    ...sync,
    lastCursor: data.cursor,
    lastSyncAt: now,
    lastError: null,
  };
  const summary = [
    `Sent ${sentEntries.live.length}. Received ${incomingEntries.length}.`,
    skipped.n > 0 ? `Skipped ${skipped.n} the server shaped unexpectedly.` : "",
  ]
    .filter(Boolean)
    .join(" ");

  return {
    summary,
    snapshot: {
      entries,
      categories: markClean(categoriesMerged.next, sentCategories.live),
      tags: markClean(tagsMerged.next, sentTags.live),
      views: markClean(viewsMerged.next, sentViews.live),
      sync: nextSync,
    },
  };
}
