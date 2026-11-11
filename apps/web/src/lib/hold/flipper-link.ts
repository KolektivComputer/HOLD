import { DUMP_ROOTS, parseStorageList, parseStorageRead, type FlipperFile } from "./flipper-cli.ts";
import { RpcLink, type BytePipe } from "./flipper-rpc.ts";
import { connectWebBluetooth, connectWebSerial, connectWebUsb, flipperTransportAvailable, sessionOnPipe } from "./flipper-web.ts";

export type FlipperChoice = "serial" | "webusb" | "bluetooth";

export type FlipperCaps = {
  serial: boolean;
  webusb: boolean;
  bluetooth: boolean;
  native: boolean;
  nativeUsb: boolean;
  nativeBle: boolean;
  framed: boolean;
};

export type FlipperClient = {
  label: string;
  list(path: string): Promise<FlipperFile[]>;
  read(path: string): Promise<string>;
  close(): Promise<void>;
};

export type HoldFlipperNative = {
  available: () => Promise<{ usb: boolean; bluetooth: boolean }> | { usb: boolean; bluetooth: boolean };
  open: (transport: "usb" | "bluetooth") => Promise<void>;
  write: (base64: string) => Promise<void>;
  close: () => Promise<void>;
};

declare global {
  interface Window {
    holdFlipper?: HoldFlipperNative;
  }
}

const CHOICE_KEY = "hold.flipper.transport";
const BYTE_EVENT = "hold-flipper-bytes";

export const FLIPPER_SHELVES: { path: (typeof DUMP_ROOTS)[number]; label: string }[] = [
  { path: "/ext/nfc", label: "NFC" },
  { path: "/ext/lfrfid", label: "RFID" },
  { path: "/ext/subghz", label: "Sub-GHz" },
  { path: "/ext/infrared", label: "Infrared" },
  { path: "/ext/ibutton", label: "iButton" },
];

export function rememberChoice(choice: FlipperChoice) {
  try {
    localStorage.setItem(CHOICE_KEY, choice);
  } catch {
    /* private mode */
  }
}

export function storedChoice(): FlipperChoice | null {
  try {
    const value = localStorage.getItem(CHOICE_KEY);
    if (value === "serial" || value === "webusb" || value === "bluetooth") return value;
  } catch {
    /* private mode */
  }
  return null;
}

function framed(): boolean {
  try {
    return window.self !== window.top;
  } catch {
    return true;
  }
}

export async function loadFlipperCaps(): Promise<FlipperCaps> {
  const web = flipperTransportAvailable();
  const native = window.holdFlipper;
  let nativeUsb = false;
  let nativeBle = false;
  if (native) {
    const caps = await Promise.resolve(native.available());
    nativeUsb = caps.usb === true;
    nativeBle = caps.bluetooth === true;
  }
  return {
    serial: web.serial || nativeUsb,
    webusb: web.usb || nativeUsb,
    bluetooth: web.bluetooth || nativeBle,
    native: Boolean(native),
    nativeUsb,
    nativeBle,
    framed: framed() && !native,
  };
}

export function preferredChoice(caps: FlipperCaps): FlipperChoice {
  const stored = storedChoice();
  if (caps.native) {
    if (stored === "bluetooth" && caps.nativeBle) return "bluetooth";
    if ((stored === "serial" || stored === "webusb") && caps.nativeUsb) return "serial";
    if (caps.nativeUsb) return "serial";
    return "bluetooth";
  }
  if (stored) return stored;
  if (caps.serial) return "serial";
  if (caps.webusb) return "webusb";
  return "bluetooth";
}

export function choiceHint(choice: FlipperChoice, caps: FlipperCaps): string {
  if (caps.framed && !caps.native) {
    return "This preview frame can't claim USB or Bluetooth. Open HOLD in Chrome, or use the phone app.";
  }
  if (choice === "bluetooth") {
    if (!caps.bluetooth) return "Bluetooth needs Chrome, Edge, or the phone install.";
    return "Turn Bluetooth on in the Flipper, and leave the official app disconnected.";
  }
  if (choice === "webusb") {
    if (caps.native && caps.nativeUsb) return "The phone app takes the cable directly.";
    if (!caps.webusb) return "WebUSB needs Chrome. On a computer, USB serial is the one that usually works.";
    return "Android Chrome can take the cable this way. A desktop often already owns it as a serial port.";
  }
  if (caps.native && !caps.nativeUsb) return "This iPhone can't take a Flipper cable. Use Bluetooth.";
  if (!caps.serial && !caps.nativeUsb) return "USB serial needs Chrome or Edge on a computer, or the Android install.";
  return "Plug in the Flipper. qFlipper has to be closed or it keeps the port.";
}

function explain(error: unknown): Error {
  if (error instanceof DOMException && error.name === "NotFoundError") return new Error("No Flipper was chosen.");
  if (error instanceof DOMException && error.name === "SecurityError") {
    return new Error("The browser blocked the device prompt. Open HOLD in Chrome itself, not inside a frame.");
  }
  if (error instanceof Error) return error;
  return new Error("The Flipper link failed.");
}

function encodeBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let index = 0; index < bytes.length; index += 0x2000) {
    const slice = bytes.subarray(index, index + 0x2000);
    binary += String.fromCharCode(...slice);
  }
  return btoa(binary);
}

function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function nativePipe(): BytePipe {
  const native = window.holdFlipper;
  if (!native) throw new Error("This install has no Flipper bridge.");
  const listeners = new Set<(bytes: Uint8Array) => void>();
  const onEvent = (event: Event) => {
    const detail = (event as CustomEvent<{ base64?: string }>).detail;
    if (!detail?.base64) return;
    const bytes = decodeBase64(detail.base64);
    for (const listener of listeners) listener(bytes);
  };
  window.addEventListener(BYTE_EVENT, onEvent);
  return {
    onData(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    write: (bytes) => native.write(encodeBase64(bytes)),
    close: async () => {
      window.removeEventListener(BYTE_EVENT, onEvent);
      await native.close();
    },
  };
}

async function cliFromSession(session: Awaited<ReturnType<typeof connectWebSerial>>, label: string): Promise<FlipperClient> {
  return {
    label,
    list: async (path) => parseStorageList(path, await session.command(`storage list ${path}`, 12000)),
    read: async (path) => parseStorageRead(`storage read ${path}`, await session.command(`storage read ${path}`, 25000)),
    close: () => session.close(),
  };
}

export async function connectFlipper(choice: FlipperChoice): Promise<FlipperClient> {
  rememberChoice(choice);
  try {
    const native = window.holdFlipper;
    if (native) {
      const caps = await Promise.resolve(native.available());
      const useBle = choice === "bluetooth";
      if (useBle && !caps.bluetooth) throw new Error("This install has no Bluetooth link.");
      if (!useBle && !caps.usb) throw new Error("This phone can't take a Flipper cable. Use Bluetooth.");
      const pipe = nativePipe();
      try {
        await native.open(useBle ? "bluetooth" : "usb");
        if (useBle) {
          const rpc = new RpcLink(pipe);
          return {
            label: "Bluetooth",
            list: (path) => rpc.list(path),
            read: (path) => rpc.read(path),
            close: () => rpc.close(),
          };
        }
        const session = sessionOnPipe(pipe);
        await session.wake();
        return cliFromSession(session, "Cable");
      } catch (error) {
        await pipe.close().catch(() => undefined);
        throw error;
      }
    }
    if (choice === "bluetooth") {
      const pipe = await connectWebBluetooth();
      const rpc = new RpcLink(pipe);
      return {
        label: "Bluetooth",
        list: (path) => rpc.list(path),
        read: (path) => rpc.read(path),
        close: () => rpc.close(),
      };
    }
    if (choice === "webusb") {
      return cliFromSession(await connectWebUsb(), "WebUSB");
    }
    return cliFromSession(await connectWebSerial(), "USB serial");
  } catch (error) {
    throw explain(error);
  }
}
