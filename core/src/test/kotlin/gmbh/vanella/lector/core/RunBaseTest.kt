package gmbh.vanella.lector.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RunBaseTest : BuildErrorFixture() {
    @Test
    fun `a build error whose message ends with the build's output never matches the base's`() {
        // The Maven adapter's message for a failure it can't explain: the tail carries times.
        fun unexplained(seconds: String) =
            buildError("Maven failed with exit code 1. The end of its output:\n[INFO] BUILD FAILURE\n[INFO] Total time:  $seconds s")
        val adapter = TwoCheckoutAdapter(head = { listOf(unexplained("12.304")) }, base = { listOf(unexplained("14.117")) })

        val verification = attribute(adapter, repo, RunBase(CountingBase(baseCheckout)))

        assertEquals(listOf(unexplained("12.304")), verification.causedByUpdate)
        assertEquals(emptyList(), verification.preExisting)
    }

    @Test
    fun `a pre-existing build error is reported with the pre-existing tests`() {
        val failing = Diagnostic(null, null, null, null, "expected: <1>", Severity.ERROR, "surefire", "com.example.AppTest#adds")
        val adapter = TwoCheckoutAdapter(head = { listOf(gpg, failing) }, base = { listOf(gpg) })

        val verification = attribute(adapter, repo, RunBase(CountingBase(baseCheckout)))

        assertEquals(listOf(failing), verification.causedByUpdate, "passes on the base")
        assertEquals(listOf(gpg), verification.preExisting)
    }
}
