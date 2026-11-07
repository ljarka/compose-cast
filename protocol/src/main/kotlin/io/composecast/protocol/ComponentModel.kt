package io.composecast.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ComponentModel {

    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        val style: String = "body"
    ) : ComponentModel()

    @Serializable
    @SerialName("image")
    data class Image(
        val url: String,
        val alt: String = ""
    ) : ComponentModel()

    @Serializable
    @SerialName("button")
    data class Button(
        val label: String,
        val action: String
    ) : ComponentModel()

    @Serializable
    @SerialName("spacer")
    data class Spacer(
        val height: Int = 8
    ) : ComponentModel()
}
