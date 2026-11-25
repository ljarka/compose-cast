package io.composecast.protocol

// Validation of a ScreenDefinition — the only validation of screen content (DSL, JSON loader,
// admin panel). Entry points are the ScreenDefinition members problems(), contentProblems(),
// warnings() and validate(); every message starts with a JSON-like path.

/** Structural problems (no template checks): id, values, components and error components. */
internal fun ScreenDefinition.screenContentProblems(): List<String> = buildList {
    if (id.isBlank()) add("id: must not be blank")
    live?.let {
        if (it.everyMs !in Live.MIN_EVERY_MS..Live.MAX_EVERY_MS) {
            add("live.everyMs: must be ${Live.MIN_EVERY_MS}..${Live.MAX_EVERY_MS}, was ${it.everyMs}")
        }
    }
    values.forEach { (key, spec) ->
        val at = "values.$key"
        if (!spec.min.isFinite() || !spec.max.isFinite() || !(spec.max > spec.min)) {
            add("$at.max: must be greater than min (${spec.min}), was ${spec.max}")
        }
        if (!Template.hasBindings(spec.default) && NumberList.single(spec.default) == null) {
            add("$at.default: expected a number or a {{binding}}, was '${spec.default}'")
        }
    }
    val slidersByValue = mutableSetOf<String>()
    addAll(componentProblems(components, "components", values = values, slidersByValue = slidersByValue))
    addAll(componentProblems(error, "error", values = emptyMap(), slidersByValue = mutableSetOf()))
}

/** Non-fatal accessibility/authoring warnings (T-016). */
internal fun ScreenDefinition.screenWarnings(): List<String> =
    componentWarnings(components, "components") + componentWarnings(error, "error")

private fun componentWarnings(list: List<Component>, path: String): List<String> = buildList {
    list.forEachIndexed { i, component ->
        val at = "$path[$i]"
        if ((component is Component.Image || component is Component.Icon) &&
            component.modifier.action != null &&
            component.modifier.semantics?.hidden != true &&
            component.modifier.semantics?.description.isNullOrBlank()
        ) {
            val alt = if (component is Component.Image) component.alt else (component as Component.Icon).alt
            if (alt.isBlank()) {
                add("$at: clickable image/icon has no alt or semantics.description")
            }
        }
        component.childLists().forEach { (field, children) ->
            addAll(componentWarnings(children, "$at.$field"))
        }
    }
}

/** Where a component list sits: decides whether `weight` is allowed. */
private enum class Parent { Screen, Column, Row, Other }

private fun componentProblems(
    list: List<Component>,
    path: String,
    parent: Parent = Parent.Screen,
    depth: Int = 1,
    values: Map<String, ScreenValue> = emptyMap(),
    slidersByValue: MutableSet<String> = mutableSetOf(),
): List<String> = buildList {
    if (depth > MAX_DEPTH) {
        add("$path: nested deeper than $MAX_DEPTH levels")
        return@buildList
    }
    list.forEachIndexed { i, component ->
        val at = "$path[$i]"
        if (component !is Component.Repeat) addAll(modifierProblems(component.modifier, "$at.modifier", parent))
        when (component) {
            is Component.Text -> {
                component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
                component.fontSize?.let { if (it <= 0) add("$at.fontSize: must be > 0, was $it") }
                component.maxLines?.let { if (it <= 0) add("$at.maxLines: must be > 0, was $it") }
            }
            is Component.Image -> {
                if (component.url.isBlank()) add("$at.url: must not be blank")
                component.alpha?.let { if (it.isNaN() || it !in 0f..1f) add("$at.alpha: must be 0..1, was $it") }
                component.aspectRatio?.let {
                    if (!(it > 0f) || it.isInfinite()) add("$at.aspectRatio: must be > 0, was $it")
                }
            }
            is Component.Icon -> {
                if (component.url.isBlank()) add("$at.url: must not be blank")
                if (component.size <= 0) add("$at.size: must be > 0, was ${component.size}")
            }
            is Component.Divider -> {
                if (component.thickness <= 0) add("$at.thickness: must be > 0, was ${component.thickness}")
                component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
            }
            is Component.Button -> {
                actionProblem(component.action)?.let { add("$at.action: $it") }
                if (component.icon != null && component.icon.isBlank()) add("$at.icon: must not be blank")
            }
            is Component.Card -> {
                if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                addAll(
                    componentProblems(
                        component.children,
                        "$at.children",
                        Parent.Column,
                        depth + 1,
                        values,
                        slidersByValue,
                    ),
                )
            }
            is Component.Spacer ->
                if (component.height < 0) add("$at.height: must be >= 0, was ${component.height}")
            is Component.Gauge -> {
                if (!Template.hasBindings(component.value) && NumberList.single(component.value) == null) {
                    add("$at.value: expected a number or a {{binding}}, was '${component.value}'")
                }
                if (!(component.max > component.min)) {
                    add("$at.max: must be greater than min (${component.min}), was ${component.max}")
                }
                if (component.size <= 0) add("$at.size: must be > 0, was ${component.size}")
                if (component.thickness <= 0 || component.thickness * 2 >= component.size) {
                    add("$at.thickness: must be > 0 and less than half of size, was ${component.thickness}")
                }
                component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
                component.trackColor?.let { colorProblem(it)?.let { problem -> add("$at.trackColor: $problem") } }
            }
            is Component.Sparkline -> {
                if (!Template.hasBindings(component.values) && NumberList.parse(component.values).size < 2) {
                    add("$at.values: expected at least 2 numbers or a {{binding}}, was '${component.values}'")
                }
                val max = component.max
                val min = component.min
                if (min != null && max != null && max <= min) add("$at.max: must be greater than min ($min), was $max")
                if (component.height <= 0) add("$at.height: must be > 0, was ${component.height}")
                if (component.thickness <= 0) add("$at.thickness: must be > 0, was ${component.thickness}")
                component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
            }
            is Component.Kpi ->
                component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
            is Component.Slider -> {
                val spec = values[component.controls]
                if (spec == null) {
                    add("$at.controls: unknown value '${component.controls}' (declare it in \"values\")")
                } else if (!slidersByValue.add(component.controls)) {
                    add("$at.controls: value '${component.controls}' is already controlled by another slider")
                }
                component.step?.let { step ->
                    if (!step.isFinite() || step <= 0f) {
                        add("$at.step: must be > 0, was $step")
                    } else if (spec != null && spec.max > spec.min && step > (spec.max - spec.min)) {
                        add("$at.step: must be <= range (${spec.max - spec.min}), was $step")
                    }
                }
                component.color?.let { colorProblem(it)?.let { problem -> add("$at.color: $problem") } }
                component.trackColor?.let { colorProblem(it)?.let { problem -> add("$at.trackColor: $problem") } }
            }
            is Component.Repeat -> {
                if ((component.limit ?: 0) < 0) add("$at.limit: must be >= 0, was ${component.limit}")
                if (component.items is RepeatItems.Zip && component.items.arrays.isEmpty()) {
                    add("$at.items: must name at least one array")
                }
                // Expanded in place: its components are children of this repeat's parent.
                addAll(componentProblems(component.template, "$at.template", parent, depth, values, slidersByValue))
                addAll(componentProblems(component.empty, "$at.empty", parent, depth, values, slidersByValue))
            }
            is Component.Expandable -> {
                if (component.title.isBlank()) add("$at.title: must not be blank")
                if (component.content.isEmpty()) add("$at.content: must not be empty")
                addAll(
                    componentProblems(
                        component.content,
                        "$at.content",
                        Parent.Column,
                        depth + 1,
                        values,
                        slidersByValue,
                    ),
                )
            }
            is Component.Column -> {
                if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                addAll(
                    componentProblems(
                        component.children,
                        "$at.children",
                        Parent.Column,
                        depth + 1,
                        values,
                        slidersByValue,
                    ),
                )
            }
            is Component.Row -> {
                if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                addAll(
                    componentProblems(
                        component.children,
                        "$at.children",
                        Parent.Row,
                        depth + 1,
                        values,
                        slidersByValue,
                    ),
                )
            }
            is Component.Box -> addAll(
                componentProblems(component.children, "$at.children", Parent.Other, depth + 1, values, slidersByValue),
            )
            is Component.FitBox -> addAll(
                componentProblems(component.children, "$at.children", Parent.Other, depth + 1, values, slidersByValue),
            )
            is Component.Flow -> {
                component.maxItemsInEachRow?.let {
                    if (it < 1) add("$at.maxItemsInEachRow: must be >= 1, was $it")
                }
                component.maxLines?.let { if (it < 1) add("$at.maxLines: must be >= 1, was $it") }
                addAll(
                    componentProblems(
                        component.children,
                        "$at.children",
                        Parent.Row,
                        depth + 1,
                        values,
                        slidersByValue,
                    ),
                )
            }
            is Component.Grid -> {
                if (component.columns !in 1..12) {
                    add("$at.columns: must be 1..12, was ${component.columns}")
                }
                if (component.spacing < 0) add("$at.spacing: must be >= 0, was ${component.spacing}")
                addAll(
                    componentProblems(
                        component.children,
                        "$at.children",
                        Parent.Other,
                        depth + 1,
                        values,
                        slidersByValue,
                    ),
                )
            }
        }
    }
}

private fun modifierProblems(m: Modifier, at: String, parent: Parent): List<String> = buildList {
    m.padding?.let { p ->
        if (listOf(p.start, p.top, p.end, p.bottom).any { it < 0 }) add("$at.padding: must be >= 0, was $p")
    }
    listOf("width" to m.width, "height" to m.height).forEach { (field, dimension) ->
        if (dimension is Dimension.Dp &&
            dimension.value < 0
        ) {
            add("$at.$field: must be >= 0, was ${dimension.value}")
        }
    }
    m.weight?.let { weight ->
        if (!(weight > 0f) || weight.isInfinite()) add("$at.weight: must be > 0, was $weight")
        if (parent != Parent.Column && parent != Parent.Row) {
            add("$at.weight: only allowed for direct children of a row or column")
        }
    }
    m.background?.let { colorProblem(it)?.let { problem -> add("$at.background: $problem") } }
    m.cornerRadius?.let { if (it < 0) add("$at.cornerRadius: must be >= 0, was $it") }
    if (m.cornerRadius != null && m.shape != null) add("$at: set either cornerRadius or shape, not both")
    m.border?.let { border ->
        if (border.width <= 0) add("$at.border.width: must be > 0, was ${border.width}")
        colorProblem(border.color)?.let { add("$at.border.color: $it") }
    }
    m.action?.let { action -> actionProblem(action)?.let { add("$at.action: $it") } }
    m.minWidth?.let { if (it < 0) add("$at.minWidth: must be >= 0, was $it") }
    m.maxWidth?.let { if (it < 0) add("$at.maxWidth: must be >= 0, was $it") }
    if (m.minWidth != null && m.maxWidth != null && m.minWidth > m.maxWidth) {
        add("$at.minWidth: must be <= maxWidth (${m.maxWidth}), was ${m.minWidth}")
    }
    m.minHeight?.let { if (it < 0) add("$at.minHeight: must be >= 0, was $it") }
    m.maxHeight?.let { if (it < 0) add("$at.maxHeight: must be >= 0, was $it") }
    if (m.minHeight != null && m.maxHeight != null && m.minHeight > m.maxHeight) {
        add("$at.minHeight: must be <= maxHeight (${m.maxHeight}), was ${m.minHeight}")
    }
    m.fillFraction?.let {
        if (it.isNaN() || it <= 0f || it > 1f) add("$at.fillFraction: must be in (0, 1], was $it")
    }
    m.collapsePriority?.let {
        if (it.isNaN() || it.isInfinite()) add("$at.collapsePriority: must be finite, was $it")
    }
}

internal fun colorProblem(color: String): String? =
    if (COLOR.matches(color)) null else "expected #RRGGBB or #AARRGGBB, was '$color'"

private fun actionProblem(action: Action): String? {
    val encoded = ActionCodec.encode(action)
    return when {
        encoded.length > ActionCodec.MAX_ENCODED_LENGTH ->
            "encoded action is ${encoded.length} characters, Remote Compose allows ${ActionCodec.MAX_ENCODED_LENGTH}"
        // Catches empty screen/event names, empty param keys, empty URLs: anything the app
        // could not decode back to the same action.
        ActionCodec.decode(encoded) != action -> "$action cannot be carried to the app (empty name, key or URL?)"
        else -> null
    }
}

/** Template syntax, names, scopes, filters, lookups, secrets and source URLs (T-006). */
internal fun ScreenDefinition.bindingProblems(): List<String> = buildList {
    fun name(kind: String, at: String, value: String) {
        if (!BINDING_NAME.matches(value)) add("$at: invalid $kind name '$value' (letters, digits, _ and -)")
        if (value in Roots.RESERVED) add("$at: '$value' is reserved")
    }
    params.keys.forEach { name("param", "params.$it", it) }
    lookups.keys.forEach { name("lookup", "lookups.$it", it) }
    values.keys.forEach { name("value", "values.$it", it) }
    data.forEach { (source, spec) ->
        val at = "data.$source"
        name("data source", at, source)
        if (spec.timeoutMs !in 1..60_000) add("$at.timeoutMs: must be 1..60000, was ${spec.timeoutMs}")
        if (spec.cacheSeconds < 0) add("$at.cacheSeconds: must be >= 0, was ${spec.cacheSeconds}")
        urlProblem(spec.url)?.let { add("$at.url: $it") }
        val sourceRoots = setOf(Roots.PARAMS, Roots.SECRET)
        addAll(templateProblems(spec.url, "$at.url", sourceRoots))
        spec.headers.forEach { (header, value) ->
            addAll(templateProblems(value, "$at.headers.$header", sourceRoots))
        }
    }
    val roots = setOf(Roots.PARAMS) + data.keys
    values.forEach { (key, spec) ->
        addAll(templateProblems(spec.default, "values.$key.default", roots))
    }
    addAll(templateProblems(title, "title", roots))
    val componentRoots = if (values.isNotEmpty()) roots + Roots.VALUES else roots
    addAll(componentBindingProblems(components, "components", componentRoots))
    addAll(componentBindingProblems(error, "error", setOf(Roots.PARAMS)))
}

private fun ScreenDefinition.componentBindingProblems(
    list: List<Component>,
    path: String,
    roots: Set<String>,
): List<String> = buildList {
    list.forEachIndexed { i, component ->
        val at = "$path[$i]"
        component.strings().forEach { (field, value) -> addAll(templateProblems(value, "$at.$field", roots)) }
        if (component is Component.Repeat) {
            component.items.paths().forEach { (suffix, itemPath) ->
                try {
                    val expression = Template.parsePath(itemPath)
                    if (expression.root !in roots) {
                        add("$at.items$suffix: ${unknownRoot(expression.root, roots)}")
                    }
                } catch (e: IllegalArgumentException) {
                    add("$at.items$suffix: ${e.message}")
                }
            }
            val alias = component.alias
            if (!BINDING_NAME.matches(alias) || alias in Roots.RESERVED) {
                add("$at.as: invalid name '$alias'")
            } else if (alias in roots) {
                add("$at.as: '$alias' hides a data source or an outer repeat")
            }
            addAll(componentBindingProblems(component.template, "$at.template", roots + alias + Roots.INDEX))
            addAll(componentBindingProblems(component.empty, "$at.empty", roots))
        }
        if (component !is Component.Repeat) {
            component.childLists().forEach { (field, children) ->
                addAll(componentBindingProblems(children, "$at.$field", roots))
            }
        }
    }
}

private fun ScreenDefinition.templateProblems(text: String, at: String, roots: Set<String>): List<String> = buildList {
    if (!Template.hasBindings(text)) return@buildList
    val template = try {
        Template.parse(text)
    } catch (e: IllegalArgumentException) {
        add("$at: ${e.message}")
        return@buildList
    }
    template.expressions.forEach { e ->
        val where = "$at: {{${e.text}}}"
        addAll(pathProblems(e, where, roots))
        e.filters.forEach { f ->
            f.paths.filterNotNull().forEach { arg ->
                if (f.name !in Filters.PATH_ARGUMENTS) {
                    add("$where: filter '${f.name}' takes a literal argument, not the path '${arg.text}'")
                } else if (arg.root == Roots.SECRET) {
                    add("$where: secrets are not allowed in filter arguments")
                } else {
                    addAll(pathProblems(arg, "$where: argument '${arg.text}'", roots))
                }
            }
        }
        e.filters.forEach { f ->
            val arity = Filters.ARITY[f.name]
            when {
                arity == null ->
                    add("$where: unknown filter '${f.name}' (known: ${Filters.ARITY.keys.joinToString()})")
                f.args.size !in arity ->
                    add("$where: filter '${f.name}' takes ${arity.first}..${arity.last} arguments")
                e.root == Roots.VALUES && f.name != "round" ->
                    add("$where: on-device values only support the 'round' filter, not '${f.name}'")
                f.name == "lookup" && f.args.single() !in lookups ->
                    add("$where: unknown lookup '${f.args.single()}' (declare it in \"lookups\")")
                f.name == "round" && f.args.isNotEmpty() && f.args.single().toIntOrNull() !in 0..10 ->
                    add("$where: round digits must be 0..10")
            }
        }
    }
}

/** Root and `params`/`values` checks of one path: a template expression or a path filter argument (T-041). */
private fun ScreenDefinition.pathProblems(e: Template.Expression, where: String, roots: Set<String>): List<String> =
    buildList {
        when {
            e.root == Roots.SECRET && Roots.SECRET !in roots ->
                add("$where: secrets are only allowed in data source urls and headers")
            e.root !in roots -> add("$where: ${unknownRoot(e.root, roots)}")
            e.root == Roots.PARAMS -> {
                val param = (e.path.singleOrNull() as? PathStep.Key)?.name
                if (param == null) {
                    add("$where: use {{params.<name>}}")
                } else if (param !in params) {
                    add("$where: unknown param '$param' (declare it in \"params\")")
                }
            }
            e.root == Roots.VALUES -> {
                val valueName = (e.path.singleOrNull() as? PathStep.Key)?.name
                if (valueName == null) {
                    add("$where: use {{values.<name>}}")
                } else if (valueName !in values) {
                    add("$where: unknown value '$valueName' (declare it in \"values\")")
                }
            }
            e.root == Roots.SECRET && (e.path.singleOrNull() !is PathStep.Key) ->
                add("$where: use {{secret.<NAME>}}")
        }
    }

private fun unknownRoot(root: String, roots: Set<String>) =
    "unknown '$root' (available here: ${roots.sorted().joinToString()})"

/** Scheme and host must be literal: request values must not choose the server (SSRF). */
private fun urlProblem(url: String): String? {
    val scheme = listOf("https://", "http://").firstOrNull { url.startsWith(it) }
        ?: return "must start with http:// or https://"
    val hostEnd = url.indexOfAny(charArrayOf('/', '?', '#'), scheme.length).let { if (it < 0) url.length else it }
    val host = url.substring(scheme.length, hostEnd)
    val literal = host.isNotEmpty() && "{" !in host && "}" !in host
    return if (literal) null else "host must be literal (no {{ }} before the path)"
}

/** Deepest allowed nesting of component lists (containers, expandables) — keeps documents sane. */
private const val MAX_DEPTH = 32

private val COLOR = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")
