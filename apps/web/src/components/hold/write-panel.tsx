import { useState } from "react";
import { HeartPulse, MapPin, Phone, Radio, StickyNote } from "lucide-react";
import { AreaField, Button, SelectField, TaxonomyFields, TextField } from "@/components/hold/ui";
import { parseFlipper } from "@/lib/hold/flipper";
import { parseHealthText } from "@/lib/hold/health";
import { PROTOCOL_LABEL } from "@/lib/hold/format";
import { useHold } from "@/lib/hold/store";
import { newId, RADIO_PROTOCOLS, type Entry, type Kind, type Payload, type RadioProtocol } from "@/lib/hold/types";

const CHOICES: { kind: Exclude<Kind, "voice">; label: string; detail: string; icon: typeof StickyNote }[] = [
  { kind: "note", label: "Note", detail: "Anything you need later", icon: StickyNote },
  { kind: "number", label: "Number", detail: "A phone you just saw", icon: Phone },
  { kind: "radio", label: "Radio", detail: "NFC, RFID, Sub-GHz, infrared", icon: Radio },
  { kind: "health", label: "Health", detail: "A reading, by hand or from a file", icon: HeartPulse },
  { kind: "place", label: "Place", detail: "A spot. The map comes later", icon: MapPin },
];

export function WritePanel({ onFiled }: { onFiled: (id: string) => void }) {
  const [kind, setKind] = useState<Exclude<Kind, "voice"> | null>(null);
  if (!kind) {
    return (
      <div className="flex flex-col">
        {CHOICES.map((choice) => {
          const Icon = choice.icon;
          return (
            <button
              key={choice.kind}
              type="button"
              onClick={() => setKind(choice.kind)}
              className="flex min-h-16 items-center gap-3 border-b border-muted/20 px-4 py-3 text-left"
            >
              <Icon className="size-5 shrink-0 text-accent" />
              <span>
                <span className="block text-base text-fg">{choice.label}</span>
                <span className="block text-sm text-muted">{choice.detail}</span>
              </span>
            </button>
          );
        })}
      </div>
    );
  }
  return <EntryForm kind={kind} onBack={() => setKind(null)} onFiled={onFiled} />;
}

function EntryForm({
  kind,
  onBack,
  onFiled,
}: {
  kind: Exclude<Kind, "voice">;
  onBack: () => void;
  onFiled: (id: string) => void;
}) {
  const hold = useHold();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [phone, setPhone] = useState("");
  const [protocol, setProtocol] = useState<RadioProtocol>("nfc");
  const [uid, setUid] = useState("");
  const [frequency, setFrequency] = useState("");
  const [raw, setRaw] = useState("");
  const [metric, setMetric] = useState("Heart rate");
  const [value, setValue] = useState("");
  const [unit, setUnit] = useState("bpm");
  const [lat, setLat] = useState("");
  const [lon, setLon] = useState("");
  const [categoryName, setCategoryName] = useState("");
  const [tagIds, setTagIds] = useState<string[]>([]);
  const [tagDraft, setTagDraft] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const addTag = async () => {
    const id = await hold.ensureTag(tagDraft);
    if (!id) return;
    setTagIds((current) => (current.includes(id) ? current : [...current, id]));
    setTagDraft("");
  };

  const onRadioFile = async (file: File) => {
    const text = await file.text();
    const parsed = parseFlipper(text);
    setProtocol(parsed.protocol);
    setUid(parsed.uid);
    setFrequency(parsed.frequencyHz ? String(parsed.frequencyHz) : "");
    setRaw(parsed.raw);
    if (!title.trim()) setTitle(parsed.title);
  };

  const onHealthFile = async (file: File) => {
    try {
      const drafts = parseHealthText(await file.text());
      const count = await hold.importHealth(drafts);
      setError("");
      if (count > 0) onFiled("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not read that file.");
    }
  };

  const fileIt = async () => {
    setError("");
    const numeric = Number(value);
    if (kind === "number" && !phone.trim()) {
      setError("Add the phone number.");
      return;
    }
    if (kind === "note" && !title.trim() && !body.trim()) {
      setError("Write something first.");
      return;
    }
    if (kind === "radio" && !uid.trim() && !raw.trim() && !body.trim()) {
      setError("Add a UID or paste the dump.");
      return;
    }
    if (kind === "health" && !Number.isFinite(numeric)) {
      setError("Add a number for the reading.");
      return;
    }
    if (kind === "place" && !title.trim() && !body.trim()) {
      setError("Name the place.");
      return;
    }
    const frequencyHz = frequency.trim() ? Number(frequency) : null;
    if (kind === "radio" && frequency.trim() && !Number.isFinite(frequencyHz)) {
      setError("Frequency should be a number in Hz.");
      return;
    }
    setBusy(true);
    try {
      const categoryId = await hold.ensureCategory(categoryName);
      const payload: Payload =
        kind === "note"
          ? { kind: "note" }
          : kind === "number"
            ? { kind: "number", phone: phone.trim() }
            : kind === "radio"
              ? {
                  kind: "radio",
                  protocol,
                  uid: uid.trim(),
                  frequencyHz: frequencyHz !== null && Number.isFinite(frequencyHz) ? frequencyHz : null,
                  filetype: "",
                  raw: raw.trim(),
                }
              : kind === "health"
                ? {
                    kind: "health",
                    metric: metric.trim() || "Reading",
                    value: numeric,
                    unit: unit.trim(),
                    source: "manual",
                    recordedAt: Date.now(),
                  }
                : {
                    kind: "place",
                    lat: lat.trim() ? Number(lat) : null,
                    lon: lon.trim() ? Number(lon) : null,
                  };
      if (payload.kind === "place" && ((lat.trim() && !Number.isFinite(payload.lat)) || (lon.trim() && !Number.isFinite(payload.lon)))) {
        setError("Coordinates need to be numbers.");
        setBusy(false);
        return;
      }
      const now = Date.now();
      const entry: Entry = {
        id: newId(),
        kind,
        title: title.trim() || defaultTitle(kind, payload),
        body: body.trim(),
        createdAt: now,
        updatedAt: now,
        categoryId,
        tagIds,
        pinned: false,
        deletedAt: null,
        dirty: true,
        syncedAt: null,
        deviceId: hold.sync.deviceId,
        payload,
      };
      await hold.saveEntry(entry);
      onFiled(entry.id);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form
      className="flex flex-col gap-4 px-4 py-4"
      onSubmit={(event) => {
        event.preventDefault();
        void fileIt();
      }}
    >
      <button type="button" onClick={onBack} className="min-h-11 self-start text-sm text-accent">
        Back
      </button>
      <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} />
      {kind === "number" ? (
        <TextField label="Phone" value={phone} onChange={(event) => setPhone(event.target.value)} inputMode="tel" autoComplete="tel" />
      ) : null}
      {kind === "radio" ? (
        <>
          <SelectField label="Protocol" value={protocol} onChange={(event) => setProtocol(event.target.value as RadioProtocol)}>
            {RADIO_PROTOCOLS.map((item) => (
              <option key={item} value={item}>
                {PROTOCOL_LABEL[item]}
              </option>
            ))}
          </SelectField>
          <TextField label="UID or data" value={uid} onChange={(event) => setUid(event.target.value)} className="font-mono" />
          <TextField label="Frequency Hz" value={frequency} onChange={(event) => setFrequency(event.target.value)} inputMode="decimal" />
          <AreaField label="Dump" value={raw} onChange={(event) => setRaw(event.target.value)} className="font-mono" placeholder="Paste a Flipper file" />
          <FileButton label="Choose a Flipper file" accept=".nfc,.rfid,.sub,.ir,.ibtn,.txt,text/plain" onFile={(file) => void onRadioFile(file)} />
          <p className="text-sm text-muted">A cable to a Flipper isn’t available here. The dump still stays on this device.</p>
        </>
      ) : null}
      {kind === "health" ? (
        <>
          <TextField label="Metric" value={metric} onChange={(event) => setMetric(event.target.value)} />
          <div className="grid grid-cols-2 gap-3">
            <TextField label="Value" value={value} onChange={(event) => setValue(event.target.value)} inputMode="decimal" />
            <TextField label="Unit" value={unit} onChange={(event) => setUnit(event.target.value)} />
          </div>
          <FileButton label="Import readings" accept=".json,.csv,application/json,text/csv,text/plain" onFile={(file) => void onHealthFile(file)} />
          <p className="text-sm text-muted">Apple Health can’t be read from this pocket. Import a short JSON list or CSV with metric, value, unit, and recordedAt.</p>
        </>
      ) : null}
      {kind === "place" ? (
        <>
          <div className="grid grid-cols-2 gap-3">
            <TextField label="Latitude" value={lat} onChange={(event) => setLat(event.target.value)} inputMode="decimal" />
            <TextField label="Longitude" value={lon} onChange={(event) => setLon(event.target.value)} inputMode="decimal" />
          </div>
          <p className="text-sm text-muted">A name is enough. Coordinates are optional. The map is later.</p>
        </>
      ) : null}
      <AreaField label={kind === "note" ? "Note" : "Details"} value={body} onChange={(event) => setBody(event.target.value)} />
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
      <Button type="submit" tone="accent" disabled={busy}>
        {busy ? "Filing…" : "File it"}
      </Button>
    </form>
  );
}

function defaultTitle(kind: Exclude<Kind, "voice">, payload: Payload): string {
  if (payload.kind === "number") return payload.phone || "Number";
  if (payload.kind === "health") return payload.metric;
  if (payload.kind === "radio") return payload.uid ? `${PROTOCOL_LABEL[payload.protocol]} · ${payload.uid}` : "Radio dump";
  if (kind === "place") return "Place";
  return "Note";
}

function FileButton({ label, accept, onFile }: { label: string; accept: string; onFile: (file: File) => void }) {
  return (
    <label className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-md border border-muted/30 bg-panel px-4 text-sm font-medium text-fg">
      {label}
      <input
        type="file"
        accept={accept}
        className="sr-only"
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = "";
          if (file) onFile(file);
        }}
      />
    </label>
  );
}
