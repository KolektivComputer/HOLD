import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import type { Category, Tag } from "@/lib/hold/types";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export const fieldClass =
  "w-full rounded-md border border-muted/30 bg-bg px-3 text-base text-fg placeholder:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

export function Button({
  tone = "plain",
  className,
  type = "button",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "plain" | "accent" | "quiet" }) {
  return (
    <button
      type={type}
      className={cn(
        "inline-flex min-h-11 items-center justify-center gap-2 rounded-md px-4 text-sm font-medium disabled:opacity-50",
        tone === "accent" && "bg-accent text-bg",
        tone === "plain" && "border border-muted/30 bg-panel text-fg",
        tone === "quiet" && "bg-transparent px-2 text-muted",
        className,
      )}
      {...props}
    />
  );
}

export function TextField({ label, ...props }: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="font-mono text-xs tracking-wider text-muted uppercase">{label}</span>
      <input {...props} className={cn(fieldClass, "min-h-11", props.className)} />
    </label>
  );
}

export function AreaField({ label, ...props }: { label: string } & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="font-mono text-xs tracking-wider text-muted uppercase">{label}</span>
      <textarea {...props} className={cn(fieldClass, "min-h-28 py-3", props.className)} />
    </label>
  );
}

export function SelectField({
  label,
  children,
  ...props
}: { label: string; children: ReactNode } & SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="font-mono text-xs tracking-wider text-muted uppercase">{label}</span>
      <select {...props} className={cn(fieldClass, "min-h-11", props.className)}>
        {children}
      </select>
    </label>
  );
}

export function TaxonomyFields({
  categories,
  tags,
  categoryName,
  onCategoryName,
  tagIds,
  onToggleTag,
  tagDraft,
  onTagDraft,
  onAddTag,
}: {
  categories: Category[];
  tags: Tag[];
  categoryName: string;
  onCategoryName: (value: string) => void;
  tagIds: string[];
  onToggleTag: (id: string) => void;
  tagDraft: string;
  onTagDraft: (value: string) => void;
  onAddTag: () => void;
}) {
  const visibleTags = tags.filter((tag) => !tag.deletedAt);
  const visibleCategories = categories.filter((category) => !category.deletedAt);
  return (
    <div className="flex flex-col gap-4">
      <TextField label="Category" value={categoryName} onChange={(event) => onCategoryName(event.target.value)} list="hold-categories" placeholder="Optional" />
      <datalist id="hold-categories">
        {visibleCategories.map((category) => (
          <option key={category.id} value={category.name} />
        ))}
      </datalist>
      <div className="flex flex-col gap-2">
        <span className="font-mono text-xs tracking-wider text-muted uppercase">Tags</span>
        {visibleTags.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {visibleTags.map((tag) => {
              const on = tagIds.includes(tag.id);
              return (
                <button
                  key={tag.id}
                  type="button"
                  aria-pressed={on}
                  onClick={() => onToggleTag(tag.id)}
                  className={cn(
                    "min-h-11 rounded-full border px-3 text-sm",
                    on ? "border-transparent bg-accent text-bg" : "border-muted/30 text-fg",
                  )}
                >
                  {tag.name}
                </button>
              );
            })}
          </div>
        ) : (
          <p className="text-sm text-muted">No tags yet.</p>
        )}
        <div className="flex gap-2">
          <input
            value={tagDraft}
            onChange={(event) => onTagDraft(event.target.value)}
            placeholder="New tag"
            className={cn(fieldClass, "min-h-11")}
          />
          <Button type="button" onClick={onAddTag} disabled={!tagDraft.trim()}>
            Add
          </Button>
        </div>
      </div>
    </div>
  );
}

export function Chip({
  pressed,
  children,
  onClick,
}: {
  pressed: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        "min-h-11 shrink-0 rounded-full border px-3 text-sm",
        pressed ? "border-transparent bg-accent text-bg" : "border-muted/30 text-fg",
      )}
    >
      {children}
    </button>
  );
}
