plugins {
  alias(libs.plugins.dependencyUpdates)
  // needed here (even though not applied) to prevent class loader issues
  alias(libs.plugins.vanniktechPublish) apply false
  checkstyle
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "checkstyle")

  repositories {
    mavenCentral()
    maven {
      url = uri("https://repo.gradle.org/gradle/libs-releases")
    }
  }

  checkstyle {
    toolVersion = "10.21.3"
    maxWarnings = 0
  }
}
