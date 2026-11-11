import { entryPreview, formatWhen, KIND_LABEL, matches, sortEntries } from "@/lib/hold/format";
import { useHold } from "@/lib/hold/store";
import type { LogFilter } from "@/lib/hold/types";
import { cn } from "@/components/hold/ui";

export function LogList({
  filter,
  selectedId,
  onSelect,
  onClear,
}: {
  filter: LogFilter;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onClear: () => void;
}) {
  const hold = useHold();
  const categories = hold.categories.filter((category) => !category.deletedAt);
  const tags = hold.tags.filter((tag) => !tag.deletedAt);
  const rows = sortEntries(hold.entries.filter((entry) => matches(entry, filter, tags, categories)));
  const narrowed = Boolean(filter.query || filter.viewId || filter.kinds.length || filter.tagIds.length || filter.categoryId);

  return (
    <div className="flex flex-col">
      {hold.showIntro ? (
        <div className="flex flex-col gap-3 border-b border-muted/25 px-4 py-4">
          <p className="max-w-md text-sm text-muted">
            A few examples are in the log so it isn’t blank. They stay on this device until you delete them.
          </p>
          <button type="button" onClick={() => void hold.dismissIntro()} className="min-h-11 self-start text-sm text-accent">
            Hide this
          </button>
        </div>
      ) : null}
      {rows.length === 0 ? (
        <div className="flex flex-col gap-3 px-4 py-8">
          <p className="text-fg">Nothing matches.</p>
          {narrowed ? (
            <button type="button" onClick={onClear} className="min-h-11 self-start text-sm text-accent">
              Clear filters
            </button>
          ) : (
            <p className="max-w-sm text-sm text-muted">Write a note, or use Catch when you can’t type.</p>
          )}
        </div>
      ) : (
        <ul>
          {rows.map((entry) => {
            const preview = entryPreview(entry);
            const category = categories.find((item) => item.id === entry.categoryId);
            return (
              <li key={entry.id}>
                <button
                  type="button"
                  onClick={() => onSelect(entry.id)}
                  className={cn(
                    "flex w-full flex-col gap-1 border-b border-muted/20 px-4 py-3 text-left",
                    entry.pinned && "border-l-2 border-l-accent",
                    selectedId === entry.id && "bg-panel",
                  )}
                >
                  <span className="flex items-center justify-between gap-3">
                    <span className="font-mono text-xs tracking-wider text-muted uppercase">
                      {KIND_LABEL[entry.kind]}
                      {category ? ` · ${category.name}` : ""}
                    </span>
                    <span className="shrink-0 font-mono text-xs text-muted tabular-nums">{formatWhen(entry.updatedAt)}</span>
                  </span>
                  <span className="truncate text-base text-fg">{entry.title || "Untitled"}</span>
                  {preview && preview !== entry.title ? <span className="line-clamp-2 text-sm text-muted">{preview}</span> : null}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
