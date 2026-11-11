import { useEffect, useRef, useState } from "react";
import { AreaField, Button, SelectField, TaxonomyFields, TextField } from "@/components/hold/ui";
import { InkPad, type InkPadHandle } from "@/components/hold/ink-pad";
import { PlaceEditor, placePayload } from "@/components/hold/place-editor";
import { SpotBar } from "@/components/hold/spot-bar";
import { downloadBlob, formatHz, KIND_LABEL, PROTOCOL_LABEL, radioExtension, safeFilename, SPOT_LABEL, telHref } from "@/lib/hold/format";
import { packJpeg, readOnDevice } from "@/lib/hold/ocr";
import { readWithDeepseek } from "@/lib/hold/remote";
import { canon } from "@/lib/hold/spots";
import { useHold } from "@/lib/hold/store";
import { newId, RADIO_PROTOCOLS, type Entry, type Payload, type RadioProtocol, type SpotKind } from "@/lib/hold/types";

export function EntryPanel({ entry, onClose, onOpen }: { entry: Entry; onClose: () => void; onOpen: (id: string) => void }) {
  const hold = useHold();
  const [title, setTitle] = useState(entry.title);
  const [body, setBody] = useState(entry.body);
  const [categoryName, setCategoryName] = useState("");
  const [tagIds, setTagIds] = useState(entry.tagIds);
  const [tagDraft, setTagDraft] = useState("");
  const [payload, setPayload] = useState<Payload>(entry.payload);
  const [confirming, setConfirming] = useState(false);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [shotUrl, setShotUrl] = useState<string | null>(null);
  const [reading, setReading] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const pad = useRef<InkPadHandle>(null);

  useEffect(() => {
    const category = hold.categories.find((item) => item.id === entry.categoryId);
    setTitle(entry.title);
    setBody(entry.body);
    setCategoryName(category?.name ?? "");
    setTagIds(entry.tagIds);
    setPayload(entry.payload);
    setConfirming(false);
    setError("");
  }, [entry.id, entry.updatedAt, entry.categoryId, entry.tagIds, entry.title, entry.body, entry.payload, hold.categories]);

  useEffect(() => {
    let dead = false;
    let url: string | null = null;
    if (entry.payload.kind !== "voice" || !entry.payload.hasAudio) {
      setAudioUrl(null);
      return;
    }
    void hold.loadAudio(entry.id).then((blob) => {
      if (dead || !blob) return;
      url = URL.createObjectURL(blob);
      setAudioUrl(url);
    });
    return () => {
      dead = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [entry.id, entry.payload, hold]);

  useEffect(() => {
    let dead = false;
    let url: string | null = null;
    const shotId = entry.payload.kind === "note" ? entry.payload.shotId : undefined;
    if (!shotId) {
      setShotUrl(null);
      return;
    }
    void hold.loadBlob(shotId).then((blob) => {
      if (dead || !blob) return;
      url = URL.createObjectURL(blob);
      setShotUrl(url);
    });
    return () => {
      dead = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [entry.id, entry.payload, hold]);

  const addTag = async () => {
    const id = await hold.ensureTag(tagDraft);
    if (!id) return;
    setTagIds((current) => (current.includes(id) ? current : [...current, id]));
    setTagDraft("");
  };

  const save = async () => {
    if (payload.kind === "place") {
      if ((payload.lat !== null && !Number.isFinite(payload.lat)) || (payload.lon !== null && !Number.isFinite(payload.lon))) {
        setError("Coordinates need to be numbers.");
        return;
      }
    }
    setError("");
    const categoryId = await hold.ensureCategory(categoryName);
    await hold.saveEntry({ ...entry, title: title.trim() || entry.title, body: body.trim(), categoryId, tagIds, payload });
  };

  const readInk = async (cloud: boolean) => {
    setReading(cloud ? "Sending the page to DeepSeek…" : "Reading on this device…");
    setError("");
    try {
      const blob = await pad.current?.exportPng();
      if (!blob) throw new Error("The page is not ready.");
      const text = cloud ? (await readWithDeepseek({ data: { key: hold.settings.deepseekKey, image: await packJpeg(blob) } })).text : await readOnDevice(blob);
      setBody((current) => (current.trim() ? `${current.trim()}\n${text}` : text));
      setNotice(cloud ? "DeepSeek's reading is in the details. Check it before you trust it." : "On-device reading is in the details. Handwriting on a small phone will be rough.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not read the page.");
    } finally {
      setReading("");
    }
  };

  const markup = async () => {
    const blob = await renderCard(title || entry.title, body);
    const id = newId();
    await hold.saveBlob(id, blob);
    const now = Date.now();
    const note: Entry = {
      id,
      kind: "note",
      title: `Markup · ${title || entry.title}`.slice(0, 80),
      body: "",
      createdAt: now,
      updatedAt: now,
      categoryId: entry.categoryId,
      tagIds: entry.tagIds,
      pinned: false,
      deletedAt: null,
      dirty: true,
      syncedAt: null,
      deviceId: hold.sync.deviceId,
      payload: { kind: "note", shotId: id },
    };
    await hold.saveEntry(note);
    onOpen(id);
  };

  const useSpot = async (kind: SpotKind, value: string) => {
    const { filedSpot } = await import("@/lib/hold/spots");
    const filed = filedSpot(kind, value, hold.sync.deviceId, title);
    await hold.saveEntry(filed);
    setNotice(`Filed as ${SPOT_LABEL[kind].toLowerCase()}.`);
  };

  const addTo = async (id: string, kind: SpotKind, value: string) => {
    const target = hold.entries.find((item) => item.id === id && item.payload.kind === "contact");
    if (!target || target.payload.kind !== "contact") return;
    if (target.payload.handles.some((handle) => handle.kind === kind && canon(kind, handle.value) === canon(kind, value))) return;
    await hold.saveEntry({ ...target, payload: { kind: "contact", handles: [...target.payload.handles, { kind, value: value.trim() }] } });
    setNotice(`Added to ${target.title}.`);
  };

  const downloadRadio = () => {
    if (payload.kind !== "radio") return;
    const text = payload.raw.trim() || [`Protocol: ${payload.protocol}`, payload.uid ? `UID: ${payload.uid}` : "", payload.frequencyHz ? `Frequency: ${payload.frequencyHz}` : ""].filter(Boolean).join("\n");
    downloadBlob(safeFilename(title || "dump", radioExtension(payload.protocol)), new Blob([text], { type: "text/plain;charset=utf-8" }));
  };

  const phone = payload.kind === "number" ? telHref(payload.phone) : null;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex min-h-14 shrink-0 items-center justify-between gap-3 border-b border-muted/25 px-4">
        <button type="button" onClick={onClose} className="min-h-11 text-sm text-accent lg:invisible">
          Back
        </button>
        <p className="font-mono text-xs tracking-wider text-muted uppercase">{KIND_LABEL[entry.kind]}</p>
        <button
          type="button"
          onClick={() => void hold.saveEntry({ ...entry, pinned: !entry.pinned })}
          className="min-h-11 text-sm text-fg"
        >
          {entry.pinned ? "Unpin" : "Pin"}
        </button>
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 py-4">
        <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} />
        {payload.kind === "note" ? (
          <>
            <InkPad ref={pad} strokes={payload.ink ?? []} background={shotUrl} onChange={(ink) => setPayload({ kind: "note", ink, shotId: payload.shotId })} />
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => void readInk(false)} disabled={Boolean(reading)}>
                Read on this device
              </Button>
              <Button onClick={() => void readInk(true)} disabled={Boolean(reading) || !hold.settings.deepseekKey}>
                Read with DeepSeek
              </Button>
            </div>
            {reading ? <p className="text-sm text-muted">{reading}</p> : null}
            <p className="text-sm text-muted">
              On-device reading never leaves the phone. It is slow, and a Stylo 4 will struggle with handwriting. DeepSeek can read a scrawl, and it only runs when you tap it. The key stays in Library.
            </p>
          </>
        ) : (
          <Button onClick={() => void markup()}>Draw on this</Button>
        )}
        {payload.kind === "number" ? (
          <>
            <TextField label="Phone" value={payload.phone} onChange={(event) => setPayload({ kind: "number", phone: event.target.value })} inputMode="tel" />
            {phone ? (
              <a href={phone} className="inline-flex min-h-11 items-center text-sm text-accent">
                Call
              </a>
            ) : null}
          </>
        ) : null}
        {payload.kind === "voice" ? (
          <>
            {audioUrl ? <audio controls src={audioUrl} className="w-full" /> : <p className="text-sm text-muted">No audio stored with this one.</p>}
            <AreaField label="Transcript" value={payload.transcript} onChange={(event) => setPayload({ ...payload, transcript: event.target.value })} />
          </>
        ) : null}
        {payload.kind === "radio" ? (
          <>
            <SelectField label="Protocol" value={payload.protocol} onChange={(event) => setPayload({ ...payload, protocol: event.target.value as RadioProtocol })}>
              {RADIO_PROTOCOLS.map((item) => (
                <option key={item} value={item}>
                  {PROTOCOL_LABEL[item]}
                </option>
              ))}
            </SelectField>
            <TextField label="UID or data" value={payload.uid} onChange={(event) => setPayload({ ...payload, uid: event.target.value })} className="font-mono" />
            <TextField
              label="Frequency Hz"
              value={payload.frequencyHz ?? ""}
              onChange={(event) => {
                const next = event.target.value.trim();
                setPayload({ ...payload, frequencyHz: next ? Number(next) : null });
              }}
              inputMode="decimal"
            />
            {payload.frequencyHz ? <p className="font-mono text-sm text-muted">{formatHz(payload.frequencyHz)}</p> : null}
            <AreaField label="Dump" value={payload.raw} onChange={(event) => setPayload({ ...payload, raw: event.target.value })} className="font-mono" />
            <Button onClick={downloadRadio}>Download dump</Button>
          </>
        ) : null}
        {payload.kind === "health" ? (
          <>
            <TextField label="Metric" value={payload.metric} onChange={(event) => setPayload({ ...payload, metric: event.target.value })} />
            <div className="grid grid-cols-2 gap-3">
              <TextField
                label="Value"
                value={String(payload.value)}
                onChange={(event) => setPayload({ ...payload, value: Number(event.target.value) })}
                inputMode="decimal"
              />
              <TextField label="Unit" value={payload.unit} onChange={(event) => setPayload({ ...payload, unit: event.target.value })} />
            </div>
            <p className="text-sm text-muted">{payload.source === "import" ? "Imported" : "Filed by hand"}</p>
          </>
        ) : null}
        {payload.kind === "place" ? (
          <PlaceEditor place={placePayload(payload.lat, payload.lon, payload)} onChange={setPayload} onFix={(fix) => void hold.dropFix(fix)} />
        ) : null}
        {payload.kind === "contact" ? (
          <div className="flex flex-col gap-1">
            {payload.handles.map((handle) => (
              <p key={`${handle.kind}:${handle.value}`} className="text-sm text-fg">
                <span className="font-mono text-xs tracking-wider text-muted uppercase">{SPOT_LABEL[handle.kind]}</span> {handle.value}
              </p>
            ))}
            {payload.handles.length === 0 ? <p className="text-sm text-muted">No handles yet. Tag one from the details below.</p> : null}
          </div>
        ) : null}
        <AreaField label="Details" value={body} onChange={(event) => setBody(event.target.value)} />
        <SpotBar text={`${title}\n${body}`} explicit={body || title} entries={hold.entries} onUse={(kind, value) => void useSpot(kind, value)} onAddTo={(id, kind, value) => void addTo(id, kind, value)} />
        {notice ? <p className="text-sm text-fg">{notice}</p> : null}
        <TaxonomyFields
          categories={hold.categories}
          tags={hold.tags}
          categoryName={categoryName}
          onCategoryName={setCategoryName}
          tagIds={tagIds}
          onToggleTag={(id) => setTagIds((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]))}
          tagDraft={tagDraft}
          onTagDraft={setTagDraft}
          onAddTag={() => void addTag()}
        />
        {error ? <p className="text-sm text-fg">{error}</p> : null}
        <Button tone="accent" onClick={() => void save()}>
          Save changes
        </Button>
        {confirming ? (
          <div className="flex flex-col gap-2 rounded-md border border-muted/30 p-3">
            <p className="text-sm text-fg">Delete this from the device? If you sync later, the server is told to drop it too.</p>
            <div className="flex gap-2">
              <Button
                onClick={() => {
                  void hold.removeEntry(entry.id);
                  onClose();
                }}
              >
                Delete
              </Button>
              <Button tone="quiet" onClick={() => setConfirming(false)}>
                Keep
              </Button>
            </div>
          </div>
        ) : (
          <Button tone="quiet" onClick={() => setConfirming(true)}>
            Delete
          </Button>
        )}
      </div>
    </div>
  );
}

function renderCard(title: string, body: string): Promise<Blob> {
  const canvas = document.createElement("canvas");
  canvas.width = 800;
  canvas.height = 1120;
  const context = canvas.getContext("2d");
  if (!context) return Promise.reject(new Error("Could not start a page."));
  context.fillStyle = "#ebe6d6";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = "#12140f";
  context.font = "600 40px sans-serif";
  const lines = wrap(`${title}\n\n${body}`, context, 720);
  lines.forEach((line, index) => context.fillText(line, 40, 80 + index * 52));
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not start a page."))), "image/png");
  });
}

function wrap(text: string, context: CanvasRenderingContext2D, width: number): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = "";
  for (const word of words) {
    const next = line ? `${line} ${word}` : word;
    if (context.measureText(next).width > width && line) {
      lines.push(line);
      line = word;
    } else line = next;
  }
  if (line) lines.push(line);
  return lines.slice(0, 18);
}
