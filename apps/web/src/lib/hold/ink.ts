export const INK_TOOLS = ["pen", "fountain", "pencil", "brush", "marker", "highlighter", "eraser"] as const;
export type InkTool = (typeof INK_TOOLS)[number];

export const DRAW_TOOLS = ["pen", "fountain", "pencil", "brush", "marker", "highlighter"] as const;
export type DrawTool = (typeof DRAW_TOOLS)[number];

export type InkPoint = { x: number; y: number; p: number; t?: number };
export type InkStroke = { tool: DrawTool; color: string; width: number; points: InkPoint[] };

export const INK_COLORS = ["#12140f", "#e0a106", "#9c3b2e", "#2d5d8a", "#ebe6d6"] as const;

const WIDTH: Record<DrawTool, number> = {
  pen: 2.6,
  fountain: 3.2,
  pencil: 1.5,
  brush: 14,
  marker: 18,
  highlighter: 32,
};

export function inkWidth(tool: DrawTool): number {
  return WIDTH[tool];
}

function clamp(value: number, max = 1000) {
  return Math.min(max, Math.max(0, value));
}

function isDrawTool(value: unknown): value is DrawTool {
  return typeof value === "string" && (DRAW_TOOLS as readonly string[]).includes(value);
}

export function sanitizeInk(value: unknown): InkStroke[] {
  if (!Array.isArray(value)) return [];
  const strokes: InkStroke[] = [];
  for (const item of value.slice(0, 300)) {
    if (!item || typeof item !== "object") continue;
    const row = item as Record<string, unknown>;
    if (!isDrawTool(row.tool) || !Array.isArray(row.points)) continue;
    const color = typeof row.color === "string" && /^#[0-9a-fA-F]{6}$/.test(row.color) ? row.color : INK_COLORS[0];
    const width = typeof row.width === "number" && row.width > 0 && row.width < 80 ? row.width : inkWidth(row.tool);
    const points: InkPoint[] = [];
    for (const point of row.points.slice(0, 4000)) {
      if (!point || typeof point !== "object") continue;
      const raw = point as Record<string, unknown>;
      if (typeof raw.x !== "number" || typeof raw.y !== "number" || !Number.isFinite(raw.x) || !Number.isFinite(raw.y)) continue;
      points.push({
        x: clamp(raw.x),
        y: clamp(raw.y, 1400),
        p: typeof raw.p === "number" && Number.isFinite(raw.p) ? Math.min(1, Math.max(0, raw.p)) : 0.5,
        t: typeof raw.t === "number" && Number.isFinite(raw.t) ? raw.t : undefined,
      });
    }
    if (points.length > 0) strokes.push({ tool: row.tool, color, width, points });
  }
  return strokes;
}
