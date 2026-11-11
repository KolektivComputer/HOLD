import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { Kv } from "./db.ts";
import { openKv } from "./db.ts";
import { buildSeed, SEED_IDS } from "./seed.ts";
import { pushSync } from "./sync.ts";
import {
  defaultSync,
  newId,
  type Category,
  type Entry,
  type HealthDraft,
  type SavedView,
  type Snapshot,
  type SyncConfig,
  type Tag,
} from "./types.ts";

type Flags = { seeded: boolean; dismissedIntro: boolean };

type HoldApi = Snapshot & {
  ready: boolean;
  persisted: boolean;
  problem: string | null;
  showIntro: boolean;
  saveEntry: (entry: Entry) => Promise<void>;
  removeEntry: (id: string) => Promise<void>;
  saveAudio: (id: string, blob: Blob) => Promise<void>;
  loadAudio: (id: string) => Promise<Blob | undefined>;
  ensureCategory: (name: string) => Promise<string | null>;
  renameCategory: (id: string, name: string) => Promise<void>;
  removeCategory: (id: string) => Promise<void>;
  ensureTag: (name: string) => Promise<string | null>;
  renameTag: (id: string, name: string) => Promise<void>;
  removeTag: (id: string) => Promise<void>;
  saveView: (name: string, view: Pick<SavedView, "kinds" | "tagIds" | "categoryId" | "query">) => Promise<void>;
  removeView: (id: string) => Promise<void>;
  setSync: (patch: Partial<Omit<SyncConfig, "deviceId">>) => Promise<void>;
  dismissIntro: () => Promise<void>;
  runSync: () => Promise<string>;
  importEntries: (entries: Entry[]) => Promise<number>;
  importHealth: (drafts: HealthDraft[]) => Promise<number>;
  importBundle: (bundle: Pick<Snapshot, "categories" | "tags" | "views" | "entries"> & { audio?: Record<string, string> }) => Promise<number>;
  wipe: () => Promise<void>;
};

const HoldContext = createContext<HoldApi | null>(null);

async function readSnapshot(kv: Kv): Promise<Snapshot> {
  const [entries, categories, tags, views, sync, flags] = await Promise.all([
    kv.getAll<Entry>("entries"),
    kv.getAll<Category>("categories"),
    kv.getAll<Tag>("tags"),
    kv.getAll<SavedView>("views"),
    kv.get<SyncConfig>("meta", "sync"),
    kv.get<Flags>("meta", "flags"),
  ]);
  return {
    entries,
    categories,
    tags,
    views,
    sync: sync ?? defaultSync(),
    dismissedIntro: flags?.dismissedIntro === true,
  };
}

let opening: Promise<{ kv: Kv; persisted: boolean; snap: Snapshot }> | null = null;

function loadOrSeed() {
  if (!opening) {
    opening = (async () => {
      const { kv, persisted } = await openKv();
      let sync = await kv.get<SyncConfig>("meta", "sync");
      if (!sync) {
        sync = defaultSync();
        await kv.put("meta", "sync", sync);
      }
      const flags = await kv.get<Flags>("meta", "flags");
      const existing = await kv.getAll<Entry>("entries");
      if (!flags?.seeded && existing.length === 0) {
        const seed = buildSeed(sync.deviceId, Date.now());
        await kv.replace("entries", seed.entries);
        await kv.replace("categories", seed.categories);
        await kv.replace("tags", seed.tags);
        await kv.replace("views", seed.views);
      }
      if (!flags?.seeded) {
        await kv.put("meta", "flags", { seeded: true, dismissedIntro: flags?.dismissedIntro === true });
      }
      const snap = await readSnapshot(kv);
      return { kv, persisted, snap };
    })().catch((error) => {
      opening = null;
      throw error;
    });
  }
  return opening;
}

function blankStamp(id: string, now: number) {
  return { id, updatedAt: now, deletedAt: null, dirty: true, syncedAt: null };
}

function base64ToBlob(encoded: string): Blob | null {
  try {
    const binary = atob(encoded);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
    return new Blob([bytes]);
  } catch {
    return null;
  }
}

export function HoldProvider({ children }: { children: ReactNode }) {
  const kvRef = useRef<Kv | null>(null);
  const [snap, setSnap] = useState<Snapshot | null>(null);
  const [persisted, setPersisted] = useState(true);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    let cancel = false;
    void loadOrSeed()
      .then((result) => {
        if (cancel) return;
        kvRef.current = result.kv;
        setPersisted(result.persisted);
        setSnap(result.snap);
      })
      .catch((error: unknown) => {
        if (cancel) return;
        setProblem(error instanceof Error ? error.message : "Could not open the log");
      });
    return () => {
      cancel = true;
    };
  }, []);

  const api = useMemo<HoldApi | null>(() => {
    if (!snap) return null;
    const reload = async () => {
      const kv = kvRef.current;
      if (!kv) return;
      setSnap(await readSnapshot(kv));
    };
    const mutate = async (work: (kv: Kv) => Promise<void>) => {
      const kv = kvRef.current;
      if (!kv) return;
      await work(kv);
      await reload();
    };

    return {
      ...snap,
      ready: true,
      persisted,
      problem,
      showIntro: !snap.dismissedIntro && snap.entries.some((entry) => (SEED_IDS as readonly string[]).includes(entry.id)),
      saveEntry: (entry) =>
        mutate(async (kv) => {
          const next = { ...entry, updatedAt: Date.now(), dirty: true };
          await kv.put("entries", next.id, next);
        }),
      removeEntry: (id) =>
        mutate(async (kv) => {
          const current = await kv.get<Entry>("entries", id);
          if (!current) return;
          await kv.del("audio", id);
          if (current.syncedAt === null) {
            await kv.del("entries", id);
            return;
          }
          await kv.put("entries", id, { ...current, deletedAt: Date.now(), updatedAt: Date.now(), dirty: true });
        }),
      saveAudio: async (id, blob) => {
        const kv = kvRef.current;
        if (!kv) return;
        await kv.put("audio", id, blob);
      },
      loadAudio: async (id) => {
        const kv = kvRef.current;
        if (!kv) return undefined;
        const blob = await kv.get<Blob>("audio", id);
        return blob instanceof Blob ? blob : undefined;
      },
      ensureCategory: async (name) => {
        const trimmed = name.trim();
        if (!trimmed) return null;
        const kv = kvRef.current;
        if (!kv) return null;
        const all = await kv.getAll<Category>("categories");
        const found = all.find((item) => !item.deletedAt && item.name.toLowerCase() === trimmed.toLowerCase());
        if (found) return found.id;
        const created: Category = { ...blankStamp(newId(), Date.now()), name: trimmed };
        await kv.put("categories", created.id, created);
        await reload();
        return created.id;
      },
      renameCategory: (id, name) =>
        mutate(async (kv) => {
          const current = await kv.get<Category>("categories", id);
          const trimmed = name.trim();
          if (!current || !trimmed) return;
          await kv.put("categories", id, { ...current, name: trimmed, updatedAt: Date.now(), dirty: true });
        }),
      removeCategory: (id) =>
        mutate(async (kv) => {
          const current = await kv.get<Category>("categories", id);
          if (!current) return;
          if (current.syncedAt === null) await kv.del("categories", id);
          else {
            await kv.put("categories", id, { ...current, deletedAt: Date.now(), updatedAt: Date.now(), dirty: true });
          }
          const entries = await kv.getAll<Entry>("entries");
          for (const entry of entries) {
            if (entry.categoryId === id && !entry.deletedAt) {
              await kv.put("entries", entry.id, { ...entry, categoryId: null, updatedAt: Date.now(), dirty: true });
            }
          }
        }),
      ensureTag: async (name) => {
        const trimmed = name.trim();
        if (!trimmed) return null;
        const kv = kvRef.current;
        if (!kv) return null;
        const all = await kv.getAll<Tag>("tags");
        const found = all.find((item) => !item.deletedAt && item.name.toLowerCase() === trimmed.toLowerCase());
        if (found) return found.id;
        const created: Tag = { ...blankStamp(newId(), Date.now()), name: trimmed };
        await kv.put("tags", created.id, created);
        await reload();
        return created.id;
      },
      renameTag: (id, name) =>
        mutate(async (kv) => {
          const current = await kv.get<Tag>("tags", id);
          const trimmed = name.trim();
          if (!current || !trimmed) return;
          await kv.put("tags", id, { ...current, name: trimmed, updatedAt: Date.now(), dirty: true });
        }),
      removeTag: (id) =>
        mutate(async (kv) => {
          const current = await kv.get<Tag>("tags", id);
          if (!current) return;
          if (current.syncedAt === null) await kv.del("tags", id);
          else await kv.put("tags", id, { ...current, deletedAt: Date.now(), updatedAt: Date.now(), dirty: true });
          const entries = await kv.getAll<Entry>("entries");
          for (const entry of entries) {
            if (entry.tagIds.includes(id)) {
              await kv.put("entries", entry.id, {
                ...entry,
                tagIds: entry.tagIds.filter((tagId) => tagId !== id),
                updatedAt: Date.now(),
                dirty: true,
              });
            }
          }
          const views = await kv.getAll<SavedView>("views");
          for (const view of views) {
            if (view.tagIds.includes(id)) {
              await kv.put("views", view.id, {
                ...view,
                tagIds: view.tagIds.filter((tagId) => tagId !== id),
                updatedAt: Date.now(),
                dirty: true,
              });
            }
          }
        }),
      saveView: (name, view) =>
        mutate(async (kv) => {
          const trimmed = name.trim();
          if (!trimmed) return;
          const created: SavedView = { ...blankStamp(newId(), Date.now()), name: trimmed, ...view };
          await kv.put("views", created.id, created);
        }),
      removeView: (id) =>
        mutate(async (kv) => {
          const current = await kv.get<SavedView>("views", id);
          if (!current) return;
          if (current.syncedAt === null) await kv.del("views", id);
          else await kv.put("views", id, { ...current, deletedAt: Date.now(), updatedAt: Date.now(), dirty: true });
        }),
      setSync: (patch) =>
        mutate(async (kv) => {
          const current = (await kv.get<SyncConfig>("meta", "sync")) ?? defaultSync();
          await kv.put("meta", "sync", { ...current, ...patch, deviceId: current.deviceId });
        }),
      dismissIntro: () =>
        mutate(async (kv) => {
          const flags = (await kv.get<Flags>("meta", "flags")) ?? { seeded: true, dismissedIntro: false };
          await kv.put("meta", "flags", { ...flags, dismissedIntro: true });
        }),
      runSync: async () => {
        const kv = kvRef.current;
        if (!kv) throw new Error("The log is not open yet.");
        const current = await readSnapshot(kv);
        try {
          const result = await pushSync(current);
          await kv.replace("entries", result.snapshot.entries);
          await kv.replace("categories", result.snapshot.categories);
          await kv.replace("tags", result.snapshot.tags);
          await kv.replace("views", result.snapshot.views);
          await kv.put("meta", "sync", result.snapshot.sync);
          await reload();
          return result.summary;
        } catch (error) {
          const message = error instanceof Error ? error.message : "Sync failed.";
          const sync = (await kv.get<SyncConfig>("meta", "sync")) ?? current.sync;
          await kv.put("meta", "sync", { ...sync, lastError: message });
          await reload();
          throw new Error(message);
        }
      },
      importEntries: (entries) =>
        mutate(async (kv) => {
          const local = await kv.getAll<Entry>("entries");
          const map = new Map(local.map((entry) => [entry.id, entry]));
          for (const entry of entries) {
            if (entry.deletedAt) continue;
            const existing = map.get(entry.id);
            if (!existing || entry.updatedAt >= existing.updatedAt) {
              map.set(entry.id, { ...entry, dirty: true, deletedAt: null, syncedAt: null });
            }
          }
          await kv.replace("entries", [...map.values()]);
        }).then(async () => {
          const kv = kvRef.current;
          if (!kv) return 0;
          return entries.length;
        }),
      importHealth: (drafts) =>
        mutate(async (kv) => {
          const sync = (await kv.get<SyncConfig>("meta", "sync")) ?? snap.sync;
          const now = Date.now();
          for (const [index, draft] of drafts.entries()) {
            const entry: Entry = {
              ...blankStamp(newId(), now + index),
              kind: "health",
              title: draft.metric,
              body: "",
              createdAt: draft.recordedAt,
              categoryId: null,
              tagIds: [],
              pinned: false,
              deviceId: sync.deviceId,
              payload: { kind: "health", ...draft, source: "import" },
            };
            await kv.put("entries", entry.id, entry);
          }
        }).then(() => drafts.length),
      importBundle: (bundle) =>
        mutate(async (kv) => {
          const mergeNamed = async <T extends { id: string; updatedAt: number; deletedAt: number | null }>(
            store: "categories" | "tags" | "views",
            incoming: T[],
          ) => {
            const local = await kv.getAll<T>(store);
            const map = new Map(local.map((item) => [item.id, item]));
            for (const item of incoming) {
              if (item.deletedAt) continue;
              const existing = map.get(item.id);
              if (!existing || item.updatedAt >= existing.updatedAt) {
                map.set(item.id, { ...item, dirty: true, syncedAt: null, deletedAt: null });
              }
            }
            await kv.replace(store, [...map.values()]);
          };
          await mergeNamed("categories", bundle.categories);
          await mergeNamed("tags", bundle.tags);
          await mergeNamed("views", bundle.views);
          const local = await kv.getAll<Entry>("entries");
          const map = new Map(local.map((entry) => [entry.id, entry]));
          for (const entry of bundle.entries) {
            if (entry.deletedAt) continue;
            const existing = map.get(entry.id);
            if (!existing || entry.updatedAt >= existing.updatedAt) {
              map.set(entry.id, { ...entry, dirty: true, deletedAt: null, syncedAt: null });
            }
          }
          await kv.replace("entries", [...map.values()]);
          if (bundle.audio) {
            for (const [id, encoded] of Object.entries(bundle.audio)) {
              const blob = base64ToBlob(encoded);
              if (blob) await kv.put("audio", id, blob);
            }
          }
        }).then(() => bundle.entries.length),
      wipe: () =>
        mutate(async (kv) => {
          await kv.clear("entries");
          await kv.clear("categories");
          await kv.clear("tags");
          await kv.clear("views");
          await kv.clear("audio");
          await kv.put("meta", "flags", { seeded: true, dismissedIntro: true });
        }),
    };
  }, [persisted, problem, snap]);

  if (!api) {
    return (
      <main className="flex min-h-dvh flex-col bg-bg px-5 py-6 text-fg">
        <p className="font-mono text-lg tracking-widest">HOLD</p>
        <p className="mt-6 max-w-sm text-muted">{problem ?? "Opening the log on this device…"}</p>
      </main>
    );
  }

  return <HoldContext.Provider value={api}>{children}</HoldContext.Provider>;
}

export function useHold() {
  const value = useContext(HoldContext);
  if (!value) throw new Error("useHold must be used inside HoldProvider");
  return value;
}
