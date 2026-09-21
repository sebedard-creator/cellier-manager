import test from "node:test";
import assert from "node:assert/strict";

import { dispatchedRequestId, withDispatchedRequestId } from "../dist/dispatch.js";


test("reads a dispatched request id from the search URL fragment", () => {
  const id = "123e4567-e89b-42d3-a456-426614174000";
  assert.equal(
    dispatchedRequestId(`https://www.saq.com/fr/catalogsearch/result/?q=test#cellier-request=${id}`),
    id,
  );
});


test("rejects malformed dispatch markers", () => {
  assert.equal(dispatchedRequestId("https://www.saq.com/#cellier-request=../../secret"), null);
  assert.equal(dispatchedRequestId("not a URL"), null);
});


test("preserves the product URL while moving the request marker", () => {
  const id = "123e4567-e89b-42d3-a456-426614174000";
  const result = withDispatchedRequestId("https://www.saq.com/fr/15525715", id);
  assert.equal(dispatchedRequestId(result), id);
  assert.equal(new URL(result).pathname, "/fr/15525715");
});
