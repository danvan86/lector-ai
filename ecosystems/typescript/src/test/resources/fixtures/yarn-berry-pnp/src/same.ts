import { dequal } from "dequal";

export function same(a: unknown, b: unknown): boolean {
  return dequal(a, b);
}
