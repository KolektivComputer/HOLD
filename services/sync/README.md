# Sync

Not built yet. Clients already speak this.

`POST {base}/v1/sync`

`base` may already end in `/v1` or `/v1/sync`. Send `Authorization: Bearer <token>` when a token is set.

```json
{
  "deviceId": "…",
  "deviceName": "This pocket",
  "since": null,
  "categories": [],
  "tags": [],
  "views": [],
  "upserts": [],
  "deleted": []
}
```

Reply:

```json
{
  "cursor": "opaque",
  "upserts": [],
  "deleted": [],
  "categories": [],
  "tags": [],
  "views": []
}
```

`cursor` is required. Entries use `payload.kind` as the discriminator (`note`, `number`, `voice`, `radio`, `health`, `place`). Ignore `dirty` and `syncedAt` if a client sends them. Last write by `updatedAt` wins. A dirty local edit newer than the server copy is kept.

Voice audio stays on the device for now. The transcript syncs.
