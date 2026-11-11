import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from "react";
import { cn } from "@/components/hold/ui";
import { INK_COLORS, INK_TOOLS, inkWidth, type DrawTool, type InkPoint, type InkStroke, type InkTool } from "@/lib/hold/ink";

export type { InkPoint, InkStroke, InkTool };
export { INK_COLORS, INK_TOOLS };

const TOOL_LABEL: Record<InkTool, string> = {
  pen: "Pen",
  fountain: "Fountain",
  pencil: "Pencil",
  brush: "Brush",
  marker: "Marker",
  highlighter: "Highlighter",
  eraser: "Eraser",
};

export type InkPadHandle = { exportPng: () => Promise<Blob | null> };

export const InkPad = forwardRef<
  InkPadHandle,
  {
    strokes: InkStroke[];
    onChange?: (strokes: InkStroke[]) => void;
    readOnly?: boolean;
    background?: string | null;
  }
>(function InkPad({ strokes, onChange, readOnly = false, background = null }, ref) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const [bgTick, setBgTick] = useState(0);
  const [tool, setTool] = useState<InkTool>("pen");
  const [color, setColor] = useState<string>(INK_COLORS[0]);
  const drawing = useRef<InkStroke | null>(null);
  const penDown = useRef(false);
  const pointerId = useRef<number | null>(null);

  useImperativeHandle(ref, () => ({
    exportPng: () =>
      new Promise((resolve) => {
        const canvas = canvasRef.current;
        if (!canvas) {
          resolve(null);
          return;
        }
        canvas.toBlob((blob) => resolve(blob), "image/png");
      }),
  }));

  useEffect(() => {
    if (!background) {
      imageRef.current = null;
      setBgTick((tick) => tick + 1);
      return;
    }
    const image = new Image();
    image.onload = () => {
      imageRef.current = image;
      setBgTick((tick) => tick + 1);
    };
    image.src = background;
  }, [background]);

  const render = (extra?: InkStroke | null) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    const width = Math.max(1, Math.round(rect.width * ratio));
    const height = Math.max(1, Math.round(rect.height * ratio));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const context = canvas.getContext("2d");
    if (!context) return;
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, rect.width, rect.height);
    context.fillStyle = getComputedStyle(canvas).backgroundColor || "#ebe6d6";
    context.fillRect(0, 0, rect.width, rect.height);
    const photo = imageRef.current;
    if (photo) {
      const scale = Math.min(rect.width / photo.width, rect.height / photo.height);
      const width = photo.width * scale;
      const height = photo.height * scale;
      context.drawImage(photo, (rect.width - width) / 2, (rect.height - height) / 2, width, height);
    }
    for (const stroke of extra ? [...strokes, extra] : strokes) drawStroke(context, stroke, rect.width, rect.height);
  };

  useEffect(() => {
    render(drawing.current);
    const canvas = canvasRef.current;
    if (!canvas) return;
    const observer = new ResizeObserver(() => render(drawing.current));
    observer.observe(canvas);
    return () => observer.disconnect();
    // Repaint only when the stored strokes change. `render` closes over that array.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [strokes, bgTick]);

  const pointAt = (rect: DOMRect, clientX: number, clientY: number, pressure: number, time: number): InkPoint => ({
    x: clamp(((clientX - rect.left) / Math.max(1, rect.width)) * 1000),
    y: clamp(((clientY - rect.top) / Math.max(1, rect.height)) * 1400, 1400),
    p: pressure > 0 ? Math.min(1, pressure) : 0.5,
    t: time,
  });

  const down = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (readOnly || pointerId.current != null) return;
    if (event.pointerType === "touch" && penDown.current) return;
    event.preventDefault();
    pointerId.current = event.pointerId;
    if (event.pointerType === "pen") penDown.current = true;
    event.currentTarget.setPointerCapture(event.pointerId);
    const rect = event.currentTarget.getBoundingClientRect();
    const point = pointAt(rect, event.clientX, event.clientY, event.pressure, event.timeStamp);
    if (tool === "eraser") {
      onChange?.(eraseAt(strokes, point));
      drawing.current = null;
      return;
    }
    drawing.current = { tool, color, width: inkWidth(tool), points: [point] };
    render(drawing.current);
  };

  const extend = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const samples = event.nativeEvent.getCoalescedEvents?.() ?? [event.nativeEvent];
    const stroke = drawing.current;
    if (tool === "eraser") {
      if (!event.buttons) return;
      let next = strokes;
      for (const sample of samples) next = eraseAt(next, pointAt(rect, sample.clientX, sample.clientY, sample.pressure, sample.timeStamp));
      onChange?.(next);
      return;
    }
    if (!stroke) return;
    for (const sample of samples) {
      const point = pointAt(rect, sample.clientX, sample.clientY, sample.pressure, sample.timeStamp);
      const last = stroke.points[stroke.points.length - 1];
      if (last && Math.hypot(last.x - point.x, last.y - point.y) < 1.5) continue;
      stroke.points.push(point);
    }
    render(stroke);
  };

  const move = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (readOnly || pointerId.current !== event.pointerId) return;
    if (event.pointerType === "touch" && penDown.current && tool !== "eraser") return;
    extend(event);
  };

  const up = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (pointerId.current !== event.pointerId) return;
    pointerId.current = null;
    if (event.pointerType === "pen") window.setTimeout(() => (penDown.current = false), 350);
    const stroke = drawing.current;
    drawing.current = null;
    if (!stroke || stroke.points.length === 0 || tool === "eraser") {
      render(null);
      return;
    }
    onChange?.([...strokes, stroke]);
  };

  return (
    <div className="flex flex-col gap-3">
      {readOnly ? null : (
        <>
          <div className="chip-row -mx-4 flex gap-2 overflow-x-auto px-4">
            {INK_TOOLS.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setTool(item)}
                className={cn("min-h-11 rounded-md border px-3 font-mono text-xs uppercase", tool === item ? "border-accent text-accent" : "border-muted/30 text-muted")}
              >
                {TOOL_LABEL[item]}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            {INK_COLORS.map((ink) => (
              <button
                key={ink}
                type="button"
                aria-label={`Ink ${ink}`}
                onClick={() => setColor(ink)}
                className={cn("size-11 rounded-full border-2", color === ink ? "border-accent" : "border-bg")}
                style={{ background: ink }}
              />
            ))}
            <button type="button" onClick={() => onChange?.(strokes.slice(0, -1))} className="ml-auto min-h-11 px-2 text-sm text-muted">
              Undo
            </button>
          </div>
        </>
      )}
      <canvas
        ref={canvasRef}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={up}
        onContextMenu={(event) => event.preventDefault()}
        className="ink-page w-full rounded-md"
        aria-label="Ink page"
      />
    </div>
  );
});

function clamp(value: number, max = 1000) {
  return Math.min(max, Math.max(0, value));
}

function eraseAt(strokes: InkStroke[], point: InkPoint): InkStroke[] {
  return strokes.filter((stroke) => !stroke.points.some((item) => Math.hypot(item.x - point.x, item.y - point.y) < 28));
}

function drawStroke(context: CanvasRenderingContext2D, stroke: InkStroke, width: number, height: number) {
  if (stroke.points.length === 0) return;
  context.save();
  context.lineCap = stroke.tool === "marker" ? "square" : "round";
  context.lineJoin = "round";
  context.strokeStyle = stroke.color;
  context.fillStyle = stroke.color;
  context.globalCompositeOperation = stroke.tool === "highlighter" ? "multiply" : "source-over";
  context.globalAlpha = stroke.tool === "highlighter" ? 0.45 : stroke.tool === "pencil" ? 0.72 : stroke.tool === "marker" ? 0.82 : stroke.tool === "brush" ? 0.9 : 1;
  const place = (point: InkPoint) => ({ x: (point.x / 1000) * width, y: (point.y / 1400) * height });
  if (stroke.points.length === 1) {
    const point = place(stroke.points[0]);
    context.beginPath();
    context.arc(point.x, point.y, Math.max(0.6, markWidth(stroke, 0, width) / 2), 0, Math.PI * 2);
    context.fill();
    context.restore();
    return;
  }
  for (let index = 1; index < stroke.points.length; index += 1) {
    const prev = stroke.points[index - 1];
    const point = stroke.points[index];
    const from = place(prev);
    const to = place(point);
    const size = markWidth(stroke, index, width);
    if (stroke.tool === "brush") {
      context.beginPath();
      context.arc(to.x, to.y, Math.max(0.8, size / 2), 0, Math.PI * 2);
      context.fill();
      continue;
    }
    const grain = stroke.tool === "pencil" ? (hash(index + point.x) - 0.5) * 1.4 : 0;
    context.beginPath();
    context.moveTo(from.x + grain, from.y - grain);
    context.lineTo(to.x + grain, to.y - grain);
    context.lineWidth = size;
    context.stroke();
  }
  context.restore();
}

function markWidth(stroke: InkStroke, index: number, canvasWidth: number): number {
  const point = stroke.points[index];
  const prev = stroke.points[index - 1];
  let scale = 0.45 + point.p * 1.05;
  if (stroke.tool === "fountain" && prev?.t != null && point.t != null) {
    const speed = Math.hypot(point.x - prev.x, point.y - prev.y) / Math.max(8, point.t - prev.t);
    scale *= Math.min(2.4, Math.max(0.35, 0.12 / Math.max(0.015, speed)));
  } else if (stroke.tool === "pencil") {
    scale = 0.65 + point.p * 0.45;
  } else if (stroke.tool === "marker" || stroke.tool === "highlighter") {
    scale = 1;
  } else if (stroke.tool === "brush") {
    scale = 0.28 + point.p * 1.25;
  }
  return (stroke.width * scale * canvasWidth) / 1000;
}

function hash(value: number): number {
  const raw = Math.sin(value * 127.1) * 43758.5453;
  return raw - Math.floor(raw);
}
