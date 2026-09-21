import { cp, mkdir } from "node:fs/promises";

await mkdir("dist", { recursive: true });
await cp("manifest.json", "dist/manifest.json");
await cp("src/popup/popup.html", "dist/popup/popup.html", { recursive: true });
await cp("src/popup/popup.css", "dist/popup/popup.css", { recursive: true });
