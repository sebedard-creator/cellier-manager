export type Source = "VIVINO" | "UNTAPPD" | "SAQ";

export interface Job {
  requestId: string;
  source: Source;
  identity: { type: "VIN" | "BIERE"; producer: string; name: string; vintage?: string | null };
  state: string;
  createdAt: string;
}

export interface Settings {
  serverUrl: string;
  token: string;
}

export interface TabAssociation {
  tabId: number;
  requestId: string;
  source: Source;
  identity: Job["identity"];
  leaseToken: string;
  leaseExpiresAt: string;
  createdAt: string;
  automationState?: "NAVIGATING" | "CAPTURING" | "SUBMITTED" | "NEEDS_USER";
  automationUrl?: string;
  automationMessage?: string;
}

export interface PageSnapshot {
  url: string;
  canonicalUrl: string | null;
  title: string;
  language: string | null;
  jsonLdBlocks: unknown[];
  productHtml: string;
  visibleText: string;
  userSelectedText: string | null;
  captureStrategy: string;
  truncated: boolean;
}
