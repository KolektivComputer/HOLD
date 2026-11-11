import type { HealthDraft } from "./types.ts";

function asRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function one(value: unknown, fallbackTime: number): HealthDraft | null {
  const row = asRecord(value);
  if (!row) return null;
  const metric = String(row.metric ?? row.type ?? row.name ?? "Reading").trim() || "Reading";
  const numeric = typeof row.value === "number" ? row.value : Number(row.value);
  if (!Number.isFinite(numeric)) return null;
  const unit = String(row.unit ?? "").trim();
  const recorded = row.recordedAt ?? row.at ?? row.date;
  let recordedAt = fallbackTime;
  if (typeof recorded === "number" && Number.isFinite(recorded)) recordedAt = recorded;
  else if (typeof recorded === "string" && recorded.trim()) {
    const parsed = Date.parse(recorded);
    if (Number.isFinite(parsed)) recordedAt = parsed;
  }
  return { metric, value: numeric, unit, recordedAt };
}

function parseJson(text: string): HealthDraft[] {
  const data = JSON.parse(text) as unknown;
  const rows = Array.isArray(data) ? data : [data];
  const drafts = rows.map((row, index) => one(row, Date.now() + index)).filter((row) => row !== null);
  if (drafts.length === 0) throw new Error("No readings in that JSON.");
  return drafts;
}

function parseCsv(text: string): HealthDraft[] {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  if (lines.length < 2) throw new Error("The CSV needs a header and at least one row.");
  const header = lines[0].split(",").map((cell) => cell.trim().toLowerCase());
  const metricAt = header.findIndex((cell) => cell === "metric" || cell === "type" || cell === "name");
  const valueAt = header.findIndex((cell) => cell === "value");
  const unitAt = header.findIndex((cell) => cell === "unit");
  const timeAt = header.findIndex((cell) => ["recordedat", "at", "date", "time"].includes(cell));
  if (valueAt < 0) throw new Error("The CSV needs a value column.");
  const drafts: HealthDraft[] = [];
  for (const line of lines.slice(1)) {
    const cells = line.split(",").map((cell) => cell.trim());
    const draft = one(
      {
        metric: metricAt >= 0 ? cells[metricAt] : "Reading",
        value: cells[valueAt],
        unit: unitAt >= 0 ? cells[unitAt] : "",
        recordedAt: timeAt >= 0 ? cells[timeAt] : undefined,
      },
      Date.now(),
    );
    if (draft) drafts.push(draft);
  }
  if (drafts.length === 0) throw new Error("No readings in that CSV.");
  return drafts;
}

export function parseHealthText(text: string): HealthDraft[] {
  const trimmed = text.trim();
  if (!trimmed) throw new Error("That file was empty.");
  if (trimmed.startsWith("[") || trimmed.startsWith("{")) return parseJson(trimmed);
  return parseCsv(trimmed);
}
