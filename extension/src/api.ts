import { loadSettings } from "./storage.js";
import type { Job } from "./types.js";
import { extensionDeviceId } from "./storage.js";

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const settings = await loadSettings();
  if (!settings.token) throw new Error("L’extension n’est pas appairée.");
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${settings.token}`);
  if (init.body) headers.set("Content-Type", "application/json");
  return fetch(`${settings.serverUrl}${path}`, { ...init, headers });
}

async function checkedJson<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = body?.error?.message || body?.detail || `Erreur HTTP ${response.status}`;
    throw new Error(error);
  }
  return body as T;
}

export async function heartbeat(): Promise<void> {
  await checkedJson(await request("/extension/v1/heartbeat", { method: "POST" }));
}

export async function listJobs(): Promise<Job[]> {
  const body = await checkedJson<{ jobs: Job[] }>(await request("/extension/v1/jobs"));
  return body.jobs;
}

export async function claimJob(requestId: string): Promise<{ leaseToken: string; leaseExpiresAt: string }> {
  return checkedJson(await request(`/extension/v1/jobs/${encodeURIComponent(requestId)}/claim`, {
    method: "POST", body: JSON.stringify({ extensionVersion: chrome.runtime.getManifest().version })
  }));
}

export async function submitCapture(requestId: string, body: unknown): Promise<unknown> {
  return checkedJson(await request(`/extension/v1/jobs/${encodeURIComponent(requestId)}/captures`, {
    method: "POST", body: JSON.stringify(body)
  }));
}

export async function reportJobProgress(
  requestId: string,
  state: "SEARCHING" | "NAVIGATING" | "CAPTURING" | "NEEDS_USER" | "FAILED",
): Promise<void> {
  await checkedJson(await request(`/extension/v1/jobs/${encodeURIComponent(requestId)}/progress`, {
    method: "POST", body: JSON.stringify({ state })
  }));
}

export async function pairExtension(serverUrl: string, pairingId: string, pairingSecret: string): Promise<string> {
  const response = await fetch(`${serverUrl.replace(/\/$/, "")}/extension/v1/pair`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      pairingId, pairingSecret, deviceId: await extensionDeviceId(),
      name: "Chrome", extensionId: chrome.runtime.id
    })
  });
  const body = await checkedJson<{ token: string }>(response);
  return body.token;
}
