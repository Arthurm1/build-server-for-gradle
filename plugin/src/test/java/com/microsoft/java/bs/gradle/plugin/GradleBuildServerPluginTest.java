// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.microsoft.java.bs.gradle.model.AntlrExtension;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.GroovyExtension;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.actions.GetSourceSetsAction;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSets;

import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.BeforeAll;
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
          .addArguments("-Dorg.gradle.logging.level=quiet")
          .addJvmArguments("-Dbsp.gradle.supportedLanguages="
            + String.join(",", SupportedLanguages.allBspNames))
          // Add back in to remote debug
          //.addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
          .setStandardOutput(System.out)
          .setStandardError(System.err);

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
            .forProjectDirectory(projectDir)
            .useGradleVersion(gradleVersion.getVersion());
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
        assertEquals(gradleVersion.getVersion(), gradleSourceSet.getGradleVersion());
        assertEquals(projectDir, gradleSourceSet.getRootDir());
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

  static Stream<GradleVersion> allVersions() {
    return versionProvider("2.12", null);
  }

  @ParameterizedTest(name = "testModelBuilder {0}")
  @MethodSource("allVersions")
  void testModelBuilder(GradleVersion gradleVersion) throws IOException {
    withSourceSets("junit5-jupiter-starter-gradle", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals("junit5-jupiter-starter-gradle", gradleSourceSet.getProjectName());
        assertEquals(projectPath.resolve("junit5-jupiter-starter-gradle").toFile(),
            gradleSourceSet.getProjectDir());
        assertEquals(":", gradleSourceSet.getProjectPath());
        assertTrue(gradleSourceSet.getSourceSetName().equals("main")
            || gradleSourceSet.getSourceSetName().equals("test"));
        assertTrue(gradleSourceSet.getClassesTaskName().equals(":classes")
            || gradleSourceSet.getClassesTaskName().equals(":testClasses"));
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertFalse(gradleSourceSet.getRuntimeClasspath().isEmpty());
        assertEquals(1, gradleSourceSet.getSourceDirs().size());
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("java")));
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
            dependency -> dependency.getModule().equals("a.jar")
        ));

        if (gradleVersion.compareTo(GradleVersion.version("3.0")) >= 0) {
          assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
              dependency -> dependency.getModule().contains("gradle-api")
          ));
        } else {
          assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
              dependency -> dependency.getModule().contains("gradle-tooling-api")
          ));
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
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
        }
      }
    });
  }

  @ParameterizedTest(name = "testGetSourceContainerFromOldGradle {0}")
  @MethodSource("allVersions")
  void testMissingRepository(GradleVersion gradleVersion) throws IOException {
    withSourceSets("missing-repository", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testGetSourceContainerFromOldGradle {0}")
  @MethodSource("allVersions")
  void testGetSourceContainerFromOldGradle(GradleVersion gradleVersion) throws IOException {
    withSourceSets("non-java", gradleVersion, gradleSourceSets -> {
      assertEquals(0, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testGetOutputLocationFromOldGradle {0}")
  @MethodSource("allVersions")
  void testGetOutputLocationFromOldGradle(GradleVersion gradleVersion) throws IOException {
    withSourceSets("legacy-gradle", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testGetAnnotationProcessorGeneratedLocation {0}")
  @MethodSource("allVersions")
  void testGetAnnotationProcessorGeneratedLocation(GradleVersion gradleVersion) throws IOException {
    // this test case is to ensure that the plugin won't throw no such method error
    // for JavaCompile.getAnnotationProcessorGeneratedSourcesDirectory()
    withSourceSets("legacy-gradle", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
    });
  }

  @ParameterizedTest(name = "testSourceInference {0}")
  @MethodSource("allVersions")
  void testSourceInference(GradleVersion gradleVersion) throws IOException {
    File projectDir = projectPath.resolve("infer-source-roots").toFile();
    withConnection(projectDir, gradleVersion, connect -> {
      connect.newBuild().forTasks("clean", "compileJava").run();
      GradleSourceSets gradleSourceSets = getGradleSourceSets(connect);
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      int generatedSourceDirCount = 0;
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        assertEquals(1, gradleSourceSet.getSourceDirs().size());
        generatedSourceDirCount += gradleSourceSet.getGeneratedSourceDirs().size();
        assertTrue(gradleSourceSet.getGeneratedSourceDirs().stream().anyMatch(
            dir -> dir.getAbsolutePath().replaceAll("\\\\", "/")
                .endsWith("build/generated/sources")
        ));
      }
      
      // annotation processor dirs weren't auto created before 5.2
      if (gradleVersion.compareTo(GradleVersion.version("5.2")) >= 0) {
        assertEquals(4, generatedSourceDirCount);
      } else {
        assertEquals(2, generatedSourceDirCount);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs1 {0}")
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
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--release|"), () -> "Available args: " + args);
        if (gradleVersion.compareTo(GradleVersion.version("3.0")) >= 0) {
          assertTrue(args.contains("|-source|1.8"), () -> "Available args: " + args);
        }
        assertTrue(args.contains("|-target|" + targetVersion), () -> "Available args: " + args);
        assertTrue(args.contains("|-Xlint:all"), () -> "Available args: " + args);
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
  @ParameterizedTest(name = "testJavaCompilerArgs2 {0}")
  @MethodSource("versionsFrom6_6")
  void testJavaCompilerArgs2(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-2", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-target|"), () -> "Available args: " + args);
        assertTrue(args.contains("|--release|9"), () -> "Available args: " + args);
        assertTrue(args.contains("|-Xlint:all"), () -> "Available args: " + args);
        String version9 = gradleVersion.compareTo(GradleVersion.version("8.0")) >= 0 ? "9" : "1.9";
        assertEquals(version9, javaExtension.getSourceCompatibility(),
                () -> "Available args: " + args);
        assertEquals(version9, javaExtension.getTargetCompatibility(),
            () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs3 {0}")
  @MethodSource("allVersions")
  void testJavaCompilerArgs3(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-3", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-target|"), () -> "Available args: " + args);
        assertTrue(args.contains("|--release|9"), () -> "Available args: " + args);
        assertTrue(args.contains("|-Xlint:all"), () -> "Available args: " + args);
        assertFalse(javaExtension.getSourceCompatibility().isEmpty(),
                () -> "Available args: " + args);
        assertFalse(javaExtension.getTargetCompatibility().isEmpty(),
                () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs4 {0}")
  @MethodSource("allVersions")
  void testJavaCompilerArgs4(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-4", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--release|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-target|"), () -> "Available args: " + args);
        assertTrue(args.contains("|--source|1.8"), () -> "Available args: " + args);
        assertTrue(args.contains("|--target|9"), () -> "Available args: " + args);
        assertTrue(args.contains("|-Xlint:all"), () -> "Available args: " + args);
        assertFalse(javaExtension.getSourceCompatibility().isEmpty(),
                () -> "Available args: " + args);
        assertFalse(javaExtension.getTargetCompatibility().isEmpty(),
                () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testJavaCompilerArgs5 {0}")
  @MethodSource("allVersions")
  void testJavaCompilerArgs5(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-5", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--release|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertTrue(args.contains("|-source|1.8"), () -> "Available args: " + args);
        assertTrue(args.contains("|-target|9"), () -> "Available args: " + args);
        assertTrue(args.contains("|-Xlint:all"), () -> "Available args: " + args);
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
  @ParameterizedTest(name = "testJavaCompilerArgs6 {0}")
  @MethodSource("versionsFrom2_14")
  void testJavaCompilerArgs6(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-6", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--release|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertTrue(args.contains("|-source|"), () -> "Available args: " + args);
        assertTrue(args.contains("|-target|"), () -> "Available args: " + args);
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
  @ParameterizedTest(name = "testJavaCompilerArgsToolchain {0}")
  @MethodSource("versionsFrom7_6")
  void testJavaCompilerArgsToolchain(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-compilerargs-toolchain", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--release|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertTrue(args.contains("|-source|17|"), () -> "Available args: " + args);
        assertTrue(args.contains("|-target|17|"), () -> "Available args: " + args);
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
  @ParameterizedTest(name = "testJavaSourceTarget {0}")
  @MethodSource("versionsFrom5_0")
  void testJavaSourceTarget(GradleVersion gradleVersion) throws IOException {
    withSourceSets("java-source-target", gradleVersion, gradleSourceSets -> {
      assertEquals(2, gradleSourceSets.getGradleSourceSets().size());
      for (GradleSourceSet gradleSourceSet : gradleSourceSets.getGradleSourceSets()) {
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);
        assertNotNull(javaExtension);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|-target|"), () -> "Available args: " + args);
        assertTrue(args.contains("|--release|11"), () -> "Available args: " + args);
        String version9 = gradleVersion.compareTo(GradleVersion.version("8.0")) >= 0 ? "9" : "1.9";
        assertEquals(version9, javaExtension.getSourceCompatibility(),
                () -> "Available args: " + args);
        assertEquals("1.8", javaExtension.getTargetCompatibility(),
                () -> "Available args: " + args);
      }
    });
  }

  @ParameterizedTest(name = "testScala2ModelBuilder {0}")
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
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("java")));
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("scala")));
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
                dependency -> dependency.getModule().equals("scala-library")
        ));
        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
            dependency -> dependency.getArtifacts().stream()
              .anyMatch(artifact -> artifact.getUri().toString()
                .contains("scala-library-2.13.12.jar"))
        ));
        ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(gradleSourceSet);
        assertNotNull(scalaExtension);
        assertEquals("org.scala-lang", scalaExtension.getScalaOrganization());
        assertEquals("2.13.12", scalaExtension.getScalaVersion());
        assertEquals("2.13", scalaExtension.getScalaBinaryVersion());
        List<String> args = scalaExtension.getScalaCompilerArgs();
        assertTrue(args.contains("-deprecation"), () -> "Available args: " + args);
        assertTrue(args.contains("-unchecked"), () -> "Available args: " + args);
        assertTrue(args.contains("-g:notailcalls"), () -> "Available args: " + args);
        assertTrue(args.contains("-optimise"), () -> "Available args: " + args);
        assertTrue(args.contains("-encoding"), () -> "Available args: " + args);
        assertTrue(args.contains("utf8"), () -> "Available args: " + args);
        assertTrue(args.contains("-verbose"), () -> "Available args: " + args);
        assertTrue(args.contains("-Ylog:erasure"), () -> "Available args: " + args);
        assertTrue(args.contains("-Ylog:lambdalift"), () -> "Available args: " + args);
        assertTrue(args.contains("-foo"), () -> "Available args: " + args);

        assertTrue(gradleSourceSet.getCompileClasspath().stream().anyMatch(
                file -> file.getName().equals("scala-library-2.13.12.jar")));
        assertTrue(gradleSourceSet.getRuntimeClasspath().stream().anyMatch(
                file -> file.getName().equals("scala-library-2.13.12.jar")));
        assertFalse(scalaExtension.getScalaJars().isEmpty());
        assertTrue(scalaExtension.getScalaJars().stream().anyMatch(
                file -> file.getName().equals("scala-compiler-2.13.12.jar")));
        assertFalse(scalaExtension.getScalaCompilerArgs().isEmpty());
        assertTrue(scalaExtension.getScalaCompilerArgs().stream()
                .anyMatch(arg -> arg.equals("-deprecation")));

        // dirs not split by language before 4.0
        if (gradleVersion.compareTo(GradleVersion.version("4.0")) >= 0) {
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "scala",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
          assertTrue(scalaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "scala",
              gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName()))));
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
  @ParameterizedTest(name = "testScala3ModelBuilder {0}")
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
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("java")));
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("scala")));
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
                dependency -> dependency.getModule().contains("scala3-library_3")
        ));

        ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(gradleSourceSet);
        assertNotNull(scalaExtension);
        assertEquals("org.scala-lang", scalaExtension.getScalaOrganization());
        assertEquals("3.3.1", scalaExtension.getScalaVersion());
        assertEquals("3.3", scalaExtension.getScalaBinaryVersion());
        List<String> args = scalaExtension.getScalaCompilerArgs();
        assertTrue(args.contains("-deprecation"), () -> "Available args: " + args);
        assertTrue(args.contains("-unchecked"), () -> "Available args: " + args);
        assertTrue(args.contains("-g:notailcalls"), () -> "Available args: " + args);
        assertTrue(args.contains("-optimise"), () -> "Available args: " + args);
        assertTrue(args.contains("-encoding"), () -> "Available args: " + args);
        assertTrue(args.contains("utf8"), () -> "Available args: " + args);
        assertTrue(args.contains("-verbose"), () -> "Available args: " + args);
        assertTrue(args.contains("-Ylog:erasure"), () -> "Available args: " + args);
        assertTrue(args.contains("-Ylog:lambdalift"), () -> "Available args: " + args);
        assertTrue(args.contains("-foo"), () -> "Available args: " + args);

        assertTrue(gradleSourceSet.getCompileClasspath().stream().anyMatch(
                file -> file.getName().equals("scala3-library_3-3.3.1.jar")));
        assertTrue(gradleSourceSet.getRuntimeClasspath().stream().anyMatch(
                file -> file.getName().equals("scala3-library_3-3.3.1.jar")));
        assertFalse(scalaExtension.getScalaJars().isEmpty());
        assertTrue(scalaExtension.getScalaJars().stream().anyMatch(
                file -> file.getName().equals("scala3-compiler_3-3.3.1.jar")));
        assertFalse(scalaExtension.getScalaCompilerArgs().isEmpty());
        assertTrue(scalaExtension.getScalaCompilerArgs().stream()
                .anyMatch(arg -> arg.equals("-deprecation")));

        assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
            .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "java",
            gradleSourceSet.getSourceSetName()))));
        assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
            .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "scala",
            gradleSourceSet.getSourceSetName()))));
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

  @ParameterizedTest(name = "testNebulaPlugin_11_10 {0}")
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

  @ParameterizedTest(name = "testNebulaPlugin_11_5 {0}")
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
  @ParameterizedTest(name = "testKotlinModelBuilder {0}")
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
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("java")));
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("kotlin")));
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
                dependency -> dependency.getModule().contains("kotlin-stdlib")
        ));

        KotlinExtension kotlinExtension = SupportedLanguages.KOTLIN.getExtension(gradleSourceSet);
        assertNotNull(kotlinExtension);
        assertEquals("1.2", kotlinExtension.getKotlinApiVersion());
        assertEquals("1.3", kotlinExtension.getKotlinLanguageVersion());
        assertFalse(gradleSourceSet.getCompileClasspath().isEmpty());
        assertTrue(gradleSourceSet.getCompileClasspath().stream().anyMatch(
                file -> file.getName().equals("kotlin-stdlib-1.9.21.jar")));
        assertFalse(kotlinExtension.getKotlincOptions().isEmpty());
        assertTrue(kotlinExtension.getKotlincOptions().stream()
                .anyMatch(arg -> arg.equals("-opt-in=org.mylibrary.OptInAnnotation")));
                
        // dirs not split by language before 4.0
        if (gradleVersion.compareTo(GradleVersion.version("4.0")) >= 0) {
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "kotlin",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
          assertTrue(kotlinExtension.getClassesDir().toPath().endsWith(
              Paths.get("classes", "kotlin", gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(kotlinExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
        }
      }
    });
  }

  @ParameterizedTest(name = "testGroovyModelBuilder {0}")
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
        assertTrue(gradleSourceSet.getSourceDirs().stream()
                .anyMatch(file -> file.toPath().endsWith("java")));
        assertTrue(gradleSourceSet.getSourceDirs().stream()
                .anyMatch(file -> file.toPath().endsWith("groovy")));
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
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "groovy",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
              gradleSourceSet.getSourceSetName())));
          assertTrue(groovyExtension.getClassesDir().toPath().endsWith(
              Paths.get("classes", "groovy", gradleSourceSet.getSourceSetName())));
        } else {
          assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
              .anyMatch(file -> file.toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName()))));
          assertTrue(groovyExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
          assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes",
              gradleSourceSet.getSourceSetName())));
        }
      }
    });
  }

  @ParameterizedTest(name = "testAntlrModelBuilder {0}")
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
        assertTrue(gradleSourceSet.getSourceDirs().stream()
                .anyMatch(file -> file.toPath().endsWith("java")));
        assertTrue(gradleSourceSet.getSourceDirs().stream()
                .anyMatch(file -> file.toPath().endsWith("antlr")));
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

        assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
            .anyMatch(file -> file.toPath().endsWith(Paths.get("classes", "java",
            gradleSourceSet.getSourceSetName()))));
        assertTrue(javaExtension.getClassesDir().toPath().endsWith(Paths.get("classes", "java",
            gradleSourceSet.getSourceSetName())));
      }
    });
  }

  static Stream<GradleVersion> androidVersions() {
    return versionProvider("8.7", 17);
  }

  @ParameterizedTest(name = "testAndroid {0}")
  @MethodSource("androidVersions")
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
      assertEquals(":mylibrary:assembleDebugUnitTest", mylibraryDebugUnitTest.getClassesTaskName());
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
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith(Paths.get("java"))),
            gradleSourceSet::toString);
        assertTrue(gradleSourceSet.getSourceDirs().stream()
            .anyMatch(file -> file.toPath().endsWith(Paths.get("kotlin"))),
            gradleSourceSet::toString);

        assertEquals(1, gradleSourceSet.getGeneratedSourceDirs().size(), gradleSourceSet::toString);
        assertEquals(4, gradleSourceSet.getResourceDirs().size(), gradleSourceSet::toString);
        assertEquals(1, gradleSourceSet.getSourceOutputDirs().size(), gradleSourceSet::toString);

        assertTrue(gradleSourceSet.getBuildTargetDependencies().isEmpty(),
            gradleSourceSet::toString);
        assertFalse(gradleSourceSet.getModuleDependencies().isEmpty(), gradleSourceSet::toString);
        assertTrue(gradleSourceSet.getModuleDependencies().stream().anyMatch(
            dependency -> dependency.getArtifacts().stream().anyMatch(
                artifact -> artifact.getUri().toString().endsWith("/android.jar")
            )));
        assertTrue(gradleSourceSet.getSourceOutputDirs().stream()
            .anyMatch(file -> file.toPath().endsWith("classes")));

        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(gradleSourceSet);

        assertNotNull(javaExtension, gradleSourceSet::toString);
        assertNotNull(javaExtension.getJavaHome(), gradleSourceSet::toString);
        assertNotNull(javaExtension.getJavaVersion(), gradleSourceSet::toString);
        assertNotNull(javaExtension.getSourceCompatibility(), gradleSourceSet::toString);
        assertNotNull(javaExtension.getTargetCompatibility(), gradleSourceSet::toString);
        assertEquals(2, javaExtension.getSourceDirs().size(), gradleSourceSet::toString);
        assertNotNull(javaExtension.getCompilerArgs(), gradleSourceSet::toString);
        String args = "|" + String.join("|", javaExtension.getCompilerArgs());
        assertFalse(args.contains("|--source|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--target|"), () -> "Available args: " + args);
        assertFalse(args.contains("|--release|"), () -> "Available args: " + args);
        if (gradleVersion.compareTo(GradleVersion.version("3.0")) >= 0) {
          assertTrue(args.contains("|-source|1.8"), () -> "Available args: " + args);
        }
        assertTrue(args.contains("|-target|1.8"), () -> "Available args: " + args);
        assertTrue(args.contains("|-bootclasspath"), () -> "Available args: " + args);
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

  private GradleSourceSet findSourceSet(List<GradleSourceSet> sourceSets,
      String projectName, String sourceSetName) {
    for (GradleSourceSet sourceSet : sourceSets) {
      if (sourceSet.getProjectName().equals(projectName)
          && sourceSet.getSourceSetName().equals(sourceSetName)) {
        return sourceSet;
      }
    }
    throw new IllegalStateException("Source Set " + projectName + " " + sourceSetName
        + " not found in " + sourceSets);
  }
}