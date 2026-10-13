import { describe, expect, it } from "vitest";
import { welcome } from "./welcome.js";

describe("welcome", () => {
  it("shouts the greeting", () => {
    expect(welcome("Ada")).toBe("HELLO, ADA!");
  });
});
