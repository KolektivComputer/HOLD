export const KINDS = ["note", "number", "voice", "radio", "health", "place"] as const;
export type Kind = (typeof KINDS)[number];

export const RADIO_PROTOCOLS = ["nfc", "rfid", "subghz", "ir", "ibutton", "other"] as const;
export type RadioProtocol = (typeof RADIO_PROTOCOLS)[number];

export type Stamp = {
  id: string;
  updatedAt: number;
  deletedAt: number | null;
  dirty: boolean;
  syncedAt: number | null;
};

export type Category = Stamp & { name: string };
export type Tag = Stamp & { name: string };

export type SavedView = Stamp & {
  name: string;
  kinds: Kind[];
  tagIds: string[];
  categoryId: string | null;
  query: string;
};

export type Payload =
  | { kind: "note" }
  | { kind: "number"; phone: string }
  | { kind: "voice"; durationMs: number; transcript: string; hasAudio: boolean; mime: string }
  | {
      kind: "radio";
      protocol: RadioProtocol;
      uid: string;
      frequencyHz: number | null;
      filetype: string;
      raw: string;
    }
  | {
      kind: "health";
      metric: string;
      value: number;
      unit: string;
      source: "manual" | "import";
      recordedAt: number;
    }
  | { kind: "place"; lat: number | null; lon: number | null };

export type Entry = Stamp & {
  kind: Kind;
  title: string;
  body: string;
  createdAt: number;
  categoryId: string | null;
  tagIds: string[];
  pinned: boolean;
  deviceId: string;
  payload: Payload;
};

export type SyncConfig = {
  baseUrl: string;
  token: string;
  deviceName: string;
  deviceId: string;
  lastCursor: string | null;
  lastSyncAt: number | null;
  lastError: string | null;
};

export type LogFilter = {
  kinds: Kind[];
  tagIds: string[];
  categoryId: string | null;
  query: string;
  viewId: string | null;
};

export type Snapshot = {
  entries: Entry[];
  categories: Category[];
  tags: Tag[];
  views: SavedView[];
  sync: SyncConfig;
  dismissedIntro: boolean;
};

export type HealthDraft = {
  metric: string;
  value: number;
  unit: string;
  recordedAt: number;
};

export const EMPTY_FILTER: LogFilter = {
  kinds: [],
  tagIds: [],
  categoryId: null,
  query: "",
  viewId: null,
};

export function newId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `id_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

export function defaultSync(deviceId = newId()): SyncConfig {
  return {
    baseUrl: "",
    token: "",
    deviceName: "This pocket",
    deviceId,
    lastCursor: null,
    lastSyncAt: null,
    lastError: null,
  };
}

export function filterFromView(view: SavedView): LogFilter {
  return {
    kinds: view.kinds,
    tagIds: view.tagIds,
    categoryId: view.categoryId,
    query: view.query,
    viewId: view.id,
  };
}
