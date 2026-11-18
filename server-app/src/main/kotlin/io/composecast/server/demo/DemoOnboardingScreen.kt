package io.composecast.server.demo

import io.composecast.protocol.Action
import io.composecast.server.sdk.RegistryBuilder

/** The demo `onboarding` screen. */
internal fun RegistryBuilder.onboardingScreen() {
    screen("onboarding") {
        title = "Get Started"
        text("How it works", style = "h2")
        text("The server defines your UI. The app renders it.", style = "body")
        spacer(16)
        expandable("Local state on device", expanded = true) {
            text("Both states are in the document. Tap the header to collapse or expand without a server request.")
        }
        spacer(8)
        expandable("Wire format") {
            text("The server compiles screens to binary Remote Compose documents played natively on Android.")
        }
        spacer(16)
        button("Continue", action = "navigate:main")
        spacer(8)
        button("Actions demo", Action.Navigate("actions"))
    }
}
