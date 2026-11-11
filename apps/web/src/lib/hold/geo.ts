const ALPHABET = "23456789CFGHJMPQRVWX";

/** 10-character Open Location Code. The stored latitude and longitude stay exact. */
export function plusCode(lat: number, lon: number): string {
  let latitude = Math.min(90, Math.max(-90, lat));
  let longitude = lon;
  while (longitude < -180) longitude += 360;
  while (longitude >= 180) longitude -= 360;
  if (latitude === 90) latitude = 90 - 1e-12;
  latitude += 90;
  longitude += 180;
  let code = "";
  let latPlace = 20;
  let lonPlace = 20;
  for (let pair = 0; pair < 5; pair += 1) {
    const latDigit = Math.min(19, Math.floor(latitude / latPlace));
    const lonDigit = Math.min(19, Math.floor(longitude / lonPlace));
    code += ALPHABET[latDigit]! + ALPHABET[lonDigit]!;
    latitude -= latDigit * latPlace;
    longitude -= lonDigit * lonPlace;
    latPlace /= 20;
    lonPlace /= 20;
    if (pair === 3) code += "+";
  }
  return code;
}

export function haversineMeters(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const toRad = (value: number) => (value * Math.PI) / 180;
  const dLat = toRad(bLat - aLat);
  const dLon = toRad(bLon - aLon);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.min(1, Math.sqrt(h)));
}

export const TILE_SIZE = 256;
const TILE_HOST = "https://basemaps.cartocdn.com/rastertiles/voyager";

export function tileUrl(z: number, x: number, y: number): string {
  const n = 2 ** z;
  const wrapped = ((x % n) + n) % n;
  const clampedY = Math.min(n - 1, Math.max(0, y));
  return `${TILE_HOST}/${z}/${wrapped}/${clampedY}.png`;
}

export function project(lat: number, lon: number, zoom: number): { x: number; y: number } {
  const n = 2 ** zoom;
  const x = ((lon + 180) / 360) * n * TILE_SIZE;
  const sine = Math.sin((lat * Math.PI) / 180);
  const y = (0.5 - Math.log((1 + sine) / (1 - sine)) / (4 * Math.PI)) * n * TILE_SIZE;
  return { x, y };
}

export function unproject(x: number, y: number, zoom: number): { lat: number; lon: number } {
  const n = 2 ** zoom;
  const lon = (x / (n * TILE_SIZE)) * 360 - 180;
  const latRad = Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / (n * TILE_SIZE))));
  return { lat: (latRad * 180) / Math.PI, lon };
}

export type TileCoord = { z: number; x: number; y: number };

export function tilesAround(lat: number, lon: number): TileCoord[] {
  const tiles: TileCoord[] = [];
  for (const zoom of [13, 14, 15]) {
    const point = project(lat, lon, zoom);
    const x = Math.floor(point.x / TILE_SIZE);
    const y = Math.floor(point.y / TILE_SIZE);
    for (let dx = -1; dx <= 1; dx += 1) {
      for (let dy = -1; dy <= 1; dy += 1) tiles.push({ z: zoom, x: x + dx, y: y + dy });
    }
  }
  return tiles;
}

export async function cacheTiles(tiles: TileCoord[]): Promise<{ stored: number; failed: number }> {
  if (typeof caches === "undefined") return { stored: 0, failed: tiles.length };
  const cache = await caches.open("hold-tiles");
  let stored = 0;
  let failed = 0;
  for (const tile of tiles) {
    const url = tileUrl(tile.z, tile.x, tile.y);
    try {
      if (await cache.match(url)) {
        stored += 1;
        continue;
      }
      const response = await fetch(url);
      if (!response.ok) {
        failed += 1;
        continue;
      }
      await cache.put(url, response.clone());
      stored += 1;
    } catch {
      failed += 1;
    }
  }
  return { stored, failed };
}
