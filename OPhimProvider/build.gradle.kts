version = 1

cloudstream {
    status = 1
    authors = listOf("MuaToolHay")

    language = "vi"
    description = "MuaToolHay"

    tvTypes = listOf("Movie", "TvSeries")

    iconUrl = "https://raw.githubusercontent.com/muatoolhay/cloudstream/main/vn_icon.png"
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
}
