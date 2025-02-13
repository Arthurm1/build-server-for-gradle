import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("application")
  // source generation - to put build info in the app
  id("com.github.gmazzo.buildconfig") version "5.5.1"
  // publishing to Central Portal
  id("com.vanniktech.maven.publish") version ("0.30.0")
}

buildConfig {
  packageName("com.microsoft.java.bs.core")
  className("BuildInfo")
  useJavaOutput()
  buildConfigField("bspVersion", project.findProperty("bspVersion") as String)
  buildConfigField("serverName", "gradle-build-server")
  buildConfigField("groupId", project.findProperty("GROUP") as String)
  buildConfigField("pluginArtifactId", "plugin")
  buildConfigField("version", project.findProperty("VERSION_NAME") as String)
}

// exclude the generated sources
tasks.withType<Checkstyle>().configureEach {
  exclude("**/BuildInfo.java")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(17)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.add("-Xlint:all")
}

application {
  mainClass = "com.microsoft.java.bs.core.Launcher"
}

tasks.named<Test>("test") {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
}

dependencies {
  implementation(project(":model"))
  implementation("ch.epfl.scala:bsp4j:${project.findProperty("bspVersion") as String}")
  implementation("org.apache.commons:commons-lang3:3.17.0")
  implementation("org.gradle:gradle-tooling-api:8.12")

  testImplementation(platform("org.junit:junit-bom:5.11.4"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.mockito:mockito-core:5.15.2")
  testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}