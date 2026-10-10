package com.example

class Greeter {
    fun greet(name: String): String = "Hello, " + name.capitalized()

    fun count(names: List<String>): Int = names.size.toString()
}
