export const FLIPPER_VENDOR_ID = 0x0483;
export const FLIPPER_PRODUCT_ID = 0x5740;

export const DUMP_ROOTS = ["/ext/nfc", "/ext/lfrfid", "/ext/subghz", "/ext/infrared", "/ext/ibutton"] as const;

export type FlipperFile = { path: string; directory: boolean };

export function parseStorageList(directory: string, text: string): FlipperFile[] {
  const root = directory.replace(/\/+$/, "");
  const files: FlipperFile[] = [];
  for (const line of text.split(/\r?\n/)) {
    const match = line.trim().match(/^\[([DFdf])\]\s+(.+)$/);
    if (!match) continue;
    const name = match[2].trim().split(/\s+/).pop() ?? "";
    if (!name || name === "." || name === "..") continue;
    files.push({ path: `${root}/${name}`, directory: match[1].toUpperCase() === "D" });
  }
  return files;
}

export function parseStorageRead(command: string, text: string): string {
  const lines = text.replace(/\r/g, "").split("\n");
  const start = lines.findIndex((line) => line.trim() === command.trim() || line.trim().endsWith(command.trim()));
  const body = (start >= 0 ? lines.slice(start + 1) : lines).filter((line) => !/^>:/.test(line.trim()));
  const sizeAt = body.findIndex((line) => /^Size:\s*\d+/.test(line.trim()));
  const content = (sizeAt >= 0 ? body.slice(sizeAt + 1) : body).join("\n").trim();
  return content;
}
