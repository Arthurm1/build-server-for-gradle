// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.microsoft.java.bs.core.Launcher;
import com.microsoft.java.bs.core.internal.managers.PreferenceManager;
import com.microsoft.java.bs.core.internal.model.Preferences;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.StatusCode;

import org.gradle.tooling.model.build.BuildEnvironment;

class GradleApiConnectorTest {

  private static Path projectPath;

  @BeforeAll
  static void beforeClass() {
    projectPath = Paths.get(
        System.getProperty("user.dir"),
        "..",
        "testProjects"
    ).normalize();
    String pluginDir = Paths.get(System.getProperty("user.dir"),
        "build", "libs", "plugins").toString();
    System.setProperty(Launcher.PROP_PLUGIN_DIR, pluginDir);
  }

  private <A> A withConnector(Function<GradleApiConnector, A> function) {
    return withConnector(function, new Preferences());
  }

  private <A> A withConnector(Function<GradleApiConnector, A> function, Preferences preferences) {
    PreferenceManager preferenceManager = new PreferenceManager();
    preferenceManager.setPreferences(preferences);
    preferenceManager.setClientSupportedLanguages(SupportedLanguages.allBspNames);
    GradleApiConnector connector = new GradleApiConnector(preferenceManager);
    try {
      return function.apply(connector);
    } finally {
      connector.shutdown();
    }
  }
  
  private GradleSourceSets getGradleSourceSets(File projectDir) {
    return withConnector(connector -> connector.getGradleSourceSets(projectDir.toURI(),
        null, null));
  }

  @Test
  void testGetGradleVersion() {
    File projectDir = projectPath.resolve("gradle-4.3-with-wrapper").toFile();
    String version = withConnector(connector -> connector.getGradleVersion(projectDir.toURI(),
        null));
    assertEquals("4.3", version);
  }

  @Test
  void testGetGradleSourceSets() {
    File projectDir = projectPath.resolve("junit5-jupiter-starter-gradle").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
      assertNotNull(SupportedLanguages.JAVA.getExtension(gradleSourceSet));
      assertNull(SupportedLanguages.SCALA.getExtension(gradleSourceSet));
      assertNull(SupportedLanguages.KOTLIN.getExtension(gradleSourceSet));
      assertEquals("junit5-jupiter-starter-gradle", gradleSourceSet.getProjectName());
      assertEquals(":", gradleSourceSet.getProjectPath());
      assertEquals(projectDir, gradleSourceSet.getProjectDir());
      assertEquals(projectDir, gradleSourceSet.getRootDir());
    }

    assertEquals("main", findSourceSet(gradleSourceSets,
        "junit5-jupiter-starter-gradle", "main").getSourceSetName());
    assertEquals("test", findSourceSet(gradleSourceSets,
        "junit5-jupiter-starter-gradle", "test").getSourceSetName());
  }

  @Test
  void testAndroidSourceSets() {
    File projectDir = projectPath.resolve("android-test").toFile();
    PreferenceManager preferenceManager = new PreferenceManager();
    preferenceManager.setPreferences(new Preferences());
    GradleApiConnector connector = new GradleApiConnector(preferenceManager);
    GradleSourceSets gradleSourceSets = connector.getGradleSourceSets(projectDir.toURI(),
        null, null);
    assertEquals(10, gradleSourceSets.getGradleSourceSets().size());
    findSourceSet(gradleSourceSets, "app", "debug");
    findSourceSet(gradleSourceSets, "app", "debugUnitTest");
    findSourceSet(gradleSourceSets, "app", "debugAndroidTest");
    findSourceSet(gradleSourceSets, "app", "release");
    findSourceSet(gradleSourceSets, "app", "releaseUnitTest");
    findSourceSet(gradleSourceSets, "mylibrary", "debug");
    findSourceSet(gradleSourceSets, "mylibrary", "debugUnitTest");
    findSourceSet(gradleSourceSets, "mylibrary", "debugAndroidTest");
    findSourceSet(gradleSourceSets, "mylibrary", "release");
    findSourceSet(gradleSourceSets, "mylibrary", "releaseUnitTest");
    Set<GradleModuleDependency> combinedModuleDependencies = new HashSet<>();
    for (GradleSourceSet sourceSet : gradleSourceSets.getGradleSourceSets()) {
      combinedModuleDependencies.addAll(sourceSet.getModuleDependencies());
    }
    // This test can vary depending on the environment due to generated files.
    // Specifically R file and Android Components. For eg:
    // 1. When android-test project has not been or doesn't have the resources compiled
    //    the R.jar files don't exist for the build targets and are not included.
    // 2. ANDROID_HOME is not configured in which case the Android Component classpath
    //    is not added to module dependencies.
    assertTrue(combinedModuleDependencies.size() >= 82);
  }

  private GradleSourceSet findSourceSet(GradleSourceSets gradleSourceSets,
        String projectName, String sourceSetName) {
    GradleSourceSet sourceSet = gradleSourceSets.getGradleSourceSets().stream()
        .filter(ss -> ss.getProjectName().equals(projectName)
            && ss.getSourceSetName().equals(sourceSetName))
        .findFirst()
        .orElse(null);
    assertNotNull(sourceSet, () -> {
      String availableSourceSets = gradleSourceSets.getGradleSourceSets().stream()
          .map(ss -> ss.getProjectName() + " " + ss.getSourceSetName())
          .collect(Collectors.joining(", "));
      return "SourceSet not found " + projectName + " " + sourceSetName
          + ". Available: " + availableSourceSets;
    });
    return sourceSet;
  }

  @Test
  void testGetGradleDuplicateNestedProjectNames() {
    File projectDir = projectPath.resolve("duplicate-nested-project-names").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(12, gradleSourceSets.getGradleSourceSets().size());
    findSourceSet(gradleSourceSets, "a", "main");
    findSourceSet(gradleSourceSets, "a", "test");
    findSourceSet(gradleSourceSets, "b", "main");
    findSourceSet(gradleSourceSets, "b", "test");
    findSourceSet(gradleSourceSets, "b-test", "main");
    findSourceSet(gradleSourceSets, "b-test", "test");
    findSourceSet(gradleSourceSets, "c", "main");
    findSourceSet(gradleSourceSets, "c", "test");
    findSourceSet(gradleSourceSets, "d", "main");
    findSourceSet(gradleSourceSets, "d", "test");
    findSourceSet(gradleSourceSets, "e", "main");
    findSourceSet(gradleSourceSets, "e", "test");
  }

  @Test
  void testGetGradleHasTests() {
    File projectDir = projectPath.resolve("test-tag").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(5, gradleSourceSets.getGradleSourceSets().size());
    assertFalse(findSourceSet(gradleSourceSets, "test-tag", "main").hasTests());
    assertTrue(findSourceSet(gradleSourceSets, "test-tag", "test").hasTests());
    assertFalse(findSourceSet(gradleSourceSets, "test-tag", "noTests").hasTests());
    assertTrue(findSourceSet(gradleSourceSets, "test-tag", "intTest").hasTests());
    assertFalse(findSourceSet(gradleSourceSets, "test-tag", "testFixtures").hasTests());
  }

  @Test
  void testCompositeBuild1() {
    File projectDir = projectPath.resolve("composite-build-1").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(4, gradleSourceSets.getGradleSourceSets().size());
    findSourceSet(gradleSourceSets, "projectA", "main");
    findSourceSet(gradleSourceSets, "projectA", "test");
    findSourceSet(gradleSourceSets, "projectB", "main");
    findSourceSet(gradleSourceSets, "projectB", "test");
  }

  @Test
  void testCompositeBuild2() {
    File projectDir = projectPath.resolve("composite-build-2").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(6, gradleSourceSets.getGradleSourceSets().size());
    findSourceSet(gradleSourceSets, "app", "test");
    findSourceSet(gradleSourceSets, "string-utils", "test");
    findSourceSet(gradleSourceSets, "number-utils", "test");
    GradleSourceSet mainApp = findSourceSet(gradleSourceSets, "app", "main");
    GradleSourceSet mainStringUtils = findSourceSet(gradleSourceSets, "string-utils", "main");
    GradleSourceSet mainNumberUtils = findSourceSet(gradleSourceSets, "number-utils", "main");
    assertHasBuildTargetDependency(mainApp, mainStringUtils);
    assertHasBuildTargetDependency(mainApp, mainNumberUtils);
  }

  private void assertHasBuildTargetDependency(GradleSourceSet sourceSet,
      GradleSourceSet dependency) {
    boolean exists = sourceSet.getBuildTargetDependencies().stream()
        .anyMatch(dep -> dep.getProjectDir().equals(dependency.getProjectDir().getAbsolutePath())
                      && dep.getSourceSetName().equals(dependency.getSourceSetName()));
    assertTrue(exists, () -> {
      String availableDependencies = sourceSet.getBuildTargetDependencies().stream()
          .map(ss -> ss.getProjectDir() + ' ' + ss.getSourceSetName())
          .collect(Collectors.joining(", "));
      return "Dependency not found " + dependency.getProjectPath() + ' '
        + dependency.getSourceSetName() + ". Available: " + availableDependencies;
    });
  }

  @Test
  void testGetGradleDependenciesWithTestFixtures() {
    File projectDir = projectPath.resolve("project-dependency-test-fixtures").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(5, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet mainA = findSourceSet(gradleSourceSets, "a", "main");
    assertEquals(0, mainA.getBuildTargetDependencies().size());
    GradleSourceSet testFixturesA = findSourceSet(gradleSourceSets, "a", "testFixtures");
    assertEquals(1, testFixturesA.getBuildTargetDependencies().size());
    GradleSourceSet testA = findSourceSet(gradleSourceSets, "a", "test");
    assertEquals(2, testA.getBuildTargetDependencies().size());
    GradleSourceSet mainB = findSourceSet(gradleSourceSets, "b", "main");
    assertEquals(0, mainB.getBuildTargetDependencies().size());
    GradleSourceSet testB = findSourceSet(gradleSourceSets, "b", "test");
    assertEquals(3, testB.getBuildTargetDependencies().size());
    assertHasBuildTargetDependency(testFixturesA, mainA);
    assertHasBuildTargetDependency(testA, mainA);
    assertHasBuildTargetDependency(testA, testFixturesA);
    assertHasBuildTargetDependency(testB, testFixturesA);
    assertHasBuildTargetDependency(testB, mainA);
    assertHasBuildTargetDependency(testB, mainB);
  }

  @Test
  void testGetGradleDependenciesWithTestToMain() {
    File projectDir = projectPath.resolve("project-dependency-test-to-main").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet main = findSourceSet(gradleSourceSets,
        "project-dependency-test-to-main", "main");
    GradleSourceSet test = findSourceSet(gradleSourceSets,
        "project-dependency-test-to-main", "test");
    assertEquals(0, main.getBuildTargetDependencies().size());
    assertHasBuildTargetDependency(test, main);
    assertEquals(1, test.getBuildTargetDependencies().size());
  }

  @Test
  void testGetGradleDependenciesWithSourceSetOutput() {
    File projectDir = projectPath.resolve("project-dependency-sourceset-output").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(4, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet testA = findSourceSet(gradleSourceSets, "a", "test");
    assertEquals(1, testA.getBuildTargetDependencies().size());
    GradleSourceSet mainB = findSourceSet(gradleSourceSets, "b", "main");
    assertEquals(0, mainB.getBuildTargetDependencies().size());
    GradleSourceSet testB = findSourceSet(gradleSourceSets, "b", "test");
    assertEquals(2, testB.getBuildTargetDependencies().size());
    assertHasBuildTargetDependency(testB, testA);
    assertHasBuildTargetDependency(testB, mainB);
  }

  @Test
  void testGetGradleDependenciesWithConfiguration() {
    File projectDir = projectPath.resolve("project-dependency-configuration").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(4, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet mainA = findSourceSet(gradleSourceSets, "a", "main");
    GradleSourceSet mainB = findSourceSet(gradleSourceSets, "b", "main");
    assertHasBuildTargetDependency(mainB, mainA);
  }

  @Test
  void testGetGradleDependenciesWithTestConfiguration() {
    File projectDir = projectPath.resolve("project-dependency-test-configuration").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(4, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet testA = findSourceSet(gradleSourceSets, "a", "test");
    GradleSourceSet testB = findSourceSet(gradleSourceSets, "b", "test");
    assertHasBuildTargetDependency(testB, testA);
  }

  @Test
  void testGetGradleDependenciesWithLazyArchive() {
    File projectDir = projectPath.resolve("project-dependency-lazy-archive").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(4, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet testA = findSourceSet(gradleSourceSets, "a", "test");
    GradleSourceSet testB = findSourceSet(gradleSourceSets, "b", "test");
    assertHasBuildTargetDependency(testB, testA);
  }

  @Test
  void testGetGradleHasScala2() {
    File projectDir = projectPath.resolve("scala-2").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet main = findSourceSet(gradleSourceSets, "scala-2", "main");
    ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(main);
    assertNotNull(scalaExtension);
    assertEquals("org.scala-lang", scalaExtension.getScalaOrganization());
    assertEquals("2.13.12", scalaExtension.getScalaVersion());
    assertEquals("2.13", scalaExtension.getScalaBinaryVersion());

    assertFalse(main.getCompileClasspath().isEmpty());
    assertTrue(main.getCompileClasspath().stream().anyMatch(
            file -> file.getName().contains("scala-library")));
    assertFalse(scalaExtension.getScalaJars().isEmpty());
    assertTrue(scalaExtension.getScalaJars().stream().anyMatch(
            file -> file.getName().contains("scala-compiler")));
    assertFalse(scalaExtension.getScalaCompilerArgs().isEmpty());
    assertTrue(scalaExtension.getScalaCompilerArgs().stream()
        .anyMatch(arg -> arg.equals("-deprecation")));
  }

  @Test
  void testGetGradleHasScala3() {
    File projectDir = projectPath.resolve("scala-3").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet main = findSourceSet(gradleSourceSets, "scala-3", "main");
    ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(main);
    assertNotNull(scalaExtension);
    assertEquals("org.scala-lang", scalaExtension.getScalaOrganization());
    assertEquals("3.3.1", scalaExtension.getScalaVersion());
    assertEquals("3.3", scalaExtension.getScalaBinaryVersion());

    assertFalse(main.getCompileClasspath().isEmpty());
    assertTrue(main.getCompileClasspath().stream().anyMatch(
            file -> file.getName().contains("scala3-library_3")));
    assertFalse(scalaExtension.getScalaJars().isEmpty());
    assertTrue(scalaExtension.getScalaJars().stream().anyMatch(
            file -> file.getName().contains("scala3-compiler_3")));
    assertFalse(scalaExtension.getScalaCompilerArgs().isEmpty());
    assertTrue(scalaExtension.getScalaCompilerArgs().stream()
        .anyMatch(arg -> arg.equals("-deprecation")));
  }
  
  @Test
  void testBuildTargetTest() {
    File projectDir = projectPath.resolve("java-tests").toFile();
    withConnector(connector -> {
      Map<BuildTargetIdentifier, Map<String, Set<String>>> testClassesMap = new HashMap<>();
      BuildTargetIdentifier fakeBt = new BuildTargetIdentifier("fake");
      Map<String, Set<String>> classes = new HashMap<>();
      testClassesMap.put(fakeBt, classes);
      Set<String> methods = new HashSet<>();
      classes.put("com.example.project.PassingTests", methods);
      StatusCode passingTest = connector.runTests(projectDir.toURI(),
          testClassesMap, null, null, null, null, null, null, null);
      assertEquals(StatusCode.OK, passingTest);
      classes.clear();
      classes.put("com.example.project.FailingTests", methods);
      StatusCode failingTest = connector.runTests(projectDir.toURI(),
          testClassesMap, null, null, null, null, null, null, null);
      assertEquals(StatusCode.ERROR, failingTest);
      return null;
    });
  }

  @Test
  void testGradleProperties() {
    File projectDir = projectPath.resolve("gradle-properties").toFile();
    withConnector(connector -> {
      BuildEnvironment buildEnv = connector.getBuildEnvironment(projectDir.toURI(),
          null);
      assertTrue(buildEnv.getJava().getJvmArguments().stream()
          .anyMatch(arg -> arg.contains("-Xmx1234m")));
      return null;
    });
    // check supplying args doesn't wipe the gradle.properties ones
    Preferences preferences = new Preferences();
    preferences.setGradleJvmArguments(new ArrayList<>());
    withConnector(connector -> {
      BuildEnvironment buildEnv = connector.getBuildEnvironment(projectDir.toURI(),
          null);
      assertTrue(buildEnv.getJava().getJvmArguments().stream()
          .anyMatch(arg -> arg.contains("-Xmx1234m")));
      return null;
    }, preferences);
  }

  @Test
  void testGetGradleHasKotlin() {
    File projectDir = projectPath.resolve("kotlin").toFile();
    GradleSourceSets gradleSourceSets = getGradleSourceSets(projectDir);
    assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    GradleSourceSet main = findSourceSet(gradleSourceSets, "kotlin", "main");
    KotlinExtension kotlinExtension = SupportedLanguages.KOTLIN.getExtension(main);
    assertNotNull(kotlinExtension);
    assertEquals("1.2", kotlinExtension.getKotlinApiVersion());
    assertEquals("1.3", kotlinExtension.getKotlinLanguageVersion());
    assertFalse(main.getCompileClasspath().isEmpty());
    assertTrue(main.getCompileClasspath().stream().anyMatch(
            file -> file.getName().equals("kotlin-stdlib-1.9.21.jar")));
    assertFalse(kotlinExtension.getKotlincOptions().isEmpty());
    assertTrue(kotlinExtension.getKotlincOptions().stream()
            .anyMatch(arg -> arg.equals("-opt-in=org.mylibrary.OptInAnnotation")));

    // TODO test getKotlinAssociates
  }
}
