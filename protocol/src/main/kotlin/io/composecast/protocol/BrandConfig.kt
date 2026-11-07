package io.composecast.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BrandConfig(
    val name: String,
    val primaryColor: String,
    val secondaryColor: String,
    val backgroundColor: String = "#FFFFFF",
    val fontFamily: String = "default",
)
