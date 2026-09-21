import type { PageSnapshot, Source } from "../types.js";

export function capturePage(source: Source): PageSnapshot {
  const expected = { VIVINO: "vivino.com", UNTAPPD: "untappd.com", SAQ: "saq.com" }[source];
  const host = location.hostname.toLowerCase();
  if (!(host === expected || host.endsWith(`.${expected}`)) || location.protocol !== "https:") {
    throw new Error("Cette page ne correspond pas à la source demandée.");
  }
  const lowerPath = location.pathname.toLowerCase();
  if (/\/(login|signin|search|cart|checkout|challenge)/.test(lowerPath)) {
    throw new Error("Ouvre une fiche produit avant de la capturer.");
  }

  // Vivino's product page has no semantic <main> or Product itemtype.  Its
  // stable application root is `.wrap`; the other sources expose a semantic
  // product container.
  const productRoot = (source === "VIVINO"
    ? document.querySelector(".wrap") || document.body
    : source === "UNTAPPD"
      ? document.querySelector(".box.b_info") || document.querySelector("#slide")
      : document.querySelector("main, [itemtype*='Product'], article, #maincontent")) as HTMLElement | null;
  const heading = productRoot?.querySelector("h1")?.textContent?.trim();
  if (!productRoot || !heading) throw new Error("La fiche produit n’est pas encore prête.");

  const clone = productRoot.cloneNode(true) as HTMLElement;
  clone.querySelectorAll("script:not([type='application/ld+json']), form, input, textarea, select, button, nav, [hidden]")
    .forEach(node => node.remove());

  const jsonLdBlocks: unknown[] = [];
  document.querySelectorAll<HTMLScriptElement>("script[type='application/ld+json']").forEach(script => {
    try { jsonLdBlocks.push(JSON.parse(script.textContent || "null")); } catch { /* donnée inutilisable */ }
  });
  const canonical = document.querySelector<HTMLLinkElement>("link[rel='canonical']")?.href || null;
  const selection = window.getSelection()?.toString().trim() || null;
  const html = clone.outerHTML;
  const text = clone.innerText.trim();
  return {
    url: location.href,
    canonicalUrl: canonical,
    title: document.title.slice(0, 500),
    language: document.documentElement.lang || null,
    jsonLdBlocks: jsonLdBlocks.slice(0, 20),
    productHtml: html,
    visibleText: text,
    userSelectedText: selection,
    captureStrategy: "MAIN_ELEMENT",
    truncated: false
  };
}
