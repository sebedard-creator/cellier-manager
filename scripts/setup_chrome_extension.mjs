import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";


const [chromePath, profilePath, extensionPath, companionUrl = "http://127.0.0.1:18765"] = process.argv.slice(2);
if (!chromePath || !profilePath || !extensionPath) {
  throw new Error("Usage: node setup_chrome_extension.mjs <chrome> <profile> <extension> [companion-url]");
}

const debuggingPort = 19223;
const debuggingUrl = `http://127.0.0.1:${debuggingPort}`;

async function json(url, init) {
  const response = await fetch(url, init);
  if (!response.ok) throw new Error(`HTTP ${response.status} pour ${url}`);
  return response.json();
}

async function targets() {
  return json(`${debuggingUrl}/json/list`);
}

async function waitForExtensionTarget() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const all = await targets();
      const target = all.find(candidate =>
        candidate.type === "service_worker" && candidate.url?.startsWith("chrome-extension://")
      ) || all.find(candidate =>
        candidate.type === "page" && candidate.url?.startsWith("chrome-extension://")
      );
      if (target) return target;
    } catch {
      // Chrome n'écoute pas encore.
    }
    await delay(250);
  }
  throw new Error("L’extension Chrome n’a pas démarré.");
}

function cdp(webSocketDebuggerUrl) {
  const socket = new WebSocket(webSocketDebuggerUrl);
  let nextId = 1;
  const pending = new Map();
  socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (!message.id) return;
    const request = pending.get(message.id);
    if (!request) return;
    pending.delete(message.id);
    if (message.error) request.reject(new Error(message.error.message));
    else request.resolve(message.result);
  });
  return {
    ready: new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    }),
    send(method, params = {}) {
      const id = nextId++;
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        socket.send(JSON.stringify({ id, method, params }));
      });
    },
    close() { socket.close(); },
  };
}

const chrome = spawn(chromePath, [
  `--user-data-dir=${profilePath}`,
  `--load-extension=${extensionPath}`,
  `--remote-debugging-port=${debuggingPort}`,
  "--remote-allow-origins=*",
  "--no-first-run",
  "--no-default-browser-check",
  `${companionUrl}/`,
], { detached: true, stdio: "ignore" });
chrome.unref();

const workerTarget = await waitForExtensionTarget();
const extensionId = new URL(workerTarget.url).hostname;
if (!/^[a-p]{32}$/.test(extensionId)) throw new Error("Identifiant d’extension Chrome invalide.");

const pairing = await json(`${companionUrl}/local/v1/pairing-bundle?role=EXTENSION`, { method: "POST" });
const connection = cdp(workerTarget.webSocketDebuggerUrl);
await connection.ready;
await connection.send("Runtime.enable");

const expression = `
  (async () => {
    const serverUrl = ${JSON.stringify(companionUrl)}.replace(/\\/$/, '');
    const deviceKey = 'cellierExtensionDeviceId';
    const stored = await chrome.storage.local.get(deviceKey);
    const deviceId = stored[deviceKey] || crypto.randomUUID();
    await chrome.storage.local.set({ [deviceKey]: deviceId });

    const pairResponse = await fetch(serverUrl + '/extension/v1/pair', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        pairingId: ${JSON.stringify(pairing.pairingId)},
        pairingSecret: ${JSON.stringify(pairing.pairingSecret)},
        deviceId,
        name: 'Chrome',
        extensionId: chrome.runtime.id,
      }),
    });
    const pairBody = await pairResponse.json().catch(() => ({}));
    if (!pairResponse.ok) {
      throw new Error(pairBody?.error?.message || pairBody?.detail || 'Jumelage refusé');
    }

    const token = pairBody.token;
    await chrome.storage.local.set({
      cellierSettings: { serverUrl, token },
    });

    const heartbeatResponse = await fetch(serverUrl + '/extension/v1/heartbeat', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
    });
    if (!heartbeatResponse.ok) throw new Error('Battement de connexion refusé');
    return 'Compagnon connecté';
  })()
`;
const result = await connection.send("Runtime.evaluate", {
  expression,
  awaitPromise: true,
  returnByValue: true,
});
if (result.exceptionDetails || result.result?.subtype === "error") {
  throw new Error(result.exceptionDetails?.text || result.result?.description || "Jumelage impossible.");
}
if (result.result?.value !== "Compagnon connecté") {
  throw new Error("Le compagnon n’a pas confirmé le jumelage de l’extension.");
}

await connection.send("Browser.close");
connection.close();
console.log(`Extension Cellier Manager installée et jumelée (${extensionId}).`);
