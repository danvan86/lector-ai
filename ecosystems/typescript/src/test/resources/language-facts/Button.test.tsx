// @ts-nocheck
export function Button({ label }: { label: string }) {
  return <button>{label}</button>;
}

export const Empty = () => {};

describe("Button", () => {
  it("renders", () => {
    expect(<Button label="ok" />).toBeTruthy();
  });
  it.skip("is skipped", () => {
    return undefined;
  });
});
