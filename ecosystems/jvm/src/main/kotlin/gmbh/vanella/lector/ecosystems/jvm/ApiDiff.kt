package gmbh.vanella.lector.ecosystems.jvm

import gmbh.vanella.lector.core.DependencyChange

/**
 * What kind of API change an entry is, in the order they are decided: the first that applies wins. An [unambiguous]
 * change has exactly one replacement and can be applied mechanically; the others need judgement.
 */
internal enum class ApiChangeKind(
    val heading: String,
    val unambiguous: Boolean,
) {
    RENAMED_PACKAGE("Renamed packages:", true),
    MOVED_CLASS("Moved classes:", true),
    REMOVED_CLASS("Removed classes:", false),
    CHANGED_SIGNATURE("Changed signatures:", false),
    MOVED_MEMBER("Moved members:", true),
    RENAME_CANDIDATE("Rename candidates:", false),
    REMOVED_MEMBER("Removed members:", false),
}

/**
 * One API change between two versions. [oldName] and [newName] are fully qualified: packages for a renamed package,
 * classes otherwise; [newName] is null for something removed. A member change has the gone member in [oldMembers] and
 * its replacement in [newMembers]; a changed signature has every old and every new overload of the name.
 */
internal data class ApiChange(
    val kind: ApiChangeKind,
    val oldName: String,
    val newName: String? = null,
    val oldMembers: List<ApiMember> = emptyList(),
    val newMembers: List<ApiMember> = emptyList(),
) {
    val unambiguous get() = kind.unambiguous

    /** The entry as one line for the model, without the leading `- `. */
    fun render(): String =
        when (kind) {
            ApiChangeKind.RENAMED_PACKAGE, ApiChangeKind.MOVED_CLASS -> {
                "$oldName → $newName"
            }

            ApiChangeKind.REMOVED_CLASS -> {
                oldName
            }

            ApiChangeKind.CHANGED_SIGNATURE -> {
                val member = oldMembers.first()
                val owner = if (member.kind == MemberKind.CONSTRUCTOR) oldName else "$oldName.${member.name}"
                "$owner: ${overloads(oldMembers)} → ${overloads(newMembers)}"
            }

            ApiChangeKind.MOVED_MEMBER, ApiChangeKind.RENAME_CANDIDATE -> {
                "${oldMembers.single().render(oldName)} → ${newMembers.single().render("$newName")}"
            }

            ApiChangeKind.REMOVED_MEMBER -> {
                oldMembers.single().render(oldName)
            }
        }

    /** `(int), static (long)`; a field by its type, `static int`. */
    private fun overloads(members: List<ApiMember>) =
        members
            .map { (if (it.static) "static " else "") + if (it.kind == MemberKind.FIELD) it.returnType else it.parameterList() }
            .sorted()
            .joinToString(", ")
}

/**
 * The API diff between an [old] and a [new] model, for the [diffed] classes: the classes the errors refer to. Moved
 * classes and moved members are searched in the whole new model. Anything ambiguous is removed, never guessed.
 */
internal object ApiDiff {
    fun of(
        old: ApiModel,
        new: ApiModel,
        diffed: Set<String>,
    ): List<ApiChange> =
        classChanges(old, new, diffed) + diffed.filter { it in old.classes && it in new.classes }.flatMap { memberChanges(it, old, new) }

    private fun classChanges(
        old: ApiModel,
        new: ApiModel,
        diffed: Set<String>,
    ): List<ApiChange> {
        val gone = diffed.filter { it in old.classes && it !in new.classes }
        val added = new.classes.keys - old.classes.keys
        val successors =
            gone.associateWith { name -> added.singleOrNull { simpleName(it) == simpleName(name) && packageOf(it) != packageOf(name) } }
        val newPackages =
            new.classes.keys
                .map(::packageOf)
                .toSet()
        // A package whose gone classes all moved to the same other package, and that is gone itself.
        val renamed =
            gone
                .groupBy(::packageOf)
                .filter { (pkg, classes) -> classes.size >= 2 && pkg !in newPackages }
                .mapValues { (_, classes) -> classes.map { successors[it]?.let(::packageOf) }.distinct().singleOrNull() }
                .filterValues { it != null }
        return renamed.map { (pkg, target) -> ApiChange(ApiChangeKind.RENAMED_PACKAGE, pkg, target) } +
            gone.filter { packageOf(it) !in renamed }.map { name ->
                val successor = successors[name]
                if (successor ==
                    null
                ) {
                    ApiChange(ApiChangeKind.REMOVED_CLASS, name)
                } else {
                    ApiChange(ApiChangeKind.MOVED_CLASS, name, successor)
                }
            }
    }

    private fun memberChanges(
        name: String,
        old: ApiModel,
        new: ApiModel,
    ): List<ApiChange> {
        val before = old.classes.getValue(name)
        val after = new.classes.getValue(name)
        val namesBefore = before.map { it.name }.toSet()
        val namesAfter = after.map { it.name }.toSet()
        val gone = before - after
        val changed =
            gone.map { it.name }.filter { it in namesAfter }.distinct().map { member ->
                ApiChange(
                    ApiChangeKind.CHANGED_SIGNATURE,
                    name,
                    name,
                    before.filter { it.name == member },
                    after.filter { it.name == member },
                )
            }
        val vanished = gone.filter { it.name !in namesAfter }
        val moved = vanished.associateWith { member -> movedTo(member, name, new) }
        val left = vanished.filter { moved[it] == null }
        val fresh = (after - before).filter { it.name !in namesBefore }
        val renamed = left.singleOrNull()?.takeIf { gone -> fresh.singleOrNull()?.sameSignature(gone) == true }
        return changed +
            moved.mapNotNull { (member, target) ->
                target?.let { ApiChange(ApiChangeKind.MOVED_MEMBER, name, it.first, listOf(member), listOf(it.second)) }
            } +
            left.map { member ->
                if (member == renamed) {
                    ApiChange(ApiChangeKind.RENAME_CANDIDATE, name, name, listOf(member), fresh)
                } else {
                    ApiChange(ApiChangeKind.REMOVED_MEMBER, name, oldMembers = listOf(member))
                }
            }
    }

    /** The one other class of [new] with a static member of the same kind, name, and parameters as the static [member], and that member. */
    private fun movedTo(
        member: ApiMember,
        owner: String,
        new: ApiModel,
    ): Pair<String, ApiMember>? {
        if (!member.static) return null
        return new.classes
            .filterKeys { it != owner }
            .mapNotNull { (name, members) ->
                members.firstOrNull { it.name == member.name && it.sameSignature(member) }?.let { name to it }
            }.singleOrNull()
    }
}

/**
 * The API changes of a change as text for the model: one block per kind, one line per entry, sorted. The kinds share
 * limits ([LIMITS]) that fill in kind order; what doesn't fit is counted.
 */
internal object ApiChangesText {
    private val LIMITS =
        listOf(
            setOf(ApiChangeKind.RENAMED_PACKAGE, ApiChangeKind.MOVED_CLASS, ApiChangeKind.REMOVED_CLASS) to 15,
            setOf(ApiChangeKind.MOVED_MEMBER, ApiChangeKind.REMOVED_MEMBER) to 15,
            setOf(ApiChangeKind.CHANGED_SIGNATURE) to 10,
            setOf(ApiChangeKind.RENAME_CANDIDATE) to 10,
        )

    fun render(
        change: DependencyChange,
        entries: List<ApiChange>,
    ): String {
        if (entries.isEmpty()) return ""
        val room = LIMITS.associate { it.first to it.second }.toMutableMap()
        val blocks =
            ApiChangeKind.entries.mapNotNull { kind ->
                val lines = entries.filter { it.kind == kind }.map { it.render() }.sorted()
                if (lines.isEmpty()) return@mapNotNull null
                val limit = room.keys.single { kind in it }
                val shown = lines.take(room.getValue(limit))
                room[limit] = room.getValue(limit) - shown.size
                val more = lines.size - shown.size
                (
                    listOf(kind.heading) + shown.map { "- $it" } +
                        if (more >
                            0
                        ) {
                            listOf("- … and $more more")
                        } else {
                            emptyList()
                        }
                ).joinToString("\n")
            }
        return "API changes of ${change.`package`} from ${change.fromVersion} to ${change.toVersion}\n\n" + blocks.joinToString("\n\n")
    }
}
