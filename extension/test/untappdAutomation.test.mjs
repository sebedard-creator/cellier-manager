import test from "node:test";
import assert from "node:assert/strict";

import {
  isUntappdProductUrl,
  isUntappdSearchUrl,
  selectUntappdCandidate,
  untappdCandidateMatches,
} from "../dist/untappdAutomation.js";

const identity = {
  type: "BIERE",
  producer: "Dieu du Ciel",
  name: "Péché Mortel Bourbon",
  vintage: "2024",
};
const productUrl = "https://untappd.com/b/brasserie-dieu-du-ciel-peche-mortel-bourbon-2024/6061213";

test("recognizes Untappd search and beer addresses", () => {
  assert.equal(isUntappdSearchUrl("https://untappd.com/search?q=dieu+du+ciel"), true);
  assert.equal(isUntappdProductUrl(productUrl), true);
  assert.equal(isUntappdProductUrl("https://example.com/b/beer/6061213"), false);
});

test("skips a popular near-match and selects the exact beer", () => {
  const metamorfosis = {
    type: "BIERE",
    producer: "The Referend Bier Blendery",
    name: "Metamorfosis (2018)",
    vintage: "2018",
  };
  const candidates = [
    {
      title: "Metamorfosis Unblended 2018 (Still)",
      producer: "The Referend Bier Blendery",
      url: "https://untappd.com/b/the-referend-bier-blendery-metamorfosis-unblended-2018-still/5146572",
    },
    {
      title: "Metamorfosis (2018)",
      producer: "The Referend Bier Blendery",
      url: "https://untappd.com/b/the-referend-bier-blendery-metamorfosis-2018/3536027",
    },
  ];

  assert.equal(selectUntappdCandidate(candidates, metamorfosis)?.url, candidates[1].url);
});

test("accepts the exact beer, brewery and year", () => {
  assert.equal(untappdCandidateMatches(
    "Péché Mortel Bourbon (2024)", "Brasserie Dieu du Ciel!", productUrl, identity,
  ), true);
  assert.equal(untappdCandidateMatches(
    "Péché Mortel Framboise Bourbon (2024)", "Brasserie Dieu du Ciel!",
    productUrl.replace("peche-mortel-bourbon", "peche-mortel-framboise-bourbon"), identity,
  ), false);
  assert.equal(untappdCandidateMatches(
    "Péché Mortel Bourbon (2023)", "Brasserie Dieu du Ciel!",
    productUrl.replace("2024", "2023"), identity,
  ), false);
});

test("accepts a unique coordinate result with one missing short qualifier", () => {
  const coordinateIdentity = {
    type: "BIERE",
    producer: "Cantillon",
    name: "50N (Batch 7- 2020)",
    vintage: null,
  };
  const coordinateResult = {
    title: "50°N - 4°E (Batch 7 - 2020)",
    producer: "Brasserie Cantillon",
    url: "https://untappd.com/b/brasserie-cantillon-50-deg-n-4-deg-e-batch-7-2020/3999624",
  };

  assert.equal(selectUntappdCandidate([coordinateResult], coordinateIdentity)?.url, coordinateResult.url);
});

test("does not accept a named variant merely because it is the only result", () => {
  const variant = {
    title: "Péché Mortel Framboise Bourbon (2024)",
    producer: "Brasserie Dieu du Ciel!",
    url: productUrl.replace("peche-mortel-bourbon", "peche-mortel-framboise-bourbon"),
  };

  assert.equal(selectUntappdCandidate([variant], identity), null);
});
