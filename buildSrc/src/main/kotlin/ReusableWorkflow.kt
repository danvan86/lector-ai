/**
 * The rules that keep `.github/workflows/lector.yml` in line with `action/action.yml` and the tool's version: it runs the
 * action and the Ollama image of this version, it offers the action's inputs with the action's defaults, and the docs pin
 * the same version. Reads the YAML line by line, as `checkActionImage` does: both files are ours and keep one layout.
 */
object ReusableWorkflow {
    // Inputs the workflow fills itself: the token and the bot from the App token step, the Dependabot metadata, and the
    // URL of the Ollama service it starts.
    private val FILLED = setOf("token", "app-slug", "bot-name", "bot-email", "dependabot-metadata", "ollama-url")
    private val DOCS_PIN = Regex("""danvan86/lector-ai/[\w./-]*@v(\S+)""")
    private val DOCS_IMAGE = Regex("""lector-ollama:(\d+\.\d+\.\d+)-""")

    /** What is out of line, one sentence each; empty if nothing. [docs] maps a file name to its text. */
    fun problems(
        action: List<String>,
        workflow: List<String>,
        docs: Map<String, String>,
        version: String,
    ): List<String> {
        val actionInputs = inputs(action, keyIndent = 2, from = "inputs:", until = "runs:")
        val workflowInputs = inputs(workflow, keyIndent = 6, from = "    inputs:", until = "    secrets:")
        val problems = mutableListOf<String>()
        val pins = workflow.map { it.trim().removePrefix("- ") }.filter { it.startsWith("uses: danvan86/lector-ai/action@") }
        if (pins.size != 2 || pins.any { !it.endsWith("@v$version") }) {
            problems += "lector.yml must use danvan86/lector-ai/action@v$version in both jobs, not $pins"
        }
        val image = "ghcr.io/danvan86/lector-ollama:$version-${actionInputs["model"].orEmpty().replace(':', '-')}"
        if (workflowInputs["ollama-image"] != image) {
            problems += "lector.yml's ollama-image default must be $image, not ${workflowInputs["ollama-image"]}"
        }
        for ((name, default) in actionInputs - FILLED) {
            when (name) {
                !in workflowInputs -> problems += "lector.yml lacks the action's input $name"
                else -> if (workflowInputs[name] != default) problems += "lector.yml's $name default is ${workflowInputs[name]}, the action's $default"
            }
        }
        for ((file, text) in docs) {
            DOCS_PIN.findAll(text).map { it.groupValues[1] }.filter { it != version }.forEach {
                problems += "$file pins lector-ai @v$it, not @v$version"
            }
            DOCS_IMAGE.findAll(text).map { it.groupValues[1] }.filter { it != version }.forEach {
                problems += "$file pins lector-ollama $it, not $version"
            }
        }
        return problems
    }

    /** The inputs between the lines [from] and [until], keyed at [keyIndent] spaces, with their defaults, unquoted. */
    private fun inputs(
        lines: List<String>,
        keyIndent: Int,
        from: String,
        until: String,
    ): Map<String, String> {
        val key = Regex("^ {$keyIndent}([\\w-]+):\\s*$")
        val default = Regex("^ {${keyIndent + 2}}default:\\s*(.*)$")
        val result = linkedMapOf<String, String>()
        var current = ""
        for (line in lines.dropWhile { it != from }.drop(1).takeWhile { it != until }) {
            key.find(line)?.let {
                current = it.groupValues[1]
                result[current] = ""
            }
            default.find(line)?.let { result[current] = it.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'") }
        }
        return result
    }
}
