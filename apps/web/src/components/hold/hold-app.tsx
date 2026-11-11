import { useState, type ReactNode } from "react";
import { BookOpen, Library, Mic, SquarePen } from "lucide-react";
import { EntryPanel } from "@/components/hold/entry-panel";
import { LibraryPanel } from "@/components/hold/library-panel";
import { LogList } from "@/components/hold/log-list";
import { Chip, cn } from "@/components/hold/ui";
import { VoiceDeck } from "@/components/hold/voice-deck";
import { WritePanel } from "@/components/hold/write-panel";
import { KIND_LABEL } from "@/lib/hold/format";
import { useHold } from "@/lib/hold/store";
import { EMPTY_FILTER, KINDS, filterFromView, type LogFilter } from "@/lib/hold/types";

type Mode = "log" | "voice" | "write" | "library";

export function HoldApp() {
  const hold = useHold();
  const [mode, setMode] = useState<Mode>("log");
  const [filter, setFilter] = useState<LogFilter>(EMPTY_FILTER);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = hold.entries.find((entry) => entry.id === selectedId && !entry.deletedAt) ?? null;
  const views = hold.views.filter((view) => !view.deletedAt);
  const unsynced = hold.entries.filter((entry) => !entry.deletedAt && (entry.dirty || entry.syncedAt === null)).length;

  const openEntry = (id: string) => {
    setSelectedId(id);
    setMode("log");
  };

  return (
    <div className="mx-auto flex h-dvh w-full max-w-6xl overflow-hidden bg-bg text-fg">
      <section className="flex h-full min-w-0 flex-1 flex-col">
        <header className="flex shrink-0 flex-col gap-3 border-b border-muted/25 px-4 pt-4 pb-3">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="size-2 bg-accent" aria-hidden="true" />
              <p className="font-mono text-lg tracking-widest">HOLD</p>
            </div>
            {hold.sync.baseUrl && unsynced > 0 ? (
              <p className="font-mono text-xs text-muted tabular-nums">{unsynced} waiting</p>
            ) : (
              <p className="text-sm text-muted">On this device</p>
            )}
          </div>
          {mode === "log" ? (
            <>
              <input
                value={filter.query}
                onChange={(event) => setFilter((current) => ({ ...current, query: event.target.value, viewId: null }))}
                placeholder="Search the log"
                aria-label="Search the log"
                className="min-h-11 w-full rounded-md border border-muted/30 bg-bg px-3 text-base text-fg placeholder:text-muted"
              />
              <div className="chip-row -mx-4 flex gap-2 overflow-x-auto px-4">
                <Chip
                  pressed={!filter.viewId && filter.kinds.length === 0 && filter.tagIds.length === 0 && !filter.categoryId}
                  onClick={() => setFilter((current) => ({ ...EMPTY_FILTER, query: current.query }))}
                >
                  All
                </Chip>
                {KINDS.map((kind) => (
                  <Chip
                    key={kind}
                    pressed={!filter.viewId && filter.kinds.length === 1 && filter.kinds[0] === kind}
                    onClick={() => setFilter((current) => ({ ...current, kinds: [kind], viewId: null, tagIds: [], categoryId: null }))}
                  >
                    {KIND_LABEL[kind]}
                  </Chip>
                ))}
                {views.map((view) => (
                  <Chip key={view.id} pressed={filter.viewId === view.id} onClick={() => setFilter(filterFromView(view))}>
                    {view.name}
                  </Chip>
                ))}
              </div>
            </>
          ) : (
            <p className="font-mono text-xs tracking-wider text-muted uppercase">
              {mode === "voice" ? "Voice catch" : mode === "write" ? "Write" : "Library"}
            </p>
          )}
        </header>
        <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
          {mode === "log" ? (
            <LogList filter={filter} selectedId={selected?.id ?? null} onSelect={setSelectedId} onClear={() => setFilter(EMPTY_FILTER)} />
          ) : null}
          {mode === "voice" ? <VoiceDeck onOpen={openEntry} /> : null}
          {mode === "write" ? <WritePanel onFiled={openEntry} /> : null}
          {mode === "library" ? <LibraryPanel filter={filter} onApply={setFilter} onShowLog={() => setMode("log")} /> : null}
        </div>
        <nav className="pb-safe grid shrink-0 grid-cols-4 items-end border-t border-muted/25 bg-bg px-2 pt-2" aria-label="Primary">
          <NavButton label="Log" current={mode === "log"} onClick={() => setMode("log")} icon={<BookOpen className="size-5" />} />
          <button
            type="button"
            aria-current={mode === "voice" ? "page" : undefined}
            aria-label="Voice catch"
            onClick={() => setMode("voice")}
            className="flex min-h-11 flex-col items-center justify-center gap-1 text-xs"
          >
            <span className={cn("flex size-12 items-center justify-center rounded-full bg-accent text-bg", mode === "voice" && "ring-2 ring-fg ring-offset-2 ring-offset-bg")}>
              <Mic className="size-5" />
            </span>
            Catch
          </button>
          <NavButton label="Write" current={mode === "write"} onClick={() => setMode("write")} icon={<SquarePen className="size-5" />} />
          <NavButton label="Library" current={mode === "library"} onClick={() => setMode("library")} icon={<Library className="size-5" />} />
        </nav>
      </section>
      <aside
        className={cn(
          "min-h-0 flex-col bg-bg lg:h-full lg:w-1/2 lg:border-l lg:border-muted/25",
          selected ? "fixed inset-0 z-20 flex lg:static" : "hidden lg:flex",
        )}
      >
        {selected ? (
          <EntryPanel entry={selected} onClose={() => setSelectedId(null)} onOpen={openEntry} />
        ) : (
          <div className="px-6 py-8">
            <p className="font-mono text-xs tracking-wider text-muted uppercase">Nothing open</p>
            <p className="mt-2 max-w-xs text-fg">Pick a line in the log, or catch something by voice.</p>
          </div>
        )}
      </aside>
    </div>
  );
}

function NavButton({
  label,
  current,
  onClick,
  icon,
}: {
  label: string;
  current: boolean;
  onClick: () => void;
  icon: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-current={current ? "page" : undefined}
      onClick={onClick}
      className={cn("flex min-h-11 flex-col items-center justify-center gap-1 text-xs", current ? "text-accent" : "text-muted")}
    >
      {icon}
      {label}
    </button>
  );
}
