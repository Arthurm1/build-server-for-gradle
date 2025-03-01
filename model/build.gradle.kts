plugins {
  id("java-library")
  // publishing to Central Portal
  alias (libs.plugins.vanniktechPublish)
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(8)
  }
}

tasks.withType<Checkstyle>().configureEach {
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
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