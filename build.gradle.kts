// Top-level build file. Plugin versions come from the version catalog
// (gradle/libs.versions.toml); apply false here, apply per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
