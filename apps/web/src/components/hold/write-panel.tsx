import { useRef, useState } from "react";
import { HeartPulse, MapPin, Phone, Radio, StickyNote, UserRound } from "lucide-react";
import { AreaField, Button, SelectField, TaxonomyFields, TextField } from "@/components/hold/ui";
import { FlipperDock } from "@/components/hold/flipper-dock";
import { InkPad, type InkPadHandle } from "@/components/hold/ink-pad";
import { PlaceEditor, placePayload, type PlacePayload } from "@/components/hold/place-editor";
import { SpotBar } from "@/components/hold/spot-bar";
import { parseFlipper } from "@/lib/hold/flipper";
import { parseHealthText } from "@/lib/hold/health";
import { PROTOCOL_LABEL, SPOT_LABEL } from "@/lib/hold/format";
import type { InkStroke } from "@/lib/hold/ink";
import { canon, filedSpot } from "@/lib/hold/spots";
import { useHold } from "@/lib/hold/store";
import { newId, RADIO_PROTOCOLS, SPOT_KINDS, type ContactHandle, type Entry, type Kind, type Payload, type RadioProtocol, type SpotKind } from "@/lib/hold/types";

const CHOICES: { kind: Exclude<Kind, "voice">; label: string; detail: string; icon: typeof StickyNote }[] = [
  { kind: "note", label: "Note", detail: "Pen first. A picture if you have one", icon: StickyNote },
  { kind: "number", label: "Number", detail: "A phone you just saw", icon: Phone },
  { kind: "contact", label: "Contact", detail: "A person, and the handles you have", icon: UserRound },
  { kind: "radio", label: "Radio", detail: "A file, a cable, or Bluetooth", icon: Radio },
  { kind: "health", label: "Health", detail: "A reading, by hand or from a file", icon: HeartPulse },
  { kind: "place", label: "Place", detail: "Coordinates first. Street only if you ask", icon: MapPin },
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
  const pad = useRef<InkPadHandle>(null);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [ink, setInk] = useState<InkStroke[]>([]);
  const [shot, setShot] = useState<Blob | null>(null);
  const [shotUrl, setShotUrl] = useState<string | null>(null);
  const [phone, setPhone] = useState("");
  const [handles, setHandles] = useState<ContactHandle[]>([]);
  const [handleKind, setHandleKind] = useState<SpotKind>("discord");
  const [handleValue, setHandleValue] = useState("");
  const [protocol, setProtocol] = useState<RadioProtocol>("nfc");
  const [uid, setUid] = useState("");
  const [frequency, setFrequency] = useState("");
  const [raw, setRaw] = useState("");
  const [metric, setMetric] = useState("Heart rate");
  const [value, setValue] = useState("");
  const [unit, setUnit] = useState("bpm");
  const [place, setPlace] = useState<PlacePayload>(placePayload(null, null));
  const [categoryName, setCategoryName] = useState("");
  const [tagIds, setTagIds] = useState<string[]>([]);
  const [tagDraft, setTagDraft] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const usePicture = (blob: Blob) => {
    if (shotUrl) URL.revokeObjectURL(shotUrl);
    setShot(blob);
    setShotUrl(URL.createObjectURL(blob));
  };

  const snapScreen = async () => {
    if (!navigator.mediaDevices?.getDisplayMedia) {
      setError("This browser can't grab the screen. Import a picture instead.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getDisplayMedia({ video: true });
      const video = document.createElement("video");
      video.srcObject = stream;
      await video.play();
      const canvas = document.createElement("canvas");
      canvas.width = video.videoWidth || 800;
      canvas.height = video.videoHeight || 1120;
      canvas.getContext("2d")?.drawImage(video, 0, 0);
      stream.getTracks().forEach((track) => track.stop());
      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob((next) => (next ? resolve(next) : reject(new Error("Could not grab that screen."))), "image/jpeg", 0.85);
      });
      usePicture(blob);
      setError("");
    } catch {
      setError("The screen grab was cancelled.");
    }
  };

  const fileSpot = async (spotKind: SpotKind, spotValue: string) => {
    const value = spotKind === "phone" ? spotValue : spotValue.replace(new RegExp(`^${spotKind}\\s*`, "i"), "").trim() || spotValue.trim();
    if (kind === "contact") {
      setHandles((current) => (current.some((handle) => handle.kind === spotKind && canon(spotKind, handle.value) === canon(spotKind, value)) ? current : [...current, { kind: spotKind, value }]));
      setNotice(`Added as ${SPOT_LABEL[spotKind].toLowerCase()}. File the contact when the name is right.`);
      return;
    }
    const entry = filedSpot(spotKind, value, hold.sync.deviceId, title);
    await hold.saveEntry(entry);
    setNotice(`Filed as ${SPOT_LABEL[spotKind].toLowerCase()}.`);
  };

  const addToContact = async (id: string, spotKind: SpotKind, spotValue: string) => {
    const entry = hold.entries.find((item) => item.id === id && item.payload.kind === "contact");
    if (!entry || entry.payload.kind !== "contact") return;
    if (entry.payload.handles.some((handle) => handle.kind === spotKind && canon(spotKind, handle.value) === canon(spotKind, spotValue))) {
      setNotice(`Already on ${entry.title}.`);
      return;
    }
    await hold.saveEntry({ ...entry, payload: { kind: "contact", handles: [...entry.payload.handles, { kind: spotKind, value: spotValue.trim() }] } });
    setNotice(`Added to ${entry.title}.`);
  };

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
    if (kind === "note" && !title.trim() && !body.trim() && ink.length === 0 && !shot) {
      setError("Write something first.");
      return;
    }
    if (kind === "contact" && !title.trim() && handles.length === 0 && !body.trim()) {
      setError("Name the person, or add a handle.");
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
    if (kind === "place" && !title.trim() && !body.trim() && place.lat == null) {
      setError("Name the place, or take a position.");
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
      const id = newId();
      const payload: Payload =
        kind === "note"
          ? { kind: "note", ink, ...(shot ? { shotId: id } : {}) }
          : kind === "number"
            ? { kind: "number", phone: phone.trim() }
            : kind === "contact"
              ? { kind: "contact", handles }
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
                : place;
      if (payload.kind === "place" && ((payload.lat != null && !Number.isFinite(payload.lat)) || (payload.lon != null && !Number.isFinite(payload.lon)))) {
        setError("Coordinates need to be numbers.");
        setBusy(false);
        return;
      }
      const now = Date.now();
      const entry: Entry = {
        id,
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
      if (shot) await hold.saveBlob(id, shot);
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
      {kind === "note" ? (
        <>
          <InkPad ref={pad} strokes={ink} onChange={setInk} background={shotUrl} />
          <div className="flex flex-wrap gap-2">
            <FileButton label="Import a picture" accept="image/*" onFile={(file) => usePicture(file)} />
            <Button type="button" onClick={() => void snapScreen()}>
              Grab the screen
            </Button>
          </div>
          <p className="text-sm text-muted">Draw on the page or on a picture. The picture stays on this device.</p>
        </>
      ) : (
        <TextField label={kind === "contact" ? "Name" : "Title"} value={title} onChange={(event) => setTitle(event.target.value)} />
      )}
      {kind === "note" ? <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} /> : null}
      {kind === "number" ? (
        <TextField label="Phone" value={phone} onChange={(event) => setPhone(event.target.value)} inputMode="tel" autoComplete="tel" />
      ) : null}
      {kind === "contact" ? (
        <div className="flex flex-col gap-2">
          {handles.map((handle) => (
            <p key={`${handle.kind}:${handle.value}`} className="text-sm text-fg">
              <span className="font-mono text-xs tracking-wider text-muted uppercase">{SPOT_LABEL[handle.kind]}</span> {handle.value}
            </p>
          ))}
          <div className="flex flex-col gap-2">
            <SelectField label="Kind" value={handleKind} onChange={(event) => setHandleKind(event.target.value as SpotKind)}>
              {SPOT_KINDS.map((item) => (
                <option key={item} value={item}>
                  {SPOT_LABEL[item]}
                </option>
              ))}
            </SelectField>
            <TextField label="Handle" value={handleValue} onChange={(event) => setHandleValue(event.target.value)} />
            <Button
              type="button"
              onClick={() => {
                if (!handleValue.trim()) return;
                setHandles((current) => [...current, { kind: handleKind, value: handleValue.trim() }]);
                setHandleValue("");
              }}
            >
              Add handle
            </Button>
          </div>
        </div>
      ) : null}
      {kind === "radio" ? (
        <>
          <FlipperDock
            onImport={(parsed) => {
              setProtocol(parsed.protocol);
              setUid(parsed.uid);
              setFrequency(parsed.frequencyHz ? String(parsed.frequencyHz) : "");
              setRaw(parsed.raw);
              if (!title.trim()) setTitle(parsed.title);
            }}
          />
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
      {kind === "place" ? <PlaceEditor place={place} onChange={setPlace} onFix={(fix) => void hold.dropFix(fix)} /> : null}
      <AreaField label={kind === "note" ? "Note" : "Details"} value={body} onChange={(event) => setBody(event.target.value)} />
      <SpotBar text={`${title}\n${body}\n${phone}`} explicit={body || phone || title} entries={hold.entries} onUse={(spotKind, spotValue) => void fileSpot(spotKind, spotValue)} onAddTo={(id, spotKind, spotValue) => void addToContact(id, spotKind, spotValue)} />
      {notice ? <p className="text-sm text-fg">{notice}</p> : null}
      <details className="rounded-md border border-muted/25 px-3">
        <summary className="flex min-h-11 cursor-pointer items-center text-sm text-muted">Category and tags</summary>
        <div className="pb-3">
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
        </div>
      </details>
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
  if (payload.kind === "contact") return payload.handles[0]?.value || "Contact";
  if (kind === "place") return payload.kind === "place" && payload.plus ? payload.plus : "Place";
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
