import { claimJob, heartbeat, listJobs, pairExtension } from "../api.js";
import { associationForTab, loadSettings, saveAssociation, saveSettings } from "../storage.js";
import { capturePage } from "../capture/pageCapture.js";
import type { Job } from "../types.js";

const statusNode = document.querySelector<HTMLParagraphElement>("#status")!;
const jobsNode = document.querySelector<HTMLElement>("#jobs")!;
const activeNode = document.querySelector<HTMLElement>("#active")!;
const targetNode = document.querySelector<HTMLParagraphElement>("#target")!;
const configNode = document.querySelector<HTMLElement>("#config")!;
const serverInput = document.querySelector<HTMLInputElement>("#server")!;
const pairingIdInput = document.querySelector<HTMLInputElement>("#pairingId")!;
const pairingSecretInput = document.querySelector<HTMLInputElement>("#pairingSecret")!;

function setStatus(value: string, error = false): void {
  statusNode.textContent = value;
  statusNode.classList.toggle("error", error);
}

function searchUrl(job: Job): string {
  const vintage = job.identity.vintage?.trim() || "";
  const name = vintage
    ? job.identity.name
      .replace(new RegExp(`\\b${vintage.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`, "g"), " ")
      .replace(/\(\s*\)|\[\s*\]|\{\s*\}/g, " ")
      .replace(/\s+/g, " ")
      .trim()
    : job.identity.name.trim();
  const terms = [job.identity.producer.trim(), name, vintage].filter(Boolean).join(" ");
  if (job.source === "VIVINO") return `https://www.vivino.com/search/wines?q=${encodeURIComponent(terms)}`;
  if (job.source === "UNTAPPD") return `https://untappd.com/search?q=${encodeURIComponent(terms)}`;
  return `https://www.saq.com/fr/catalogsearch/result/?q=${encodeURIComponent(terms)}`;
}

async function associate(job: Job): Promise<void> {
  const lease = await claimJob(job.requestId);
  const tab = await chrome.tabs.create({ url: searchUrl(job), active: true });
  if (tab.id === undefined) throw new Error("Chrome n’a pas créé l’onglet.");
  await saveAssociation({
    tabId: tab.id, requestId: job.requestId, source: job.source, identity: job.identity,
    leaseToken: lease.leaseToken, leaseExpiresAt: lease.leaseExpiresAt, createdAt: new Date().toISOString()
  });
  window.close();
}

async function refresh(): Promise<void> {
  const settings = await loadSettings();
  serverInput.value = settings.serverUrl;
  if (!settings.token) {
    configNode.hidden = false;
    setStatus("Configure l’appairage avec le compagnon.", true);
    return;
  }
  await heartbeat();
  setStatus("Compagnon connecté");
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tab?.id !== undefined) {
    const association = await associationForTab(tab.id);
    if (association) {
      activeNode.hidden = false;
      targetNode.textContent = `${association.identity.producer} — ${association.identity.name} ${association.identity.vintage || ""}`;
    }
  }
  const jobs = await listJobs();
  jobsNode.replaceChildren();
  for (const job of jobs) {
    const card = document.createElement("div");
    card.className = "job";
    const text = document.createElement("div");
    text.textContent = `${job.source} · ${job.identity.producer} — ${job.identity.name} ${job.identity.vintage || ""}`;
    const button = document.createElement("button");
    button.textContent = "Rechercher";
    button.addEventListener("click", () => void associate(job).catch(error => setStatus(String(error.message || error), true)));
    card.append(text, button);
    jobsNode.append(card);
  }
}

document.querySelector("#showConfig")!.addEventListener("click", () => { configNode.hidden = !configNode.hidden; });
document.querySelector("#save")!.addEventListener("click", () => {
  void (async () => {
    const serverUrl = serverInput.value.trim().replace(/\/$/, "");
    const token = await pairExtension(serverUrl, pairingIdInput.value.trim(), pairingSecretInput.value.trim());
    await saveSettings({ serverUrl, token });
    pairingIdInput.value = "";
    pairingSecretInput.value = "";
    configNode.hidden = true;
    await refresh();
  })().catch(error => setStatus(String(error.message || error), true));
});
document.querySelector("#capture")!.addEventListener("click", () => {
  void (async () => {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tab?.id === undefined) throw new Error("Aucun onglet actif.");
    const association = await associationForTab(tab.id);
    if (!association) throw new Error("Cet onglet n’est pas associé à une recherche.");
    // Refresh the short-lived lease at capture time so a retry after a parser
    // error does not require reopening the search page.
    const lease = await claimJob(association.requestId);
    await saveAssociation({
      ...association,
      leaseToken: lease.leaseToken,
      leaseExpiresAt: lease.leaseExpiresAt,
    });
    const [injection] = await chrome.scripting.executeScript({
      target: { tabId: tab.id }, func: capturePage, args: [association.source]
    });
    if (!injection?.result) throw new Error("La page n’a retourné aucune capture.");
    const response = await chrome.runtime.sendMessage({ type: "SUBMIT_CAPTURE", tabId: tab.id, snapshot: injection.result });
    if (!response?.ok) throw new Error(response?.error || "Échec de l’envoi.");
    setStatus("Proposition prête à vérifier sur le téléphone.");
  })().catch(error => setStatus(String(error.message || error), true));
});

void refresh().catch(error => {
  configNode.hidden = false;
  setStatus(String(error.message || error), true);
});
