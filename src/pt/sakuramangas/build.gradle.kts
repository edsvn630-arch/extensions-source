import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sakura Mangás"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "Sakura Mangás"
        baseUrl = "https://sakuramangas.org"
        lang = "pt-BR"
        id = 7350291846120537194L
        versionId = 1
    }
}
