import { useEffect, useRef, useState } from "react";
import { Mic, Square } from "lucide-react";
import { formatDuration } from "@/lib/hold/format";
import { phraseEntry } from "@/lib/hold/spots";
import { useHold } from "@/lib/hold/store";
import { newId, type Entry } from "@/lib/hold/types";

type SpeechCtor = new () => {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: () => void;
  stop: () => void;
  onresult: ((event: Event) => void) | null;
  onerror: ((event: Event) => void) | null;
  onend: (() => void) | null;
};

function speechCtor(): SpeechCtor | null {
  if (typeof window === "undefined") return null;
  const host = window as unknown as { SpeechRecognition?: SpeechCtor; webkitSpeechRecognition?: SpeechCtor };
  return host.SpeechRecognition ?? host.webkitSpeechRecognition ?? null;
}

function pickMime() {
  if (typeof MediaRecorder === "undefined") return "";
  const types = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4"];
  return types.find((type) => MediaRecorder.isTypeSupported(type)) ?? "";
}

export function VoiceDeck({ onOpen }: { onOpen: (id: string) => void }) {
  const hold = useHold();
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [transcript, setTranscript] = useState("");
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [savedId, setSavedId] = useState<string | null>(null);
  const [phrase, setPhrase] = useState("");
  const [phraseKind, setPhraseKind] = useState("voice");
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const speech = useRef<InstanceType<SpeechCtor> | null>(null);
  const chunks = useRef<Blob[]>([]);
  const started = useRef(0);
  const finalText = useRef("");
  const liveText = useRef("");
  const timer = useRef<number | null>(null);
  const recordingRef = useRef(false);

  const stopTracks = () => {
    stream.current?.getTracks().forEach((track) => track.stop());
    stream.current = null;
    if (timer.current !== null) window.clearInterval(timer.current);
    timer.current = null;
  };

  useEffect(() => {
    return () => {
      recorder.current?.stop();
      speech.current?.stop();
      stopTracks();
    };
  }, []);

  const finish = async (mime: string) => {
    recordingRef.current = false;
    setRecording(false);
    stopTracks();
    const durationMs = Date.now() - started.current;
    const blob = new Blob(chunks.current, { type: mime || "audio/webm" });
    const text = liveText.current.trim();
    if (durationMs < 400 && blob.size < 1000 && !text) {
      setStatus("");
      setError("That was too short to keep.");
      return;
    }
    const id = newId();
    const entry: Entry = {
      id,
      kind: "voice",
      title: text ? text.slice(0, 80) : "Voice",
      body: "",
      createdAt: Date.now(),
      updatedAt: Date.now(),
      categoryId: null,
      tagIds: [],
      pinned: false,
      deletedAt: null,
      dirty: true,
      syncedAt: null,
      deviceId: hold.sync.deviceId,
      payload: {
        kind: "voice",
        durationMs,
        transcript: text,
        hasAudio: blob.size > 0,
        mime: blob.type || mime,
      },
    };
    await hold.saveEntry(entry);
    if (blob.size > 0) await hold.saveAudio(id, blob);
    setSavedId(id);
    setStatus("Saved on this device.");
    setError("");
  };

  const start = async () => {
    setError("");
    setStatus("");
    setSavedId(null);
    setTranscript("");
    finalText.current = "";
    liveText.current = "";
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setError("This browser can’t record audio. Write a note instead.");
      return;
    }
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.current = media;
      const mime = pickMime();
      const rec = new MediaRecorder(media, mime ? { mimeType: mime } : undefined);
      chunks.current = [];
      rec.ondataavailable = (event) => {
        if (event.data.size > 0) chunks.current.push(event.data);
      };
      rec.onstop = () => {
        void finish(rec.mimeType);
      };
      rec.start();
      recorder.current = rec;
      started.current = Date.now();
      setElapsed(0);
      recordingRef.current = true;
      setRecording(true);
      timer.current = window.setInterval(() => setElapsed(Date.now() - started.current), 200);
      const Ctor = speechCtor();
      if (Ctor) {
        const recognition = new Ctor();
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.lang = navigator.language || "en-US";
        recognition.onresult = (event) => {
          const host = event as unknown as {
            resultIndex: number;
            results: ArrayLike<{ isFinal: boolean; 0?: { transcript: string } }>;
          };
          let interim = "";
          for (let index = host.resultIndex; index < host.results.length; index += 1) {
            const piece = host.results[index]?.[0]?.transcript ?? "";
            if (host.results[index]?.isFinal) finalText.current = `${finalText.current} ${piece}`.trim();
            else interim += piece;
          }
          liveText.current = `${finalText.current} ${interim}`.trim();
          setTranscript(liveText.current);
        };
        recognition.onerror = () => {
          /* Audio still saves if speech recognition fails. */
        };
        try {
          recognition.start();
          speech.current = recognition;
        } catch {
          speech.current = null;
        }
      }
    } catch {
      stopTracks();
      setError("The mic is blocked. Allow it for this site, or write a note instead.");
    }
  };

  const stop = () => {
    speech.current?.stop();
    if (recorder.current && recorder.current.state !== "inactive") recorder.current.stop();
    else {
      recordingRef.current = false;
      setRecording(false);
      stopTracks();
    }
  };

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-6 px-6 py-8">
      <p className="font-mono text-xs tracking-wider text-muted uppercase" role="status">
        {recording ? "Listening" : status || "Voice"}
      </p>
      <p className="font-mono text-5xl text-fg tabular-nums">{formatDuration(elapsed)}</p>
      <button
        type="button"
        onClick={() => (recording ? stop() : void start())}
        className="flex size-36 items-center justify-center rounded-full bg-accent text-bg"
        aria-pressed={recording}
        aria-label={recording ? "Stop and save" : "Start voice catch"}
      >
        {recording ? <Square className="size-10" /> : <Mic className="size-12" />}
      </button>
      <p className="max-w-sm text-center text-sm text-muted">
        {recording
          ? transcript || "Talk. Tap again when you’re done."
          : "Tap once to start. Tap again to file it. Keep HOLD open while you drive."}
      </p>
      {transcript && !recording ? <p className="max-w-sm text-center text-fg">{transcript}</p> : null}
      {error ? <p className="max-w-sm text-center text-sm text-fg">{error}</p> : null}
      {savedId ? (
        <button type="button" onClick={() => onOpen(savedId)} className="min-h-11 text-sm text-accent">
          Open it in the log
        </button>
      ) : null}
      <form
        className="flex w-full max-w-sm flex-col gap-3 border-t border-muted/25 pt-6"
        onSubmit={(event) => {
          event.preventDefault();
          const filed = phraseEntry(phraseKind, phrase, hold.sync.deviceId);
          if (!filed) {
            setError("Say something first.");
            return;
          }
          void hold.saveEntry(filed).then(() => {
            setSavedId(filed.id);
            setPhrase("");
            setStatus("Filed from the phrase.");
          });
        }}
      >
        <p className="text-sm text-muted">
          On the watch, a Shortcut can open this pocket as /?kind=voice&text= plus the dictated line. kind is voice, phone, or discord. The same phrases work in the box below.
        </p>
        <label className="flex flex-col gap-1.5">
          <span className="font-mono text-xs tracking-wider text-muted uppercase">Phrase</span>
          <select value={phraseKind} onChange={(event) => setPhraseKind(event.target.value)} className="min-h-11 rounded-md border border-muted/30 bg-bg px-3">
            <option value="voice">Save a voice note</option>
            <option value="phone">Save a phone number</option>
            <option value="discord">Save a Discord account</option>
            <option value="note">Save a note</option>
          </select>
        </label>
        <input
          value={phrase}
          onChange={(event) => setPhrase(event.target.value)}
          placeholder="What you dictated"
          aria-label="Dictated line"
          className="min-h-11 rounded-md border border-muted/30 bg-bg px-3"
        />
        <button type="submit" className="min-h-11 text-sm text-accent">
          File the phrase
        </button>
      </form>
    </div>
  );
}
