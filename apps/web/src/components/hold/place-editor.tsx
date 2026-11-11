import { useState } from "react";
import { Button, TextField } from "@/components/hold/ui";
import { SlippyMap } from "@/components/hold/slippy-map";
import { cacheTiles, plusCode, tilesAround } from "@/lib/hold/geo";
import { lookupStreet } from "@/lib/hold/remote";
import { newId, type Fix, type Payload } from "@/lib/hold/types";

export type PlacePayload = Extract<Payload, { kind: "place" }>;

export function placePayload(
  lat: number | null,
  lon: number | null,
  rest: Partial<Omit<PlacePayload, "kind" | "lat" | "lon" | "plus">> = {},
): PlacePayload {
  const ok = lat != null && lon != null && Number.isFinite(lat) && Number.isFinite(lon);
  return {
    kind: "place",
    lat: lat != null && Number.isFinite(lat) ? lat : null,
    lon: lon != null && Number.isFinite(lon) ? lon : null,
    accuracyM: rest.accuracyM ?? null,
    plus: ok ? plusCode(lat as number, lon as number) : "",
    address: rest.address ?? "",
    w3w: rest.w3w ?? "",
  };
}

export function PlaceEditor({
  place,
  onChange,
  onFix,
}: {
  place: PlacePayload;
  onChange: (place: PlacePayload) => void;
  onFix?: (fix: Fix) => void;
}) {
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const located = place.lat != null && place.lon != null;

  const here = () => {
    if (!navigator.geolocation) {
      setNote("This browser won't share a position.");
      return;
    }
    setBusy(true);
    setNote("");
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const next = placePayload(position.coords.latitude, position.coords.longitude, {
          accuracyM: Number.isFinite(position.coords.accuracy) ? position.coords.accuracy : null,
          address: place.address,
          w3w: place.w3w,
        });
        onChange(next);
        onFix?.({
          id: newId(),
          at: Date.now(),
          lat: position.coords.latitude,
          lon: position.coords.longitude,
          accuracyM: next.accuracyM,
        });
        setBusy(false);
      },
      () => {
        setNote("Position was blocked. Type the coordinates if you have them.");
        setBusy(false);
      },
      { enableHighAccuracy: true, timeout: 12000 },
    );
  };

  const street = async () => {
    if (place.lat == null || place.lon == null) return;
    setBusy(true);
    setNote("");
    try {
      const result = await lookupStreet({ data: { lat: place.lat, lon: place.lon } });
      onChange({ ...place, address: result.address });
      setNote("Street saved. The coordinates stay the accurate record.");
    } catch (error) {
      setNote(error instanceof Error ? error.message : "No street name came back.");
    } finally {
      setBusy(false);
    }
  };

  const pack = async () => {
    if (place.lat == null || place.lon == null) return;
    setBusy(true);
    setNote("");
    const result = await cacheTiles(tilesAround(place.lat, place.lon));
    setBusy(false);
    setNote(
      result.failed
        ? `Packed ${result.stored} tiles. ${result.failed} did not store, so that part still needs the network.`
        : `Packed ${result.stored} tiles on this device.`,
    );
  };

  return (
    <div className="flex flex-col gap-3">
      {located ? (
        <SlippyMap
          lat={place.lat as number}
          lon={place.lon as number}
          pins={[{ lat: place.lat as number, lon: place.lon as number, label: "Here" }]}
          onPick={(lat, lon) => onChange(placePayload(lat, lon, { address: "", w3w: place.w3w, accuracyM: null }))}
        />
      ) : (
        <p className="text-sm text-muted">No position yet. Use this device, or type coordinates. A tap on the map moves the pin.</p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="button" onClick={here} disabled={busy}>
          {busy ? "Working…" : "Use this position"}
        </Button>
        <Button type="button" onClick={() => void street()} disabled={busy || !located}>
          Nearest street
        </Button>
        <Button type="button" onClick={() => void pack()} disabled={busy || !located}>
          Download this area
        </Button>
      </div>
      {place.plus ? <p className="font-mono text-sm text-fg">{place.plus}</p> : null}
      {place.accuracyM != null ? <p className="text-sm text-muted">Accurate to about {Math.round(place.accuracyM)} m. The coordinates themselves are kept in full.</p> : null}
      {place.address ? <p className="text-sm text-fg">{place.address}</p> : null}
      <div className="grid grid-cols-2 gap-3">
        <TextField
          label="Latitude"
          value={place.lat ?? ""}
          inputMode="decimal"
          onChange={(event) => onChange(placePayload(event.target.value.trim() ? Number(event.target.value) : null, place.lon, place))}
        />
        <TextField
          label="Longitude"
          value={place.lon ?? ""}
          inputMode="decimal"
          onChange={(event) => onChange(placePayload(place.lat, event.target.value.trim() ? Number(event.target.value) : null, place))}
        />
      </div>
      <TextField
        label="what3words"
        value={place.w3w}
        placeholder="///word.word.word"
        onChange={(event) => onChange({ ...place, w3w: event.target.value })}
      />
      <p className="text-sm text-muted">
        The plus code is computed here. The street is only asked for when you tap, from OpenStreetMap. what3words is not contacted — paste one if you already have it.
      </p>
      {note ? <p className="text-sm text-fg">{note}</p> : null}
    </div>
  );
}
