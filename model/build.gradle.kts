plugins {
  id("java-library")
  id("maven-publish")
}

// use `gradlew model:publishToLocalMaven` to publish local and test
publishing {
  repositories {
    mavenLocal()
  }
  publications {
    create<MavenPublication>("maven") {
      groupId = project.findProperty("group") as String
      artifactId = project.findProperty("modelArtifactId") as String
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

dependencies {
  implementation("org.gradle:gradle-tooling-api:8.12")
}