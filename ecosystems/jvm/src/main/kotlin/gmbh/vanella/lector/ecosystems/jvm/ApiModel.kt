package gmbh.vanella.lector.ecosystems.jvm

import java.lang.classfile.ClassFile
import java.lang.constant.ClassDesc
import java.lang.reflect.AccessFlag
import java.nio.file.Path
import java.util.zip.ZipFile

internal enum class MemberKind { METHOD, CONSTRUCTOR, FIELD }

/**
 * A public member of a class. Its signature is the [kind], the [name], the erased [parameters] in order (varargs as an
 * array, none for a field), and whether it is [static]. The [returnType] (a field's type) is for display, not for
 * matching. A constructor is named `<init>`.
 */
internal data class ApiMember(
    val kind: MemberKind,
    val name: String,
    val parameters: List<String>,
    val static: Boolean,
    val returnType: String,
) {
    /** The member as Java code names it, in the class [owner]: `a.B.m(int)`, `a.B(int)` for a constructor, `a.B.F` for a field. */
    fun render(owner: String) =
        when (kind) {
            MemberKind.METHOD -> "$owner.$name${parameterList()}"
            MemberKind.CONSTRUCTOR -> "$owner${parameterList()}"
            MemberKind.FIELD -> "$owner.$name"
        }

    fun parameterList() = parameters.joinToString(", ", "(", ")")

    /** Whether [other] has the same signature, whatever its name: kind, parameters, and static-ness. */
    fun sameSignature(other: ApiMember) = kind == other.kind && parameters == other.parameters && static == other.static
}

/**
 * The public API of a set of JARs, read from their class files with the JDK's ClassFile API: every public top-level
 * class (fully qualified name) with its declared public members. Nested classes, and members the compiler generated
 * (a `$` in the name, bridges, synthetic members), are left out. Inherited members are not followed.
 */
internal data class ApiModel(
    val classes: Map<String, Set<ApiMember>>,
) {
    companion object {
        private val SKIPPED = listOf("module-info.class", "package-info.class")

        /** Reads [jars]. Fails with an `IOException` for a file that isn't a JAR, an `IllegalArgumentException` for a broken class. */
        fun read(jars: List<Path>): ApiModel = ApiModel(jars.flatMap(::classes).toMap())

        private fun classes(jar: Path): List<Pair<String, Set<ApiMember>>> =
            ZipFile(jar.toFile()).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .filter { entry ->
                        val name = entry.name
                        name.endsWith(".class") && '$' !in name && !name.startsWith("META-INF/") && SKIPPED.none { name.endsWith(it) }
                    }.mapNotNull { entry -> zip.getInputStream(entry).use { parse(it.readAllBytes()) } }
                    .toList()
            }

        private fun parse(bytes: ByteArray): Pair<String, Set<ApiMember>>? {
            val model = ClassFile.of().parse(bytes)
            if (!model.flags().has(AccessFlag.PUBLIC)) return null
            val methods =
                model.methods().filter { visible(it.flags().flags(), it.methodName().stringValue()) }.map { method ->
                    val name = method.methodName().stringValue()
                    val type = method.methodTypeSymbol()
                    ApiMember(
                        kind = if (name == "<init>") MemberKind.CONSTRUCTOR else MemberKind.METHOD,
                        name = name,
                        parameters = type.parameterList().map(::javaName),
                        static = method.flags().has(AccessFlag.STATIC),
                        returnType = javaName(type.returnType()),
                    )
                }
            val fields =
                model.fields().filter { visible(it.flags().flags(), it.fieldName().stringValue()) }.map { field ->
                    ApiMember(
                        kind = MemberKind.FIELD,
                        name = field.fieldName().stringValue(),
                        parameters = emptyList(),
                        static = field.flags().has(AccessFlag.STATIC),
                        returnType = javaName(field.fieldTypeSymbol()),
                    )
                }
            return model.thisClass().asInternalName().replace('/', '.') to (methods + fields).toSet()
        }

        private fun visible(
            flags: Set<AccessFlag>,
            name: String,
        ) = AccessFlag.PUBLIC in flags && AccessFlag.SYNTHETIC !in flags && AccessFlag.BRIDGE !in flags && '$' !in name

        /** `I` → `int`, `[Ljava/lang/String;` → `java.lang.String[]`, `La/B$C;` → `a.B.C`. */
        private fun javaName(type: ClassDesc): String =
            when {
                type.isArray -> {
                    javaName(type.componentType()) + "[]"
                }

                type.isPrimitive -> {
                    type.displayName()
                }

                else -> {
                    type
                        .descriptorString()
                        .removePrefix("L")
                        .removeSuffix(";")
                        .replace('/', '.')
                        .replace('$', '.')
                }
            }
    }
}
