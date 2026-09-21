import type { Job } from "./types.js";

function parsedVivinoUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    return url.protocol === "https:" && (host === "vivino.com" || host.endsWith(".vivino.com")) ? url : null;
  } catch {
    return null;
  }
}

export function isVivinoSearchUrl(value: string): boolean {
  const url = parsedVivinoUrl(value);
  return Boolean(url && /^\/[a-z]{2}\/explore\/?$/i.test(url.pathname) && url.searchParams.has("search_term"));
}

export function isVivinoProductUrl(value: string): boolean {
  const url = parsedVivinoUrl(value);
  return Boolean(url && /^\/[a-z]{2}\/.+\/w\/\d+\/?$/i.test(url.pathname));
}

function normalizedTokens(value: string): string[] {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().match(/[a-z0-9]+/g)?.filter(token => token.length > 1) || [];
}

export function vivinoCandidateMatches(
  title: string,
  candidateUrl: string,
  identity: Job["identity"],
): boolean {
  const parsed = parsedVivinoUrl(candidateUrl);
  if (!parsed || !isVivinoProductUrl(candidateUrl)) return false;
  const titleTokens = new Set(normalizedTokens(title));
  const titleCompact = [...titleTokens].join("");
  const nameTokens = normalizedTokens(identity.name);
  const producerTokens = normalizedTokens(identity.producer);
  if (!nameTokens.length || !nameTokens.every(token => titleTokens.has(token))) return false;
  if (producerTokens.length) {
    const producerCompact = producerTokens.join("");
    const producerMatches = producerTokens.some(token => titleTokens.has(token)) ||
      (producerCompact.length >= 5 && titleCompact.includes(producerCompact));
    if (!producerMatches) return false;
  }
  if (identity.vintage) {
    const observedYear = parsed.searchParams.get("year");
    if (observedYear && observedYear !== identity.vintage) return false;
    if (!observedYear && !titleTokens.has(identity.vintage)) return false;
  }
  return true;
}

export function vivinoProductUrlForIdentity(value: string, identity: Job["identity"]): string {
  const url = parsedVivinoUrl(value);
  if (!url || !isVivinoProductUrl(value)) return value;
  if (identity.vintage) url.searchParams.set("year", identity.vintage);
  return url.toString();
}
