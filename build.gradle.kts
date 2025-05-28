import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
  alias(libs.plugins.dependencyUpdates)
  // needed here (even though not applied) to prevent class loader issues
  alias(libs.plugins.vanniktechPublish) apply false
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
  checkConstraints = true
  //checkBuildEnvironmentConstraints = true
  checkForGradleUpdate = true

  resolutionStrategy {
    componentSelection {
      all(Action<com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent>({
        if (candidate.group == "ch.epfl.scala" &&
            candidate.module == "bsp4j") {
          if (candidate.version == "2.2.0-M4.TEST") {
            reject("Invalid release")
          }
        } else if (isNonStable(candidate.version)) {
            reject("Release candidate")
        }
      }))
    }
  }
}