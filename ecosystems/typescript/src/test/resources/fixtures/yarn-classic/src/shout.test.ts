import { test } from "node:test";
import assert from "node:assert/strict";
import { shout } from "./shout.ts";

test("shouts", () => {
  assert.equal(shout("hi"), "HI");
});
