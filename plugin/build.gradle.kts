import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("java-gradle-plugin")
  id("java")
  // publishing to Central Portal
  id("com.vanniktech.maven.publish") version ("0.30.0")
}

/*
gradlePlugin {
  website.set("https://github.com/arthurm1/build-server-for-gradle")
  vcsUrl.set("https://github.com/arthurm1/build-server-for-gradle.git")
  plugins {
    create("gradleBuildServerPlugin") {
      id = "io.github.arthurm1.gradle-bsp-plugin"
      displayName = "Gradle Build Server Plugin"
      description = "A Gradle plugin to aid with BSP implementation"
      implementationClass = "com.microsoft.java.bs.gradle.plugin.GradleBuildServerPlugin"
      tags.set(listOf("Build Server Protocol", "BSP"))
    }
  }
}
*/

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
/*
repositories {
  maven {
    url = uri("https://maven.google.com/")
  }
}
*/

dependencies {
  implementation(project(":model"))
  // DO NOT USE THESE in the codebase but it's handy to bring up the source in the IDE
  // Java toolchain must be switched to JDK 17 for this to compile
  // compileOnly("com.android.application:com.android.application.gradle.plugin:8.5.1")
  // compileOnly("com.android.library:com.android.library.gradle.plugin:8.5.1")
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}