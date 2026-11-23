package io.composecast.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Debug builds only: overrides app-shell's https-only `@xml/composecast_network_security_config`
 * with one that also allows plain HTTP to local dev hosts (the emulator's 10.0.2.2, localhost).
 * [allowAllHosts] (the Preview app, every build): plain HTTP to any host.
 */
abstract class GenerateDebugNetworkConfigTask : DefaultTask() {
    @get:Input
    abstract val allowAllHosts: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile.resolve("xml").apply { mkdirs() }
        if (allowAllHosts.get()) {
            dir.resolve("composecast_network_security_config.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <network-security-config>
                    <base-config cleartextTrafficPermitted="true" />
                </network-security-config>
                """.trimIndent() + "\n",
            )
            return
        }
        dir.resolve("composecast_network_security_config.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <domain-config cleartextTrafficPermitted="true">
                    <domain includeSubdomains="false">10.0.2.2</domain>
                    <domain includeSubdomains="false">localhost</domain>
                    <domain includeSubdomains="false">127.0.0.1</domain>
                </domain-config>
            </network-security-config>
            """.trimIndent() + "\n",
        )
    }
}
