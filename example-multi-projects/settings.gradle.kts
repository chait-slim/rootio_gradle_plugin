pluginManagement {
    repositories {
        maven {
            url = uri("https://pkg.root.io/gradle-plugins")
            credentials {
                username = "token"
                password = providers.environmentVariable("ROOTIO_API_KEY").get()
            }
        }
        gradlePluginPortal()
    }
}

rootProject.name = "rootio-multi-project-example"
include("app", "lib")
