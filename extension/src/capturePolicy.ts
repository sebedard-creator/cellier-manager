import type { Source } from "./types.js";

const DOMAINS: Record<Source, string> = {
  VIVINO: "vivino.com",
  UNTAPPD: "untappd.com",
  SAQ: "saq.com"
};

export function validateSourceUrl(value: string, source: Source): boolean {
  try {
    const url = new URL(value);
    const expected = DOMAINS[source];
    const host = url.hostname.toLowerCase();
    return url.protocol === "https:" && !url.username && !url.password &&
      (host === expected || host.endsWith(`.${expected}`));
  } catch {
    return false;
  }
}

export function captureFitsBounds(productHtml: string, visibleText: string): boolean {
  return new TextEncoder().encode(productHtml).length <= 1_048_576 &&
    new TextEncoder().encode(visibleText).length <= 200_000;
}
