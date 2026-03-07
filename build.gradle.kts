plugins {
    alias(libs.plugins.android.application) apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    alias(libs.plugins.hilt) apply false
	alias(libs.plugins.ksp) apply false
}
