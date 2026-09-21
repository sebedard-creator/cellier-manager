import { claimJob, listJobs, reportJobProgress, submitCapture } from "./api.js";
import { associationForTab, removeAssociation, saveAssociation } from "./storage.js";
import type { PageSnapshot, TabAssociation } from "./types.js";
import { captureFitsBounds, validateSourceUrl } from "./capturePolicy.js";
import { dispatchedRequestId, withDispatchedRequestId } from "./dispatch.js";
import { capturePage } from "./capture/pageCapture.js";
import { isSaqProductUrl, isSaqSearchUrl, saqCandidateMatches } from "./saqAutomation.js";
import {
  isVivinoProductUrl,
  isVivinoSearchUrl,
  vivinoCandidateMatches,
  vivinoProductUrlForIdentity,
} from "./vivinoAutomation.js";
import {
  isUntappdProductUrl,
  isUntappdSearchUrl,
  selectUntappdCandidate,
  type UntappdCandidate,
} from "./untappdAutomation.js";

chrome.tabs.onRemoved.addListener(tabId => { void removeAssociation(tabId); });

const associationsInProgress = new Map<number, Promise<void>>();
const automationsInProgress = new Set<number>();

async function safelyReportProgress(
  requestId: string,
  state: "SEARCHING" | "NAVIGATING" | "CAPTURING" | "NEEDS_USER" | "FAILED",
): Promise<void> {
  try {
    await reportJobProgress(requestId, state);
  } catch (error) {
    console.warn(`Impossible de signaler l’état ${state}`, error);
  }
}

async function closeCompletedTabOrBrowser(tabId: number): Promise<void> {
  // Ce profil Chrome est réservé à Cellier Manager/SudFinder et l'utilisateur
  // n'intervient jamais sur le PC. Une association d'onglet périmée ne doit
  // donc jamais empêcher sa fermeture après une tentative terminale.
  void tabId;
  const windows = await chrome.windows.getAll();
  for (const browserWindow of windows) {
    if (browserWindow.id !== undefined) {
      await chrome.windows.remove(browserWindow.id).catch(() => undefined);
    }
  }
}

async function withTimeout<T>(promise: Promise<T>, milliseconds: number, message: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<T>((_, reject) => {
        timer = setTimeout(() => reject(new Error(message)), milliseconds);
      }),
    ]);
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}

async function associateDispatchedTab(tabId: number, url?: string): Promise<void> {
  if (!url || await associationForTab(tabId)) return;
  const running = associationsInProgress.get(tabId);
  if (running) return running;
  const requestId = dispatchedRequestId(url);
  if (!requestId) return;
  const task = (async () => {
    try {
      const job = (await listJobs()).find(candidate => candidate.requestId === requestId);
      if (!job || !validateSourceUrl(url, job.source)) return;
      const lease = await claimJob(job.requestId);
      await saveAssociation({
        tabId, requestId: job.requestId, source: job.source, identity: job.identity,
        leaseToken: lease.leaseToken, leaseExpiresAt: lease.leaseExpiresAt,
        createdAt: new Date().toISOString()
      });
      await chrome.action.setBadgeBackgroundColor({ tabId, color: "#8b3548" });
      await chrome.action.setBadgeText({ tabId, text: "CM" });
    } catch (error) {
      console.warn("Association automatique Cellier Manager impossible", error);
    }
  })();
  associationsInProgress.set(tabId, task);
  try {
    await task;
  } finally {
    associationsInProgress.delete(tabId);
  }
}

async function firstSaqResult(tabId: number): Promise<{ url: string; title: string } | null> {
  const [injection] = await chrome.scripting.executeScript({
    target: { tabId },
    func: async () => {
      for (let attempt = 0; attempt < 32; attempt += 1) {
        const anchors = Array.from(document.querySelectorAll<HTMLAnchorElement>("a.product-item-link"));
        for (const anchor of anchors) {
          const title = anchor.textContent?.trim() || anchor.getAttribute("title")?.trim() || "";
          if (title && anchor.href) return { url: anchor.href, title };
        }
        await new Promise(resolve => setTimeout(resolve, 250));
      }
      return null;
    },
  });
  return injection?.result || null;
}

async function firstVivinoResult(tabId: number): Promise<{ url: string; title: string } | null> {
  const [injection] = await chrome.scripting.executeScript({
    target: { tabId },
    func: async () => {
      for (let attempt = 0; attempt < 40; attempt += 1) {
        const anchors = Array.from(document.querySelectorAll<HTMLAnchorElement>("a[href*='/w/']"));
        const seen = new Set<string>();
        for (const anchor of anchors) {
          if (!anchor.href || seen.has(anchor.href) || !/\/w\/\d+\/?(?:\?|#|$)/i.test(anchor.href)) continue;
          seen.add(anchor.href);
          let title = anchor.textContent?.trim() || anchor.getAttribute("aria-label")?.trim() || "";
          let node: HTMLElement | null = anchor;
          for (let depth = 0; depth < 6 && node; depth += 1, node = node.parentElement) {
            const text = node.innerText?.trim() || "";
            if (text.length > title.length && text.length <= 1200) title = text;
          }
          if (title) return { url: anchor.href, title };
        }
        await new Promise(resolve => setTimeout(resolve, 250));
      }
      return null;
    },
  });
  return injection?.result || null;
}

async function untappdResults(
  tabId: number,
): Promise<UntappdCandidate[]> {
  const [injection] = await chrome.scripting.executeScript({
    target: { tabId },
    func: async () => {
      let firstCandidateAttempt: number | null = null;
      for (let attempt = 0; attempt < 40; attempt += 1) {
        const candidates = Array.from(document.querySelectorAll<HTMLElement>("#algolia-hits .beer-item"))
          .map(item => {
            const anchor = item.querySelector<HTMLAnchorElement>(".name a[href*='/b/'], a[href*='/b/']");
            const title = item.querySelector<HTMLElement>(".name")?.innerText.trim() ||
              anchor?.innerText.trim() || "";
            const producer = item.querySelector<HTMLElement>(".brewery")?.innerText.trim() || "";
            return anchor?.href && title ? { url: anchor.href, title, producer } : null;
          })
          .filter((candidate): candidate is { url: string; title: string; producer: string } => candidate !== null);
        if (candidates.length) {
          firstCandidateAttempt ??= attempt;
          const statsText = document.querySelector<HTMLElement>("#algolia-stats")?.innerText || "";
          const expectedCount = Number.parseInt(statsText.match(/\d+/)?.[0] || "0", 10);
          // Algolia inserts hits incrementally. Prefer its announced total;
          // keep a bounded fallback for pages where the stats widget is absent.
          if ((expectedCount > 0 && candidates.length >= expectedCount) ||
              attempt - firstCandidateAttempt >= 12) {
            return candidates;
          }
        }
        await new Promise(resolve => setTimeout(resolve, 250));
      }
      return Array.from(document.querySelectorAll<HTMLElement>("#algolia-hits .beer-item"))
        .map(item => {
          const anchor = item.querySelector<HTMLAnchorElement>(".name a[href*='/b/'], a[href*='/b/']");
          const title = item.querySelector<HTMLElement>(".name")?.innerText.trim() ||
            anchor?.innerText.trim() || "";
          const producer = item.querySelector<HTMLElement>(".brewery")?.innerText.trim() || "";
          return anchor?.href && title ? { url: anchor.href, title, producer } : null;
        })
        .filter((candidate): candidate is { url: string; title: string; producer: string } => candidate !== null);
    },
  });
  return injection?.result || [];
}

async function submitAssociatedCapture(
  association: TabAssociation,
  snapshot: PageSnapshot,
): Promise<unknown> {
  if (!validateSourceUrl(snapshot.url, association.source)) {
    throw new Error("Cette page ne correspond pas à la source demandée.");
  }
  if (!captureFitsBounds(snapshot.productHtml, snapshot.visibleText)) {
    throw new Error("La page est trop volumineuse. Sélectionne les renseignements utiles puis recommence.");
  }
  const envelope = {
    protocolVersion: 1,
    captureId: crypto.randomUUID(),
    requestId: association.requestId,
    leaseToken: association.leaseToken,
    source: association.source,
    page: {
      url: snapshot.url,
      canonicalUrl: snapshot.canonicalUrl,
      title: snapshot.title,
      capturedAt: new Date().toISOString(),
      language: snapshot.language,
    },
    content: {
      jsonLdBlocks: snapshot.jsonLdBlocks,
      productHtml: snapshot.productHtml,
      visibleText: snapshot.visibleText,
      userSelectedText: snapshot.userSelectedText,
      captureStrategy: snapshot.captureStrategy,
      truncated: snapshot.truncated,
    },
    extensionVersion: chrome.runtime.getManifest().version,
  };
  return submitCapture(association.requestId, envelope);
}

async function automateSupportedTab(tabId: number, url?: string, complete = false): Promise<void> {
  if (!url || automationsInProgress.has(tabId)) return;
  const association = await associationForTab(tabId);
  if (!association || !["SAQ", "VIVINO", "UNTAPPD"].includes(association.source) || association.automationState === "SUBMITTED") return;
  // Untappd's new Algolia page can keep the tab in a loading state because of
  // advertising requests. Search extractors already wait for their dynamic
  // results, so they must be allowed to start as soon as the URL is known.
  const searchPage = association.source === "SAQ" ? isSaqSearchUrl(url)
    : association.source === "VIVINO" ? isVivinoSearchUrl(url)
      : isUntappdSearchUrl(url);
  if (!complete && !searchPage) return;
  automationsInProgress.add(tabId);
  try {
    if (association.source === "SAQ" && isSaqSearchUrl(url)) {
      const candidate = await withTimeout(
        firstSaqResult(tabId), 15_000, "La liste de résultats SAQ tarde trop à répondre.",
      );
      if (!candidate || !isSaqProductUrl(candidate.url) || !saqCandidateMatches(candidate.title, association.identity)) {
        await safelyReportProgress(association.requestId, "FAILED");
        await saveAssociation({
          ...association,
          automationState: "NEEDS_USER",
          automationMessage: "Le premier résultat SAQ ne correspond pas assez clairement à la fiche.",
        });
        await chrome.action.setBadgeBackgroundColor({ tabId, color: "#c53030" });
        await chrome.action.setBadgeText({ tabId, text: "!" });
        await closeCompletedTabOrBrowser(tabId);
        return;
      }
      const productUrl = withDispatchedRequestId(candidate.url, association.requestId);
      await safelyReportProgress(association.requestId, "NAVIGATING");
      await saveAssociation({
        ...association,
        automationState: "NAVIGATING",
        automationUrl: candidate.url,
        automationMessage: undefined,
      });
      await chrome.tabs.update(tabId, { url: productUrl });
      return;
    }
    if (association.source === "VIVINO" && isVivinoSearchUrl(url)) {
      const candidate = await withTimeout(
        firstVivinoResult(tabId), 15_000, "La liste de résultats Vivino tarde trop à répondre.",
      );
      if (!candidate || !vivinoCandidateMatches(candidate.title, candidate.url, association.identity)) {
        await safelyReportProgress(association.requestId, "FAILED");
        await saveAssociation({
          ...association,
          automationState: "NEEDS_USER",
          automationMessage: "Le premier résultat Vivino ne correspond pas assez clairement à la fiche et au millésime.",
        });
        await chrome.action.setBadgeBackgroundColor({ tabId, color: "#c53030" });
        await chrome.action.setBadgeText({ tabId, text: "!" });
        await closeCompletedTabOrBrowser(tabId);
        return;
      }
      const productUrl = withDispatchedRequestId(
        vivinoProductUrlForIdentity(candidate.url, association.identity),
        association.requestId,
      );
      await safelyReportProgress(association.requestId, "NAVIGATING");
      await saveAssociation({
        ...association,
        automationState: "NAVIGATING",
        automationUrl: candidate.url,
        automationMessage: undefined,
      });
      await chrome.tabs.update(tabId, { url: productUrl });
      return;
    }
    if (association.source === "UNTAPPD" && isUntappdSearchUrl(url)) {
      const candidates = await withTimeout(
        untappdResults(tabId), 15_000, "La liste de résultats Untappd tarde trop à répondre.",
      );
      const candidate = selectUntappdCandidate(candidates, association.identity);
      if (!candidate) {
        await safelyReportProgress(association.requestId, "FAILED");
        await saveAssociation({
          ...association,
          automationState: "NEEDS_USER",
          automationMessage: "Aucun résultat Untappd ne correspond assez clairement à la bière et à l’année.",
        });
        await chrome.action.setBadgeBackgroundColor({ tabId, color: "#c53030" });
        await chrome.action.setBadgeText({ tabId, text: "!" });
        await closeCompletedTabOrBrowser(tabId);
        return;
      }
      const productUrl = withDispatchedRequestId(candidate.url, association.requestId);
      await safelyReportProgress(association.requestId, "NAVIGATING");
      await saveAssociation({
        ...association,
        automationState: "NAVIGATING",
        automationUrl: candidate.url,
        automationMessage: undefined,
      });
      await chrome.tabs.update(tabId, { url: productUrl });
      return;
    }
    const supportedProduct = association.source === "SAQ" ? isSaqProductUrl(url)
      : association.source === "VIVINO" ? isVivinoProductUrl(url)
        : isUntappdProductUrl(url);
    if (!supportedProduct) return;

    const lease = await claimJob(association.requestId);
    const capturing: TabAssociation = {
      ...association,
      leaseToken: lease.leaseToken,
      leaseExpiresAt: lease.leaseExpiresAt,
      automationState: "CAPTURING",
      automationUrl: url,
      automationMessage: undefined,
    };
    await safelyReportProgress(association.requestId, "CAPTURING");
    await saveAssociation(capturing);
    const [injection] = await withTimeout(
      chrome.scripting.executeScript({
        target: { tabId },
        func: capturePage,
        args: [capturing.source],
      }),
      15_000,
      "La fiche produit tarde trop à répondre.",
    );
    if (!injection?.result) throw new Error("La fiche produit n’a retourné aucune capture.");
    await submitAssociatedCapture(capturing, injection.result);
    await saveAssociation({ ...capturing, automationState: "SUBMITTED" });
    await chrome.action.setBadgeBackgroundColor({ tabId, color: "#2f855a" });
    await chrome.action.setBadgeText({ tabId, text: "OK" });
    await closeCompletedTabOrBrowser(tabId);
  } catch (error) {
    const latest = await associationForTab(tabId) || association;
    await safelyReportProgress(association.requestId, "FAILED");
    await saveAssociation({
      ...latest,
      automationState: "NEEDS_USER",
      automationMessage: String((error as Error)?.message || error),
    });
    await chrome.action.setBadgeBackgroundColor({ tabId, color: "#c53030" });
    await chrome.action.setBadgeText({ tabId, text: "!" });
    console.warn("Traitement automatique de la fiche impossible", error);
    await closeCompletedTabOrBrowser(tabId);
  } finally {
    automationsInProgress.delete(tabId);
  }
}

async function handleTab(tabId: number, url?: string, complete = false): Promise<void> {
  await associateDispatchedTab(tabId, url);
  await automateSupportedTab(tabId, url, complete);
}

chrome.tabs.onCreated.addListener(tab => {
  if (tab.id !== undefined) void handleTab(tab.id, tab.url, tab.status === "complete");
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.url || changeInfo.status === "complete") {
    void handleTab(tabId, changeInfo.url || tab.url, changeInfo.status === "complete" || tab.status === "complete");
  }
});

async function resumeDispatchedTab(tab: chrome.tabs.Tab): Promise<void> {
  if (tab.id === undefined || !tab.url || !dispatchedRequestId(tab.url)) return;
  if (tab.status === "complete") {
    await handleTab(tab.id, tab.url, true);
    return;
  }
  // The extension can start between Chrome's loading and complete events.
  // Poll only the dispatched Cellier tab so this narrow race cannot strand it.
  for (let attempt = 0; attempt < 60; attempt += 1) {
    await new Promise(resolve => setTimeout(resolve, 500));
    const latest = await chrome.tabs.get(tab.id).catch(() => null);
    if (!latest) return;
    if (latest.status === "complete") {
      await handleTab(tab.id, latest.url, true);
      return;
    }
  }
}

// An unpacked extension may be reloaded while a dispatched search tab is
// already open. Resume those tabs instead of requiring another Android search.
void chrome.tabs.query({}).then(tabs => Promise.all(tabs.map(resumeDispatchedTab)));

chrome.runtime.onMessage.addListener((message: unknown, _sender, sendResponse) => {
  if (!message || typeof message !== "object" || (message as { type?: string }).type !== "SUBMIT_CAPTURE") return;
  const payload = message as { type: string; tabId: number; snapshot: PageSnapshot };
  void (async () => {
    const association = await associationForTab(payload.tabId);
    if (!association) throw new Error("Cet onglet n’est associé à aucune recherche.");
    return submitAssociatedCapture(association, payload.snapshot);
  })().then(async result => {
    sendResponse({ ok: true, result });
    await closeCompletedTabOrBrowser(payload.tabId);
  }).catch(error => sendResponse({ ok: false, error: String(error.message || error) }));
  return true;
});
