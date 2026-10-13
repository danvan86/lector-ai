package gmbh.vanella.lector.sources

import gmbh.vanella.lector.core.SourceContext

/** The context of a source that reads only the PR: any look into the repository fails the test. */
internal object NoContext : SourceContext {
    override val ecosystem get() = error("the source reads only the PR")

    override fun baseVersions() = error("the source reads only the PR")

    override fun headVersions() = error("the source reads only the PR")
}
