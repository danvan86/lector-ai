package gmbh.vanella.lector.cli

// Excluded from coverage: `main` exits the process, so only the end-to-end tests run it. Keep it a one-liner.
fun main(args: Array<String>) = rootCommand(AppVersion.read()).execute(args.asList())
