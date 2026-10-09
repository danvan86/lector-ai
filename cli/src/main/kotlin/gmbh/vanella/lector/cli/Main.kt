package gmbh.vanella.lector.cli

import com.github.ajalt.clikt.core.main

fun main(args: Array<String>) = LectorCommand(appVersion()).main(args)
