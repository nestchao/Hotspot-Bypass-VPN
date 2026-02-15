plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // ADD THIS LINE:
    alias(libs.plugins.kotlin.compose) apply false
}