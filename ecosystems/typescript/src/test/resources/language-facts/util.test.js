// eslint-disable-next-line no-unused-vars
export function check(value) {
  return undefined;
}

class Store {
  get(key) {}
  clear = () => {};
}

describe("util", () => {
  test("checks", () => {
    expect(check(1)).toBeUndefined();
  });
  xtest("off", () => {});
});
