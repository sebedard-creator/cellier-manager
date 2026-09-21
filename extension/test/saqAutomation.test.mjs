import test from "node:test";
import assert from "node:assert/strict";

import {
  isSaqProductUrl,
  isSaqSearchUrl,
  saqCandidateMatches,
} from "../dist/saqAutomation.js";

const identity = {
  type: "VIN",
  producer: "joy hill",
  name: "raisin brin",
  vintage: "2025",
};

test("recognizes SAQ search and product addresses", () => {
  assert.equal(isSaqSearchUrl("https://www.saq.com/fr/catalogsearch/result/?q=joy+hill"), true);
  assert.equal(isSaqProductUrl("https://www.saq.com/fr/15525715"), true);
  assert.equal(isSaqProductUrl("https://example.com/fr/15525715"), false);
});

test("accepts the first result only when name and producer agree", () => {
  assert.equal(saqCandidateMatches("Maison Agricole Joy Hill Raisin Brin", identity), true);
  assert.equal(saqCandidateMatches("Un autre vin Raisin Brin", identity), false);
  assert.equal(saqCandidateMatches("Maison Agricole Joy Hill Cuvée Orange", identity), false);
});
