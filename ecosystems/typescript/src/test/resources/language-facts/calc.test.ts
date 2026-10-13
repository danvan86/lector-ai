// @ts-ignore
import { add } from "./calc";

/* eslint-disable no-console */
export function double(x: number): number {
  return add(x, x);
}

export const triple = (x: number): number => add(x, double(x));

export const nothing = (): number | null => {
  return null;
};

abstract class Shape {
  abstract area(): number;
  describe(): string {
    // TODO describe the shape
    return "shape";
  }
  private reset = () => {};
}

describe("calc", () => {
  describe.each([1, 2])("with %i", (n) => {
    it("adds", () => {
      expect(add(n, 1)).toBe(n + 1);
      assert.ok(true);
    });
    it.only("doubles", async () => {
      await expect(Promise.resolve(double(n))).resolves.toBe(2 * n);
    });
  });
  test.skip("skipped", () => {});
  it.todo("later");
  xit("off", () => {});
  test(`template ${1}`, () => {
    const value = 1 as any; // @ts-expect-error
    expect(value).toBeDefined();
  });
});

xdescribe("off too", () => {});
