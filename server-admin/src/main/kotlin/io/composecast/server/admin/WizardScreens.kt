package io.composecast.server.admin

import io.composecast.protocol.Action
import io.composecast.protocol.Alignment
import io.composecast.protocol.Component
import io.composecast.protocol.ContentScale
import io.composecast.protocol.DataSource
import io.composecast.protocol.Dimension
import io.composecast.protocol.Modifier
import io.composecast.protocol.Padding
import io.composecast.protocol.ParamSpec
import io.composecast.protocol.RepeatItems
import io.composecast.protocol.ScreenDefinition
import io.composecast.protocol.TextOverflow
import io.composecast.protocol.TextStyle

/**
 * The screens the data source wizard creates (T-031 W2): a list screen (a `repeat` of cards, tap →
 * detail) and a detail screen, ordinary screen definitions the visual editor edits afterwards.
 */
internal object WizardScreens {
    /** What the owner picked in the wizard. Field paths are inside one list item (`name`, `image.medium`). */
    data class Plan(
        val source: String,
        val url: String,
        val headers: Map<String, String>,
        /** Path of the list in the response, `""` when the response is the list. */
        val listPath: String,
        val id: String?,
        val title: String,
        val subtitle: String?,
        val image: String?,
        /** Fields that hold HTML (`ApiShape.Kind.LongText` with tags): shown through `plain`. */
        val html: Set<String>,
        val listScreen: String,
        val listTitle: String,
        val detailScreen: String,
        /** `…/shows/{{params.id}}`, or null: the detail shows the fields passed from the list. */
        val detailUrl: String?,
        /** A real id for the detail's `params.id` default, so the detail renders without a tap. */
        val sampleId: String?,
        val limit: Int = 20,
    )

    private const val ITEM = "item"
    private const val DETAIL_SOURCE = "detail"

    fun list(plan: Plan): ScreenDefinition {
        fun bind(field: String) = binding(ITEM, field, plan.html)
        val texts = Component.Column(
            children = listOfNotNull(
                Component.Text(bind(plan.title), style = TextStyle.H3, maxLines = 2, overflow = TextOverflow.Ellipsis),
                plan.subtitle?.let {
                    Component.Text(bind(it), style = TextStyle.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
            ),
            spacing = 4,
            modifier = Modifier(weight = 1f),
        )
        val image = plan.image?.let {
            Component.Image(
                url = bind(it),
                alt = bind(plan.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier(width = Dimension.Dp(72), height = Dimension.Dp(72), cornerRadius = 8),
            )
        }
        val params = if (plan.detailUrl != null) {
            mapOf("id" to bind(requireNotNull(plan.id) { "a detail address needs an id field" }))
        } else {
            // No detail endpoint: the detail shows what the list already has.
            listOfNotNull(
                "title" to plan.title,
                plan.subtitle?.let { "subtitle" to it },
                plan.image?.let {
                    "image" to
                        it
                },
            )
                .associate { (name, field) -> name to "{{$ITEM.$field}}" }
        }
        val card = Component.Card(
            children = listOf(
                Component.Row(
                    children = listOfNotNull(image, texts),
                    spacing = 12,
                    verticalAlignment = Alignment.Center,
                ),
            ),
            modifier = Modifier(action = Action.Navigate(plan.detailScreen, params)),
        )
        return ScreenDefinition(
            id = plan.listScreen,
            title = plan.listTitle,
            data = mapOf(plan.source to DataSource(plan.url, plan.headers)),
            components = listOf(
                Component.Column(
                    children = listOf(
                        Component.Repeat(
                            items = RepeatItems.Path(
                                listOf(plan.source, plan.listPath).filter(String::isNotEmpty).joinToString("."),
                            ),
                            alias = ITEM,
                            limit = plan.limit,
                            template = listOf(card),
                            empty = listOf(Component.Text("Nothing here yet.")),
                        ),
                    ),
                    spacing = 12,
                    modifier = Modifier(padding = Padding(16, 16, 16, 16)),
                ),
            ),
            error = errorComponents(),
        )
    }

    fun detail(plan: Plan): ScreenDefinition {
        val fromApi = plan.detailUrl != null
        fun bind(field: String, param: String) = if (fromApi) {
            binding(DETAIL_SOURCE, field, plan.html)
        } else {
            binding(
                "params",
                param,
                plan.html.mapTo(HashSet()) {
                    if (it ==
                        plan.subtitle
                    ) {
                        "subtitle"
                    } else {
                        it
                    }
                },
            )
        }
        val children = listOfNotNull(
            plan.image?.let {
                Component.Image(
                    url = bind(it, "image"),
                    alt = bind(plan.title, "title"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier(height = Dimension.Dp(240), cornerRadius = 12),
                )
            },
            Component.Text(bind(plan.title, "title"), style = TextStyle.H2),
            plan.subtitle?.let { Component.Text(bind(it, "subtitle")) },
        )
        return ScreenDefinition(
            id = plan.detailScreen,
            title = bind(plan.title, "title"),
            params = if (fromApi) {
                mapOf("id" to ParamSpec(default = plan.sampleId))
            } else {
                listOfNotNull(
                    "title",
                    plan.subtitle?.let {
                        "subtitle"
                    },
                    plan.image?.let { "image" },
                ).associateWith { ParamSpec(default = "") }
            },
            data = if (fromApi) mapOf(DETAIL_SOURCE to DataSource(plan.detailUrl!!, plan.headers)) else emptyMap(),
            components = listOf(
                Component.Column(
                    children = children,
                    spacing = 12,
                    modifier = Modifier(padding = Padding(16, 16, 16, 16)),
                ),
            ),
            error = if (fromApi) errorComponents() else emptyList(),
        )
    }

    private fun binding(root: String, field: String, html: Set<String>) =
        "{{$root.$field${if (field in html) " | plain" else ""}}}"

    private fun errorComponents() = listOf(
        Component.Column(
            children = listOf(
                Component.Text("Could not load the data.", style = TextStyle.H3),
                Component.Text("The service did not answer. Try again in a moment."),
                Component.Button("Retry", Action.Reload),
            ),
            spacing = 12,
            modifier = Modifier(padding = Padding(16, 16, 16, 16)),
        ),
    )
}
