import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseFlipper } from "./flipper.ts";
import { decodeMain, encodeListFrame, encodeReadFrame, rpcStatusMessage, takeFrames } from "./flipper-rpc.ts";
import { sanitizeInk } from "./ink.ts";
import { parseHealthText } from "./health.ts";
import { applyRemote, needsPush } from "./merge.ts";
import { plusCode } from "./geo.ts";
import { isExampleSet } from "./seed.ts";
import { phraseEntry, spotText } from "./spots.ts";
import { syncEndpoint } from "./sync.ts";
import type { Entry } from "./types.ts";

function entry(partial: Partial<Entry> & Pick<Entry, "id" | "updatedAt">): Entry {
  return {
    kind: "note",
    title: partial.id,
    body: "",
    createdAt: partial.updatedAt,
    categoryId: null,
    tagIds: [],
    pinned: false,
    deletedAt: null,
    dirty: false,
    syncedAt: 1,
    deviceId: "device",
    payload: { kind: "note" },
    ...partial,
  };
}

describe("flipper rpc", () => {
  it("frames a storage list the way the Flipper protobuf schema expects", () => {
    const frame = encodeListFrame(1, "/ext/nfc");
    assert.equal(Buffer.from(frame).toString("hex"), "0e08013a0a0a082f6578742f6e6663");
  });

  it("reads a directory entry out of a list response", () => {
    const frame = Buffer.from("0b080142070a050801120161", "hex");
    const { frames, rest } = takeFrames(frame);
    assert.equal(rest.length, 0);
    const message = decodeMain(frames[0]);
    assert.equal(message.commandId, 1);
    assert.equal(message.hasNext, false);
    assert.equal(message.files?.[0]?.name, "a");
    assert.equal(message.files?.[0]?.directory, true);
  });

  it("names a missing file", () => {
    const message = decodeMain(Buffer.from("08011007", "hex"));
    assert.equal(message.status, 7);
    assert.match(rpcStatusMessage(message.status), /isn't on the Flipper/);
    assert.ok(encodeReadFrame(2, "/ext/nfc/card.nfc").length > 4);
  });
});

describe("sanitizeInk", () => {
  it("keeps a pen stroke and drops junk", () => {
    const ink = sanitizeInk([
      { tool: "fountain", color: "#e0a106", width: 3, points: [{ x: 10, y: 20, p: 0.4 }] },
      { tool: "spray", color: "red", points: [{ x: 1, y: 1 }] },
    ]);
    assert.equal(ink.length, 1);
    assert.equal(ink[0]?.tool, "fountain");
    assert.equal(ink[0]?.points[0]?.x, 10);
  });
});
describe("parseFlipper", () => {
  it("reads an NFC dump", () => {
    const parsed = parseFlipper("Filetype: Flipper NFC device\nDevice type: NTAG215\nUID: 04 11 22\n");
    assert.equal(parsed.protocol, "nfc");
    assert.equal(parsed.uid, "04 11 22");
    assert.match(parsed.title, /NTAG215/);
  });

  it("reads a Sub-GHz frequency", () => {
    const parsed = parseFlipper("Filetype: Flipper SubGhz RAW File\nFrequency: 433920000\nProtocol: RAW\n");
    assert.equal(parsed.protocol, "subghz");
    assert.equal(parsed.frequencyHz, 433920000);
  });
});

describe("parseHealthText", () => {
  it("reads a JSON list", () => {
    const rows = parseHealthText('[{"metric":"Heart rate","value":62,"unit":"bpm","recordedAt":"2026-11-10T12:00:00Z"}]');
    assert.equal(rows.length, 1);
    assert.equal(rows[0]?.value, 62);
    assert.equal(rows[0]?.unit, "bpm");
  });
});

describe("merge", () => {
  it("keeps a newer dirty local edit", () => {
    const local = entry({ id: "a", updatedAt: 20, dirty: true, body: "local" });
    const remote = entry({ id: "a", updatedAt: 10, body: "remote" });
    const { next } = applyRemote([local], [remote], []);
    assert.equal(next[0]?.body, "local");
    assert.equal(next[0]?.dirty, true);
  });

  it("takes a clean remote update and drops a clean delete", () => {
    const local = [entry({ id: "a", updatedAt: 5 }), entry({ id: "b", updatedAt: 5 })];
    const remote = entry({ id: "a", updatedAt: 8, body: "server" });
    const { next, removed } = applyRemote(local, [remote], ["b"]);
    assert.equal(next.length, 1);
    assert.equal(next[0]?.body, "server");
    assert.deepEqual(removed, ["b"]);
  });

  it("does not push a delete that never synced", () => {
    assert.equal(needsPush(entry({ id: "a", updatedAt: 1, deletedAt: 2, syncedAt: null, dirty: true })), false);
  });
});

describe("syncEndpoint", () => {
  it("appends the sync path once", () => {
    assert.equal(syncEndpoint("https://hold.home"), "https://hold.home/v1/sync");
    assert.equal(syncEndpoint("https://hold.home/v1/"), "https://hold.home/v1/sync");
    assert.equal(syncEndpoint("http://10.7.0.2:8787/v1/sync"), "http://10.7.0.2:8787/v1/sync");
  });
});

describe("places and spots", () => {
  it("builds a plus code without leaving the device", () => {
    const code = plusCode(47.0000625, 8.0000625);
    assert.match(code, /^[23456789CFGHJMPQRVWX]{8}\+[23456789CFGHJMPQRVWX]{2}$/);
    assert.equal(code.slice(0, 4), "8FVC");
    assert.equal(plusCode(47.0000625, 8.0000625), code);
  });

  it("tags a phone, a discord account, and a fluxer handle", () => {
    const spots = spotText("Call +1 555 014 0188. discord: lizzy. fluxer: mey. ray@example.com");
    assert.ok(spots.some((spot) => spot.kind === "phone"));
    assert.ok(spots.some((spot) => spot.kind === "discord" && spot.value === "lizzy"));
    assert.ok(spots.some((spot) => spot.kind === "fluxer" && spot.value === "mey"));
    assert.ok(spots.some((spot) => spot.kind === "email"));
  });

  it("files a dictated discord account", () => {
    const filed = phraseEntry("discord", "lizzythewitch", "device");
    assert.equal(filed?.kind, "contact");
    assert.equal(filed?.payload.kind === "contact" ? filed.payload.handles[0]?.value : "", "lizzythewitch");
  });

  it("knows the untouched example set", () => {
    assert.equal(
      isExampleSet(["ent_shed", "ent_tow", "ent_voice", "ent_nfc", "ent_hr", "ent_place"].map((id) => ({ id, deletedAt: null }))),
      true,
    );
    assert.equal(isExampleSet([{ id: "mine", deletedAt: null }]), false);
  });
});
