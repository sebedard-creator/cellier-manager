export function dispatchedRequestId(url: string): string | null {
  try {
    const value = new URLSearchParams(new URL(url).hash.slice(1)).get("cellier-request");
    return value && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
      ? value
      : null;
  } catch {
    return null;
  }
}

export function withDispatchedRequestId(url: string, requestId: string): string {
  const parsed = new URL(url);
  const fragment = new URLSearchParams(parsed.hash.slice(1));
  fragment.set("cellier-request", requestId);
  parsed.hash = fragment.toString();
  return parsed.toString();
}
