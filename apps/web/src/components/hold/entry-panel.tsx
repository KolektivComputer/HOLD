import { useEffect, useState } from "react";
import { AreaField, Button, SelectField, TaxonomyFields, TextField } from "@/components/hold/ui";
import { downloadBlob, formatHz, KIND_LABEL, PROTOCOL_LABEL, radioExtension, safeFilename, telHref } from "@/lib/hold/format";
import { useHold } from "@/lib/hold/store";
import { RADIO_PROTOCOLS, type Entry, type Payload, type RadioProtocol } from "@/lib/hold/types";

export function EntryPanel({ entry, onClose }: { entry: Entry; onClose: () => void }) {
  const hold = useHold();
  const [title, setTitle] = useState(entry.title);
  const [body, setBody] = useState(entry.body);
  const [categoryName, setCategoryName] = useState("");
  const [tagIds, setTagIds] = useState(entry.tagIds);
  const [tagDraft, setTagDraft] = useState("");
  const [payload, setPayload] = useState<Payload>(entry.payload);
  const [confirming, setConfirming] = useState(false);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [error, setError] = useState("");

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
          <div className="grid grid-cols-2 gap-3">
            <TextField
              label="Latitude"
              value={payload.lat ?? ""}
              onChange={(event) => setPayload({ ...payload, lat: event.target.value.trim() ? Number(event.target.value) : null })}
              inputMode="decimal"
            />
            <TextField
              label="Longitude"
              value={payload.lon ?? ""}
              onChange={(event) => setPayload({ ...payload, lon: event.target.value.trim() ? Number(event.target.value) : null })}
              inputMode="decimal"
            />
          </div>
        ) : null}
        <AreaField label="Details" value={body} onChange={(event) => setBody(event.target.value)} />
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
