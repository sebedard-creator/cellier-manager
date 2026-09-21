import test from "node:test";
import assert from "node:assert/strict";

import { hasRecentOpenJob } from "../dist/browserLifecycle.js";

test("keeps Chrome open only for a recent unfinished job", () => {
  const now = Date.parse("2026-09-21T12:00:00Z");
  assert.equal(hasRecentOpenJob([{ state: "SEARCHING", createdAt: "2026-09-21T11:45:00Z" }], now), true);
  assert.equal(hasRecentOpenJob([{ state: "SEARCHING", createdAt: "2026-09-20T18:00:00Z" }], now), false);
  assert.equal(hasRecentOpenJob([{ state: "SEARCHING", createdAt: "invalid" }], now), false);
  assert.equal(hasRecentOpenJob([{ state: "FAILED", createdAt: "2026-09-21T11:59:00Z" }], now), false);
  assert.equal(hasRecentOpenJob([{ state: "NEEDS_USER", createdAt: "2026-09-21T11:59:00Z" }], now), false);
});
