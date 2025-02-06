import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("application")
  id("maven-publish")
  id("com.opencastsoftware.gradle.buildinfo") version "0.3.1"
}

// Sourcecode generation.  Gradle properties can now be referenced by java code
buildInfo {
  packageName = "com.microsoft.java.bs.core"
  className = "BuildInfo"
  properties = mapOf(
    "bspVersion" to project.findProperty("bspVersion") as String,
    "serverName" to "gradle-build-server",
    "groupId" to project.findProperty("group") as String,
    "pluginArtifactId" to project.findProperty("pluginArtifactId") as String,
    "version" to project.findProperty("version") as String
  )
}

// exclude the generated sources
tasks.withType<Checkstyle>().configureEach {
  exclude("**/BuildInfo.java")
}

// use `gradlew server:publishToLocalMaven` to publish local and test
publishing {
  repositories {
    mavenLocal()
  }
  publications {
    create<MavenPublication>("maven") {
      groupId = project.findProperty("group") as String
      artifactId = project.findProperty("serverArtifactId") as String
      version = project.findProperty("version") as String

      from(components["java"])
    }
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