package gmbh.vanella.lector.core

/**
 * How an ecosystem orders its versions and reads version selectors (an exact version or a range like `3.4.x`).
 * Every version and selector question goes through here, so the core and the recipe index stay free of version syntax.
 */
interface VersionScheme {
    /** Orders two versions: negative if [a] is lower, `0` if equal, positive if higher. */
    fun compare(
        a: String,
        b: String,
    ): Int

    /** Whether [selector] allows [version]. False for a selector this scheme doesn't support. */
    fun contains(
        selector: String,
        version: String,
    ): Boolean

    /**
     * Orders two selectors by the highest version each allows. A selector that contains a given version is tighter the
     * lower it sorts here (`3.4.1` < `3.4.x` < `3.x`).
     */
    fun compareSelectors(
        a: String,
        b: String,
    ): Int

    fun updateType(
        from: String,
        to: String,
    ): UpdateType
}
