import { useEffect, useRef, useState } from "react";
import { TILE_SIZE, project, tileUrl, unproject } from "@/lib/hold/geo";

export type MapPin = { lat: number; lon: number; label: string };

export function SlippyMap({
  lat,
  lon,
  pins = [],
  onPick,
}: {
  lat: number;
  lon: number;
  pins?: MapPin[];
  onPick?: (lat: number, lon: number) => void;
}) {
  const frame = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(14);
  const [size, setSize] = useState({ width: 320, height: 240 });
  const [center, setCenter] = useState({ lat, lon });
  const drag = useRef<{ x: number; y: number; lat: number; lon: number } | null>(null);

  useEffect(() => {
    setCenter({ lat, lon });
  }, [lat, lon]);

  useEffect(() => {
    const node = frame.current;
    if (!node) return;
    const observer = new ResizeObserver(() => {
      const rect = node.getBoundingClientRect();
      setSize({ width: Math.max(1, rect.width), height: Math.max(1, rect.height) });
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const origin = project(center.lat, center.lon, zoom);
  const left = origin.x - size.width / 2;
  const top = origin.y - size.height / 2;
  const x0 = Math.floor(left / TILE_SIZE) - 1;
  const x1 = Math.floor((left + size.width) / TILE_SIZE) + 1;
  const y0 = Math.floor(top / TILE_SIZE) - 1;
  const y1 = Math.floor((top + size.height) / TILE_SIZE) + 1;
  const tiles = [];
  for (let x = x0; x <= x1; x += 1) {
    for (let y = y0; y <= y1; y += 1) {
      if (y < 0 || y >= 2 ** zoom) continue;
      tiles.push({ x, y, key: `${zoom}/${x}/${y}` });
    }
  }

  const place = (pinLat: number, pinLon: number) => {
    const point = project(pinLat, pinLon, zoom);
    return { left: point.x - left, top: point.y - top };
  };

  return (
    <div className="flex flex-col gap-2">
      <div
        ref={frame}
        className="relative h-64 w-full overflow-hidden rounded-md border border-muted/30 bg-panel"
        style={{ touchAction: "none" }}
        onPointerDown={(event) => {
          drag.current = { x: event.clientX, y: event.clientY, lat: center.lat, lon: center.lon };
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => {
          const start = drag.current;
          if (!start) return;
          const moved = project(start.lat, start.lon, zoom);
          const next = unproject(moved.x - (event.clientX - start.x), moved.y - (event.clientY - start.y), zoom);
          setCenter(next);
        }}
        onPointerUp={(event) => {
          const start = drag.current;
          drag.current = null;
          if (!start || !onPick) return;
          if (Math.hypot(event.clientX - start.x, event.clientY - start.y) > 6) return;
          const rect = event.currentTarget.getBoundingClientRect();
          const next = unproject(left + (event.clientX - rect.left), top + (event.clientY - rect.top), zoom);
          onPick(next.lat, next.lon);
        }}
        onPointerCancel={() => {
          drag.current = null;
        }}
      >
        {tiles.map((tile) => (
          <Tile key={tile.key} zoom={zoom} x={tile.x} y={tile.y} left={tile.x * TILE_SIZE - left} top={tile.y * TILE_SIZE - top} />
        ))}
        {pins.map((pin) => {
          const point = place(pin.lat, pin.lon);
          return (
            <span
              key={`${pin.label}-${pin.lat}`}
              className="absolute size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-bg bg-accent"
              style={{ left: point.left, top: point.top }}
              title={pin.label}
            />
          );
        })}
      </div>
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-muted">© OpenStreetMap © CARTO</p>
        <div className="flex gap-2">
          <button type="button" className="min-h-11 min-w-11 text-fg" onClick={() => setZoom((value) => Math.max(3, value - 1))} aria-label="Zoom out">
            −
          </button>
          <button type="button" className="min-h-11 min-w-11 text-fg" onClick={() => setZoom((value) => Math.min(16, value + 1))} aria-label="Zoom in">
            +
          </button>
        </div>
      </div>
    </div>
  );
}

function Tile({ zoom, x, y, left, top }: { zoom: number; x: number; y: number; left: number; top: number }) {
  const [src, setSrc] = useState(() => tileUrl(zoom, x, y));
  useEffect(() => {
    const url = tileUrl(zoom, x, y);
    let objectUrl = "";
    let dead = false;
    setSrc(url);
    void (async () => {
      if (typeof caches === "undefined") return;
      const cache = await caches.open("hold-tiles");
      const hit = await cache.match(url);
      if (!hit || dead) return;
      objectUrl = URL.createObjectURL(await hit.blob());
      if (!dead) setSrc(objectUrl);
    })();
    return () => {
      dead = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [zoom, x, y]);
  return <img src={src} alt="" draggable={false} className="absolute size-64 max-w-none" style={{ left, top, width: TILE_SIZE, height: TILE_SIZE }} />;
}
