export async function lookupStreet(input: { data: { lat: number; lon: number } }) {
  const { lat, lon } = input.data;
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) throw new Error("Need a real position first.");
  if (Math.abs(lat) > 90 || Math.abs(lon) > 180) throw new Error("That position is off the map.");
  const url = new URL("https://nominatim.openstreetmap.org/reverse");
  url.searchParams.set("format", "jsonv2");
  url.searchParams.set("lat", String(lat));
  url.searchParams.set("lon", String(lon));
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error("The street lookup did not answer. The coordinates are still saved.");
  const json = (await response.json()) as { display_name?: string };
  if (!json.display_name) throw new Error("No street name came back. The coordinates are still saved.");
  return { address: json.display_name };
}

export async function readWithDeepseek(input: { data: { key: string; image: string } }) {
  const key = input.data.key.trim();
  if (!key) throw new Error("Add a DeepSeek key in Library first.");
  if (!input.data.image.startsWith("data:image/")) throw new Error("That picture could not be packed.");
  const response = await fetch("https://api.deepseek.com/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "deepseek-flash",
      messages: [
        {
          role: "user",
          content: [
            {
              type: "text",
              text: "Read the handwriting or screenshot. Return only the text you can see, one item per line. Keep phone numbers and handles exact. If a part is illegible, skip it.",
            },
            { type: "image_url", image_url: { url: input.data.image } },
          ],
        },
      ],
    }),
  });
  if (!response.ok) throw new Error(response.status === 401 ? "DeepSeek refused that key." : "DeepSeek could not read it.");
  const json = (await response.json()) as { choices?: { message?: { content?: string } }[] };
  const text = json.choices?.[0]?.message?.content?.trim() ?? "";
  if (!text) throw new Error("DeepSeek returned nothing.");
  return { text };
}
