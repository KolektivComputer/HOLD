import { FLIPPER_PRODUCT_ID, FLIPPER_VENDOR_ID } from "./flipper-cli.ts";
import type { BytePipe } from "./flipper-rpc.ts";

const encoder = new TextEncoder();

const BLE_SERVICE = "8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000";
const BLE_TX = "19ed82ae-ed21-4c9d-4145-228e61fe0000";
const BLE_RX = "19ed82ae-ed21-4c9d-4145-228e62fe0000";
const BLE_FLOW = "19ed82ae-ed21-4c9d-4145-228e63fe0000";

type SerialPortLike = {
  open(options: { baudRate: number }): Promise<void>;
  close(): Promise<void>;
  readable: ReadableStream<Uint8Array> | null;
  writable: WritableStream<Uint8Array> | null;
};

type UsbEndpointLike = { direction: "in" | "out"; type: string; endpointNumber: number };
type UsbAlternateLike = { interfaceClass: number; endpoints: UsbEndpointLike[] };
type UsbInterfaceLike = { interfaceNumber: number; alternates: UsbAlternateLike[] };
type UsbDeviceLike = {
  configuration: { interfaces: UsbInterfaceLike[] } | null;
  open(): Promise<void>;
  close(): Promise<void>;
  selectConfiguration(id: number): Promise<void>;
  claimInterface(index: number): Promise<void>;
  controlTransferOut(setup: UsbSetup, data?: BufferSource): Promise<{ status: string }>;
  transferOut(endpoint: number, data: BufferSource): Promise<{ status: string }>;
  transferIn(endpoint: number, length: number): Promise<{ status: string; data?: DataView }>;
};

type UsbSetup = {
  requestType: "class";
  recipient: "interface";
  request: number;
  value: number;
  index: number;
};

type GattCharacteristic = {
  value: DataView | null;
  startNotifications(): Promise<GattCharacteristic>;
  readValue(): Promise<DataView>;
  writeValue(value: BufferSource): Promise<void>;
  writeValueWithoutResponse?(value: BufferSource): Promise<void>;
  addEventListener(type: "characteristicvaluechanged", listener: (event: Event) => void): void;
};

type GattServer = {
  disconnect(): void;
  connect(): Promise<GattServer>;
  getPrimaryService(uuid: string): Promise<{ getCharacteristic(uuid: string): Promise<GattCharacteristic> }>;
};

type DeviceNav = Navigator & {
  serial?: { requestPort(options?: { filters?: { usbVendorId?: number; usbProductId?: number }[] }): Promise<SerialPortLike> };
  usb?: { requestDevice(options: { filters: { vendorId: number; productId: number }[] }): Promise<UsbDeviceLike> };
  bluetooth?: {
    requestDevice(options: { filters: { namePrefix?: string; services?: string[] }[]; optionalServices?: string[] }): Promise<{
      gatt?: GattServer;
    }>;
  };
};

function nav(): DeviceNav {
  return navigator as DeviceNav;
}

export class FlipperSession {
  private buffer = "";
  private readonly pending: {
    marker: string;
    resolve: (value: string) => void;
    reject: (error: Error) => void;
    timer: number;
  }[] = [];

  constructor(
    private readonly writeBytes: (bytes: Uint8Array) => Promise<void>,
    private readonly stop: () => Promise<void>,
  ) {}

  push(text: string) {
    this.buffer += text;
    this.flush();
  }

  fail(error: Error) {
    for (const waiter of this.pending.splice(0)) {
      window.clearTimeout(waiter.timer);
      waiter.reject(error);
    }
  }

  async wake() {
    await this.writeBytes(encoder.encode("\r"));
    await this.readUntil(">:", 2500).catch(() => "");
  }

  async command(line: string, timeoutMs = 8000): Promise<string> {
    await this.writeBytes(encoder.encode(`${line}\r`));
    const raw = await this.readUntil(">:", timeoutMs);
    return raw.replace(new RegExp(`^\\s*${line.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*`), "").trim();
  }

  async close() {
    this.fail(new Error("Flipper connection closed."));
    await this.stop();
  }

  private readUntil(marker: string, timeoutMs: number): Promise<string> {
    const ready = this.take(marker);
    if (ready != null) return Promise.resolve(ready);
    return new Promise((resolve, reject) => {
      const waiter = {
        marker,
        resolve,
        reject,
        timer: window.setTimeout(() => {
          const index = this.pending.indexOf(waiter);
          if (index >= 0) this.pending.splice(index, 1);
          reject(new Error("The Flipper didn't answer."));
        }, timeoutMs),
      };
      this.pending.push(waiter);
      this.flush();
    });
  }

  private flush() {
    for (let index = 0; index < this.pending.length; ) {
      const taken = this.take(this.pending[index].marker);
      if (taken == null) {
        index += 1;
        continue;
      }
      const waiter = this.pending.splice(index, 1)[0];
      window.clearTimeout(waiter.timer);
      waiter.resolve(taken);
    }
  }

  private take(marker: string): string | null {
    const at = this.buffer.indexOf(marker);
    if (at < 0) return null;
    const value = this.buffer.slice(0, at);
    this.buffer = this.buffer.slice(at + marker.length);
    return value;
  }
}

function pumpText(session: FlipperSession, read: () => Promise<Uint8Array | null>) {
  const decoder = new TextDecoder();
  void (async () => {
    try {
      for (;;) {
        const chunk = await read();
        if (!chunk) break;
        session.push(decoder.decode(chunk, { stream: true }));
      }
    } catch (error) {
      session.fail(error instanceof Error ? error : new Error("Flipper link dropped."));
    }
  })();
}

export function sessionOnPipe(pipe: BytePipe): FlipperSession {
  const decoder = new TextDecoder();
  const session = new FlipperSession((bytes) => pipe.write(bytes), () => pipe.close());
  pipe.onData((bytes) => session.push(decoder.decode(bytes, { stream: true })));
  return session;
}

export async function connectWebSerial(): Promise<FlipperSession> {
  const serial = nav().serial;
  if (!serial) throw new Error("This browser has no USB serial. Use Chrome or Edge on a computer.");
  const port = await serial.requestPort({
    filters: [{ usbVendorId: FLIPPER_VENDOR_ID, usbProductId: FLIPPER_PRODUCT_ID }],
  });
  await port.open({ baudRate: 115200 });
  const reader = port.readable?.getReader();
  const writer = port.writable?.getWriter();
  if (!reader || !writer) throw new Error("The serial port didn't open.");
  const session = new FlipperSession(
    async (bytes) => {
      await writer.write(bytes);
    },
    async () => {
      await reader.cancel().catch(() => undefined);
      writer.releaseLock();
      await port.close().catch(() => undefined);
    },
  );
  pumpText(session, async () => {
    const result = await reader.read();
    if (result.done || !result.value) return null;
    return result.value;
  });
  await session.wake();
  return session;
}

function findInterfaces(device: UsbDeviceLike): { data: { interfaceNumber: number; inbound: number; outbound: number }; comm: number | null } {
  const configuration = device.configuration;
  if (!configuration) throw new Error("The Flipper has no USB configuration.");
  let data: { interfaceNumber: number; inbound: number; outbound: number } | null = null;
  let comm: number | null = null;
  for (const iface of configuration.interfaces) {
    for (const alternate of iface.alternates) {
      if (alternate.interfaceClass === 2) comm = iface.interfaceNumber;
      const inbound = alternate.endpoints.find((endpoint) => endpoint.direction === "in" && endpoint.type === "bulk");
      const outbound = alternate.endpoints.find((endpoint) => endpoint.direction === "out" && endpoint.type === "bulk");
      if (inbound && outbound) {
        data = { interfaceNumber: iface.interfaceNumber, inbound: inbound.endpointNumber, outbound: outbound.endpointNumber };
      }
    }
  }
  if (!data) throw new Error("WebUSB couldn't find the Flipper's serial endpoints.");
  return { data, comm };
}

export async function connectWebUsb(): Promise<FlipperSession> {
  const usb = nav().usb;
  if (!usb) throw new Error("This browser has no WebUSB. Use Chrome or Edge.");
  const device = await usb.requestDevice({
    filters: [{ vendorId: FLIPPER_VENDOR_ID, productId: FLIPPER_PRODUCT_ID }],
  });
  await device.open();
  if (device.configuration == null) await device.selectConfiguration(1);
  const found = findInterfaces(device);
  await device.claimInterface(found.data.interfaceNumber);
  if (found.comm != null && found.comm !== found.data.interfaceNumber) {
    await device.claimInterface(found.comm).catch(() => undefined);
  }
  const controlIndex = found.comm ?? found.data.interfaceNumber;
  const lineCoding = new Uint8Array([0x00, 0xc2, 0x01, 0x00, 0x00, 0x00, 0x08]);
  await device
    .controlTransferOut(
      { requestType: "class", recipient: "interface", request: 0x20, value: 0, index: controlIndex },
      lineCoding,
    )
    .catch(() => undefined);
  await device
    .controlTransferOut({ requestType: "class", recipient: "interface", request: 0x22, value: 0x0003, index: controlIndex })
    .catch(() => undefined);
  const session = new FlipperSession(
    async (bytes) => {
      const result = await device.transferOut(found.data.outbound, Uint8Array.from(bytes));
      if (result.status !== "ok") throw new Error("WebUSB write failed.");
    },
    async () => {
      await device.close().catch(() => undefined);
    },
  );
  pumpText(session, async () => {
    const result = await device.transferIn(found.data.inbound, 512);
    if (result.status !== "ok" || !result.data) return null;
    return new Uint8Array(result.data.buffer, result.data.byteOffset, result.data.byteLength);
  });
  await session.wake();
  return session;
}

function viewBytes(view: DataView): Uint8Array {
  return new Uint8Array(view.buffer, view.byteOffset, view.byteLength);
}

function creditOf(view: DataView): number {
  if (view.byteLength < 4) return 0;
  return view.getUint32(0, false);
}

export async function connectWebBluetooth(): Promise<BytePipe> {
  const bluetooth = nav().bluetooth;
  if (!bluetooth) throw new Error("This browser has no Bluetooth. Use Chrome, or the phone install.");
  const device = await bluetooth.requestDevice({
    filters: [{ namePrefix: "Flipper" }, { services: [BLE_SERVICE] }],
    optionalServices: [BLE_SERVICE],
  });
  const server = await device.gatt?.connect();
  if (!server) throw new Error("Bluetooth didn't connect. Accept the pair prompt on the Flipper.");
  const service = await server.getPrimaryService(BLE_SERVICE);
  const rx = await service.getCharacteristic(BLE_RX);
  const tx = await service.getCharacteristic(BLE_TX);
  const flow = await service.getCharacteristic(BLE_FLOW);
  const listeners = new Set<(bytes: Uint8Array) => void>();
  let credit = 0;
  const waiters: (() => void)[] = [];
  const grant = (value: number) => {
    credit = value;
    waiters.splice(0).forEach((resume) => resume());
  };
  const onTx = (event: Event) => {
    const value = (event.target as GattCharacteristic | null)?.value;
    if (!value) return;
    const bytes = viewBytes(value);
    for (const listener of listeners) listener(bytes);
  };
  const onFlow = (event: Event) => {
    const value = (event.target as GattCharacteristic | null)?.value;
    if (value) grant(creditOf(value));
  };
  tx.addEventListener("characteristicvaluechanged", onTx);
  flow.addEventListener("characteristicvaluechanged", onFlow);
  await tx.startNotifications();
  await flow.startNotifications();
  grant(creditOf(await flow.readValue()));
  let closed = false;
  return {
    onData(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    async write(bytes) {
      let offset = 0;
      while (offset < bytes.length) {
        if (closed) throw new Error("Flipper connection closed.");
        if (credit <= 0) {
          await new Promise<void>((resolve, reject) => {
            const timer = window.setTimeout(() => reject(new Error("The Flipper's Bluetooth link stalled.")), 8000);
            waiters.push(() => {
              window.clearTimeout(timer);
              resolve();
            });
          });
          continue;
        }
        const size = Math.min(20, credit, bytes.length - offset);
        const slice = bytes.slice(offset, offset + size);
        credit -= size;
        offset += size;
        if (rx.writeValueWithoutResponse) await rx.writeValueWithoutResponse(slice);
        else await rx.writeValue(slice);
      }
    },
    async close() {
      closed = true;
      waiters.splice(0).forEach((resume) => resume());
      device.gatt?.disconnect();
    },
  };
}

export function flipperTransportAvailable(): { serial: boolean; usb: boolean; bluetooth: boolean } {
  const current = nav();
  return {
    serial: typeof current.serial !== "undefined",
    usb: typeof current.usb !== "undefined",
    bluetooth: typeof current.bluetooth !== "undefined",
  };
}
