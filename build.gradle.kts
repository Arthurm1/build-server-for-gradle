plugins {
  id("com.github.ben-manes.versions") version "0.52.0"
  checkstyle
  // needed here (even though not applied) to prevent class loader issues
  id("com.vanniktech.maven.publish") version ("0.30.0") apply false
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "checkstyle")

  repositories {
    mavenCentral()
    maven {
      url = uri("https://repo.gradle.org/gradle/libs-releases")
    }
    maven {
      name = "checkstyle-backport-jre8-maven"
      url = uri("https://rnveach.github.io/checkstyle-backport-jre8/maven2")
    }
  }

  checkstyle {
    toolVersion = "10.18.1"
    maxWarnings = 0
  }

  dependencies {
    // use the backport as current checkstyle requires JDK 11
    checkstyle("com.puppycrawl.tools:checkstyle-backport-jre8:${checkstyle.toolVersion}")
  }
}
