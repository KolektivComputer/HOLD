export const STORES = ["entries", "categories", "tags", "views", "audio", "meta"] as const;
export type StoreName = (typeof STORES)[number];

export interface Kv {
  get<T>(store: StoreName, key: string): Promise<T | undefined>;
  getAll<T>(store: StoreName): Promise<T[]>;
  put(store: StoreName, key: string, value: unknown): Promise<void>;
  del(store: StoreName, key: string): Promise<void>;
  replace(store: StoreName, items: { id: string }[]): Promise<void>;
  clear(store: StoreName): Promise<void>;
}

function request<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("The log could not be read"));
  });
}

class IdbKv implements Kv {
  constructor(private database: IDBDatabase) {}

  private tx(store: StoreName, mode: IDBTransactionMode) {
    return this.database.transaction(store, mode).objectStore(store);
  }

  async get<T>(store: StoreName, key: string): Promise<T | undefined> {
    const value = await request(this.tx(store, "readonly").get(key));
    return value as T | undefined;
  }

  async getAll<T>(store: StoreName): Promise<T[]> {
    const value = await request(this.tx(store, "readonly").getAll());
    return (value ?? []) as T[];
  }

  async put(store: StoreName, key: string, value: unknown): Promise<void> {
    await request(this.tx(store, "readwrite").put(value, key));
  }

  async del(store: StoreName, key: string): Promise<void> {
    await request(this.tx(store, "readwrite").delete(key));
  }

  async clear(store: StoreName): Promise<void> {
    await request(this.tx(store, "readwrite").clear());
  }

  replace(store: StoreName, items: { id: string }[]): Promise<void> {
    return new Promise((resolve, reject) => {
      const tx = this.database.transaction(store, "readwrite");
      const os = tx.objectStore(store);
      os.clear();
      for (const item of items) os.put(item, item.id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error ?? new Error("The log could not be saved"));
    });
  }
}

class MemoryKv implements Kv {
  private maps = new Map<StoreName, Map<string, unknown>>();

  private map(store: StoreName) {
    let current = this.maps.get(store);
    if (!current) {
      current = new Map();
      this.maps.set(store, current);
    }
    return current;
  }

  async get<T>(store: StoreName, key: string): Promise<T | undefined> {
    return this.map(store).get(key) as T | undefined;
  }

  async getAll<T>(store: StoreName): Promise<T[]> {
    return [...this.map(store).values()] as T[];
  }

  async put(store: StoreName, key: string, value: unknown): Promise<void> {
    this.map(store).set(key, value);
  }

  async del(store: StoreName, key: string): Promise<void> {
    this.map(store).delete(key);
  }

  async clear(store: StoreName): Promise<void> {
    this.map(store).clear();
  }

  async replace(store: StoreName, items: { id: string }[]): Promise<void> {
    const map = new Map<string, unknown>();
    for (const item of items) map.set(item.id, item);
    this.maps.set(store, map);
  }
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open("hold", 1);
    req.onupgradeneeded = () => {
      const database = req.result;
      for (const name of STORES) {
        if (!database.objectStoreNames.contains(name)) database.createObjectStore(name);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("Could not open the log"));
  });
}

export async function openKv(): Promise<{ kv: Kv; persisted: boolean }> {
  if (typeof indexedDB === "undefined") return { kv: new MemoryKv(), persisted: false };
  try {
    const database = await openDb();
    return { kv: new IdbKv(database), persisted: true };
  } catch {
    return { kv: new MemoryKv(), persisted: false };
  }
}
