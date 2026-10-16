import { greet } from "@fixture/lib";

export function welcome(name: string): string {
  return greet(name).toUpperCase();
}
