package io.composecast.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class BrandConfigTest {
    @Test
    fun `problems name the field`() {
        assertEquals(emptyList(), BrandConfig("Acme", "#112233", "#AA112233", surfaceColor = "#FFFFFF").problems())
        assertEquals(
            listOf(
                "name: must not be blank",
                "primaryColor: expected #RRGGBB or #AARRGGBB, was 'red'",
                "outlineColor: expected #RRGGBB or #AARRGGBB, was '#12345'",
            ),
            BrandConfig(" ", "red", "#112233", outlineColor = "#12345").problems(),
        )
    }
}
