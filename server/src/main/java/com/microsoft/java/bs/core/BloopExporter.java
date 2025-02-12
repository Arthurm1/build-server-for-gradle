// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import com.microsoft.java.bs.core.internal.gradle.Utils;
import com.microsoft.java.bs.gradle.model.Artifact;
import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
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
    for (GradleSourceSet sourceSet : sourceSets) {
      BuildTargetDependency dep = new DefaultBuildTargetDependency(sourceSet);
      String displayName = displayNames.get(dep);
      Path targetPath = bloopPath.resolve(displayName + ".json");
      File targetFile = targetPath.toFile();
      try (PrintWriter writer = new PrintWriter(targetFile)) {
        writeJson(writer, displayName, sourceSet, projectDir, bloopPath,
            oldOutputDirToNewOutputDir, displayNames);
      } catch (IOException e) {
        throw new IllegalStateException("Error writing to file " + targetPath, e);
      }
    }
  }
  
  private void writeJson(PrintWriter writer, String displayName, GradleSourceSet sourceSet,
      File projectDir, Path bloopPath, Map<File, List<File>> oldClassesDirToNewClassesDir,
      Map<BuildTargetDependency, String> displayNames) {
    writer.println("{");
    writer.println("  \"version\": \"1.4.0\",");
    writer.println("  \"project\": {");
    writer.print("    \"name\": ");
    writer.print(quote(displayName));
    writer.println(",");
    writer.print("    \"directory\": ");
    writer.print(quote(sourceSet.getProjectDir()));
    writer.println(",");
    writer.print("    \"workspaceDir\": ");
    writer.print(quote(projectDir));
    writer.println(",");
    writer.println("    \"sources\": [");
    write(writer, sourceSet.getSourceDirs(), "       ",
        sourceDir -> writer.print(quote(sourceDir)));
    writer.println("    ],");

    writer.println("    \"dependencies\": [");
    write(writer, sourceSet.getBuildTargetDependencies(), "       ",
        dependency -> writer.print(quote(displayNames.get(dependency))));
    writer.println("    ],");

    writer.println("    \"classpath\": [");
    boolean isFirst = true;
    Set<File> usedPaths = new HashSet<>();
    for (File compileClasspath : sourceSet.getCompileClasspath()) {
      List<File> mappedClasspaths = oldClassesDirToNewClassesDir.get(compileClasspath);
      if (mappedClasspaths == null) {
        mappedClasspaths = new ArrayList<>();
        mappedClasspaths.add(compileClasspath);
      }
      for (File classpath : mappedClasspaths) {
        if (!usedPaths.contains(classpath)) {
          if (!isFirst) {
            writer.println(",");
          }
          writer.print("       ");
          writer.print(quote(classpath));
          isFirst = false;
          usedPaths.add(classpath);
        }
      }
    }
    if (!isFirst) {
      writer.println();
    }
    writer.println("    ],");
    File outDir = bloopPath.resolve(displayName).resolve("build").toFile();
    writer.print("    \"out\": ");
    writer.print(quote(outDir));
    writer.println(",");
    File classesDir = bloopPath.resolve(displayName).resolve("build").resolve("classes").toFile();
    writer.print("    \"classesDir\": ");
    writer.print(quote(classesDir));
    writer.println(",");

    writer.println("    \"resources\": [");
    write(writer, sourceSet.getResourceDirs(), "       ",
        resourceDir -> writer.print(quote(resourceDir)));
    writer.println("    ],");
    
    ScalaExtension scalaExt = SupportedLanguages.SCALA.getExtension(sourceSet);
    if (scalaExt != null) {
      writer.println("    \"scala\": {");
      writer.print("    \"organization\": ");
      writer.print(quote(scalaExt.getScalaOrganization()));
      writer.println(",");
      writer.println("    \"name\": \"scala-compiler\",");
      writer.print("    \"version\": ");
      writer.print(quote(scalaExt.getScalaVersion()));
      writer.println(",");
      writer.println("      \"options\": [");

      write(writer, scalaExt.getScalaCompilerArgs(), "         ",
          scalaOpt -> writer.print(quote(scalaOpt)));
      writer.println("      ],");
      writer.println("      \"jars\": [");
      write(writer, scalaExt.getScalaJars(), "         ",
          scalaJar -> writer.print(quote(scalaJar)));
      writer.println("      ],");
      writer.println("      \"setup\": {");
      writer.println("        \"order\": \"mixed\",");
      writer.println("        \"addLibraryToBootClasspath\": true,");
      writer.println("        \"addCompilerToClasspath\": false,");
      writer.println("        \"addExtraJarsToClasspath\": false,");
      writer.println("        \"manageBootClasspath\": true,");
      writer.println("        \"filterLibraryFromClasspath\": true");
      writer.println("      }");
      writer.println("    },");
    }

    JavaExtension javaExt = SupportedLanguages.JAVA.getExtension(sourceSet);
    if (javaExt != null) {
      writer.println("    \"java\": {");
      writer.println("      \"options\": [");
      write(writer, javaExt.getCompilerArgs(), "         ",
          javaOpt -> writer.print(quote(javaOpt)));
      writer.println("      ]");
      writer.println("    },");
    }

    if (sourceSet.hasTests()) {
      writer.println("""
          "test": {
            "frameworks": [
              {
                "names": [
                      "com.novocode.junit.JUnitFramework"
                ]
              },
              {
                "names": [
                      "org.scalatest.tools.Framework",
                      "org.scalatest.tools.ScalaTestFramework"
                ]
              },
              {
                "names": [
                      "org.scalacheck.ScalaCheckFramework"
                ]
              },
              {
                "names": [
                      "org.specs.runner.SpecsFramework",
                      "org.specs2.runner.Specs2Framework",
                      "org.specs2.runner.SpecsFramework"
                ]
              },
              {
                "names": [
                      "utest.runner.Framework"
                ]
              },
              {
                "names": [
                      "munit.Framework"
                ]
              }
            ],
            "options": {
              "excludes": [],
              "arguments": [
                {
                  "args": [
                        "-v",
                        "-a"
                  ],
                  "framework": {
                    "names": [
                            "com.novocode.junit.JUnitFramework"
                    ]
                  }
                }
              ]
            }
          },""");
    }

    if (javaExt != null) {
      writer.println("    \"platform\": {");
      writer.println("      \"name\": \"jvm\",");
      writer.println("      \"config\": {");
      writer.print("        \"home\": ");
      writer.print(quote(javaExt.getJavaHome()));
      writer.println(",");
      writer.println("        \"options\": []");
      writer.println("      },");
      writer.println("      \"mainClass\": [],");
      writer.println("      \"classpath\": [");
      usedPaths = new HashSet<>();
      isFirst = true;
      for (File compileClasspath : sourceSet.getRuntimeClasspath()) {
        List<File> mappedClasspaths = oldClassesDirToNewClassesDir.get(compileClasspath);
        if (mappedClasspaths == null) {
          mappedClasspaths = new ArrayList<>();
          mappedClasspaths.add(compileClasspath);
        }
        for (File classpath : mappedClasspaths) {
          if (!usedPaths.contains(classpath)) {
            if (!isFirst) {
              writer.println(",");
            }
            writer.print("         ");
            writer.print(quote(classpath));
            isFirst = false;
            usedPaths.add(classpath);
          }
        }
      }
      if (!isFirst) {
        writer.println();
      }
      writer.println("      ]");
      writer.println("    },");
    }

    writer.println("    \"resolution\": {");
    writer.println("      \"modules\": [");
    isFirst = true;
    for (GradleModuleDependency moduleDep : sourceSet.getModuleDependencies()) {
      if (!isFirst) {
        writer.println(",");
      }
      writer.println("         {");
      writer.print("           \"organization\": \"");
      writer.print(moduleDep.getGroup());
      writer.println("\",");
      writer.print("           \"name\": \"");
      writer.print(moduleDep.getModule());
      writer.println("\",");
      writer.print("           \"version\": \"");
      writer.print(moduleDep.getVersion());
      writer.println("\",");
      writer.println("           \"artifacts\": [");

      boolean isFirstArtifact = true;
      for (Artifact artifact : moduleDep.getArtifacts()) {
        if (artifact.getClassifier() == null || artifact.getClassifier().equals("sources")) {
          if (!isFirstArtifact) {
            writer.println(",");
          }
          writer.println("             {");
          writer.print("               \"name\": \"");
          writer.print(moduleDep.getModule());
          writer.println("\",");
          if (artifact.getClassifier() != null) {
            writer.print("               \"classifier\": \"");
            writer.print(artifact.getClassifier());
            writer.println("\",");
          }
          writer.print("               \"path\": ");
          writer.print(quote(new File(artifact.getUri())));
          writer.println();
          writer.print("             }");
          isFirstArtifact = false;
        }
      }
      if (!isFirstArtifact) {
        writer.println();
      }

      writer.println("           ]");
      writer.print("         }");
      isFirst = false;
    }
    if (!isFirst) {
      writer.println();
    }
    writer.println("      ]");
    writer.println("    },");

    writer.println("    \"tags\": [");
    if (sourceSet.hasTests()) {
      writer.println("      \"test\"");
    } else {
      writer.println("      \"library\"");
    }
    writer.println("    ]");
    writer.println("  }");
    writer.println("}");
  }

  private <T> void write(PrintWriter writer, Iterable<T> items, String indent, Consumer<T> write) {
    boolean isFirst = true;
    for (T item : items) {
      if (!isFirst) {
        writer.println(",");
      }
      writer.print(indent);
      write.accept(item);
      isFirst = false;
    }
    if (!isFirst) {
      writer.println();
    }
  }

  private String quote(File file) {
    return quote(file.toString());
  }

  private String quote(String str) {
    return "\"" + str.replace("\\", "\\\\") + "\"";
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
    try (ByteArrayOutputStream errorOut = new ByteArrayOutputStream()) {
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
                  .setStandardError(errorOut)
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
        throw new IllegalStateException("Error extracting config " + errorOut, e);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error extracting config", e);
    }
  }
}
