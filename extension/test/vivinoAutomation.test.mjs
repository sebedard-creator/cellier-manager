import test from "node:test";
import assert from "node:assert/strict";

import {
  isVivinoProductUrl,
  isVivinoSearchUrl,
  vivinoCandidateMatches,
  vivinoProductUrlForIdentity,
} from "../dist/vivinoAutomation.js";

const identity = {
  type: "VIN",
  producer: "L'Orpailleur",
  name: "L'Orpailleur Gris",
  vintage: "2018",
};

test("recognizes Vivino search and wine addresses", () => {
  assert.equal(isVivinoSearchUrl("https://www.vivino.com/en/explore?search_term=orpailleur"), true);
  assert.equal(isVivinoProductUrl("https://www.vivino.com/en/l-orpailleur-gris-quebec/w/8299665?year=2018"), true);
  assert.equal(isVivinoProductUrl("https://example.com/en/wine/w/8299665"), false);
});

test("accepts only a matching first result and vintage", () => {
  const url = "https://www.vivino.com/en/l-orpailleur-gris-quebec/w/8299665?year=2018";
  assert.equal(vivinoCandidateMatches("L'Orpailleur L'Orpailleur Gris 2018", url, identity), true);
  assert.equal(vivinoCandidateMatches("L'Orpailleur Rouge 2018", url, identity), false);
  assert.equal(vivinoCandidateMatches("L'Orpailleur L'Orpailleur Gris 2019", url.replace("2018", "2019"), identity), false);
});

test("forces the requested vintage on the product address", () => {
  const url = vivinoProductUrlForIdentity(
    "https://www.vivino.com/en/l-orpailleur-gris-quebec/w/8299665",
    identity,
  );
  assert.equal(new URL(url).searchParams.get("year"), "2018");
});
