package io.composecast.server.sdk

import io.composecast.protocol.Action
import io.composecast.protocol.Component
import io.composecast.protocol.PathStep
import io.composecast.protocol.RepeatItems
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.Template
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.logging.Logger

/**
 * Shared by all bound screens of one registry: the [DataLoader], the response cache and the thread
 * pool that fetches a screen's sources in parallel.
 */
internal class DataFetcher(
    val loader: DataLoader,
    val env: (String) -> String?,
    private val cache: DataCache = DataCache(),
) {
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "composecast-data").apply { isDaemon = true }
    }

    /** Starts [request]; the result is served from the cache while fresh. */
    fun submit(request: DataRequest, cacheSeconds: Int): Future<Result<JsonElement>> =
        executor.submit<Result<JsonElement>> {
            cache.get(request, cacheSeconds) {
                runCatching { Json.parseToJsonElement(loader.load(request)) }
            }
        }
}

/**
 * LRU of data-source responses keyed by resolved URL + headers. Successes live [cacheSeconds]
 * (0 = not cached), failures [failureTtlMillis], so an API outage does not multiply requests.
 */
internal class DataCache(
    private val maxEntries: Int = 256,
    private val failureTtlMillis: Long = 10_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val value: Result<JsonElement>, val expiresAt: Long)

    private val entries = object : LinkedHashMap<DataRequest, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DataRequest, Entry>) = size > maxEntries
    }

    fun get(request: DataRequest, cacheSeconds: Int, load: () -> Result<JsonElement>): Result<JsonElement> {
        synchronized(entries) {
            val cached = entries[request]
            if (cached != null && cached.expiresAt > clock()) return cached.value
        }
        val value = load()
        val ttl = if (value.isSuccess) cacheSeconds * 1000L else failureTtlMillis
        if (ttl > 0) synchronized(entries) { entries[request] = Entry(value, clock() + ttl) }
        return value
    }
}

/**
 * A screen with params, data sources or bindings ([ScreenDefinition.isBound]): every [bind] resolves
 * params, fetches the sources in parallel and turns the definition into a concrete one (no
 * templates, repeats expanded) for the unchanged renderer (T-006, ADR 0008).
 *
 * Construction checks what depends on the environment: secrets exist, date patterns are valid.
 */
internal class BoundScreen(val definition: ScreenDefinition, private val fetcher: DataFetcher) {
    private val id = definition.id

    /** `secret.NAME` → value of `COMPOSECAST_SECRET_NAME`, read once at load. */
    private val secrets: Map<String, String> = buildMap {
        definition.data.forEach { (source, spec) ->
            (listOf(spec.url) + spec.headers.values).flatMap { expressions(it) }.filter { it.root == SECRET }.forEach {
                val name = (it.path.single() as PathStep.Key).name
                val variable = "COMPOSECAST_SECRET_$name"
                val value = fetcher.env(variable)
                requireNotNull(value) { "Screen '$id', data.$source: environment variable $variable is not set" }
                put(name, value)
            }
        }
    }

    private val warned = ConcurrentHashMap.newKeySet<String>()

    init {
        val datePatterns = allTemplates(definition).flatMap { expressions(it) }.flatMap { it.filters }
            .filter { it.name == "date" }.map { it.args.single() }
        datePatterns.forEach { pattern ->
            try {
                formatter(pattern)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Screen '$id': invalid date pattern '$pattern': ${e.message}", e)
            }
        }
    }

    /**
     * @throws ScreenParameterException when a required param is missing (HTTP 400)
     * @throws DataSourceException when a source fails and there are no `error` components (HTTP 502)
     */
    fun bind(context: RenderContext): ScreenDefinition {
        val params = definition.params.mapValues { (name, spec) ->
            context.params[name] ?: spec.default ?: throw ScreenParameterException("Missing parameter '$name'")
        }
        val paramsJson = JsonObject(params.mapValues { JsonPrimitive(it.value) })
        val secretsJson = JsonObject(secrets.mapValues { JsonPrimitive(it.value) })
        val sourceRoots = mapOf(PARAMS to paramsJson, SECRET to secretsJson)

        val pending = definition.data.mapValues { (name, spec) ->
            val request = DataRequest(
                url = render(spec.url, sourceRoots) { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") },
                headers = spec.headers.mapValues { render(it.value, sourceRoots) },
                timeoutMs = spec.timeoutMs,
            )
            Triple(spec, request, fetcher.submit(request, spec.cacheSeconds))
        }
        val results = pending.mapValues { (name, value) ->
            val (spec, request, future) = value
            val result = try {
                future.get(spec.timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                Result.failure(IllegalStateException("timeout after ${spec.timeoutMs} ms"))
            }
            result.exceptionOrNull()?.let {
                log.warning("Screen '$id': data source '$name' (${mask(request.url)}) failed: ${reason(it)}")
            }
            result
        }

        val roots = mapOf(PARAMS to paramsJson) + results.mapValues { it.value.getOrNull() ?: JsonNull }
        val failed = results.entries.firstOrNull { (name, result) ->
            result.isFailure && !definition.data.getValue(name).optional
        }
        val title = render(definition.title, roots)
        val bound = if (failed == null) {
            ScreenDefinition(id, title, components = bindComponents(definition.components, roots))
        } else if (definition.error.isNotEmpty()) {
            ScreenDefinition(id, title, components = bindComponents(definition.error, mapOf(PARAMS to paramsJson)))
        } else {
            throw DataSourceException("data source '${failed.key}' failed: ${reason(failed.value.exceptionOrNull()!!)}")
        }
        val problems = bound.contentProblems()
        require(problems.isEmpty()) { "Screen '$id' after binding: ${problems.joinToString("; ")}" }
        return bound
    }

    private fun bindComponents(list: List<Component>, roots: Map<String, JsonElement>): List<Component> =
        list.flatMap { c -> bindComponent(c, roots) }

    private fun bindComponent(c: Component, roots: Map<String, JsonElement>): List<Component> = when (c) {
        is Component.Text -> listOf(c.copy(text = render(c.text, roots)))
        is Component.Image -> {
            val url = render(c.url, roots)
            if (url.isBlank()) {
                warnOnce("image:${c.url}") { "Screen '$id': image url '${c.url}' is empty after binding; skipped" }
                emptyList()
            } else {
                listOf(Component.Image(url, render(c.alt, roots)))
            }
        }
        is Component.Button -> listOf(Component.Button(render(c.label, roots), bindAction(c.action, roots)))
        is Component.Spacer -> listOf(c)
        is Component.Repeat -> {
            val items = items(c.items, roots).let { all -> c.limit?.let { all.take(it) } ?: all }
            if (items.isEmpty()) {
                bindComponents(c.empty, roots)
            } else {
                items.flatMapIndexed { i, item ->
                    bindComponents(c.template, roots + (c.alias to item) + (INDEX to JsonPrimitive(i)))
                }
            }
        }
    }

    private fun items(items: RepeatItems, roots: Map<String, JsonElement>): List<JsonElement> = when (items) {
        is RepeatItems.Path -> evaluatePath(Template.parsePath(items.path), roots) as? JsonArray ?: emptyList()
        is RepeatItems.Zip -> {
            val arrays = items.arrays.mapValues { evaluatePath(Template.parsePath(it.value), roots) as? JsonArray }
            if (arrays.values.any { it == null }) {
                emptyList()
            } else {
                val size = arrays.values.minOf { it!!.size }
                (0 until size).map { i -> JsonObject(arrays.mapValues { it.value!![i] }) }
            }
        }
    }

    private fun bindAction(action: Action, roots: Map<String, JsonElement>): Action = when (action) {
        is Action.Navigate ->
            Action.Navigate(render(action.screen, roots), action.params.mapValues { render(it.value, roots) })
        is Action.OpenUrl -> Action.OpenUrl(render(action.url, roots))
        is Action.Reload -> action
        is Action.Track ->
            Action.Track(render(action.event, roots), action.params.mapValues { render(it.value, roots) })
        is Action.Custom -> Action.Custom(render(action.payload, roots))
    }

    /** [text] with every `{{ }}` replaced; [escape] applies to substituted values (URL encoding). */
    private fun render(text: String, roots: Map<String, JsonElement>, escape: (String) -> String = { it }): String {
        if (!Template.hasBindings(text)) return text
        return template(text).parts.joinToString("") { part ->
            when (part) {
                is Template.Literal -> part.text
                is Template.Expression -> escape(text(evaluate(part, roots)))
            }
        }
    }

    private fun evaluate(expression: Template.Expression, roots: Map<String, JsonElement>): JsonElement? {
        val value = evaluatePath(expression, roots)
        if (value == null && expression.filters.none { it.name == "default" || it.name == "lookup" }) {
            warnOnce(expression.text) { "Screen '$id': {{${expression.text}}} is missing in the data; rendered empty" }
        }
        return expression.filters.fold(value) { current, filter ->
            Filters.apply(filter.name, filter.args, current, definition.lookups)
        }
    }

    private fun evaluatePath(expression: Template.Expression, roots: Map<String, JsonElement>): JsonElement? {
        var current: JsonElement? = roots[expression.root]
        for (step in expression.path) {
            current = when (step) {
                is PathStep.Key -> (current as? JsonObject)?.get(step.name)
                is PathStep.Index -> (current as? JsonArray)?.getOrNull(step.index)
            }
        }
        return current?.takeUnless { it is JsonNull }
    }

    private fun mask(text: String): String = secrets.values.fold(text) { acc, secret ->
        if (secret.isEmpty()) {
            acc
        } else {
            acc.replace(secret, "***").replace(URLEncoder.encode(secret, Charsets.UTF_8), "***")
        }
    }

    private fun reason(e: Throwable): String =
        mask((e.message ?: e.javaClass.simpleName).lineSequence().first().take(200))

    private inline fun warnOnce(key: String, message: () -> String) {
        if (warned.add(key)) log.warning(message())
    }

    /** The filters of `{{ … | filter}}`; names and arities are validated by the model (protocol `Filters`). */
    internal object Filters {
        fun apply(
            name: String,
            args: List<String>,
            value: JsonElement?,
            lookups: Map<String, Map<String, String>>,
        ): JsonElement? = when (name) {
            "round" -> value?.let { number(it) }?.setScale(args.firstOrNull()?.toInt() ?: 0, RoundingMode.HALF_UP)
                ?.let { JsonPrimitive(it.toPlainString()) }
            "date" -> value?.let { temporal(text(it)) }?.let {
                try {
                    JsonPrimitive(formatter(args.single()).format(it))
                } catch (e: Exception) {
                    null // the pattern needs fields the value lacks (HH on a date)
                }
            }
            "lookup" -> lookups.getValue(args.single()).let { table ->
                (table[text(value)] ?: table["*"])?.let(::JsonPrimitive)
            }
            "default" -> if (value == null || text(value).isEmpty()) JsonPrimitive(args.single()) else value
            "upper" -> value?.let { JsonPrimitive(text(it).uppercase(Locale.ROOT)) }
            "lower" -> value?.let { JsonPrimitive(text(it).lowercase(Locale.ROOT)) }
            else -> error("unknown filter '$name'") // rejected by ScreenDefinition.problems()
        }

        private fun number(value: JsonElement) = (value as? JsonPrimitive)?.content?.toBigDecimalOrNull()

        private fun temporal(text: String): TemporalAccessor? = listOf<(String) -> TemporalAccessor>(
            OffsetDateTime::parse,
            LocalDateTime::parse,
            LocalDate::parse,
            LocalTime::parse,
        ).firstNotNullOfOrNull { parse -> runCatching { parse(text) }.getOrNull() }
    }

    companion object {
        private val log = Logger.getLogger(BoundScreen::class.java.name)
        private const val PARAMS = "params"
        private const val SECRET = "secret"
        private const val INDEX = "\$index"

        private val templates = ConcurrentHashMap<String, Template>()
        private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

        private fun template(text: String): Template = templates.getOrPut(text) { Template.parse(text) }

        private fun formatter(pattern: String): DateTimeFormatter =
            formatters.getOrPut(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH) }

        private fun expressions(text: String): List<Template.Expression> =
            if (Template.hasBindings(text)) template(text).expressions else emptyList()

        /**
         * Text of a value: strings as-is, integral numbers without `.0` (`21.0` → `21`), other
         * numbers and booleans as JSON, objects/arrays as compact JSON, missing → empty.
         */
        internal fun text(value: JsonElement?): String = when (value) {
            null, JsonNull -> ""
            is JsonPrimitive -> if (value.isString) {
                value.content
            } else {
                val number = value.content.toBigDecimalOrNull()
                if (number != null && number.signum() != 0 && number.stripTrailingZeros().scale() <= 0) {
                    number.toBigInteger().toString()
                } else if (number != null && number.signum() == 0) {
                    "0"
                } else {
                    value.content
                }
            }
            else -> value.toString()
        }

        private fun allTemplates(definition: ScreenDefinition): List<String> {
            fun components(list: List<Component>): List<String> = list.flatMap { c ->
                val nested = if (c is Component.Repeat) components(c.template) + components(c.empty) else emptyList()
                c.strings().map { it.second } + nested
            }
            return listOf(definition.title) + components(definition.components) + components(definition.error) +
                definition.data.values.flatMap { listOf(it.url) + it.headers.values }
        }
    }
}
