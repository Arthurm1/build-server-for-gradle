// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.java.bs.gradle.plugin.utils.AndroidUtils;
import com.microsoft.java.bs.gradle.plugin.utils.SourceSetUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.GradleVersion;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleRunTask;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.GradleTestTask;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleRunTask;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSet;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSets;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleTestTask;
import com.microsoft.java.bs.gradle.plugin.dependency.DependencyCollector;
import com.microsoft.java.bs.gradle.plugin.utils.Utils;

/**
 * The model builder for Gradle source sets.
 */
public class SourceSetsModelBuilder implements ToolingModelBuilder {
  @Override
  public boolean canBuild(String modelName) {
    return modelName.equals(GradleSourceSets.class.getName());
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public Object buildAll(String modelName, Project project) {
    // mapping Gradle source set to our customized model.
    List<GradleSourceSet> sourceSets = new ArrayList<>();

    // Fetch source sets depending on the project type
    sourceSets.addAll(AndroidUtils.getBuildVariantsAsGradleSourceSets(project));
    sourceSets.addAll(getSourceSetContainer(project).stream()
        .map(ss -> getSourceSet(project, ss))
        .collect(Collectors.toList()));

    excludeSourceDirsFromModules(sourceSets);

    return new DefaultGradleSourceSets(sourceSets);
  }

  private DefaultGradleSourceSet getSourceSet(Project project, SourceSet sourceSet) {
    DefaultGradleSourceSet gradleSourceSet = new DefaultGradleSourceSet();
    // dependencies are populated by the GradleSourceSetsAction.  Make sure not null.
    gradleSourceSet.setBuildTargetDependencies(new HashSet<>());
    gradleSourceSet.setGradleVersion(project.getGradle().getGradleVersion());
    gradleSourceSet.setProjectName(project.getName());
    String projectPath = project.getPath();
    gradleSourceSet.setProjectPath(projectPath);
    gradleSourceSet.setProjectDir(project.getProjectDir());
    gradleSourceSet.setRootDir(project.getRootDir());
    gradleSourceSet.setSourceSetName(sourceSet.getName());
    String classesTaskName =
        SourceSetUtils.getFullTaskName(projectPath, sourceSet.getClassesTaskName());
    gradleSourceSet.setClassesTaskName(classesTaskName);
    String cleanTaskName = SourceSetUtils.getFullTaskName(projectPath, "clean");
    gradleSourceSet.setCleanTaskName(cleanTaskName);
    Set<String> taskNames = new HashSet<>();
    gradleSourceSet.setTaskNames(taskNames);

    // setup module dependencies before language support check.
    gradleSourceSet.setModuleDependencies(getModuleDependencies(project, sourceSet));

    // specific languages
    Map<String, LanguageExtension> extensions = new HashMap<>();
    Set<File> srcDirs = new HashSet<>();
    Set<File> generatedSrcDirs = new HashSet<>();
    Set<File> sourceOutputDirs = new HashSet<>();
    for (LanguageModelBuilder languageModelBuilder
        : SourceSetUtils.getSupportedLanguageModelBuilders()) {
      LanguageExtension extension = languageModelBuilder.getExtensionFor(project, sourceSet,
          gradleSourceSet.getModuleDependencies());
      if (extension != null) {
        String compileTaskName =
            SourceSetUtils.getFullTaskName(projectPath, extension.getCompileTaskName());
        taskNames.add(compileTaskName);

        srcDirs.addAll(extension.getSourceDirs());
        generatedSrcDirs.addAll(extension.getGeneratedSourceDirs());
        sourceOutputDirs.add(extension.getClassesDir());

        extensions.put(languageModelBuilder.getLanguageId(), extension);
      }
    }
    gradleSourceSet.setSourceDirs(srcDirs);
    gradleSourceSet.setGeneratedSourceDirs(generatedSrcDirs);
    gradleSourceSet.setExtensions(extensions);
    gradleSourceSet.setSourceOutputDirs(sourceOutputDirs);

    // classpaths
    List<File> compileClasspath = new LinkedList<>();
    try {
      compileClasspath.addAll(sourceSet.getCompileClasspath().getFiles());
    } catch (GradleException e) {
      // ignore
    }
    gradleSourceSet.setCompileClasspath(compileClasspath);
    List<File> runtimeClasspath = new LinkedList<>();
    try {
      runtimeClasspath.addAll(sourceSet.getRuntimeClasspath().getFiles());
    } catch (GradleException e) {
      // ignore
    }
    gradleSourceSet.setRuntimeClasspath(runtimeClasspath);

    // resource
    Set<File> resourceDirs = sourceSet.getResources().getSrcDirs();
    gradleSourceSet.setResourceDirs(resourceDirs);

    // resource output dir
    File resourceOutputDir = sourceSet.getOutput().getResourcesDir();
    Set<File> resourceOutputDirs = new HashSet<>();
    if (resourceOutputDir != null) {
      resourceOutputDirs.add(resourceOutputDir);
    }
    gradleSourceSet.setResourceOutputDirs(resourceOutputDirs);

    // archive output dirs
    Map<File, List<File>> archiveOutputFiles = getArchiveOutputFiles(project, sourceSet);
    gradleSourceSet.setArchiveOutputFiles(archiveOutputFiles);

    // tests
    gradleSourceSet.setTestTasks(getTestTasks(project, sourceOutputDirs));

    // run tasks
    gradleSourceSet.setRunTasks(getRunTasks(project, runtimeClasspath));

    return gradleSourceSet;
  }

  /**
   * find test tasks associated with the source set.
   *
   * @param project Gradle project
   * @param sourceOutputDirs output dirs of the source set
   * @return a set of GradleTestTask with info on test setup
   */
  public static Set<GradleTestTask> getTestTasks(Project project, Set<File> sourceOutputDirs) {
    Set<GradleTestTask> testTasks = new HashSet<>();
    if (!sourceOutputDirs.isEmpty()) {
      Set<Test> tasks = Utils.tasksWithType(project, Test.class);
      for (Test task : tasks) {
        boolean isForThisSourceSet = false;
        if (GradleVersion.current().compareTo(GradleVersion.version("4.0")) >= 0) {
          FileCollection files = task.getTestClassesDirs();
          for (File sourceOutputDir : sourceOutputDirs) {
            if (files.contains(sourceOutputDir)) {
              isForThisSourceSet = true;
              break;
            }
          }
        } else {
          Object testClassesDir = Utils.invokeMethodIgnoreFail(task, "getTestClassesDir");
          if (testClassesDir != null) {
            for (File sourceOutputDir : sourceOutputDirs) {
              if (sourceOutputDir.equals(testClassesDir)) {
                isForThisSourceSet = true;
                break;
              }
            }
          }
        }
        if (isForThisSourceSet) {
          String taskPath = task.getPath();
          List<File> classpath = new LinkedList<>();
          try {
            classpath.addAll(task.getClasspath().getFiles());
          } catch (GradleException e) {
            // ignore
          }
          List<String> jvmOptions = task.getAllJvmArgs();
          File workingDirectory = task.getWorkingDir();
          Map<String, String> environmentVariables = task.getEnvironment().entrySet()
              .stream()
              .collect(Collectors.toMap(Map.Entry::getKey, Object::toString));
          GradleTestTask testTask = new DefaultGradleTestTask(taskPath, classpath,
              jvmOptions, workingDirectory, environmentVariables);
          testTasks.add(testTask);
        }
      }
    }
    return testTasks;
  }

  /**
   * find run tasks associated with the source set.
   *
   * @param project Gradle project
   * @param runtimeClasspath runtime classpath of the source set
   * @return a set of GradleRunTask with info on run setup
   */
  public static Set<GradleRunTask> getRunTasks(Project project, List<File> runtimeClasspath) {
    Set<GradleRunTask> runTasks = new HashSet<>();
    if (!runtimeClasspath.isEmpty()) {
      Set<JavaExec> tasks = Utils.tasksWithType(project, JavaExec.class);
      for (JavaExec task : tasks) {
        
        List<File> classpath = new LinkedList<>();
        try {
          classpath.addAll(task.getClasspath().getFiles());
        } catch (GradleException e) {
          // ignore
        }
        boolean isForThisSourceSet = classpath.equals(runtimeClasspath);
        if (isForThisSourceSet) {
          String taskPath = task.getPath();
          List<String> jvmOptions = task.getAllJvmArgs();
          File workingDirectory = task.getWorkingDir();
          Map<String, String> environmentVariables = task.getEnvironment().entrySet()
              .stream()
              .collect(Collectors.toMap(Map.Entry::getKey, Object::toString));
          String mainClass = task.getMainClass().getOrNull();
          List<String> arguments = task.getArgs();
          GradleRunTask runTask = new DefaultGradleRunTask(taskPath, classpath,
              jvmOptions, workingDirectory, environmentVariables, mainClass,
              arguments);
          runTasks.add(runTask);
        }
      }
    }
    return runTasks;
  }

  /**
   * get all archive tasks for this project and maintain the archive file
   * to source set mapping.
   */
  @SuppressWarnings("deprecation")
  private Map<File, List<File>> getArchiveOutputFiles(Project project, SourceSet sourceSet) {
    // get all archive tasks for this project and find the dirs that are included in the archive
    Set<AbstractArchiveTask> archiveTasks = Utils.tasksWithType(project, AbstractArchiveTask.class);
    Map<File, List<File>> archiveOutputFiles = new HashMap<>();
    for (AbstractArchiveTask archiveTask : archiveTasks) {
      Set<Object> archiveSourcePaths = getArchiveSourcePaths(archiveTask.getRootSpec());
      for (Object sourcePath : archiveSourcePaths) {
        if (sourceSet.getOutput().equals(sourcePath)) {
          File archiveFile;
          if (GradleVersion.current().compareTo(GradleVersion.version("5.1")) >= 0) {
            archiveFile = archiveTask.getArchiveFile().get().getAsFile();
          } else {
            archiveFile = archiveTask.getArchivePath();
          }
          List<File> sourceSetOutputs = new LinkedList<>(sourceSet.getOutput().getFiles());
          archiveOutputFiles.put(archiveFile, sourceSetOutputs);
        }
      }
    }
    return archiveOutputFiles;
  }

  private Set<GradleModuleDependency> getModuleDependencies(Project project, SourceSet sourceSet) {
    Set<String> configurationNames = getClasspathConfigurationNames(sourceSet);
    return DependencyCollector.getModuleDependencies(project, configurationNames);
  }

  // remove source set dirs from modules
  private void excludeSourceDirsFromModules(List<GradleSourceSet> sourceSets) {
    Set<File> exclusions = new HashSet<>();
    for (GradleSourceSet sourceSet : sourceSets) {
      if (sourceSet.getSourceDirs() != null) {
        exclusions.addAll(sourceSet.getSourceDirs());
      }
      if (sourceSet.getGeneratedSourceDirs() != null) {
        exclusions.addAll(sourceSet.getGeneratedSourceDirs());
      }
      if (sourceSet.getResourceDirs() != null) {
        exclusions.addAll(sourceSet.getResourceDirs());
      }
      if (sourceSet.getSourceOutputDirs() != null) {
        exclusions.addAll(sourceSet.getSourceOutputDirs());
      }
      if (sourceSet.getResourceOutputDirs() != null) {
        exclusions.addAll(sourceSet.getResourceOutputDirs());
      }
      if (sourceSet.getArchiveOutputFiles() != null) {
        exclusions.addAll(sourceSet.getArchiveOutputFiles().keySet());
      }
    }

    Set<URI> exclusionUris = exclusions.stream().map(File::toURI).collect(Collectors.toSet());

    for (GradleSourceSet sourceSet : sourceSets) {
      Set<GradleModuleDependency> filteredModuleDependencies = sourceSet.getModuleDependencies()
          .stream().filter(mod -> mod.getArtifacts()
              .stream().anyMatch(art -> !exclusionUris.contains(art.getUri())))
          .collect(Collectors.toSet());
      if (sourceSet instanceof DefaultGradleSourceSet) {
        ((DefaultGradleSourceSet) sourceSet).setModuleDependencies(filteredModuleDependencies);
      }
    }
  }

  private Collection<SourceSet> getSourceSetContainer(Project project) {
    if (GradleVersion.current().compareTo(GradleVersion.version("5.0")) >= 0) {
      SourceSetContainer sourceSetContainer = project.getExtensions()
          .findByType(SourceSetContainer.class);
      if (sourceSetContainer != null) {
        return sourceSetContainer;
      }
    }
    try {
      // query the java plugin.  This limits support to Java only if other
      // languages add their own sourcesets
      // use reflection because `getConvention` will be removed in Gradle 9.0
      Object convention = Utils.invokeMethod(project, "getConvention");
      Object plugins = Utils.invokeMethod(convention, "getPlugins");
      Method getGet = plugins.getClass().getMethod("get", Object.class);
      Object pluginConvention = getGet.invoke(plugins, "java");
      if (pluginConvention != null) {
        return Utils.invokeMethod(pluginConvention, "getSourceSets");
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
             | IllegalArgumentException | InvocationTargetException e) {
      throw new IllegalStateException("Error getting source sets", e);
    }
    return new LinkedList<>();
  }

  private Set<Object> getArchiveSourcePaths(CopySpec copySpec) {
    Set<Object> sourcePaths = new HashSet<>();
    if (copySpec instanceof DefaultCopySpec) {
      DefaultCopySpec defaultCopySpec = (DefaultCopySpec) copySpec;
      sourcePaths.addAll(defaultCopySpec.getSourcePaths());
      // DefaultCopySpec#getChildren changed from Iterable to Collection
      if (GradleVersion.current().compareTo(GradleVersion.version("6.2")) >= 0) {
        for (CopySpec child : defaultCopySpec.getChildren()) {
          sourcePaths.addAll(getArchiveSourcePaths(child));
        }
      } else {
        Object children = Utils.invokeMethodIgnoreFail(defaultCopySpec, "getChildren");
        if (children instanceof Iterable) {
          for (Object child : (Iterable<?>) children) {
            if (child instanceof CopySpec) {
              sourcePaths.addAll(getArchiveSourcePaths((CopySpec) child));
            }
          }
        }
      }
    }
    return sourcePaths;
  }

  private Set<String> getClasspathConfigurationNames(SourceSet sourceSet) {
    Set<String> configurationNames = new HashSet<>();
    configurationNames.add(sourceSet.getCompileClasspathConfigurationName());
    if (GradleVersion.current().compareTo(GradleVersion.version("3.4")) >= 0) {
      configurationNames.add(sourceSet.getRuntimeClasspathConfigurationName());
    }
    return configurationNames;
  }
}
