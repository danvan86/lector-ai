package com.example

import org.apache.commons.lang3.StringUtils

class Greeter {
    fun greet(name: String) = "Hello, " + StringUtils.capitalizeFully(name)

    fun shout(name: String) = name.capitalized()
}
