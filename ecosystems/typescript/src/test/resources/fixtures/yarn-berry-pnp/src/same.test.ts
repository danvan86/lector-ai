import { same } from "./same";

describe("same", () => {
  it("compares deeply", () => {
    expect(same({ a: [1] }, { a: [1] })).toBe(true);
  });
});
