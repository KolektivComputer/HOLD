import type { Stamp } from "./types.ts";

export function needsPush(item: Stamp): boolean {
  if (item.deletedAt && item.syncedAt === null) return false;
  return item.dirty || item.syncedAt === null;
}

export function outbound<T extends Stamp>(items: T[]) {
  return {
    live: items.filter((item) => needsPush(item) && !item.deletedAt),
    deleted: items.filter((item) => item.deletedAt && item.dirty && item.syncedAt !== null).map((item) => item.id),
    dropLocal: items.filter((item) => item.deletedAt && item.syncedAt === null).map((item) => item.id),
  };
}

export function applyRemote<T extends Stamp>(
  local: T[],
  incoming: T[],
  deletedIds: string[],
): { next: T[]; removed: string[] } {
  const map = new Map(local.map((item) => [item.id, item]));
  const removed: string[] = [];
  const drop = (id: string) => {
    const current = map.get(id);
    if (!current || !current.dirty) {
      if (current) removed.push(id);
      map.delete(id);
    }
  };
  for (const id of deletedIds) drop(id);
  for (const item of incoming) {
    if (item.deletedAt) {
      drop(item.id);
      continue;
    }
    const current = map.get(item.id);
    if (!current || !(current.dirty && current.updatedAt >= item.updatedAt)) {
      map.set(item.id, { ...item, dirty: false });
    }
  }
  return { next: [...map.values()], removed };
}
