import { SPOT_LABEL } from "@/lib/hold/format";
import { canon, spotHints } from "@/lib/hold/spots";
import type { Entry, SpotKind } from "@/lib/hold/types";

export function SpotBar({
  text,
  explicit,
  entries,
  onUse,
  onAddTo,
}: {
  text: string;
  explicit?: string;
  entries: Entry[];
  onUse: (kind: SpotKind, value: string) => void;
  onAddTo: (id: string, kind: SpotKind, value: string) => void;
}) {
  const hints = spotHints(entries, text);
  const whole = (explicit ?? text).trim();
  if (!text.trim() && !whole) return null;
  return (
    <div className="flex flex-col gap-2">
      {hints.length ? (
        hints.map((hint) => (
          <div key={`${hint.spot.kind}:${canon(hint.spot.kind, hint.spot.value)}`} className="flex flex-col gap-1 border-b border-muted/20 pb-2">
            <p className="text-sm text-fg">
              <span className="font-mono text-xs tracking-wider text-muted uppercase">{SPOT_LABEL[hint.spot.kind]}</span> {hint.spot.value}
            </p>
            <div className="flex flex-wrap gap-2">
              <button type="button" className="min-h-11 text-sm text-accent" onClick={() => onUse(hint.spot.kind, hint.spot.value)}>
                Save as {SPOT_LABEL[hint.spot.kind].toLowerCase()}
              </button>
              {hint.contacts
                .filter((contact) => !hint.already)
                .map((contact) => (
                  <button
                    key={contact.id}
                    type="button"
                    className="min-h-11 text-sm text-accent"
                    onClick={() => onAddTo(contact.id, hint.spot.kind, hint.spot.value)}
                  >
                    Add to {contact.title}
                  </button>
                ))}
            </div>
            {hint.already ? <p className="text-sm text-muted">Already filed as {hint.already}.</p> : null}
          </div>
        ))
      ) : (
        <p className="text-sm text-muted">Nothing tagged itself. You can still say what it is.</p>
      )}
      <div className="flex flex-wrap gap-2">
        {(["phone", "discord", "email", "x", "fluxer", "address"] as const).map((kind) => (
          <button key={kind} type="button" className="min-h-11 text-sm text-muted" onClick={() => onUse(kind, whole)}>
            Save this as {SPOT_LABEL[kind].toLowerCase()}
          </button>
        ))}
      </div>
    </div>
  );
}
