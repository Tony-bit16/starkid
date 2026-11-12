pluginManagement {
    repositories {
        // 国内镜像优先，官方仓库兜底；只影响依赖下载，不影响工程结构。
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        // MPAndroidChart 只发布在 JitPack，限定 group，避免其他依赖误走 JitPack。
        maven("https://jitpack.io") {
            content { includeGroup("com.github.PhilJay") }
        }
    }
}

rootProject.name = "Fridge"
include(":refrigerator_main")
include(":dashboard_module")
