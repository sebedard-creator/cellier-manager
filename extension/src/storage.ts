import type { Settings, TabAssociation } from "./types.js";

const SETTINGS_KEY = "cellierSettings";
const TABS_KEY = "cellierTabAssociations";
const DEVICE_KEY = "cellierExtensionDeviceId";

export async function loadSettings(): Promise<Settings> {
  const result = await chrome.storage.local.get(SETTINGS_KEY);
  const value = result[SETTINGS_KEY] as Partial<Settings> | undefined;
  return {
    serverUrl: value?.serverUrl || "http://127.0.0.1:18765",
    token: value?.token || ""
  };
}

export async function saveSettings(settings: Settings): Promise<void> {
  const normalized = { ...settings, serverUrl: settings.serverUrl.replace(/\/$/, "") };
  await chrome.storage.local.set({ [SETTINGS_KEY]: normalized });
}

export async function extensionDeviceId(): Promise<string> {
  const result = await chrome.storage.local.get(DEVICE_KEY);
  const existing = result[DEVICE_KEY];
  if (typeof existing === "string" && existing) return existing;
  const created = crypto.randomUUID();
  await chrome.storage.local.set({ [DEVICE_KEY]: created });
  return created;
}

export async function loadAssociations(): Promise<Record<string, TabAssociation>> {
  const result = await chrome.storage.local.get(TABS_KEY);
  return (result[TABS_KEY] as Record<string, TabAssociation> | undefined) || {};
}

export async function saveAssociation(value: TabAssociation): Promise<void> {
  const all = await loadAssociations();
  all[String(value.tabId)] = value;
  await chrome.storage.local.set({ [TABS_KEY]: all });
}

export async function associationForTab(tabId: number): Promise<TabAssociation | undefined> {
  const all = await loadAssociations();
  return all[String(tabId)];
}

export async function removeAssociation(tabId: number): Promise<void> {
  const all = await loadAssociations();
  delete all[String(tabId)];
  await chrome.storage.local.set({ [TABS_KEY]: all });
}
