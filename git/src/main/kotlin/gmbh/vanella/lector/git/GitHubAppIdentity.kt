package gmbh.vanella.lector.git

import gmbh.vanella.lector.core.CommitIdentity
import gmbh.vanella.lector.core.ToolError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.URI
import java.net.URLEncoder

/**
 * The commit identity of the GitHub App [slug]'s bot on github.com: the name `<slug>[bot]` and the noreply email
 * `<id>+<slug>[bot]@users.noreply.github.com`, which GitHub links to the App. The id comes from `GET /users/<slug>[bot]`
 * at [apiUrl], asked with [token]. Fails with a [ToolError] that names the slug unless GitHub answers with that bot:
 * the email must be exact, because the tool skips its own commits by it.
 */
fun gitHubAppIdentity(
    apiUrl: URI,
    token: String,
    slug: String,
): CommitIdentity {
    val login = "$slug[bot]"
    val user = user(GitHubApi(apiUrl.toString().removeSuffix("/"), token), login, slug)
    val id = botId(user, login) ?: throw ToolError("GitHub's user $login is not the bot of the GitHub App $slug: $user")
    return CommitIdentity(login, "$id+$login@users.noreply.github.com")
}

/** GitHub's user [login], as JSON. Fails with a [ToolError] that names the App [slug]. */
private fun user(
    api: GitHubApi,
    login: String,
    slug: String,
): JsonElement =
    try {
        api.send("GET", "/users/${URLEncoder.encode(login, Charsets.UTF_8)}", null)
    } catch (e: ToolError) {
        throw ToolError("Can't look up the bot of the GitHub App $slug: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw ToolError("Can't look up the bot of the GitHub App $slug: GitHub's answer is not JSON: ${e.message}", e)
    }

/** The user id in [user], if it is the bot [login] with a positive numeric id. */
private fun botId(
    user: JsonElement,
    login: String,
): Long? =
    try {
        val fields = user.jsonObject
        val id = fields.getValue("id").jsonPrimitive
        val bot = fields.getValue("type").jsonPrimitive.content == "Bot" && fields.getValue("login").jsonPrimitive.content == login
        id.long.takeIf { bot && !id.isString && it > 0 }
    } catch (_: IllegalArgumentException) {
        // Not an object, a field that isn't a primitive, or an id that isn't a number.
        null
    } catch (_: NoSuchElementException) {
        null
    }
