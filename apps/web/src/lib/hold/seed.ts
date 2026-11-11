import type { Category, Entry, SavedView, Snapshot, Tag } from "./types.ts";

export const SEED_IDS = ["ent_shed", "ent_tow", "ent_voice", "ent_nfc", "ent_hr", "ent_place"] as const;
export const SEED_CATEGORY_IDS = ["cat_personal", "cat_radio", "cat_errands", "cat_health"] as const;
export const SEED_TAG_IDS = ["tag_later", "tag_driving", "tag_flipper"] as const;
export const SEED_VIEW_IDS = ["view_radio", "view_driving"] as const;

export function isExampleSet(entries: { id: string; deletedAt: number | null }[]): boolean {
  const live = entries.filter((entry) => !entry.deletedAt);
  if (live.length !== SEED_IDS.length) return false;
  const ids = new Set(live.map((entry) => entry.id));
  return SEED_IDS.every((id) => ids.has(id));
}

function stamp(id: string, now: number, offset: number): Pick<Category, "id" | "updatedAt" | "deletedAt" | "dirty" | "syncedAt"> {
  return { id, updatedAt: now - offset, deletedAt: null, dirty: false, syncedAt: null };
}

export function buildSeed(deviceId: string, now: number): Pick<Snapshot, "entries" | "categories" | "tags" | "views"> {
  const categories: Category[] = [
    { ...stamp("cat_personal", now, 10_000), name: "Personal" },
    { ...stamp("cat_radio", now, 9_000), name: "Radio" },
    { ...stamp("cat_errands", now, 8_000), name: "Errands" },
    { ...stamp("cat_health", now, 7_000), name: "Health" },
  ];
  const tags: Tag[] = [
    { ...stamp("tag_later", now, 6_000), name: "later" },
    { ...stamp("tag_driving", now, 5_000), name: "driving" },
    { ...stamp("tag_flipper", now, 4_000), name: "flipper" },
  ];
  const views: SavedView[] = [
    {
      ...stamp("view_radio", now, 3_000),
      name: "Radio dumps",
      kinds: ["radio"],
      tagIds: [],
      categoryId: null,
      query: "",
    },
    {
      ...stamp("view_driving", now, 2_000),
      name: "Driving",
      kinds: [],
      tagIds: ["tag_driving"],
      categoryId: null,
      query: "",
    },
  ];
  const base = {
    deletedAt: null,
    dirty: false,
    syncedAt: null,
    deviceId,
    pinned: false,
  } as const;
  const nfc = [
    "Filetype: Flipper NFC device",
    "Version: 4",
    "Device type: NTAG215",
    "UID: 04 A1 22 9C 5E 67 80",
    "ATQA: 44 00",
    "SAK: 00",
  ].join("\n");
  const entries: Entry[] = [
    {
      ...base,
      id: "ent_shed",
      kind: "note",
      title: "Shed gate",
      body: "Code is on the post, not the door. The lock sticks if you turn it left first.",
      createdAt: now - 3_600_000,
      updatedAt: now - 3_600_000,
      categoryId: "cat_personal",
      tagIds: ["tag_later"],
      pinned: true,
      payload: { kind: "note" },
    },
    {
      ...base,
      id: "ent_tow",
      kind: "number",
      title: "Tow — Ray's",
      body: "Ask for the night desk.",
      createdAt: now - 7_200_000,
      updatedAt: now - 7_200_000,
      categoryId: "cat_errands",
      tagIds: ["tag_driving"],
      payload: { kind: "number", phone: "+1 555 014 0188" },
    },
    {
      ...base,
      id: "ent_voice",
      kind: "voice",
      title: "Charger",
      body: "",
      createdAt: now - 5_400_000,
      updatedAt: now - 5_400_000,
      categoryId: null,
      tagIds: ["tag_driving"],
      payload: {
        kind: "voice",
        durationMs: 3200,
        transcript: "Left the charger in the truck.",
        hasAudio: false,
        mime: "",
      },
    },
    {
      ...base,
      id: "ent_nfc",
      kind: "radio",
      title: "NTAG215 · 04 A1 22 9C 5E 67 80",
      body: "Plant tag on the bench vise.",
      createdAt: now - 9_000_000,
      updatedAt: now - 9_000_000,
      categoryId: "cat_radio",
      tagIds: ["tag_flipper"],
      payload: {
        kind: "radio",
        protocol: "nfc",
        uid: "04 A1 22 9C 5E 67 80",
        frequencyHz: null,
        filetype: "Flipper NFC device",
        raw: nfc,
      },
    },
    {
      ...base,
      id: "ent_hr",
      kind: "health",
      title: "Resting heart rate",
      body: "",
      createdAt: now - 10_800_000,
      updatedAt: now - 10_800_000,
      categoryId: "cat_health",
      tagIds: [],
      payload: {
        kind: "health",
        metric: "Heart rate",
        value: 62,
        unit: "bpm",
        source: "manual",
        recordedAt: now - 10_800_000,
      },
    },
    {
      ...base,
      id: "ent_place",
      kind: "place",
      title: "Pull-off past the bridge",
      body: "Gravel on the right, before the narrow part. No signal there.",
      createdAt: now - 14_400_000,
      updatedAt: now - 14_400_000,
      categoryId: null,
      tagIds: ["tag_driving"],
      payload: { kind: "place", lat: null, lon: null, accuracyM: null, plus: "", address: "", w3w: "" },
    },
  ];
  return { entries, categories, tags, views };
}
