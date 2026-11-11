import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseFlipper } from "./flipper.ts";
import { parseHealthText } from "./health.ts";
import { applyRemote, needsPush } from "./merge.ts";
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
