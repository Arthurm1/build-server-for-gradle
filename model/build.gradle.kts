plugins {
  id("java-library")
  // publishing to Central Portal
  id("com.vanniktech.maven.publish") version ("0.30.0")
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
  implementation("org.gradle:gradle-tooling-api:8.12")
}