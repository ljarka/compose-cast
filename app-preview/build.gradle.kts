// The generic compose-cast Preview app (T-045, ADR 0024), built like an app repository: only the
// plugin and compose-cast.properties (app.preview=true). Attached to every GitHub Release as
// compose-cast-preview.apk by .github/workflows/release.yml.
plugins {
    id("io.composecast.app")
}
