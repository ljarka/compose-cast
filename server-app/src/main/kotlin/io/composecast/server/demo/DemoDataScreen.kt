package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.server.sdk.RegistryBuilder

/**
 * Data sources and bindings (T-006): live data from Open-Meteo (keyless), rendered per request;
 * `/screens/data?lat=…&lon=…&city=…` shows another place. A generic demo of the feature —
 * real apps define such screens in their own repositories (ADR 0017).
 */
internal fun RegistryBuilder.dataScreen() {
    screen("data") {
        title = "{{params.city}}"
        param("lat", default = "52.52")
        param("lon", default = "13.41")
        param("city", default = "Berlin")
        data(
            "meteo",
            url = "https://api.open-meteo.com/v1/forecast?latitude={{params.lat}}&longitude={{params.lon}}" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                "&forecast_days=3&timezone=auto",
            cacheSeconds = 600,
        )
        lookup(
            "wmo",
            mapOf(
                "0" to "Clear sky",
                "1" to "Mainly clear",
                "2" to "Partly cloudy",
                "3" to "Overcast",
                "45" to "Fog",
                "61" to "Rain",
                "71" to "Snow",
                "95" to "Thunderstorm",
                "*" to "Changing weather",
            ),
        )
        text("Now", style = "caption")
        text("{{meteo.current.temperature_2m | round}}{{meteo.current_units.temperature_2m}}", style = "h1")
        text("{{meteo.current.weather_code | lookup:wmo}}")
        spacer(16)
        text("Next days", style = "caption")
        repeat(
            zip = mapOf(
                "day" to "meteo.daily.time",
                "max" to "meteo.daily.temperature_2m_max",
                "min" to "meteo.daily.temperature_2m_min",
            ),
            alias = "d",
            empty = { text("No forecast") },
        ) {
            text("{{d.day | date:'EEEE'}}: {{d.max | round}}° / {{d.min | round}}°")
        }
        spacer(16)
        button("Warsaw", Action.Navigate("data", mapOf("lat" to "52.23", "lon" to "21.01", "city" to "Warsaw")))
        error {
            text("No data for {{params.city}}", style = "h3")
            text("The data source did not answer. Try again in a moment.")
            spacer(8)
            button("Retry", Action.Reload)
        }
    }
}
