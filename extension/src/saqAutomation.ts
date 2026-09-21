import type { Job } from "./types.js";

function parsedSaqUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    return url.protocol === "https:" && (host === "saq.com" || host.endsWith(".saq.com")) ? url : null;
  } catch {
    return null;
  }
}

export function isSaqSearchUrl(value: string): boolean {
  const url = parsedSaqUrl(value);
  return Boolean(url && /\/catalogsearch\/result\/?$/i.test(url.pathname));
}

export function isSaqProductUrl(value: string): boolean {
  const url = parsedSaqUrl(value);
  return Boolean(url && /^\/(?:fr|en)\/\d+\/?$/i.test(url.pathname));
}

function normalizedTokens(value: string): string[] {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().match(/[a-z0-9]+/g)?.filter(token => token.length > 1) || [];
}

export function saqCandidateMatches(title: string, identity: Job["identity"]): boolean {
  const titleTokens = new Set(normalizedTokens(title));
  const titleCompact = [...titleTokens].join("");
  const nameTokens = normalizedTokens(identity.name);
  const producerTokens = normalizedTokens(identity.producer);
  if (!nameTokens.length || !nameTokens.every(token => titleTokens.has(token))) return false;
  if (!producerTokens.length) return true;
  const producerCompact = producerTokens.join("");
  return producerTokens.some(token => titleTokens.has(token)) ||
    (producerCompact.length >= 5 && titleCompact.includes(producerCompact));
}
