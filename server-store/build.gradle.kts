plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // api: feature modules define their own Exposed tables and run them through StoreDatabase.tx.
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    runtimeOnly(libs.sqlite.jdbc)
    testImplementation(libs.kotlin.test)
}
