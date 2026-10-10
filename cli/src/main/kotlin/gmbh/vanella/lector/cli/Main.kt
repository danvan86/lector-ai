package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.main

// Excluded from coverage: `main` exits the process, so only the end-to-end tests run it. Keep it a one-liner.
fun main(args: Array<String>) = rootCommand(AppVersion.read()).main(args)
