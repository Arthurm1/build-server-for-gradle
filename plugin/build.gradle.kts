import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("java-gradle-plugin")
  id("maven-publish")
  id("java")
}

// disable the plugin publishing so maven & plugin don't clash on publish
gradlePlugin {
  isAutomatedPublishing = false
}

// use `gradlew plugin:publishToLocalMaven` to publish local and test
publishing {
  repositories {
    mavenLocal()
  }
  publications {
    create<MavenPublication>("maven") {
      groupId = project.findProperty("group") as String
      artifactId = project.findProperty("pluginArtifactId") as String
      version = project.findProperty("version") as String

      from(components["java"])
    }
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

tasks.named<Test>("test") {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
  systemProperty("junit.jupiter.execution.parallel.enabled", "true");
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(8)
  }
}

tasks.register<Test>("test11") {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
  systemProperty("junit.jupiter.execution.parallel.enabled", "true");
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(11)
  }
}

tasks.register<Test>("test17") {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
  systemProperty("junit.jupiter.execution.parallel.enabled", "true");
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
  }
}

tasks.register<Test>("test21") {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = TestExceptionFormat.FULL
  }
  systemProperty("junit.jupiter.execution.parallel.enabled", "true");
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

tasks.named("check") {
  dependsOn("test", "test11", "test17", "test21")
}

// for android libraries
repositories {
  maven {
    url = uri("https://maven.google.com/")
  }
}

dependencies {
  implementation(project(":model"))
  // DO NOT USE THESE in the codebase but it's handy to bring up the source in the IDE
  // Java toolchain must be switched to JDK 17 for this to compile
  // compileOnly("com.android.application:com.android.application.gradle.plugin:8.5.1")
  // compileOnly("com.android.library:com.android.library.gradle.plugin:8.5.1")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}