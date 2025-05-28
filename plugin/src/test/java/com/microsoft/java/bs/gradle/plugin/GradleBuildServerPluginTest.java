// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.microsoft.java.bs.gradle.model.AntlrExtension;
import com.microsoft.java.bs.gradle.model.Artifact;
import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.GroovyExtension;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.actions.GetSourceSetsAction;
import com.microsoft.java.bs.gradle.model.impl.DefaultBuildTargetDependency;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ConfigurableLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
class GradleBuildServerPluginTest {

  private static Path projectPath;
  private static final Map<File, ReentrantLock> projectLocks = new HashMap<>();
  private static final Map<GradleVersion, ReentrantLock> gradleLocks = new HashMap<>();

  @BeforeAll
  static void beforeClass() {
    projectPath = Paths.get(
        System.getProperty("user.dir"),
        "..",
        "testProjects"
    ).normalize();
    // uncomment this to debug the server using attach to remote
    // see GradleAPIConnector#getGradleSourceSets for usage.
    System.setProperty("bsp.plugin.debug.enabled", "true");
  }

  @AfterAll
  static void afterClass() {
    System.clearProperty("bsp.plugin.debug.enabled");
  }

  private void setupLauncher(ConfigurableLauncher<?> launcher) {
    String javaHome = System.getProperty("java.home");
    if (javaHome != null) {
      if (javaHome.endsWith("jre")) {
        // only needed for Gradle 2.12.  Otherwise fails with a mismatch on daemon java_home
        javaHome = javaHome.substring(0, javaHome.length() - 4);
      }
      launcher.setJavaHome(new File(javaHome));
    }
    launcher.setStandardOutput(System.out);
    launcher.setStandardError(System.err);
  }

  private GradleSourceSets getGradleSourceSets(ProjectConnection connect) throws IOException {
    BuildActionExecuter<GradleSourceSets> action = connect.action(new GetSourceSetsAction());
    String initScriptContents = PluginHelper.getInitScriptContents();
    File initScript = PluginHelper.getInitScript(initScriptContents);
    try {
      action
          .addArguments("--init-script", initScript.getAbsolutePath())
          .addArguments("-Dorg.gradle.daemon.idletimeout=10")
          .addArguments("-Dorg.gradle.vfs.watch=false")
          .addArguments("-Dorg.gradle.logging.level=quiet");
      if (Boolean.getBoolean("bsp.plugin.debug.enabled")) {
        action.addJvmArguments(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
      }
      setupLauncher(action);

      return new DefaultGradleSourceSets(action.run());
    } catch (Exception e) {
      throw new IllegalStateException("Error retrieving source sets", e);
    } finally {
      if (initScript != null) {
        initScript.delete();
      }
    }
  }

  private interface ConnectionConsumer {
    void accept(ProjectConnection connection) throws IOException;
  }

  private <K> ReentrantLock getLock(K key, Map<K, ReentrantLock> locks) {
    ReentrantLock lock = locks.get(key);
    if (lock == null) {
      synchronized (GradleBuildServerPluginTest.class) {
        lock = locks.get(key);
        if (lock == null) {
          lock = new ReentrantLock();
          locks.put(key, lock);
        }
      }
    }
    return lock;
  }

  private void withConnection(File projectDir, GradleVersion gradleVersion,
      ConnectionConsumer consumer) throws IOException {
    // don't allow simultaneous use of same test project
    ReentrantLock projectLock = getLock(projectDir, projectLocks);
    projectLock.lock();
    try {
      // don't allow simultaneous use of same Gradle version
      ReentrantLock gradleLock = getLock(gradleVersion, gradleLocks);
      gradleLock.lock();
      try {
        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir);
        if (gradleVersion != null) {
          connector.useGradleVersion(gradleVersion.getVersion());
        }
        try (ProjectConnection connect = connector.connect()) {
          consumer.accept(connect);
        } finally {
          connector.disconnect();
        }
      } finally {
        gradleLock.unlock();
      }
    } finally {
      projectLock.unlock();
    }
  }

  private void withSourceSets(String projectName, GradleVersion gradleVersion,
      Consumer<GradleSourceSets> consumer) throws IOException {
    File projectDir = projectPath.resolve(projectName).toFile();
    withConnection(projectDir, gradleVersion, connect -> {
      GradleSourceSets gradleSourceSets = getGradleSourceSets(connect);
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        if (gradleVersion != null) {
          assertEquals(gradleVersion.getVersion(), gradleSourceSet.getGradleVersion());
        }
      }
      consumer.accept(gradleSourceSets);
    });
  }

  private static int getJavaVersion() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      version = version.substring(2, 3);
    } else {
      int dot = version.indexOf(".");
      if (dot != -1) {
        version = version.substring(0, dot);
      }
    }
    return Integer.parseInt(version);
  }

  private static class GradleJreVersion {
    final GradleVersion gradleVersion;
    final int jreVersion;

    GradleJreVersion(String gradleVersion, int jreVersion) {
      this.gradleVersion = GradleVersion.version(gradleVersion);
      this.jreVersion = jreVersion;
    }
  }
  
  /**
   * create a list of gradle versions that work with the runtime JRE and the Gradle version passed.
   */
  private static Stream<GradleVersion> versionProvider(String gradleVersionStr,
        Integer jreVersion) {
    GradleVersion gradleVersion = gradleVersionStr != null
        ? GradleVersion.version(gradleVersionStr) : null;
    int currentJavaVersion = getJavaVersion();
    // change the last version in the below list to point to the highest Gradle version supported
    // if the Gradle API changes then keep that version forever and add a comment as to why
    return Stream.of(
      // earliest supported version
      new GradleJreVersion("2.12", 8),
      // java source/target options specified in 2.14
      // tooling api jar name changed from gradle-tooling-api to gradle-api in 3.0
      new GradleJreVersion("3.0", 8),
      // artifacts view added in 4.0
      // ArtifactResult#getId added in 4.0
      // CompileOptions#getAnnotationProcessorPath added in 3.4
      // RuntimeClasspathConfigurationName added to sourceset in 3.4
      // Test#getTestClassesDir -> Test#getTestClassesDirs in 4.0
      // sourceSet#getJava#getOutputDir added in 4.0
      new GradleJreVersion("4.2.1", 8),
      // CompileOptions#getAnnotationProcessorGeneratedSourcesDirectory added in 4.3
      new GradleJreVersion("4.3.1", 9),
      // SourceSetContainer added to project#getExtensions in 5.0
      new GradleJreVersion("5.0", 11),
      // AbstractArchiveTask#getArchiveFile -> AbstractArchiveTask#getArchiveFile in 5.1
      // annotation processor dirs auto created in 5.2
      new GradleJreVersion("5.2", 11),
      // sourceSet#getJava#getOutputDir -> sourceSet#getJava#getClassesDirectory in 6.1
      new GradleJreVersion("6.1", 13),
      // DefaultCopySpec#getChildren changed from Iterable to Collection in 6.2
      new GradleJreVersion("6.2", 13),
      // CompileOptions#getGeneratedSourceOutputDirectory added in 6.3
      new GradleJreVersion("6.3", 14),
      // CompileOptions#getRelease added in 6.6
      new GradleJreVersion("6.6", 13),
      // ScalaSourceDirectorySet added to project#getExtensions in 7.1
      new GradleJreVersion("7.1", 16),
      // Scala 3 support added in 7.3
      new GradleJreVersion("7.3", 17),
      // FoojayToolchainsPlugin requires >= 7.6
      new GradleJreVersion("7.6.1", 19),
      // JDK source/target options changed from 1.9 -> 9 in 8.0
      new GradleJreVersion("8.0", 19),
      // Android plugin support
      new GradleJreVersion("8.7", 21),
      // highest supported version
      new GradleJreVersion("8.12", 23)
    ).filter(version -> version.jreVersion >= currentJavaVersion)
     .filter(version -> jreVersion == null || currentJavaVersion >= jreVersion)
     .filter(version -> gradleVersion == null
         || version.gradleVersion.compareTo(gradleVersion) >= 0)
     .map(version -> version.gradleVersion);
  }

  @Test
  void testWrapper() throws IOException {
    assumeTrue(getJavaVersion() < 18);
    withSourceSets("gradle-7.3-with-wrapper", null, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet sourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals("7.3", sourceSet.getGradleVersion());
      }
    });
  }

  static Stream<GradleVersion> allVersions() {
    return versionProvider("2.12", null);
  }

  @ParameterizedTest(name = "testModelBuilder {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testModelBuilder(GradleVersion gradleVersion) throws IOException {
    withSourceSets("junit5-jupiter-starter-gradle", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals("junit5-jupiter-starter-gradle", gradleSourceSet.getProjectName());
        File projectDir = projectPath.resolve("junit5-jupiter-starter-gradle").toFile();
        assertEquals(projectDir, gradleSourceSet.getProjectDir());
        assertEquals(projectDir, gradleSourceSet.getRootDir());
        assertEquals(":", gradleSourceSet.getProjectPath());
        assertTrue(gradleSourceSet.getSourceSetName().equals("main")
            || gradleSourceSet.getSourceSetName().equals("test"));
        assertTrue(gradleSourceSet.getClassesTaskName().equals(":classes")
            || gradleSourceSet.getClassesTaskName().equals(":testClasses"));
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertFalse(gradleSourceSet.getRuntimeClasspath().isEmpty());
        assertEquals(1, gradleSourceSet.getSourceDirs().size());
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "java"));
        // annotation processor dirs weren't auto created before 5.2
        if (gradleVersion.compareTo(GradleVersion.version("5.2")) >= 0) {
          assertEquals(1, gradleSourceSet.getGeneratedSourceDirs().size());
        }
        assertEquals(1, gradleSourceSet.getResourceDirs().size());
        assertNotNull(gradleSourceSet.getSourceOutputDirs());
        assertNotNull(gradleSourceSet.getResourceOutputDirs());

        assertNotNull(gradleSourceSet.getBuildTargetDependencies());
        assertNotNull(gradleSourceSet.getModuleDependencies());
        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
            dependency -> dependency.getModule().equals("a.jar")),
            () -> gradleSourceSet.getModuleDependencies().toString());

        if (gradleVersion.compareTo(GradleVersion.version("3.0")) > 0) {
          assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
                  dependency -> dependency.getModule().equals("Gradle API")),
              () -> gradleSourceSet.getModuleDependencies().toString());
        } else if (gradleVersion.compareTo(GradleVersion.version("3.0")) == 0) {
          assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
                  dependency -> dependency.getModule().equals("gradle-api-3.0.jar")),
              () -> gradleSourceSet.getModuleDependencies().toString());
        } else {
          assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
              dependency -> dependency.getModule().contains("gradle-tooling-api")),
              () -> gradleSourceSet.getModuleDependencies().toString());
        }

        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        assertNotNull(javaExtension.getJavaHome());
        assertNotNull(javaExtension.getJavaVersion());
        assertNotNull(javaExtension.getSourceCompatibility());
        assertNotNull(javaExtension.getTargetCompatibility());
        assertNotNull(javaExtension.getCompilerArgs());
        
        // dirs not split by language before 4.0
        if (gradleVersion.compareTo(GradleVersion.version("4.0")) >= 0) {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "java",
              gradleSourceSet.getSourceSetName()));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes",
              gradleSourceSet.getSourceSetName()));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
        }
      }
    });
  }

  // buildSrc included in "GradleBuild#getIncludedBuilds" in 7.2
  // but init-scripts weren't applied to buildSrc before 8.0
  static Stream<GradleVersion> versionsFrom8_0() {
    return versionProvider("8.0", null);
  }

  @ParameterizedTest(name = "testBuildSrc {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom8_0")
  void testBuildSrc(GradleVersion gradleVersion) throws IOException {
    withSourceSets("build-src", gradleVersion, gradleSourceSets -> {
      assertEquals(6, gradleSourceSets.getGradleSourceSets().size());
      GradleSourceSet main = findSourceSet(gradleSourceSets.getGradleSourceSets(),
          "build-src", "main");
      GradleSourceSet fooMain = findSourceSet(gradleSourceSets.getGradleSourceSets(),
          "foo", "main");
      GradleSourceSet buildSrcMain = findSourceSet(gradleSourceSets.getGradleSourceSets(),
          "buildSrc", "main");

      assertEquals(projectPath.resolve("build-src"), main.getRootDir().toPath());
      assertEquals(projectPath.resolve("build-src"), fooMain.getRootDir().toPath());
      assertEquals(projectPath.resolve("build-src").resolve("buildSrc"),
          buildSrcMain.getRootDir().toPath());

      assertEquals(":", main.getProjectPath());
      assertEquals(":foo", fooMain.getProjectPath());
      assertEquals(":", buildSrcMain.getProjectPath());

      assertEquals("build-src", main.getRootProjectName());
      assertEquals("build-src", fooMain.getRootProjectName());
      assertEquals("buildSrc", buildSrcMain.getRootProjectName());

      assertEquals(":classes", main.getClassesTaskName());
      assertEquals(":foo:classes", fooMain.getClassesTaskName());
      assertEquals(":buildSrc:classes", buildSrcMain.getClassesTaskName());

      assertEquals(":clean", main.getCleanTaskName());
      assertEquals(":foo:clean", fooMain.getCleanTaskName());
      assertEquals(":buildSrc:clean", buildSrcMain.getCleanTaskName());

      GradleSourceSet mainTest = findSourceSet(gradleSourceSets.getGradleSourceSets(),
          "build-src", "test");
      GradleSourceSet fooTest = findSourceSet(gradleSourceSets.getGradleSourceSets(),
          "foo", "test");
      GradleSourceSet buildSrcTest = findSourceSet(gradleSourceSets.getGradleSourceSets(),
          "buildSrc", "test");

      assertEquals(1, mainTest.getTestTasks().size());
      assertEquals(1, fooTest.getTestTasks().size());
      assertEquals(1, buildSrcTest.getTestTasks().size());

      assertEquals(":test", mainTest.getTestTasks().iterator().next().getTaskPath());
      assertEquals(":foo:test", fooTest.getTestTasks().iterator().next().getTaskPath());
      assertEquals(":buildSrc:test", buildSrcTest.getTestTasks().iterator().next().getTaskPath());
    });
  }

  static Stream<GradleVersion> versionsFrom4_0() {
    return versionProvider("4.0", null);
  }

  @ParameterizedTest(name = "testMultipleSourceJars {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom4_0")
  void testMultipleSourceJars(GradleVersion gradleVersion) throws IOException {
    withSourceSets("multiple-source-jars", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertNotNull(gradleSourceSet.getModuleDependencies());
        assertFalse(gradleSourceSet.getModuleDependencies().isEmpty());
        gradleSourceSet.getModuleDependencies().forEach(dependency -> {
          Optional<Artifact> jar = dependency.getArtifacts().stream()
              .filter(artifact -> artifact.getClassifier() == null).findAny();
          Optional<Artifact> sourceJar = dependency.getArtifacts().stream()
              .filter(artifact -> "sources".equals(artifact.getClassifier())).findAny();
          assertTrue(jar.isPresent());
          assertTrue(sourceJar.isPresent());
          String jarUri = Paths.get(jar.get().getUri()).getFileName().toString();
          String sourceUri = Paths.get(sourceJar.get().getUri()).getFileName().toString();
          String sourceFromJar = jarUri.substring(0, jarUri.length() - 4) + ".src.jar";
          assertEquals(sourceUri, sourceFromJar);
        });
      }
    });
  }

  @ParameterizedTest(name = "testGetSourceContainerFromOldGradle {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testMissingRepository(GradleVersion gradleVersion) throws IOException {
    withSourceSets("missing-repository", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testGetSourceContainerFromOldGradle {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testGetSourceContainerFromOldGradle(GradleVersion gradleVersion) throws IOException {
    withSourceSets("non-java", gradleVersion, gradleSourceSets -> {
      assertEquals(0, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testGetOutputLocationFromOldGradle {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testGetOutputLocationFromOldGradle(GradleVersion gradleVersion) throws IOException {
    withSourceSets("legacy-gradle", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testGetAnnotationProcessorGeneratedLocation {0}",
      allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testGetAnnotationProcessorGeneratedLocation(GradleVersion gradleVersion) throws IOException {
    // this test case is to ensure that the plugin won't throw no such method error
    // for JavaCompile.getAnnotationProcessorGeneratedSourcesDirectory()
    withSourceSets("legacy-gradle", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testSourceInference {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testSourceInference(GradleVersion gradleVersion) throws IOException {
    File projectDir = projectPath.resolve("infer-source-roots").toFile();
    withConnection(projectDir, gradleVersion, connect -> {
      BuildLauncher launcher = connect.newBuild().forTasks("clean", "compileJava");
      setupLauncher(launcher);
      launcher.run();
      GradleSourceSets gradleSourceSets = getGradleSourceSets(connect);
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      int generatedSourceDirCount = 0;
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals(1, gradleSourceSet.getSourceDirs().size());
        generatedSourceDirCount += gradleSourceSet.getGeneratedSourceDirs().size();
        assertTrue(hasPathEntry(gradleSourceSet.getGeneratedSourceDirs(),
            "build", "generated", "sources"));
      }
      
      // annotation processor dirs weren't auto created before 5.2
      if (gradleVersion.compareTo(GradleVersion.version("5.2")) >= 0) {
        assertEquals(4, generatedSourceDirCount);
      } else {
        assertEquals(2, generatedSourceDirCount);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs1 {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testJavaCompilerArgs1(GradleVersion gradleVersion) throws IOException {
    // Gradle uses 1.9 in earlier versions to indicate JDK 9
    final String targetVersion;
    if (gradleVersion.compareTo(GradleVersion.version("8.0")) >= 0) {
      targetVersion = "9";
    } else {
      targetVersion = "1.9";
    }
    withSourceSets("java-compilerargs-1", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--release"), () -> "Available args: " + args);
        if (gradleVersion.compareTo(GradleVersion.version("3.0")) >= 0) {
          assertTrue(hasArgEntry(args, "-source", "1.8"), () -> "Available args: " + args);
        }
        assertTrue(hasArgEntry(args, "-target", targetVersion), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Xlint:all"), () -> "Available args: " + args);
        if (gradleVersion.compareTo(GradleVersion.version("3.0")) >= 0) {
          assertEquals("1.8", javaExtension.getSourceCompatibility(),
              () -> "Available args: " + args);
        }
        assertEquals(targetVersion, javaExtension.getTargetCompatibility(),
            () -> "Available args: " + args);
      }
    });
  }

  static Stream<GradleVersion> versionsFrom6_6() {
    return versionProvider("6.6", null);
  }

  // JavaCompile#options#release was added in Gradle 6.6
  @ParameterizedTest(name = "testJavaCompilerArgs2 {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom6_6")
  void testJavaCompilerArgs2(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-2", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-target"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "--release", "9"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Xlint:all"), () -> "Available args: " + args);
        String version9 = gradleVersion.compareTo(GradleVersion.version("8.0")) >= 0 ? "9" : "1.9";
        assertEquals(version9, javaExtension.getSourceCompatibility(),
                () -> "Available args: " + args);
        assertEquals(version9, javaExtension.getTargetCompatibility(),
            () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs3 {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testJavaCompilerArgs3(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-3", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-target"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "--release", "9"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Xlint:all"), () -> "Available args: " + args);
        assertFalse(javaExtension.getSourceCompatibility().isEmpty(),
                () -> "Available args: " + args);
        assertFalse(javaExtension.getTargetCompatibility().isEmpty(),
                () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs4 {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testJavaCompilerArgs4(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-4", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--release"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-target"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "--source", "1.8"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "--target", "9"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Xlint:all"), () -> "Available args: " + args);
        assertFalse(javaExtension.getSourceCompatibility().isEmpty(),
                () -> "Available args: " + args);
        assertFalse(javaExtension.getTargetCompatibility().isEmpty(),
                () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs5 {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testJavaCompilerArgs5(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-5", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--release"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-source", "1.8"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-target", "9"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Xlint:all"), () -> "Available args: " + args);
        assertFalse(javaExtension.getSourceCompatibility().isEmpty(),
                () -> "Available args: " + args);
        assertFalse(javaExtension.getTargetCompatibility().isEmpty(),
                () -> "Available args: " + args);
      }
    });
  }

  static Stream<GradleVersion> versionsFrom2_14() {
    return versionProvider("2.14", null);
  }

  // Gradle doesn't set source/target unless specified until version 2.14
  @ParameterizedTest(name = "testJavaCompilerArgs6 {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom2_14")
  void testJavaCompilerArgs6(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-6", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--release"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-source"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-target"), () -> "Available args: " + args);
        assertFalse(javaExtension.getSourceCompatibility().isEmpty(),
            () -> "Available args: " + args);
        assertFalse(javaExtension.getTargetCompatibility().isEmpty(),
            () -> "Available args: " + args);
      }
    });
  }

  static Stream<GradleVersion> versionsFrom7_6() {
    return versionProvider("7.6", null);
  }

  // FoojayToolchainsPlugin needs Gradle version 7.6 or higher
  @ParameterizedTest(name = "testJavaCompilerArgsToolchain {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom7_6")
  void testJavaCompilerArgsToolchain(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-toolchain", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--release"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-source", "17"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-target", "17"), () -> "Available args: " + args);
        assertFalse(javaExtension.getSourceCompatibility().isEmpty(),
            () -> "Available args: " + args);
        assertFalse(javaExtension.getTargetCompatibility().isEmpty(),
            () -> "Available args: " + args);
      }
    });
  }

  static Stream<GradleVersion> versionsFrom5_0() {
    return versionProvider("5.0", null);
  }

  // `java` cannot be used before 5.0
  @ParameterizedTest(name = "testJavaSourceTarget {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom5_0")
  void testJavaSourceTarget(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-source-target", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "-target"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "--release", "11"), () -> "Available args: " + args);
        String version9 = gradleVersion.compareTo(GradleVersion.version("8.0")) >= 0 ? "9" : "1.9";
        assertEquals(version9, javaExtension.getSourceCompatibility(),
                () -> "Available args: " + args);
        assertEquals("1.8", javaExtension.getTargetCompatibility(),
                () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testScala2ModelBuilder {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testScala2ModelBuilder(GradleVersion gradleVersion) throws IOException {
    withSourceSets("scala-2", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals(":", gradleSourceSet.getProjectPath());
        assertTrue(gradleSourceSet.getSourceSetName().equals("main")
                || gradleSourceSet.getSourceSetName().equals("test"));
        assertTrue(gradleSourceSet.getClassesTaskName().equals(":classes")
                || gradleSourceSet.getClassesTaskName().equals(":testClasses"));
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertFalse(gradleSourceSet.getRuntimeClasspath().isEmpty());
        assertEquals(2, gradleSourceSet.getSourceDirs().size());
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "java"));
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "scala"));
        // annotation processor dirs weren't auto created before 5.2
        if (gradleVersion.compareTo(GradleVersion.version("5.2")) >= 0) {
          assertEquals(2, gradleSourceSet.getGeneratedSourceDirs().size());
        }
        assertEquals(1, gradleSourceSet.getResourceDirs().size());
        assertNotNull(gradleSourceSet.getBuildTargetDependencies());
        assertNotNull(gradleSourceSet.getModuleDependencies());
        assertNotNull(gradleSourceSet.getSourceOutputDirs());
        assertNotNull(gradleSourceSet.getResourceOutputDirs());

        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        assertNotNull(javaExtension.getJavaHome());
        assertNotNull(javaExtension.getJavaVersion());

        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
                dependency -> dependency.getModule().equals("scala-library")),
            () -> gradleSourceSet.getModuleDependencies().toString());
        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
            dependency -> dependency.getArtifacts().stream()
              .anyMatch(artifact -> artifact.getUri().toString()
                .contains("scala-library-2.13.12.jar"))),
            () -> gradleSourceSet.getModuleDependencies().toString());
        ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(gradleSourceSet);
        assertNotNull(scalaExtension);
        assertEquals("org.scala-lang", scalaExtension.getScalaOrganization());
        assertEquals("2.13.12", scalaExtension.getScalaVersion());
        assertEquals("2.13", scalaExtension.getScalaBinaryVersion());
        List<String> args = scalaExtension.getScalaCompilerArgs();
        assertTrue(hasArgEntry(args, "-deprecation"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-unchecked"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-g:notailcalls"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-optimise"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-encoding"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "utf8"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-verbose"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Ylog:erasure"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Ylog:lambdalift"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-foo"), () -> "Available args: " + args);

        assertTrue(hasPathEntry(gradleSourceSet.getCompileClasspath(),
            "scala-library-2.13.12.jar"));
        assertTrue(hasPathEntry(gradleSourceSet.getRuntimeClasspath(),
            "scala-library-2.13.12.jar"));
        assertFalse(scalaExtension.getScalaJars().isEmpty());
        assertTrue(hasPathEntry(scalaExtension.getScalaJars(), "scala-compiler-2.13.12.jar"));

        // dirs not split by language before 4.0
        if (gradleVersion.compareTo(GradleVersion.version("4.0")) >= 0) {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "java",
              gradleSourceSet.getSourceSetName()));
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "scala",
              gradleSourceSet.getSourceSetName()));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
          assertTrue(scalaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "scala",
              gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes",
              gradleSourceSet.getSourceSetName()));
          assertTrue(scalaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
        }
      }
    });
  }

  static Stream<GradleVersion> versionsFrom7_3() {
    return versionProvider("7.3", null);
  }

  // Scala 3 was added in Gradle 7.3
  @ParameterizedTest(name = "testScala3ModelBuilder {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom7_3")
  void testScala3ModelBuilder(GradleVersion gradleVersion) throws IOException {
    withSourceSets("scala-3", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals(":", gradleSourceSet.getProjectPath());
        assertTrue(gradleSourceSet.getSourceSetName().equals("main")
                || gradleSourceSet.getSourceSetName().equals("test"));
        assertTrue(gradleSourceSet.getClassesTaskName().equals(":classes")
                || gradleSourceSet.getClassesTaskName().equals(":testClasses"));
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertFalse(gradleSourceSet.getRuntimeClasspath().isEmpty());
        assertEquals(2, gradleSourceSet.getSourceDirs().size());
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "java"));
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "scala"));
        // annotation processor dirs weren't auto created before 5.2
        if (gradleVersion.compareTo(GradleVersion.version("5.2")) >= 0) {
          assertEquals(2, gradleSourceSet.getGeneratedSourceDirs().size());
        }
        assertEquals(1, gradleSourceSet.getResourceDirs().size());
        assertNotNull(gradleSourceSet.getSourceOutputDirs());
        assertNotNull(gradleSourceSet.getResourceOutputDirs());
        assertNotNull(gradleSourceSet.getBuildTargetDependencies());
        assertNotNull(gradleSourceSet.getModuleDependencies());
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        assertNotNull(javaExtension.getJavaHome());
        assertNotNull(javaExtension.getJavaVersion());

        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
            dependency -> dependency.getModule().contains("scala3-library_3")),
            () -> gradleSourceSet.getModuleDependencies().toString());

        ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(gradleSourceSet);
        assertNotNull(scalaExtension);
        assertEquals("org.scala-lang", scalaExtension.getScalaOrganization());
        assertEquals("3.3.1", scalaExtension.getScalaVersion());
        assertEquals("3.3", scalaExtension.getScalaBinaryVersion());
        List<String> args = scalaExtension.getScalaCompilerArgs();
        assertTrue(hasArgEntry(args, "-deprecation"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-unchecked"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-g:notailcalls"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-optimise"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-encoding"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "utf8"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-verbose"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Ylog:erasure"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-Ylog:lambdalift"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-foo"), () -> "Available args: " + args);

        assertTrue(hasPathEntry(gradleSourceSet.getCompileClasspath(),
            "scala3-library_3-3.3.1.jar"));
        assertTrue(hasPathEntry(gradleSourceSet.getRuntimeClasspath(),
            "scala3-library_3-3.3.1.jar"));
        assertFalse(scalaExtension.getScalaJars().isEmpty());
        assertTrue(hasPathEntry(scalaExtension.getScalaJars(), "scala3-compiler_3-3.3.1.jar"));

        assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "java",
            gradleSourceSet.getSourceSetName()));
        assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "scala",
            gradleSourceSet.getSourceSetName()));
        assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
            gradleSourceSet.getSourceSetName())));
        assertTrue(scalaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "scala",
            gradleSourceSet.getSourceSetName())));
      }
    });
  }

  static Stream<GradleVersion> versionsFrom7_4() {
    return versionProvider("7.4", null);
  }

  @ParameterizedTest(name = "testNebulaPlugin_11_10 {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom7_4")
  void testNebulaPlugin_11_10(GradleVersion gradleVersion) throws IOException {
    withSourceSets("nebula-plugin-11-10", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
      }
    });
  }

  static Stream<GradleVersion> versionsFrom5_2() {
    return versionProvider("5.2", null);
  }

  @ParameterizedTest(name = "testNebulaPlugin_11_5 {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom5_2")
  void testNebulaPlugin_11_5(GradleVersion gradleVersion) throws IOException {
    withSourceSets("nebula-plugin-11-5", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
      }
    });
  }

  static Stream<GradleVersion> versionsFrom7_1() {
    return versionProvider("7.1", null);
  }

  // can't find a valid compatibility matrix for gradle and kotlin plugin versions
  // Gradle>7.1 seems to support kotlin-gradle-plugin 1.9.21
  @ParameterizedTest(name = "testKotlinModelBuilder {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom7_1")
  void testKotlinModelBuilder(GradleVersion gradleVersion) throws IOException {
    withSourceSets("kotlin", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals("kotlin", gradleSourceSet.getProjectName());
        assertEquals(":", gradleSourceSet.getProjectPath());
        assertTrue(gradleSourceSet.getSourceSetName().equals("main")
                || gradleSourceSet.getSourceSetName().equals("test"),
                "Task name is: " + gradleSourceSet.getClassesTaskName());
        assertTrue(gradleSourceSet.getClassesTaskName().equals(":classes")
                || gradleSourceSet.getClassesTaskName().equals(":testClasses"),
                "Task name is: " + gradleSourceSet.getClassesTaskName());
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "java"));
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "kotlin"));
        assertFalse(gradleSourceSet.getGeneratedSourceDirs().isEmpty());
        assertFalse(gradleSourceSet.getResourceDirs().isEmpty());
        assertNotNull(gradleSourceSet.getSourceOutputDirs());
        assertNotNull(gradleSourceSet.getResourceOutputDirs());
        assertNotNull(gradleSourceSet.getBuildTargetDependencies());
        assertNotNull(gradleSourceSet.getModuleDependencies());
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        assertNotNull(javaExtension.getJavaHome());
        assertNotNull(javaExtension.getJavaVersion());

        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
            dependency -> dependency.getModule().contains("kotlin-stdlib")),
            () -> gradleSourceSet.getModuleDependencies().toString());

        KotlinExtension kotlinExtension = SupportedLanguages.KOTLIN.getExtension(gradleSourceSet);
        assertNotNull(kotlinExtension);
        assertEquals("1.2", kotlinExtension.getKotlinApiVersion());
        assertEquals("1.3", kotlinExtension.getKotlinLanguageVersion());
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertTrue(hasPathEntry(gradleSourceSet.getCompileClasspath(),
            "kotlin-stdlib-1.9.21.jar"));
        assertFalse(kotlinExtension.getKotlincOptions().isEmpty());
        assertTrue(kotlinExtension.getKotlincOptions().stream()
            .anyMatch(arg -> arg.equals("-opt-in=org.mylibrary.OptInAnnotation")));
                
        // dirs not split by language before 4.0
        if (gradleVersion.compareTo(GradleVersion.version("4.0")) >= 0) {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "java",
              gradleSourceSet.getSourceSetName()));
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "kotlin",
              gradleSourceSet.getSourceSetName()));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
          assertTrue(kotlinExtension.getClassesDir().toPath().endsWith(
              Paths.get("classes", "kotlin", gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes",
              gradleSourceSet.getSourceSetName()));
          assertTrue(kotlinExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
        }
      }
    });
  }

  @ParameterizedTest(name = "testGroovyModelBuilder {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testGroovyModelBuilder(GradleVersion gradleVersion) throws IOException {
    withSourceSets("groovy", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals("groovy", gradleSourceSet.getProjectName());
        assertEquals(":", gradleSourceSet.getProjectPath());
        assertTrue(gradleSourceSet.getSourceSetName().equals("main")
                || gradleSourceSet.getSourceSetName().equals("test"));
        assertTrue(gradleSourceSet.getClassesTaskName().equals(":classes")
                || gradleSourceSet.getClassesTaskName().equals(":testClasses"));
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "java"));
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "groovy"));
        // annotation processor dirs weren't auto created before 5.2
        if (gradleVersion.compareTo(GradleVersion.version("5.2")) >= 0) {
          assertEquals(2, gradleSourceSet.getGeneratedSourceDirs().size());
        }
        assertFalse(gradleSourceSet.getResourceDirs().isEmpty());
        assertNotNull(gradleSourceSet.getSourceOutputDirs());
        assertNotNull(gradleSourceSet.getResourceOutputDirs());
        assertNotNull(gradleSourceSet.getBuildTargetDependencies());
        assertNotNull(gradleSourceSet.getModuleDependencies());
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        assertNotNull(javaExtension.getJavaHome());
        assertNotNull(javaExtension.getJavaVersion());

        GroovyExtension groovyExtension = SupportedLanguages.GROOVY.getExtension(gradleSourceSet);
        assertNotNull(groovyExtension);

        // dirs not split by language before 4.0
        if (gradleVersion.compareTo(GradleVersion.version("4.0")) >= 0) {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "java",
              gradleSourceSet.getSourceSetName()));
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "groovy",
              gradleSourceSet.getSourceSetName()));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
          assertTrue(groovyExtension.getClassesDir().toPath().endsWith(
              Paths.get("classes", "groovy", gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes",
              gradleSourceSet.getSourceSetName()));
          assertTrue(groovyExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
        }
      }
    });
  }

  @ParameterizedTest(name = "testAntlrModelBuilder {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom7_1")
  void testAntlrModelBuilder(GradleVersion gradleVersion) throws IOException {
    withSourceSets("antlr", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals("antlr", gradleSourceSet.getProjectName());
        assertEquals(":", gradleSourceSet.getProjectPath());
        assertTrue(gradleSourceSet.getSourceSetName().equals("main")
                || gradleSourceSet.getSourceSetName().equals("test"));
        assertTrue(gradleSourceSet.getClassesTaskName().equals(":classes")
                || gradleSourceSet.getClassesTaskName().equals(":testClasses"));
        assertTrue(gradleSourceSet.getCompileClasspath().isEmpty());
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "java"));
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "antlr"));
        // annotation processor dirs weren't auto created before 5.2
        if (gradleVersion.compareTo(GradleVersion.version("5.2")) >= 0) {
          assertEquals(2, gradleSourceSet.getGeneratedSourceDirs().size());
        }
        assertFalse(gradleSourceSet.getResourceDirs().isEmpty());
        assertNotNull(gradleSourceSet.getSourceOutputDirs());
        assertNotNull(gradleSourceSet.getResourceOutputDirs());
        assertNotNull(gradleSourceSet.getBuildTargetDependencies());
        assertNotNull(gradleSourceSet.getModuleDependencies());
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        assertNotNull(javaExtension.getJavaHome());
        assertNotNull(javaExtension.getJavaVersion());

        AntlrExtension antlrExtension = SupportedLanguages.ANTLR.getExtension(gradleSourceSet);
        assertNotNull(antlrExtension);

        assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes", "java",
            gradleSourceSet.getSourceSetName()));
        assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
            gradleSourceSet.getSourceSetName())));
      }
    });
  }

  static Stream<GradleVersion> androidVersions() {
    return versionProvider("8.7", 17);
  }

  // Android tests have issues running concurrently
  @ParameterizedTest(name = "testAndroid {0}", allowZeroInvocations = true)
  @MethodSource("androidVersions")
  @Execution(ExecutionMode.SAME_THREAD)
  void testAndroid(GradleVersion gradleVersion) throws IOException {
    withSourceSets("android-test", gradleVersion, gradleSourceSets -> {
      List<GradleSourceSet> sourceSets = gradleSourceSets.getGradleSourceSets();
      assertEquals(10, sourceSets.size());

      File appDir = projectPath.resolve("android-test").resolve("app").toFile();
      GradleSourceSet appDebug = findSourceSet(sourceSets, "app", "debug");
      assertEquals(":app", appDebug.getProjectPath());
      assertEquals(appDir, appDebug.getProjectDir());
      assertEquals(":app:assembleDebug", appDebug.getClassesTaskName());
      assertEquals(47, appDebug.getCompileClasspath().size());
      assertEquals(57, appDebug.getRuntimeClasspath().size());
      assertEquals(4, appDebug.getSourceDirs().size());
      assertEquals(2, appDebug.getResourceOutputDirs().size());

      GradleSourceSet appRelease = findSourceSet(sourceSets, "app", "release");
      assertEquals(":app", appRelease.getProjectPath());
      assertEquals(appDir, appRelease.getProjectDir());
      assertEquals(":app:assembleRelease", appRelease.getClassesTaskName());
      assertEquals(47, appRelease.getCompileClasspath().size());
      assertEquals(57, appRelease.getRuntimeClasspath().size());
      assertEquals(4, appRelease.getSourceDirs().size());
      assertEquals(2, appRelease.getResourceOutputDirs().size());

      GradleSourceSet appDebugAndroidTest = findSourceSet(sourceSets, "app", "debugAndroidTest");
      assertEquals(":app", appDebugAndroidTest.getProjectPath());
      assertEquals(appDir, appDebugAndroidTest.getProjectDir());
      assertEquals(":app:assembleDebugAndroidTest", appDebugAndroidTest.getClassesTaskName());
      assertEquals(66, appDebugAndroidTest.getCompileClasspath().size());
      assertEquals(26, appDebugAndroidTest.getRuntimeClasspath().size());
      assertEquals(4, appDebugAndroidTest.getSourceDirs().size());
      assertEquals(2, appDebugAndroidTest.getResourceOutputDirs().size());

      GradleSourceSet appDebugUnitTest = findSourceSet(sourceSets, "app", "debugUnitTest");
      assertEquals(":app", appDebugUnitTest.getProjectPath());
      assertEquals(appDir, appDebugUnitTest.getProjectDir());
      assertEquals(":app:assembleDebugUnitTest", appDebugUnitTest.getClassesTaskName());
      assertEquals(51, appDebugUnitTest.getCompileClasspath().size());
      assertEquals(60, appDebugUnitTest.getRuntimeClasspath().size());
      assertEquals(4, appDebugUnitTest.getSourceDirs().size());
      assertEquals(1, appDebugUnitTest.getResourceOutputDirs().size());

      GradleSourceSet appReleaseUnitTest = findSourceSet(sourceSets, "app", "releaseUnitTest");
      assertEquals(":app", appReleaseUnitTest.getProjectPath());
      assertEquals(appDir, appReleaseUnitTest.getProjectDir());
      assertEquals(":app:assembleReleaseUnitTest", appReleaseUnitTest.getClassesTaskName());
      assertEquals(51, appReleaseUnitTest.getCompileClasspath().size());
      assertEquals(60, appReleaseUnitTest.getRuntimeClasspath().size());
      assertEquals(4, appReleaseUnitTest.getSourceDirs().size());
      assertEquals(1, appReleaseUnitTest.getResourceOutputDirs().size());

      File mylibraryDir = projectPath.resolve("android-test").resolve("mylibrary").toFile();
      GradleSourceSet mylibraryDebug = findSourceSet(sourceSets, "mylibrary", "debug");
      assertEquals(":mylibrary", mylibraryDebug.getProjectPath());
      assertEquals(mylibraryDir, mylibraryDebug.getProjectDir());
      assertEquals(":mylibrary:assembleDebug", mylibraryDebug.getClassesTaskName());
      assertEquals(0, mylibraryDebug.getCompileClasspath().size());
      assertEquals(0, mylibraryDebug.getRuntimeClasspath().size());
      assertEquals(4, mylibraryDebug.getSourceDirs().size());
      assertEquals(2, mylibraryDebug.getResourceOutputDirs().size());

      GradleSourceSet mylibraryRelease = findSourceSet(sourceSets, "mylibrary", "release");
      assertEquals(":mylibrary", mylibraryRelease.getProjectPath());
      assertEquals(mylibraryDir, mylibraryRelease.getProjectDir());
      assertEquals(":mylibrary:assembleRelease", mylibraryRelease.getClassesTaskName());
      assertEquals(0, mylibraryRelease.getCompileClasspath().size());
      assertEquals(0, mylibraryRelease.getRuntimeClasspath().size());
      assertEquals(4, mylibraryRelease.getSourceDirs().size());
      assertEquals(2, mylibraryRelease.getResourceOutputDirs().size());

      GradleSourceSet mylibraryDebugAndroidTest =
          findSourceSet(sourceSets, "mylibrary", "debugAndroidTest");
      assertEquals(":mylibrary", mylibraryDebugAndroidTest.getProjectPath());
      assertEquals(mylibraryDir, mylibraryDebugAndroidTest.getProjectDir());
      assertEquals(":mylibrary:assembleDebugAndroidTest",
          mylibraryDebugAndroidTest.getClassesTaskName());
      assertEquals(15, mylibraryDebugAndroidTest.getCompileClasspath().size());
      assertEquals(0, mylibraryDebugAndroidTest.getRuntimeClasspath().size());
      assertEquals(4, mylibraryDebugAndroidTest.getSourceDirs().size());
      assertEquals(2, mylibraryDebugAndroidTest.getResourceOutputDirs().size());

      GradleSourceSet mylibraryDebugUnitTest =
          findSourceSet(sourceSets, "mylibrary", "debugUnitTest");
      assertEquals(":mylibrary", mylibraryDebugUnitTest.getProjectPath());
      assertEquals(mylibraryDir, mylibraryDebugUnitTest.getProjectDir());
      assertEquals(":mylibrary:assembleDebugUnitTest",
          mylibraryDebugUnitTest.getClassesTaskName());
      assertEquals(4, mylibraryDebugUnitTest.getCompileClasspath().size());
      assertEquals(0, mylibraryDebugUnitTest.getRuntimeClasspath().size());
      assertEquals(4, mylibraryDebugUnitTest.getSourceDirs().size());
      assertEquals(1, mylibraryDebugUnitTest.getResourceOutputDirs().size());

      GradleSourceSet mylibraryReleaseUnitTest =
          findSourceSet(sourceSets, "mylibrary", "releaseUnitTest");
      assertEquals(":mylibrary", mylibraryReleaseUnitTest.getProjectPath());
      assertEquals(mylibraryDir, mylibraryReleaseUnitTest.getProjectDir());
      assertEquals(":mylibrary:assembleReleaseUnitTest",
          mylibraryReleaseUnitTest.getClassesTaskName());
      assertEquals(4, mylibraryReleaseUnitTest.getCompileClasspath().size());
      assertEquals(0, mylibraryReleaseUnitTest.getRuntimeClasspath().size());
      assertEquals(4, mylibraryReleaseUnitTest.getSourceDirs().size());
      assertEquals(1, mylibraryReleaseUnitTest.getResourceOutputDirs().size());

      for (GradleSourceSet gradleSourceSet : sourceSets) {
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "java"),
            gradleSourceSet::toString);
        assertTrue(hasPathEntry(gradleSourceSet.getSourceDirs(), "kotlin"),
            gradleSourceSet::toString);

        assertEquals(1, gradleSourceSet.getGeneratedSourceDirs().size(), gradleSourceSet::toString);
        assertEquals(4, gradleSourceSet.getResourceDirs().size(), gradleSourceSet::toString);
        assertEquals(1, gradleSourceSet.getSourceOutputDirs().size(), gradleSourceSet::toString);

        assertTrue(gradleSourceSet.getBuildTargetDependencies().isEmpty(),
            gradleSourceSet::toString);
        assertFalse(gradleSourceSet.getModuleDependencies().isEmpty(), gradleSourceSet::toString);
        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
            dependency -> dependency.getArtifacts().stream().anyMatch(
                artifact -> artifact.getUri().toString().endsWith("/android.jar"))),
            () -> gradleSourceSet.getModuleDependencies().toString());
        assertTrue(hasPathEntry(gradleSourceSet.getSourceOutputDirs(), "classes"));

        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);

        assertNotNull(javaExtension, gradleSourceSet::toString);
        assertNotNull(javaExtension.getJavaHome(), gradleSourceSet::toString);
        assertNotNull(javaExtension.getJavaVersion(), gradleSourceSet::toString);
        assertNotNull(javaExtension.getSourceCompatibility(), gradleSourceSet::toString);
        assertNotNull(javaExtension.getTargetCompatibility(), gradleSourceSet::toString);
        assertEquals(2, javaExtension.getSourceDirs().size(), gradleSourceSet::toString);
        Collection<String> args = javaExtension.getCompilerArgs();
        assertFalse(hasArgEntry(args, "--source"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--target"), () -> "Available args: " + args);
        assertFalse(hasArgEntry(args, "--release"), () -> "Available args: " + args);
        if (gradleVersion.compareTo(GradleVersion.version("3.0")) >= 0) {
          assertTrue(hasArgEntry(args, "-source", "1.8"), () -> "Available args: " + args);
        }
        assertTrue(hasArgEntry(args, "-target", "1.8"), () -> "Available args: " + args);
        assertTrue(hasArgEntry(args, "-bootclasspath"), () -> "Available args: " + args);
        if (gradleVersion.compareTo(GradleVersion.version("3.0")) >= 0) {
          assertEquals("1.8", javaExtension.getSourceCompatibility(),
                  () -> "Available args: " + args);
        }
        assertEquals("1.8", javaExtension.getTargetCompatibility(),
                () -> "Available args: " + args);
        assertTrue(javaExtension.getClassesDir().toPath().endsWith("classes"));

        KotlinExtension kotlinExtension = SupportedLanguages.KOTLIN.getExtension(gradleSourceSet);
        assertNotNull(kotlinExtension);
        assertEquals(4, kotlinExtension.getSourceDirs().size());
      }
    });
  }

  @ParameterizedTest(name = "testSourcesResourcesFolders {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testSourcesResourcesFolders(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-source-sets", gradleVersion, gradleSourceSets -> {
      GradleSourceSet mainA = findSourceSet(gradleSourceSets.getGradleSourceSets(), "a", "main");
      assertEquals(2, mainA.getSourceDirs().size());
      assertTrue(hasPathEntry(mainA.getSourceDirs(), "src", "main", "java"));
      assertTrue(hasPathEntry(mainA.getSourceDirs(), "src", "main", "scala"));
      assertEquals(1, mainA.getResourceDirs().size());
      assertTrue(hasPathEntry(mainA.getResourceDirs(), "src", "main", "resources"));

      GradleSourceSet mainB = findSourceSet(gradleSourceSets.getGradleSourceSets(), "b", "main");
      assertEquals(1, mainB.getSourceDirs().size());
      assertTrue(hasPathEntry(mainB.getSourceDirs(), "src", "main", "scala"));
      assertEquals(1, mainB.getResourceDirs().size());
      assertTrue(hasPathEntry(mainB.getResourceDirs(), "src", "main", "scala"));
    });
  }

  static Stream<GradleVersion> versionsFrom4_6() {
    return versionProvider("4.6", null);
  }

  @ParameterizedTest(name = "testAnnotationProcessor {0}", allowZeroInvocations = true)
  @MethodSource("versionsFrom4_6")
  void testAnnotationProcessor(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-annotationprocessor", gradleVersion, gradleSourceSets -> {
      GradleSourceSet main = findSourceSet(gradleSourceSets.getGradleSourceSets(),
          "java-annotationprocessor", "main");

      assertTrue(hasPathEntry(main.getCompileClasspath(), "value-2.8.2.jar"));
      assertFalse(hasPathEntry(main.getRuntimeClasspath(), "value-2.8.2.jar"));

      JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(main);
      int idx = javaExtension.getCompilerArgs().indexOf("-processorpath");
      if (idx >= 0) {
        assertTrue(javaExtension.getCompilerArgs().get(idx + 1).endsWith("value-2.8.2.jar"));
      } else {
        fail("No processor path");
      }
    });
  }

  @ParameterizedTest(name = "testIncludeFlat {0}", allowZeroInvocations = true)
  @MethodSource("allVersions")
  void testIncludeFlat(GradleVersion gradleVersion) throws IOException {
    withSourceSets("include-flat/project", gradleVersion, gradleSourceSets -> {
      GradleSourceSet mainA = findSourceSet(gradleSourceSets.getGradleSourceSets(), "a", "main");
      GradleSourceSet mainB = findSourceSet(gradleSourceSets.getGradleSourceSets(), "b", "main");
      Path projectDir = projectPath.resolve("include-flat");
      assertEquals(projectDir.resolve("a").toFile(), mainA.getProjectDir());
      assertEquals(projectDir.resolve("project").toFile(), mainA.getRootDir());
      assertEquals(projectDir.resolve("b").toFile(), mainB.getProjectDir());
      assertEquals(projectDir.resolve("project").toFile(), mainB.getRootDir());
      BuildTargetDependency depA = new DefaultBuildTargetDependency(mainA);
      assertTrue(mainB.getBuildTargetDependencies().contains(depA));

      // dirs not split by language before 4.0
      if (gradleVersion.compareTo(GradleVersion.version("4.0")) >= 0) {
        assertTrue(hasPathEntry(mainB.getCompileClasspath(), "a", "build", "classes", "java",
            "main"));
        assertTrue(hasPathEntry(mainB.getRuntimeClasspath(), "a", "build", "classes", "java",
            "main"));
      } else {
        assertTrue(hasPathEntry(mainB.getCompileClasspath(), "a", "build", "classes", "main"));
        assertTrue(hasPathEntry(mainB.getRuntimeClasspath(), "a", "build", "classes", "main"));
      }
      assertTrue(hasPathEntry(mainB.getCompileClasspath(), "a", "build", "resources", "main"));
      assertTrue(hasPathEntry(mainB.getRuntimeClasspath(), "a", "build", "resources", "main"));
    });
  }

  private boolean hasPathEntry(Collection<File> paths, String firstPath, String... morePaths) {
    return paths.stream()
        .anyMatch(file -> file.toPath().endsWith(Paths.get(firstPath, morePaths)));
  }

  private boolean hasArgEntry(Collection<String> paths, String firstArg, String... moreArgs) {
    boolean found = false;
    Iterator<String> iter = paths.iterator();
    while (!found && iter.hasNext()) {
      String next = iter.next();
      if (next != null && next.equals(firstArg)) {
        int i = 0;
        found = true;
        while (found && i < moreArgs.length) {
          if (iter.hasNext()) {
            String nextArg = iter.next();
            if (moreArgs[i] != null && moreArgs[i].equals(nextArg)) {
              i++;
            } else {
              found = false;
            }
          } else {
            found = false;
          }
        }
      }
    }
    return found;
  }

  private GradleSourceSet findSourceSet(List<GradleSourceSet> sourceSets,
      String projectName, String sourceSetName) {
    for (GradleSourceSet sourceSet : sourceSets) {
      if (sourceSet.getProjectName().equals(projectName)
          && sourceSet.getSourceSetName().equals(sourceSetName)) {
        return sourceSet;
      }
    }
    throw new IllegalStateException("Source Set " + projectName + " " + sourceSetName
        + " not found in "
        + sourceSets.stream()
               .map(f -> f.getProjectName() + ":" + f.getSourceSetName())
               .collect(Collectors.joining(", ")));
  }
}