import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("application")
  // source generation - to put build info in the app
  alias (libs.plugins.buildConfig)
  // publishing to Central Portal
  alias (libs.plugins.vanniktechPublish)
}

buildConfig {
  packageName("com.microsoft.java.bs.core")
  className("BuildInfo")
  useJavaOutput()
  buildConfigField("bspVersion", libs.versions.bsp)
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
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(17)
  }
  // server tests use the plugin in Maven Local so plugin and model must be published first
  dependsOn(":model:publishToMavenLocal")
  dependsOn(":plugin:publishToMavenLocal")
}

dependencies {
  implementation(project(":model"))
  implementation(libs.bsp)
  implementation(libs.commonsLang)
  implementation(libs.gradleTooling)
  implementation(libs.gson)
  // The tooling API need an SLF4J implementation available at runtime
  runtimeOnly(libs.slf4j)

  testImplementation(libs.bundles.mokito)
  testImplementation(libs.junit)
  testRuntimeOnly(libs.junitLauncher)
}