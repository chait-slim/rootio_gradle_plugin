pluginManagement {
    includeBuild("../../rootio_patcher/gradle-plugin")
}

rootProject.name = "rootio-multi-project-example"
include("app", "lib")