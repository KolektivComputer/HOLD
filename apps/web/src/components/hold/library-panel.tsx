import { useEffect, useState } from "react";
import { Button, TextField } from "@/components/hold/ui";
import { SlippyMap } from "@/components/hold/slippy-map";
import { downloadBlob } from "@/lib/hold/format";
import { useHold } from "@/lib/hold/store";
import { KINDS, newId, type Category, type Entry, type LogFilter, type SavedView, type Tag } from "@/lib/hold/types";

type Bundle = {
  version: 1;
  categories: Category[];
  tags: Tag[];
  views: SavedView[];
  entries: Entry[];
  audio?: Record<string, string>;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function parseBundle(text: string): Bundle {
  const data = JSON.parse(text) as unknown;
  if (!isRecord(data) || !Array.isArray(data.entries)) throw new Error("That file is not a HOLD backup.");
  return {
    version: 1,
    categories: Array.isArray(data.categories) ? (data.categories as Category[]) : [],
    tags: Array.isArray(data.tags) ? (data.tags as Tag[]) : [],
    views: Array.isArray(data.views) ? (data.views as SavedView[]) : [],
    entries: data.entries as Entry[],
    audio: isRecord(data.audio) ? (data.audio as Record<string, string>) : undefined,
  };
}

async function blobToBase64(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunk));
  }
  return btoa(binary);
}

export function LibraryPanel({
  filter,
  onApply,
  onShowLog,
}: {
  filter: LogFilter;
  onApply: (filter: LogFilter) => void;
  onShowLog: () => void;
}) {
  const hold = useHold();
  const [viewName, setViewName] = useState("");
  const [categoryName, setCategoryName] = useState("");
  const [tagName, setTagName] = useState("");
  const [baseUrl, setBaseUrl] = useState(hold.sync.baseUrl);
  const [token, setToken] = useState(hold.sync.token);
  const [deviceName, setDeviceName] = useState(hold.sync.deviceName);
  const [syncMessage, setSyncMessage] = useState("");
  const [syncing, setSyncing] = useState(false);
  const [includeAudio, setIncludeAudio] = useState(false);
  const [backupNote, setBackupNote] = useState("");
  const [confirmWipe, setConfirmWipe] = useState(false);
  const [keyDraft, setKeyDraft] = useState(hold.settings.deepseekKey);
  const [packNote, setPackNote] = useState("");
  const categories = hold.categories.filter((category) => !category.deletedAt);
  const tags = hold.tags.filter((tag) => !tag.deletedAt);
  const views = hold.views.filter((view) => !view.deletedAt);
  const filterSummary = describeFilter(filter, categories, tags);

  const saveServer = async () => {
    await hold.setSync({ baseUrl: baseUrl.trim(), token: token.trim(), deviceName: deviceName.trim() || "This pocket" });
    setSyncMessage("Server settings saved on this device.");
  };

  const syncNow = async () => {
    setSyncing(true);
    setSyncMessage("");
    try {
      await hold.setSync({ baseUrl: baseUrl.trim(), token: token.trim(), deviceName: deviceName.trim() || "This pocket" });
      const summary = await hold.runSync();
      setSyncMessage(summary);
    } catch (error) {
      setSyncMessage(error instanceof Error ? error.message : "Sync failed.");
    } finally {
      setSyncing(false);
    }
  };

  const exportBackup = async () => {
    const audio: Record<string, string> = {};
    if (includeAudio) {
      for (const entry of hold.entries) {
        if (entry.payload.kind === "voice" && entry.payload.hasAudio) {
          const blob = await hold.loadAudio(entry.id);
          if (blob) audio[entry.id] = await blobToBase64(blob);
        }
      }
    }
    const bundle = {
      version: 1,
      exportedAt: Date.now(),
      categories: hold.categories,
      tags: hold.tags,
      views: hold.views,
      entries: hold.entries.filter((entry) => !entry.deletedAt),
      ...(includeAudio ? { audio } : {}),
    };
    downloadBlob("hold-backup.json", new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" }));
    setBackupNote("Backup downloaded. The token for your server is not in it.");
  };

  const importBackup = async (file: File) => {
    try {
      const bundle = parseBundle(await file.text());
      const count = await hold.importBundle(bundle);
      setBackupNote(`Merged ${count} entries into this device.`);
    } catch (error) {
      setBackupNote(error instanceof Error ? error.message : "Could not import that file.");
    }
  };

  return (
    <div className="flex flex-col gap-8 px-4 py-4">
      <PocketSection keyDraft={keyDraft} onKey={setKeyDraft} packNote={packNote} onPackNote={setPackNote} />
      <section className="flex flex-col gap-3">
        <h2 className="font-mono text-xs tracking-wider text-muted uppercase">Views</h2>
        <p className="text-sm text-muted">Current log filters: {filterSummary}</p>
        <div className="flex gap-2">
          <input
            value={viewName}
            onChange={(event) => setViewName(event.target.value)}
            placeholder="Name this view"
            aria-label="View name"
            className="min-h-11 w-full rounded-md border border-muted/30 bg-bg px-3 text-base"
          />
          <Button
            disabled={!viewName.trim()}
            onClick={() => {
              void hold.saveView(viewName, {
                kinds: filter.kinds,
                tagIds: filter.tagIds,
                categoryId: filter.categoryId,
                query: filter.query,
              });
              setViewName("");
            }}
          >
            Save
          </Button>
        </div>
        <ul className="flex flex-col">
          {views.map((view) => (
            <li key={view.id} className="flex items-center justify-between gap-2 border-b border-muted/20 py-1">
              <button
                type="button"
                className="min-h-11 flex-1 text-left text-fg"
                onClick={() => {
                  onApply({
                    kinds: view.kinds,
                    tagIds: view.tagIds,
                    categoryId: view.categoryId,
                    query: view.query,
                    viewId: view.id,
                  });
                  onShowLog();
                }}
              >
                {view.name}
              </button>
              <Button tone="quiet" onClick={() => void hold.removeView(view.id)}>
                Remove
              </Button>
            </li>
          ))}
        </ul>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="font-mono text-xs tracking-wider text-muted uppercase">Categories</h2>
        <div className="flex gap-2">
          <input
            value={categoryName}
            onChange={(event) => setCategoryName(event.target.value)}
            placeholder="New category"
            aria-label="New category"
            className="min-h-11 w-full rounded-md border border-muted/30 bg-bg px-3 text-base"
          />
          <Button
            disabled={!categoryName.trim()}
            onClick={() => {
              void hold.ensureCategory(categoryName);
              setCategoryName("");
            }}
          >
            Add
          </Button>
        </div>
        <ul>
          {categories.map((category) => (
            <li key={category.id} className="flex items-center justify-between gap-2 border-b border-muted/20">
              <button
                type="button"
                className="min-h-11 flex-1 text-left"
                onClick={() => {
                  onApply({ ...filter, categoryId: category.id, viewId: null });
                  onShowLog();
                }}
              >
                {category.name}
              </button>
              <Button tone="quiet" onClick={() => void hold.removeCategory(category.id)}>
                Remove
              </Button>
            </li>
          ))}
        </ul>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="font-mono text-xs tracking-wider text-muted uppercase">Tags</h2>
        <div className="flex gap-2">
          <input
            value={tagName}
            onChange={(event) => setTagName(event.target.value)}
            placeholder="New tag"
            aria-label="New tag"
            className="min-h-11 w-full rounded-md border border-muted/30 bg-bg px-3 text-base"
          />
          <Button
            disabled={!tagName.trim()}
            onClick={() => {
              void hold.ensureTag(tagName);
              setTagName("");
            }}
          >
            Add
          </Button>
        </div>
        <ul>
          {tags.map((tag) => (
            <li key={tag.id} className="flex items-center justify-between gap-2 border-b border-muted/20">
              <button
                type="button"
                className="min-h-11 flex-1 text-left"
                onClick={() => {
                  onApply({ ...filter, tagIds: [tag.id], viewId: null });
                  onShowLog();
                }}
              >
                {tag.name}
              </button>
              <Button tone="quiet" onClick={() => void hold.removeTag(tag.id)}>
                Remove
              </Button>
            </li>
          ))}
        </ul>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="font-mono text-xs tracking-wider text-muted uppercase">Sync</h2>
        <p className="text-sm text-muted">
          {hold.settings.mode === "thin"
            ? "Thin pocket. Nothing calls your server, and the trail does not follow you, until you ask."
            : "Full pocket. Sync still waits for the button. Follow me only runs if you turn it on."}
        </p>
        <TextField label="Server" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} placeholder="https://hold.home" inputMode="url" autoComplete="off" />
        <TextField label="Token" value={token} onChange={(event) => setToken(event.target.value)} type="password" autoComplete="off" />
        <TextField label="Device name" value={deviceName} onChange={(event) => setDeviceName(event.target.value)} />
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => void saveServer()}>Save server</Button>
          <Button tone="accent" disabled={syncing || !baseUrl.trim()} onClick={() => void syncNow()}>
            {syncing ? "Syncing…" : "Sync now"}
          </Button>
        </div>
        {hold.sync.lastSyncAt ? (
          <p className="text-sm text-muted">Last sync {new Date(hold.sync.lastSyncAt).toLocaleString()}</p>
        ) : null}
        {hold.sync.lastError && !syncMessage ? <p className="text-sm text-fg">{hold.sync.lastError}</p> : null}
        {syncMessage ? <p className="text-sm text-fg">{syncMessage}</p> : null}
        <details className="rounded-md border border-muted/25 px-3">
          <summary className="flex min-h-11 cursor-pointer items-center text-sm">Server contract</summary>
          <pre className="overflow-x-auto pb-3 font-mono text-xs whitespace-pre-wrap text-muted">{`POST /v1/sync
Authorization: Bearer <token>

{
  "deviceId": "…",
  "deviceName": "This pocket",
  "since": null,
  "categories": [],
  "tags": [],
  "views": [],
  "upserts": [],
  "deleted": []
}

Reply:
{ "cursor": "opaque", "upserts": [], "deleted": [], "categories": [], "tags": [], "views": [] }`}</pre>
        </details>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="font-mono text-xs tracking-wider text-muted uppercase">Backup</h2>
        <label className="flex min-h-11 items-center gap-3 text-sm">
          <input type="checkbox" className="size-5 accent-accent" checked={includeAudio} onChange={(event) => setIncludeAudio(event.target.checked)} />
          Include voice audio
        </label>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => void exportBackup()}>Download backup</Button>
          <label className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-md border border-muted/30 bg-panel px-4 text-sm font-medium">
            Import backup
            <input
              type="file"
              accept="application/json,.json"
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = "";
                if (file) void importBackup(file);
              }}
            />
          </label>
        </div>
        {backupNote ? <p className="text-sm text-fg">{backupNote}</p> : null}
      </section>

      <section className="flex flex-col gap-3 pb-6">
        <h2 className="font-mono text-xs tracking-wider text-muted uppercase">On this device</h2>
        <p className="text-sm text-muted">
          {hold.persisted ? "The log is stored in this browser." : "This browser won’t keep the log after you leave."}{" "}
          {hold.entries.filter((entry) => !entry.deletedAt).length} entries.
        </p>
        {confirmWipe ? (
          <div className="flex flex-col gap-2">
            <p className="text-sm text-fg">Erase the log here. A server you already synced can send it back on the next sync.</p>
            <div className="flex gap-2">
              <Button
                onClick={() => {
                  void hold.wipe();
                  setConfirmWipe(false);
                }}
              >
                Erase
              </Button>
              <Button tone="quiet" onClick={() => setConfirmWipe(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <Button tone="quiet" onClick={() => setConfirmWipe(true)}>
            Erase this device
          </Button>
        )}
      </section>
    </div>
  );
}

function PocketSection({
  keyDraft,
  onKey,
  packNote,
  onPackNote,
}: {
  keyDraft: string;
  onKey: (value: string) => void;
  packNote: string;
  onPackNote: (value: string) => void;
}) {
  const hold = useHold();
  const places = hold.entries.filter((entry) => !entry.deletedAt && entry.payload.kind === "place" && entry.payload.lat != null && entry.payload.lon != null);
  const first = places[0]?.payload.kind === "place" ? places[0].payload : null;
  const center = hold.trail[hold.trail.length - 1] ?? (first && first.lat != null && first.lon != null ? { lat: first.lat, lon: first.lon } : null);

  useEffect(() => {
    if (hold.settings.mode !== "full" || !hold.settings.follow || !navigator.geolocation) return;
    const watch = navigator.geolocation.watchPosition((position) => {
      void hold.dropFix({
        id: newId(),
        at: Date.now(),
        lat: position.coords.latitude,
        lon: position.coords.longitude,
        accuracyM: Number.isFinite(position.coords.accuracy) ? position.coords.accuracy : null,
      });
    });
    return () => navigator.geolocation.clearWatch(watch);
  }, [hold.settings.mode, hold.settings.follow, hold]);

  const packTrail = () => {
    const blob = new Blob(
      [JSON.stringify({ trail: hold.trail, places: places.map((entry) => ({ title: entry.title, payload: entry.payload })) }, null, 2)],
      { type: "application/json" },
    );
    downloadBlob("hold-trail.json", blob);
    onPackNote("Trail packed. Hand the file to someone you trust. A live encrypted send between pockets is not in this build.");
  };

  return (
    <section className="flex flex-col gap-3">
      <h2 className="font-mono text-xs tracking-wider text-muted uppercase">Pocket</h2>
      <p className="text-sm text-muted">
        Full is for the phone that syncs to your own server. Thin is for the Stylo: offline, and the server only when you tap Sync.
      </p>
      <div className="flex gap-2">
        <Button tone={hold.settings.mode === "full" ? "accent" : "plain"} onClick={() => void hold.setSettings({ mode: "full" })}>
          Full
        </Button>
        <Button tone={hold.settings.mode === "thin" ? "accent" : "plain"} onClick={() => void hold.setSettings({ mode: "thin", follow: false })}>
          Thin
        </Button>
      </div>
      <p className="text-sm text-muted">Passwords, one-time codes, and passkeys stay out until there is a vault. Direct send over Bluetooth or the cell net stays out too.</p>
      {center ? (
        <SlippyMap
          lat={center.lat}
          lon={center.lon}
          pins={[
            ...places.flatMap((entry) =>
              entry.payload.kind === "place" && entry.payload.lat != null && entry.payload.lon != null
                ? [{ lat: entry.payload.lat, lon: entry.payload.lon, label: entry.title }]
                : [],
            ),
            ...hold.trail.map((fix) => ({ lat: fix.lat, lon: fix.lon, label: "Trail" })),
          ]}
        />
      ) : (
        <p className="text-sm text-muted">No ground yet. File a place, or drop a pin.</p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          onClick={() => {
            if (!navigator.geolocation) return;
            navigator.geolocation.getCurrentPosition((position) => {
              void hold.dropFix({
                id: newId(),
                at: Date.now(),
                lat: position.coords.latitude,
                lon: position.coords.longitude,
                accuracyM: Number.isFinite(position.coords.accuracy) ? position.coords.accuracy : null,
              });
            });
          }}
        >
          Drop a pin
        </Button>
        <Button
          tone={hold.settings.follow ? "accent" : "plain"}
          disabled={hold.settings.mode === "thin"}
          onClick={() => void hold.setSettings({ follow: !hold.settings.follow })}
        >
          {hold.settings.follow ? "Following" : "Follow me"}
        </Button>
        <Button tone="quiet" onClick={() => void hold.clearTrail()}>
          Clear trail
        </Button>
        <Button onClick={packTrail} disabled={hold.trail.length === 0 && places.length === 0}>
          Pack trail
        </Button>
      </div>
      {hold.settings.mode === "thin" ? <p className="text-sm text-muted">Thin mode does not follow you. Drop a pin when you want one.</p> : null}
      {packNote ? <p className="text-sm text-fg">{packNote}</p> : null}
      <TextField label="DeepSeek key" value={keyDraft} onChange={(event) => onKey(event.target.value)} type="password" autoComplete="off" />
      <Button
        onClick={() => {
          void hold.setSettings({ deepseekKey: keyDraft.trim() });
          onPackNote("Key saved on this device only. It is not in backups or sync.");
        }}
      >
        Save key
      </Button>
      <p className="text-sm text-muted">Used only when you tap Read with DeepSeek. deepseek-flash can see a picture. The on-device reader does not need a key.</p>
    </section>
  );
}

function describeFilter(filter: LogFilter, categories: Category[], tags: Tag[]) {
  const parts: string[] = [];
  if (filter.kinds.length === 0) parts.push("all kinds");
  else parts.push(filter.kinds.map((kind) => KINDS.includes(kind) ? kind : kind).join(", "));
  if (filter.categoryId) parts.push(categories.find((category) => category.id === filter.categoryId)?.name ?? "category");
  if (filter.tagIds.length) {
    parts.push(filter.tagIds.map((id) => tags.find((tag) => tag.id === id)?.name ?? "tag").join(", "));
  }
  if (filter.query) parts.push(`“${filter.query}”`);
  return parts.join(" · ");
}
