import test from "node:test";
import assert from "node:assert/strict";
import { captureFitsBounds, validateSourceUrl } from "../dist/capturePolicy.js";

test("accepts only the requested HTTPS source domain", () => {
  assert.equal(validateSourceUrl("https://www.vivino.com/w/123", "VIVINO"), true);
  assert.equal(validateSourceUrl("https://vivino.com.evil.example/w/123", "VIVINO"), false);
  assert.equal(validateSourceUrl("http://vivino.com/w/123", "VIVINO"), false);
  assert.equal(validateSourceUrl("https://user:pass@vivino.com/w/123", "VIVINO"), false);
});

test("enforces capture byte limits", () => {
  assert.equal(captureFitsBounds("<main>ok</main>", "ok"), true);
  assert.equal(captureFitsBounds("x".repeat(1_048_577), "ok"), false);
  assert.equal(captureFitsBounds("ok", "é".repeat(100_001)), false);
});
