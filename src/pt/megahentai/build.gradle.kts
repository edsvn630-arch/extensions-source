import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MegaHentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "MegaHentai"
        baseUrl = "https://megahentai.biz"
        lang = "pt-BR"
        id = 4821907365518203311L
        versionId = 1
    }
}
