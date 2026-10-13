import { add } from "./calc";

describe("calc", () => {
  describe("add", () => {
    it("adds two numbers", () => {
      expect(add(1, 2)).toBe(3);
    });
  });
});

test("top level", () => {
  expect(add(0, 0)).toBe(0);
});
