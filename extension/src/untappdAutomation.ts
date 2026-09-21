import type { Job } from "./types.js";

export type UntappdCandidate = { url: string; title: string; producer: string };

function parsedUntappdUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    return url.protocol === "https:" && (host === "untappd.com" || host.endsWith(".untappd.com")) ? url : null;
  } catch {
    return null;
  }
}

export function isUntappdSearchUrl(value: string): boolean {
  const url = parsedUntappdUrl(value);
  return Boolean(url && /^\/search\/?$/i.test(url.pathname) && url.searchParams.has("q"));
}

export function isUntappdProductUrl(value: string): boolean {
  const url = parsedUntappdUrl(value);
  return Boolean(url && /^\/b\/[^/]+\/\d+\/?$/i.test(url.pathname));
}

function normalized(value: string): string {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .toLowerCase().match(/[a-z0-9]+/g)?.join(" ") || "";
}

function normalizedName(value: string): string {
  return normalized(value)
    .replace(/\b(\d+)\s+([nsew])\b/g, "$1$2")
    .replace(/\b(?:18|19|20)\d{2}\b/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function producerMatches(observed: string, expected: string): boolean {
  const observedProducer = normalized(observed).replace(/^brasserie\s+/, "");
  const expectedProducer = normalized(expected).replace(/^brasserie\s+/, "");
  return !expectedProducer || observedProducer === expectedProducer ||
    observedProducer.includes(expectedProducer) || expectedProducer.includes(observedProducer);
}

function vintageMatches(title: string, candidateUrl: string, vintage?: string | null): boolean {
  if (!vintage) return true;
  const year = normalized(`${title} ${new URL(candidateUrl).pathname}`).split(" ")
    .find(token => /^(?:18|19|20)\d{2}$/.test(token));
  return year === vintage;
}

export function untappdCandidateMatches(
  title: string,
  producer: string,
  candidateUrl: string,
  identity: Job["identity"],
): boolean {
  if (!isUntappdProductUrl(candidateUrl)) return false;
  const observedName = normalizedName(title);
  const expectedName = normalizedName(identity.name);
  if (!expectedName || observedName !== expectedName) return false;
  return producerMatches(producer, identity.producer) &&
    vintageMatches(title, candidateUrl, identity.vintage);
}

function uniqueCandidateNearMatch(
  candidate: UntappdCandidate,
  identity: Job["identity"],
): boolean {
  if (!isUntappdProductUrl(candidate.url) ||
      !producerMatches(candidate.producer, identity.producer) ||
      !vintageMatches(candidate.title, candidate.url, identity.vintage)) return false;

  const expectedTokens = normalizedName(identity.name).split(" ").filter(Boolean);
  const observedTokens = normalizedName(candidate.title).split(" ").filter(Boolean);
  if (expectedTokens.length < 2) return false;

  let expectedIndex = 0;
  const extras: string[] = [];
  for (const token of observedTokens) {
    if (token === expectedTokens[expectedIndex]) expectedIndex += 1;
    else extras.push(token);
  }
  // The only tolerated addition is a short numbered qualifier such as the
  // missing longitude "4E" in "50°N - 4°E". A named variant such as
  // "Framboise" or "Unblended" remains a mismatch.
  const extra = extras[0];
  return expectedIndex === expectedTokens.length && extras.length === 1 &&
    extra !== undefined && extra.length <= 3 && /\d/.test(extra);
}

export function selectUntappdCandidate(
  candidates: UntappdCandidate[],
  identity: Job["identity"],
): UntappdCandidate | null {
  const exact = candidates.find(candidate => untappdCandidateMatches(
    candidate.title, candidate.producer, candidate.url, identity,
  ));
  if (exact) return exact;
  const onlyCandidate = candidates.length === 1 ? candidates[0] : undefined;
  return onlyCandidate && uniqueCandidateNearMatch(onlyCandidate, identity)
    ? onlyCandidate : null;
}
