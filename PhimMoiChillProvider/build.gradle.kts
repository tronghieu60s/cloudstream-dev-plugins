apply(plugin = "org.jetbrains.kotlin.android")
version = 3

cloudstream {
    status = 1
    authors = listOf("CloudStream")

    language = "vi"
    description = "CloudStream"

    tvTypes = listOf("Movie", "TvSeries")

    iconUrl = "https://codeberg.org/cloudstream609/cloudstream-extensions-vn/raw/branch/main/Icons/vn_icon.png"
    requiresResources = true
}

android {
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.core:core-ktx:+")
}
