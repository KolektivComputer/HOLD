import { useEffect, useRef, useState } from "react";
import { Chip } from "@/components/hold/ui";
import { parseFlipper, type ParsedRadio } from "@/lib/hold/flipper";
import {
  choiceHint,
  connectFlipper,
  FLIPPER_SHELVES,
  loadFlipperCaps,
  preferredChoice,
  rememberChoice,
  type FlipperCaps,
  type FlipperChoice,
  type FlipperClient,
} from "@/lib/hold/flipper-link";
import type { FlipperFile } from "@/lib/hold/flipper-cli";

const WEB_CHOICES: { id: FlipperChoice; label: string }[] = [
  { id: "serial", label: "USB serial" },
  { id: "webusb", label: "WebUSB" },
  { id: "bluetooth", label: "Bluetooth" },
];

export function FlipperDock({ onImport }: { onImport: (parsed: ParsedRadio) => void }) {
  const client = useRef<FlipperClient | null>(null);
  const [caps, setCaps] = useState<FlipperCaps | null>(null);
  const [choice, setChoice] = useState<FlipperChoice>("serial");
  const [status, setStatus] = useState("Not linked.");
  const [busy, setBusy] = useState(false);
  const [linked, setLinked] = useState(false);
  const [path, setPath] = useState<string | null>(null);
  const [files, setFiles] = useState<FlipperFile[]>([]);

  useEffect(() => {
    let cancel = false;
    void loadFlipperCaps().then((next) => {
      if (cancel) return;
      setCaps(next);
      setChoice(preferredChoice(next));
    });
    return () => {
      cancel = true;
      void client.current?.close();
      client.current = null;
    };
  }, []);

  const choices = caps?.native
    ? [
        ...(caps.nativeUsb ? [{ id: "serial" as const, label: "Cable" }] : []),
        ...(caps.nativeBle ? [{ id: "bluetooth" as const, label: "Bluetooth" }] : []),
      ]
    : WEB_CHOICES;

  const openShelf = async (next: string) => {
    const link = client.current;
    if (!link) return;
    setBusy(true);
    setStatus("Reading the Flipper…");
    try {
      const listed = await link.list(next);
      setPath(next);
      setFiles(listed);
      setStatus(listed.length === 0 ? "That folder is empty." : `${listed.length} on the Flipper.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Couldn't read the Flipper.");
    } finally {
      setBusy(false);
    }
  };

  const connect = async () => {
    setBusy(true);
    setStatus("Waiting for the Flipper…");
    try {
      await client.current?.close();
      const link = await connectFlipper(choice);
      client.current = link;
      setLinked(true);
      setPath(null);
      setFiles([]);
      setStatus(`Linked over ${link.label}. Pick a shelf.`);
    } catch (error) {
      client.current = null;
      setLinked(false);
      setStatus(error instanceof Error ? error.message : "Couldn't link.");
    } finally {
      setBusy(false);
    }
  };

  const disconnect = async () => {
    setBusy(true);
    await client.current?.close().catch(() => undefined);
    client.current = null;
    setLinked(false);
    setPath(null);
    setFiles([]);
    setStatus("Not linked.");
    setBusy(false);
  };

  const importFile = async (file: FlipperFile) => {
    const link = client.current;
    if (!link || file.directory) {
      if (file.directory) await openShelf(file.path);
      return;
    }
    setBusy(true);
    try {
      const text = await link.read(file.path);
      const parsed = parseFlipper(text);
      onImport(parsed);
      setStatus(`Filed ${file.path.split("/").pop() ?? "dump"} into the form.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Couldn't read that file.");
    } finally {
      setBusy(false);
    }
  };

  const parent = path && path !== "/" ? path.split("/").slice(0, -1).join("/") || "/" : null;

  return (
    <div className="flex flex-col gap-3 rounded-md border border-muted/30 bg-panel p-3">
      <p className="font-mono text-xs tracking-wider text-muted uppercase">Flipper</p>
      <div className="flex flex-wrap gap-2">
        {choices.map((item) => (
          <Chip
            key={item.id}
            pressed={choice === item.id}
            onClick={() => {
              setChoice(item.id);
              rememberChoice(item.id);
            }}
          >
            {item.label}
          </Chip>
        ))}
      </div>
      <p className="text-sm text-muted" aria-live="polite">
        {caps ? choiceHint(choice, caps) : "Checking the link…"}
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={busy || !caps}
          onClick={() => void (linked ? disconnect() : connect())}
          className="inline-flex min-h-11 items-center justify-center rounded-md bg-accent px-4 text-sm font-medium text-bg disabled:opacity-50"
        >
          {linked ? "Disconnect" : busy ? "Linking…" : "Link Flipper"}
        </button>
      </div>
      {linked ? (
        <>
          <div className="flex flex-wrap gap-2">
            {FLIPPER_SHELVES.map((shelf) => (
              <Chip key={shelf.path} pressed={path === shelf.path} onClick={() => void openShelf(shelf.path)}>
                {shelf.label}
              </Chip>
            ))}
          </div>
          {path && parent ? (
            <button type="button" onClick={() => void openShelf(parent)} className="min-h-11 self-start text-sm text-accent">
              Up
            </button>
          ) : null}
          <div className="flex flex-col">
            {files.map((file) => (
              <button
                key={file.path}
                type="button"
                disabled={busy}
                onClick={() => void importFile(file)}
                className="min-h-11 border-b border-muted/20 py-2 text-left font-mono text-sm text-fg disabled:opacity-50"
              >
                {file.directory ? `${file.path.split("/").pop()}/` : file.path.split("/").pop()}
              </button>
            ))}
          </div>
        </>
      ) : null}
      <p className="text-sm text-fg" aria-live="polite">
        {status}
      </p>
    </div>
  );
}
