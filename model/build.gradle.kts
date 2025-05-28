plugins {
  id("java-library")
  id("com.microsoft.java.bs.checkstyle")
  // publishing to Central Portal
  alias (libs.plugins.vanniktechPublish)
}

repositories {
  mavenCentral()
  maven {
    url = uri("https://repo.gradle.org/gradle/libs-releases")
  }
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(8)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.add("-Xlint:all")
}

dependencies {
  implementation(libs.gradleTooling)
  // The tooling API need an SLF4J implementation available at runtime
  runtimeOnly(libs.slf4j)
}