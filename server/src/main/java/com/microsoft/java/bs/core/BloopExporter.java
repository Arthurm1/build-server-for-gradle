// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import com.microsoft.java.bs.core.internal.gradle.Utils;
import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.actions.GetSourceSetsAction;
import com.microsoft.java.bs.gradle.model.impl.DefaultBuildTargetDependency;

/**
 * class to export Gradle config to Bloop files.
 * See <a href="https://github.com/scalacenter/bloop">Bloop</a>
 * Usage: pass dir of project to export or pass nothing and current dir will be used
 */
public class BloopExporter {

  /**
   * Main entry point.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    Path projectPath;
    if (args.length > 0) {
      projectPath = Path.of(args[0]);
    } else {
      // use current dir
      projectPath = Paths.get(System.getProperty("user.dir"));
    }
    new BloopExporter().run(projectPath.toAbsolutePath());
  }

  private void run(Path projectPath) {
    File projectDir = projectPath.toFile();

    Path bloopPath = projectPath.resolve(".bloop");
    File bloopDir = bloopPath.toFile();
    bloopDir.mkdirs();

    List<GradleSourceSet> sourceSets = getSourceSets(projectDir);

    // Create unique display names that are also valid to use as Bloop filenames
    Map<BuildTargetDependency, String> displayNames = getDisplayNames(sourceSets);

    // create a map to change Gradle classes path -> Bloop path
    Map<BuildTargetDependency, List<File>> newClassesDirMap = getNewClassesDirMap(sourceSets,
        displayNames, bloopPath);
    // create a map to change Gradle output paths -> Bloop output paths
    Map<File, List<File>> oldOutputDirToNewOutputDir = getOldOutputDirToNewOutputDir(sourceSets,
        newClassesDirMap);

    // output JSON
    writeJson(sourceSets, bloopPath, displayNames, projectDir, oldOutputDirToNewOutputDir);
  }

  private void writeJson(List<GradleSourceSet> sourceSets, Path bloopPath,
      Map<BuildTargetDependency, String> displayNames, File projectDir,
      Map<File, List<File>> oldOutputDirToNewOutputDir) {
    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    for (GradleSourceSet sourceSet : sourceSets) {
      BuildTargetDependency dep = new DefaultBuildTargetDependency(sourceSet);
      String displayName = displayNames.get(dep);
      Path targetPath = bloopPath.resolve(displayName + ".json");
      BloopConfig config = convertToBloop(displayName, sourceSet, projectDir, bloopPath,
          oldOutputDirToNewOutputDir, displayNames);
      String json = gson.toJson(config);
      try {
        Files.writeString(targetPath, json);
      } catch (IOException e) {
        throw new IllegalStateException("Error writing to file " + targetPath, e);
      }
    }
  }

  private record BloopConfig(String version,
                             BloopProject project) {
  }

  private record BloopProject(String name,
                              String directory,
                              String workspaceDir,
                              Collection<String> sources,
                              Collection<String> dependencies,
                              Collection<String> classpath,
                              String out,
                              String classesDir,
                              Collection<String> resources,
                              BloopScala scala,
                              BloopJava java,
                              BloopTest test,
                              BloopPlatform platform,
                              BloopResolution resolution,
                              Collection<String> tags) {
  }

  private record BloopScalaSetup(String order,
                                 boolean addLibraryToBootClasspath,
                                 boolean addCompilerToClasspath,
                                 boolean addExtraJarsToClasspath,
                                 boolean manageBootClasspath,
                                 boolean filterLibraryFromClasspath) {
  }

  private record BloopScala(String organization,
                            String name,
                            String version,
                            Collection<String> options,
                            Collection<String> jars,
                            BloopScalaSetup setup) {
  }

  private record BloopJava(Collection<String> options) {
  }

  private record BloopTestFramework(Collection<String> names) {
  }

  private record BloopTestOptionsArguments(Collection<String> args,
                                           BloopTestFramework framework) {
  }

  private record BloopTestOptions(Collection<String> excludes,
                                  Collection<BloopTestOptionsArguments> arguments) {
  }

  private record BloopTest(List<BloopTestFramework> frameworks,
                           BloopTestOptions options) {
  }

  private record BloopPlatformConfig(String home,
                                     Collection<String> options) {
  }

  private record BloopPlatform(String name,
                               BloopPlatformConfig config,
                               Collection<String> mainClass,
                               Collection<String> classpath) {
  }

  private record BloopResolutionModuleArtifact(String name,
                                               String classifier,
                                               String path) {
  }

  private record BloopResolutionModule(String organization,
                                       String name,
                                       String version,
                                       Collection<BloopResolutionModuleArtifact> artifacts) {
  }

  private record BloopResolution(Collection<BloopResolutionModule> modules) {
  }

  private BloopTest getFixedTestConfig() {
    BloopTestFramework junit = new BloopTestFramework(List.of(
        "com.novocode.junit.JUnitFramework"));
    BloopTestFramework scalatest = new BloopTestFramework(List.of(
        "org.scalatest.tools.Framework",
        "org.scalatest.tools.ScalaTestFramework"));
    BloopTestFramework scalacheck = new BloopTestFramework(List.of(
        "org.scalacheck.ScalaCheckFramework"));
    BloopTestFramework specs2 = new BloopTestFramework(List.of(
        "org.specs.runner.SpecsFramework",
        "org.specs2.runner.Specs2Framework",
        "org.specs2.runner.SpecsFramework"));
    BloopTestFramework utest = new BloopTestFramework(List.of(
        "utest.runner.Framework"));
    BloopTestFramework munit = new BloopTestFramework(List.of(
        "munit.Framework"));

    List<BloopTestFramework> frameworks = List.of(junit, scalatest, scalacheck, specs2, utest,
        munit);
    BloopTestOptionsArguments junitArguments = new BloopTestOptionsArguments(
        List.of("-v", "-a"), junit);
    BloopTestOptions options = new BloopTestOptions(Collections.emptyList(), List.of(
        junitArguments));
    return new BloopTest(frameworks, options);
  }

  private BloopConfig convertToBloop(String displayName, GradleSourceSet sourceSet,
      File projectDir, Path bloopPath, Map<File, List<File>> oldClassesDirToNewClassesDir,
      Map<BuildTargetDependency, String> displayNames) {

    List<String> sources = Stream
        .of(sourceSet.getSourceDirs().stream().sorted(),
            sourceSet.getGeneratedSourceDirs().stream().sorted())
        .flatMap(ss -> ss.map(File::toString))
        .distinct()
        .collect(Collectors.toList());
    List<String> dependencies = sourceSet.getBuildTargetDependencies().stream()
        .map(displayNames::get)
        .sorted()
        .distinct()
        .collect(Collectors.toList());
    List<String> compileClasspath = substituteClasspath(sourceSet.getCompileClasspath(),
        oldClassesDirToNewClassesDir);
    Path projectPath = bloopPath.resolve(displayName);
    String outDir = toString(projectPath.resolve("build").toFile());
    String classesDir = toString(projectPath.resolve("build").resolve("classes").toFile());
    Set<String> resources = sourceSet.getResourceDirs().stream()
        .map(File::toString)
        .collect(Collectors.toSet());
    BloopScala scala;
    ScalaExtension scalaExt = SupportedLanguages.SCALA.getExtension(sourceSet);
    if (scalaExt != null) {
      BloopScalaSetup setup = new BloopScalaSetup("mixed", true, false, false, true, true);
      List<String> jars = scalaExt.getScalaJars().stream()
          .map(File::toString)
          .collect(Collectors.toList());
      scala = new BloopScala(scalaExt.getScalaOrganization(), "scala-compiler",
          scalaExt.getScalaVersion(), scalaExt.getScalaCompilerArgs(), jars, setup);
    } else {
      scala = null;
    }
    BloopJava java;
    BloopPlatform platform;
    JavaExtension javaExt = SupportedLanguages.JAVA.getExtension(sourceSet);
    if (javaExt != null) {
      java = new BloopJava(javaExt.getCompilerArgs());
      BloopPlatformConfig config = new BloopPlatformConfig(toString(javaExt.getJavaHome()),
          Collections.emptyList());
      List<String> runtimeClasspath = substituteClasspath(sourceSet.getRuntimeClasspath(),
          oldClassesDirToNewClassesDir);
      platform = new BloopPlatform("jvm", config, Collections.emptyList(), runtimeClasspath);
    } else {
      java = null;
      platform = null;
    }
    BloopTest test = sourceSet.hasTests() ? getFixedTestConfig() : null;
    List<BloopResolutionModule> modules = sourceSet.getModuleDependencies().stream()
        .map(dep -> new BloopResolutionModule(dep.getGroup(), dep.getModule(), dep.getVersion(),
            dep.getArtifacts().stream()
                .map(artifact -> new BloopResolutionModuleArtifact(dep.getModule(),
                    artifact.getClassifier(), toString(new File(artifact.getUri()))))
                .collect(Collectors.toList())))
        .collect(Collectors.toList());
    BloopResolution resolution = new BloopResolution(modules);
    List<String> tags = new ArrayList<>();
    if (sourceSet.hasTests()) {
      tags.add("test");
    } else {
      tags.add("library");
    }
    BloopProject bloopProject = new BloopProject(displayName, toString(sourceSet.getProjectDir()),
        toString(projectDir), sources, dependencies, compileClasspath, outDir, classesDir,
        resources, scala, java, test, platform, resolution, tags);
    return new BloopConfig("1.4.0", bloopProject);
  }

  private List<String> substituteClasspath(List<File> classpath, Map<File,
      List<File>> oldClassesDirToNewClassesDir) {
    return classpath.stream().flatMap(file ->
      oldClassesDirToNewClassesDir.getOrDefault(file, List.of(file)).stream())
        .distinct()
        .map(File::toString)
        .collect(Collectors.toList());
  }

  private String toString(File file) {
    if (file == null) {
      return null;
    }
    return file.toString();
  }

  private Map<File, List<File>> getOldOutputDirToNewOutputDir(
      List<GradleSourceSet> sourceSets, Map<BuildTargetDependency, List<File>> newClassesDirMap) {
    Map<File, List<File>> oldOutputDirToNewOutputDir = new HashMap<>();
    for (GradleSourceSet sourceSet : sourceSets) {
      BuildTargetDependency dep = new DefaultBuildTargetDependency(sourceSet);
      List<File> newClassesDirs = newClassesDirMap.get(dep);
      // bloop has no concept of compiling to an output resources dir
      // so it uses the source resources dir for in and out.
      Set<File> resourcesDirs = sourceSet.getResourceDirs();
      List<File> newOutputDirs = new ArrayList<>();
      newOutputDirs.addAll(resourcesDirs);
      newOutputDirs.addAll(newClassesDirs);
      for (File outputDir : sourceSet.getSourceOutputDirs()) {
        oldOutputDirToNewOutputDir.put(outputDir, newOutputDirs);
      }
      for (File outputDir : sourceSet.getResourceOutputDirs()) {
        oldOutputDirToNewOutputDir.put(outputDir, newOutputDirs);
      }
      for (File outputDir : resourcesDirs) {
        oldOutputDirToNewOutputDir.put(outputDir, newOutputDirs);
      }
    }
    return oldOutputDirToNewOutputDir;
  }

  private Map<BuildTargetDependency, List<File>> getNewClassesDirMap(
      List<GradleSourceSet> sourceSets, Map<BuildTargetDependency, String> displayNames,
      Path bloopPath) {
    Map<BuildTargetDependency, List<File>> newClassesDirMap = new HashMap<>();
    for (GradleSourceSet sourceSet : sourceSets) {
      BuildTargetDependency dep = new DefaultBuildTargetDependency(sourceSet);
      String displayName = displayNames.get(dep);
      Path buildPath = bloopPath.resolve(displayName).resolve("build");
      // bloop's classes output dir
      File newClassesDir = buildPath.resolve("classes").toFile();
      List<File> newClassesDirs = new ArrayList<>();
      newClassesDirs.add(newClassesDir);
      newClassesDirMap.put(dep, newClassesDirs);
    }
    return newClassesDirMap;
  }

  private String stripPathPrefix(String projectPath) {
    if (projectPath != null && projectPath.startsWith(":")) {
      return projectPath.substring(1);
    }
    return projectPath;
  }

  // display name is used as the filename so make sure they're valid filenames
  private Map<BuildTargetDependency, String> getDisplayNames(List<GradleSourceSet> sourceSets) {
    Map<BuildTargetDependency, String> displayNames = new HashMap<>();
    for (GradleSourceSet sourceSet : sourceSets) {
      String projectName = stripPathPrefix(sourceSet.getProjectPath());
      if (projectName == null || projectName.isEmpty()) {
        projectName = sourceSet.getProjectName();
      }
      String sourceSetName = sourceSet.getSourceSetName();
      String displayName = projectName + "-" + sourceSetName;
      displayName = displayName.replace(":", " ");
      displayNames.put(new DefaultBuildTargetDependency(sourceSet), displayName);
    }
    return displayNames;
  }

  private List<GradleSourceSet> getSourceSets(File projectDir) {
    GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir);
    try (ProjectConnection connection = connector.connect()) {
      GetSourceSetsAction getSourceSetsAction = new GetSourceSetsAction();
      BuildActionExecuter<GradleSourceSets> buildExecutor =
          connection.action(getSourceSetsAction);
      String initScriptContents = Utils.createPluginScript(null, null, null);
      File initScript = Utils.createInitScriptFile("bloopExport", initScriptContents);
      try {
        return buildExecutor
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .addArguments("--init-script", initScript.getAbsolutePath())
                .addJvmArguments("-Dbsp.gradle.supportedLanguages=java,scala")
                .run()
                .getGradleSourceSets();
      } finally {
        if (initScript != null) {
          initScript.delete();
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error extracting config", e);
    }
  }
}
