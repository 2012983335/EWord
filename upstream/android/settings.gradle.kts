pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                ivy {
                    url = uri("https://storage.googleapis.com/r8-releases")
                    patternLayout { artifact("/raw/[revision]/[module].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("r8") }
        }
    }
}

rootProject.name = "Artefact"
include(":app")
include(":backdrop")
include(":officecompat")
