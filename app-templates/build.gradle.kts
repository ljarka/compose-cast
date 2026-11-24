plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Bundled app templates (T-030, ADR 0031): resources + the bundle format. Applying them (drafts in
// the database) is server-admin's job.
dependencies {
    api(project(":protocol"))
    testImplementation(project(":sdk-server")) // templates must build and render (secrets, dates, bindings)
    testImplementation(libs.kotlin.test)
}
