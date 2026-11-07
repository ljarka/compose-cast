package io.composecast.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.composecast.protocol.ComponentModel
import io.composecast.protocol.ScreenModel

@Composable
fun ComposeCastScreen(
    screen: ScreenModel,
    onAction: (action: String, payload: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        screen.components.forEach { component ->
            ComponentView(component = component, onAction = onAction)
        }
    }
}

@Composable
private fun ComponentView(
    component: ComponentModel,
    onAction: (action: String, payload: String) -> Unit,
) {
    when (component) {
        is ComponentModel.Text -> {
            val style = when (component.style) {
                "h1" -> MaterialTheme.typography.headlineLarge
                "h2" -> MaterialTheme.typography.headlineMedium
                "h3" -> MaterialTheme.typography.headlineSmall
                "caption" -> MaterialTheme.typography.bodySmall
                else -> MaterialTheme.typography.bodyMedium
            }
            Text(text = component.text, style = style, modifier = Modifier.fillMaxWidth())
        }

        is ComponentModel.Image -> {
            // Image loading is handled by the host app; SDK surfaces the URL via onAction("image", url)
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = component.alt.ifBlank { component.url },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is ComponentModel.Button -> {
            val parts = component.action.split(":", limit = 2)
            val actionType = parts.getOrElse(0) { component.action }
            val actionPayload = parts.getOrElse(1) { "" }
            Button(
                onClick = { onAction(actionType, actionPayload) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(component.label)
            }
        }

        is ComponentModel.Spacer -> {
            Spacer(modifier = Modifier.height(component.height.dp))
        }
    }
}
