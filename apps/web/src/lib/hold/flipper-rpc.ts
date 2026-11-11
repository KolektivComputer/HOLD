import { type FlipperFile } from "./flipper-cli.ts";

export type BytePipe = {
  write(bytes: Uint8Array): Promise<void>;
  close(): Promise<void>;
  onData(listener: (bytes: Uint8Array) => void): () => void;
};

export type RpcFileChunk = { name: string; directory: boolean; size: number; data: Uint8Array };

export type RpcMessage = {
  commandId: number;
  status: number;
  hasNext: boolean;
  files: RpcFileChunk[] | null;
  readData: Uint8Array | null;
};

const STATUS: Record<number, string> = {
  1: "The Flipper refused.",
  2: "The Flipper couldn't read that message.",
  3: "The Flipper doesn't implement that.",
  4: "The Flipper is busy.",
  5: "The Flipper's storage isn't ready.",
  7: "That file isn't on the Flipper.",
  9: "The Flipper denied that read.",
  10: "That path isn't valid.",
  11: "The Flipper hit a storage error.",
};

export function rpcStatusMessage(status: number): string {
  return STATUS[status] ?? `The Flipper returned error ${status}.`;
}

function encodeVarint(value: number): number[] {
  const out: number[] = [];
  let rest = value >>> 0;
  while (rest > 0x7f) {
    out.push((rest & 0x7f) | 0x80);
    rest >>>= 7;
  }
  out.push(rest);
  return out;
}

function encodeKey(field: number, wire: number): number[] {
  return encodeVarint((field << 3) | wire);
}

function encodeStringField(field: number, text: string): number[] {
  const bytes = [...new TextEncoder().encode(text)];
  return [...encodeKey(field, 2), ...encodeVarint(bytes.length), ...bytes];
}

function encodeMessageField(field: number, inner: number[]): number[] {
  return [...encodeKey(field, 2), ...encodeVarint(inner.length), ...inner];
}

function encodeVarintField(field: number, value: number): number[] {
  if (value === 0) return [];
  return [...encodeKey(field, 0), ...encodeVarint(value)];
}

function frame(body: number[]): Uint8Array {
  return Uint8Array.from([...encodeVarint(body.length), ...body]);
}

function storageRequest(commandId: number, contentField: number, path: string): Uint8Array {
  const body = [...encodeVarintField(1, commandId), ...encodeMessageField(contentField, encodeStringField(1, path))];
  return frame(body);
}

export function encodeListFrame(commandId: number, path: string): Uint8Array {
  return storageRequest(commandId, 7, path);
}

export function encodeReadFrame(commandId: number, path: string): Uint8Array {
  return storageRequest(commandId, 9, path);
}

function readVarint(buf: Uint8Array, offset: number): { value: number; next: number } | null {
  let value = 0;
  let shift = 0;
  let cursor = offset;
  while (cursor < buf.length && shift <= 28) {
    const byte = buf[cursor];
    cursor += 1;
    value |= (byte & 0x7f) << shift;
    if ((byte & 0x80) === 0) return { value, next: cursor };
    shift += 7;
  }
  return null;
}

type Field = { field: number; varint?: number; bytes?: Uint8Array };

function readFields(buf: Uint8Array): Field[] {
  const fields: Field[] = [];
  let offset = 0;
  while (offset < buf.length) {
    const key = readVarint(buf, offset);
    if (!key) throw new Error("The Flipper sent a short message.");
    offset = key.next;
    const field = key.value >>> 3;
    const wire = key.value & 7;
    if (wire === 0) {
      const value = readVarint(buf, offset);
      if (!value) throw new Error("The Flipper sent a short message.");
      offset = value.next;
      fields.push({ field, varint: value.value });
    } else if (wire === 2) {
      const length = readVarint(buf, offset);
      if (!length) throw new Error("The Flipper sent a short message.");
      const start = length.next;
      const end = start + length.value;
      if (end > buf.length) throw new Error("The Flipper sent a short message.");
      fields.push({ field, bytes: asBytes(buf.subarray(start, end)) });
      offset = end;
    } else if (wire === 5) {
      offset += 4;
    } else if (wire === 1) {
      offset += 8;
    } else {
      throw new Error("The Flipper sent a message this app can't read.");
    }
  }
  return fields;
}

function asBytes(input: Uint8Array): Uint8Array {
  const copy = new Uint8Array(input.byteLength);
  copy.set(input);
  return copy;
}

function textOf(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}

function decodeFile(buf: Uint8Array): RpcFileChunk {
  let name = "";
  let directory = false;
  let size = 0;
  let data: Uint8Array = new Uint8Array();
  for (const field of readFields(buf)) {
    if (field.field === 1 && field.varint != null) directory = field.varint === 1;
    if (field.field === 2 && field.bytes) name = textOf(field.bytes);
    if (field.field === 3 && field.varint != null) size = field.varint;
    if (field.field === 4 && field.bytes) data = field.bytes;
  }
  return { name, directory, size, data };
}

export function decodeMain(buf: Uint8Array): RpcMessage {
  let commandId = 0;
  let status = 0;
  let hasNext = false;
  let files: RpcFileChunk[] | null = null;
  let readData: Uint8Array | null = null;
  for (const field of readFields(buf)) {
    if (field.field === 1 && field.varint != null) commandId = field.varint;
    if (field.field === 2 && field.varint != null) status = field.varint;
    if (field.field === 3 && field.varint != null) hasNext = field.varint !== 0;
    if (field.field === 8 && field.bytes) {
      files = [];
      for (const child of readFields(field.bytes)) {
        if (child.field === 1 && child.bytes) files.push(decodeFile(child.bytes));
      }
    }
    if (field.field === 10 && field.bytes) {
      readData = new Uint8Array();
      for (const child of readFields(field.bytes)) {
        if (child.field === 1 && child.bytes) readData = decodeFile(child.bytes).data;
      }
    }
  }
  return { commandId, status, hasNext, files, readData };
}

export function takeFrames(buffer: Uint8Array): { frames: Uint8Array[]; rest: Uint8Array } {
  const frames: Uint8Array[] = [];
  let offset = 0;
  while (offset < buffer.length) {
    const length = readVarint(buffer, offset);
    if (!length) break;
    const end = length.next + length.value;
    if (end > buffer.length) break;
    frames.push(asBytes(buffer.subarray(length.next, end)));
    offset = end;
  }
  return { frames, rest: asBytes(buffer.subarray(offset)) };
}

function concat(left: Uint8Array, right: Uint8Array): Uint8Array {
  const next = new Uint8Array(left.length + right.length);
  next.set(left, 0);
  next.set(right, left.length);
  return next;
}

const READ_LIMIT = 400_000;

export class RpcLink {
  private buffer: Uint8Array = new Uint8Array();
  private commandId = 1;
  private readonly pending = new Map<
    number,
    { messages: RpcMessage[]; resolve: (messages: RpcMessage[]) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }
  >();

  private readonly pipe: BytePipe;

  constructor(pipe: BytePipe) {
    this.pipe = pipe;
    pipe.onData((chunk) => {
      this.buffer = asBytes(concat(this.buffer, chunk));
      this.pump();
    });
  }

  list(path: string): Promise<FlipperFile[]> {
    return this.request(encodeListFrame(this.commandId, path)).then((messages) => {
      const files: FlipperFile[] = [];
      const root = path.replace(/\/+$/, "");
      for (const message of messages) {
        for (const file of message.files ?? []) {
          if (!file.name || file.name === "." || file.name === "..") continue;
          files.push({ path: `${root}/${file.name}`, directory: file.directory });
        }
      }
      return files;
    });
  }

  async read(path: string): Promise<string> {
    const messages = await this.request(encodeReadFrame(this.commandId, path));
    const parts: Uint8Array[] = [];
    let total = 0;
    for (const message of messages) {
      if (!message.readData || message.readData.length === 0) continue;
      total += message.readData.length;
      if (total > READ_LIMIT) throw new Error("That dump is too large to file here.");
      parts.push(message.readData);
    }
    return new TextDecoder().decode(concatAll(parts));
  }

  close(): Promise<void> {
    for (const waiter of this.pending.values()) {
      clearTimeout(waiter.timer);
      waiter.reject(new Error("Flipper connection closed."));
    }
    this.pending.clear();
    return this.pipe.close();
  }

  private request(frameBytes: Uint8Array): Promise<RpcMessage[]> {
    const id = this.commandId;
    this.commandId += 1;
    return new Promise((resolve, reject) => {
      const waiter = {
        messages: [] as RpcMessage[],
        resolve,
        reject,
        timer: setTimeout(() => {
          this.pending.delete(id);
          reject(new Error("The Flipper didn't answer."));
        }, 20000),
      };
      this.pending.set(id, waiter);
      void this.pipe.write(frameBytes).catch((error: unknown) => {
        clearTimeout(waiter.timer);
        this.pending.delete(id);
        reject(error instanceof Error ? error : new Error("The Flipper link dropped."));
      });
    });
  }

  private pump() {
    const taken = takeFrames(this.buffer);
    this.buffer = taken.rest;
    for (const frameBytes of taken.frames) {
      let message: RpcMessage;
      try {
        message = decodeMain(frameBytes);
      } catch (error) {
        this.failAll(error instanceof Error ? error : new Error("The Flipper sent a message this app can't read."));
        return;
      }
      const waiter = this.pending.get(message.commandId);
      if (!waiter) continue;
      if (message.status !== 0) {
        clearTimeout(waiter.timer);
        this.pending.delete(message.commandId);
        waiter.reject(new Error(rpcStatusMessage(message.status)));
        continue;
      }
      waiter.messages.push(message);
      if (!message.hasNext) {
        clearTimeout(waiter.timer);
        this.pending.delete(message.commandId);
        waiter.resolve(waiter.messages);
      }
    }
  }

  private failAll(error: Error) {
    for (const waiter of this.pending.values()) {
      clearTimeout(waiter.timer);
      waiter.reject(error);
    }
    this.pending.clear();
  }
}

function concatAll(parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}
