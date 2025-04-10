// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.actions;

import com.microsoft.java.bs.gradle.model.Artifact;
import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleRunTask;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.GradleTestTask;
import com.microsoft.java.bs.gradle.model.impl.DefaultBuildTargetDependency;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleRunTask;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSet;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSets;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleTestTask;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.gradle.GradleBuild;

/**
 * {@link BuildAction} that retrieves {@link DefaultGradleSourceSet} from a Gradle build,
 * handling both normal and composite builds.
 */
public class GetSourceSetsAction implements BuildAction<GradleSourceSets> {
  private static final long serialVersionUID = 1L;

  /**
   * Executes the build action and retrieves source sets from the Gradle build.
   *
   * @return A {@link DefaultGradleSourceSets} object containing all retrieved source sets.
   */
  @Override
  public GradleSourceSets execute(BuildController buildController) {
    GradleBuild build = buildController.getBuildModel();
    File rootProjectDir = build.getRootProject().getProjectDirectory();
    URI projectUri = rootProjectDir.toPath().toUri();
    List<GradleBuild> builds = fetchIncludedBuilds(build, rootProjectDir);
    return fetchModels(buildController, builds, rootProjectDir, projectUri);
  }

  private List<GradleBuild> fetchIncludedBuilds(GradleBuild build, File rootProjectDir) {
    return fetchIncludedBuilds(build, new HashSet<>(), rootProjectDir);
  }

  private List<GradleBuild> fetchIncludedBuilds(GradleBuild build, Set<File> retrievedBuilds,
      File rootProjectDir) {
    List<GradleBuild> results = new ArrayList<>();
    if (retrievedBuilds.add(rootProjectDir)) {
      // NOTE - gradle does not like the build-src projects being retrieved before the main project
      results.add(build);
      // Cannot use GradleVersion.current() in BuildAction as that will return the Tooling API
      // version
      // Cannot use BuildEnvironment to get GradleVersion as that doesn't work pre-3.0 even though
      // documentation has it added in version 1.
      // So just handle exceptions
      Set<? extends GradleBuild> moreBuilds;
      try {
        // added in 4.10
        moreBuilds = build.getEditableBuilds();
      } catch (Exception e1) {
        try {
          // added in 3.3
          moreBuilds = build.getIncludedBuilds();
        } catch (Exception e2) {
          moreBuilds = null;
        }
      }
      if (moreBuilds != null && !moreBuilds.isEmpty()) {
        for (GradleBuild includedBuild : moreBuilds) {
          File includedBuildDir = includedBuild.getRootProject().getProjectDirectory();
          results.addAll(fetchIncludedBuilds(includedBuild, retrievedBuilds, includedBuildDir));
        }
      }
    }
    return results;
  }

  /**
   * Fetches source sets from the provided Gradle build model.
   *
   * @param buildController The Gradle build controller used to interact with the build.
   * @param builds The Gradle build models representing the build and included builds.
   * @param rootProjectDir the dir of the main project
   * @param projectUri the URI to use for TAPI connections
   */
  private GradleSourceSets fetchModels(BuildController buildController,
      Collection<GradleBuild> builds, File rootProjectDir, URI projectUri) {

    // retrieve source sets with same root project concurrently
    // don't retrieve all source sets concurrently as Gradle may fail.
    List<GradleSourceSets> results = new ArrayList<>();
    for (GradleBuild build : builds) {
      // create an action per project
      Collection<GetSourceSetAction> projectActions = build.getProjects()
          .stream()
          .map(GetSourceSetAction::new)
          .collect(Collectors.toList());

      results.addAll(buildController.run(projectActions));
    }
    // since the model returned from Gradle TAPI is a wrapped object, here we re-construct it
    // via a copy constructor so we can treat as a DefaultGradleSourceSet and
    // populate source set dependencies.
    List<GradleSourceSet> sourceSets = results
        .stream()
        .flatMap(ss -> ss.getGradleSourceSets().stream())
        .map(DefaultGradleSourceSet::new)
        .collect(Collectors.toList());

    populateInterProjectInfo(sourceSets);
    prependTaskNames(sourceSets, rootProjectDir);
    populateProjectUri(sourceSets, projectUri);
    reduceInterProjectDependencies(sourceSets);
    removeProjectToProjectArtifacts(sourceSets);

    List<Exception> exceptions = results
        .stream()
        .flatMap(ss -> ss.getExceptions().stream())
        .collect(Collectors.toList());

    return new DefaultGradleSourceSets(sourceSets, exceptions);
  }

  /**
   * {@link BuildAction} that retrieves {@link GradleSourceSets} for a single project.
   * This allows project models to be retrieved in parallel.
   */
  static class GetSourceSetAction implements BuildAction<GradleSourceSets> {
    private static final long serialVersionUID = 1L;

    private final Model model;

    public GetSourceSetAction(Model model) {
      this.model = model;
    }

    @Override
    public GradleSourceSets execute(BuildController controller) {
      return controller.getModel(model, GradleSourceSets.class);
    }
  }

  // prepend project name to tasks in included builds
  private void prependTaskNames(List<GradleSourceSet> sourceSets,
      File rootProjectDir) {
    for (GradleSourceSet sourceSet : sourceSets) {
      if (!sourceSet.getRootDir().equals(rootProjectDir)
          && sourceSet instanceof DefaultGradleSourceSet) {
        String prefix = ':' + sourceSet.getRootProjectName();
        ((DefaultGradleSourceSet) sourceSet)
            .setTaskNames(sourceSet.getTaskNames().stream()
            .map(task -> prefix + task)
            .collect(Collectors.toSet()));
        ((DefaultGradleSourceSet) sourceSet)
            .setRunTasks(sourceSet.getRunTasks().stream()
            .map(task -> ((DefaultGradleRunTask) task).withPrefix(prefix))
            .collect(Collectors.toSet()));
        ((DefaultGradleSourceSet) sourceSet)
            .setTestTasks(sourceSet.getTestTasks().stream()
            .map(task -> ((DefaultGradleTestTask) task).withPrefix(prefix))
            .collect(Collectors.toSet()));
        ((DefaultGradleSourceSet) sourceSet)
            .setClassesTaskName(prefix + sourceSet.getClassesTaskName());
        ((DefaultGradleSourceSet) sourceSet)
            .setCleanTaskName(prefix + sourceSet.getCleanTaskName());
      }
    }
  }

  /**
   * In Gradle projects can directly depend on the output dir of another project.
   * Sometimes this is used to include another project's test classes.
   * These dirs should not appear in the artifacts.  If they do then they can make it
   * look like the build target has changed as a path reference might go from ending
   * in `/` to not ending in `/`.  This happens because the output dir may not exist
   * at the time of source set retrieval (so no slash).  Then later (after compilation)
   * it may exist.  That would trigger a BSP didChange event when nothing has changed.
   */
  private static void removeProjectToProjectArtifacts(List<GradleSourceSet> sourceSets) {
    Set<File> filesToExclude = new HashSet<>();
    for (GradleSourceSet sourceSet : sourceSets) {
      filesToExclude.addAll(sourceSet.getSourceOutputDirs());
      filesToExclude.addAll(sourceSet.getResourceOutputDirs());
      for (List<File> files : sourceSet.getArchiveOutputFiles().values()) {
        filesToExclude.addAll(files);
      }
    }
    Set<String> urisToExclude = filesToExclude.stream()
        .flatMap(file -> {
          // uri with and without final slash
          String uri1 = file.toPath().toUri().toString();
          String uri2 = uri1.endsWith("/") ? uri1.substring(0, uri1.length() - 1) : uri1 + "/";
          return Stream.of(uri1, uri2);
        })
        .collect(Collectors.toSet());
    for (GradleSourceSet sourceSet : sourceSets) {
      Set<GradleModuleDependency> modules = sourceSet.getModuleDependencies();
      for (GradleModuleDependency module : modules) {
        List<Artifact> artifacts = module.getArtifacts();
        artifacts.removeIf(artifact -> urisToExclude.contains(artifact.getUri().toString()));
      }
      modules.removeIf(module -> module.getArtifacts().isEmpty());
    }
  }

  // remove direct dependencies on other projects when they would be included transitively
  private void reduceInterProjectDependencies(List<GradleSourceSet> sourceSets) {
    Map<BuildTargetDependency, GradleSourceSet> dependencies = new HashMap<>();
    for (GradleSourceSet sourceSet : sourceSets) {
      dependencies.put(new DefaultBuildTargetDependency(sourceSet), sourceSet);
    }
    Map<BuildTargetDependency, Set<BuildTargetDependency>> transitiveDependencies =
        new HashMap<>();
    // find all transitive dependencies
    for (BuildTargetDependency dependency : dependencies.keySet()) {
      GradleSourceSet sourceSet = dependencies.get(dependency);
      Set<BuildTargetDependency> sourceSetDependencies = new HashSet<>();
      for (BuildTargetDependency dep : sourceSet.getBuildTargetDependencies()) {
        GradleSourceSet depSourceSet = dependencies.get(dep);
        sourceSetDependencies.addAll(depSourceSet.getBuildTargetDependencies());
      }
      transitiveDependencies.put(dependency, sourceSetDependencies);
    }
    // remove transitive
    for (BuildTargetDependency dependency : dependencies.keySet()) {
      GradleSourceSet sourceSet = dependencies.get(dependency);
      Set<BuildTargetDependency> sourceSetTransitiveDependencies =
          transitiveDependencies.get(dependency);
      Set<BuildTargetDependency> newDependencies =
          new HashSet<>(sourceSet.getBuildTargetDependencies());
      newDependencies.removeAll(sourceSetTransitiveDependencies);
      if (sourceSet instanceof DefaultGradleSourceSet) {
        ((DefaultGradleSourceSet) sourceSet).setBuildTargetDependencies(newDependencies);
      }
    }
  }

  // populate the project uri.  Same uri for all projects including `included` projects.
  private void populateProjectUri(List<GradleSourceSet> sourceSets, URI projectUri) {
    for (GradleSourceSet sourceSet : sourceSets) {
      if (sourceSet instanceof DefaultGradleSourceSet) {
        ((DefaultGradleSourceSet) sourceSet).setProjectUri(projectUri);
      }
    }
  }

  // Inter-sourceset dependencies must be built up after retrieval of all sourcesets
  // because they are not available before when using included builds.
  // Classpaths that reference other projects using jars are to be replaced with
  // source paths.
  private void populateInterProjectInfo(List<GradleSourceSet> sourceSets) {
    // map all output dirs to their source sets
    Map<File, List<File>> archivesToSourceOutput = new HashMap<>();
    Map<File, GradleSourceSet> outputsToSourceSet = new HashMap<>();
    for (GradleSourceSet sourceSet : sourceSets) {
      if (sourceSet.getSourceOutputDirs() != null) {
        for (File file : sourceSet.getSourceOutputDirs()) {
          outputsToSourceSet.put(file, sourceSet);
        }
      }
      if (sourceSet.getResourceOutputDirs() != null) {
        for (File file : sourceSet.getResourceOutputDirs()) {
          outputsToSourceSet.put(file, sourceSet);
        }
      }
      if (sourceSet.getArchiveOutputFiles() != null) {
        for (Map.Entry<File, List<File>> archive : sourceSet.getArchiveOutputFiles().entrySet()) {
          outputsToSourceSet.put(archive.getKey(), sourceSet);
          archivesToSourceOutput.computeIfAbsent(archive.getKey(), f -> new ArrayList<>())
              .addAll(archive.getValue());
        }
      }
    }

    // match any classpath entries to other project's output dirs/jars to create dependencies.
    // replace classpath entries that reference jars with classes dirs.
    for (GradleSourceSet sourceSet : sourceSets) {
      Set<BuildTargetDependency> dependencies = new HashSet<>();
      List<File> compileClasspath = new ArrayList<>();
      for (File file : sourceSet.getCompileClasspath()) {
        // add project dependency
        GradleSourceSet otherSourceSet = outputsToSourceSet.get(file);
        if (otherSourceSet != null && !otherSourceSet.equals(sourceSet)) {
          dependencies.add(new DefaultBuildTargetDependency(otherSourceSet));
        }
        // replace jar on classpath with source output on classpath
        List<File> sourceOutputDir = archivesToSourceOutput.get(file);
        if (sourceOutputDir == null) {
          compileClasspath.add(file);
        } else {
          compileClasspath.addAll(sourceOutputDir);
        }
      }
      List<File> runtimeClasspath = new ArrayList<>();
      for (File file : sourceSet.getRuntimeClasspath()) {
        // add project dependency
        GradleSourceSet otherSourceSet = outputsToSourceSet.get(file);
        if (otherSourceSet != null && !otherSourceSet.equals(sourceSet)) {
          dependencies.add(new DefaultBuildTargetDependency(otherSourceSet));
        }
        // replace jar on classpath with source output on classpath
        List<File> sourceOutputDir = archivesToSourceOutput.get(file);
        if (sourceOutputDir == null) {
          runtimeClasspath.add(file);
        } else {
          runtimeClasspath.addAll(sourceOutputDir);
        }
      }
      Set<GradleTestTask> testTasks = new HashSet<>();
      for (GradleTestTask task : sourceSet.getTestTasks()) {
        List<File> classpath = new ArrayList<>();
        for (File file : task.getClasspath()) {
          // replace jar on classpath with source output on classpath
          List<File> sourceOutputDir = archivesToSourceOutput.get(file);
          if (sourceOutputDir == null) {
            classpath.add(file);
          } else {
            classpath.addAll(sourceOutputDir);
          }
        }
        GradleTestTask newTask = new DefaultGradleTestTask(task.getTaskPath(),
            classpath, task.getJvmOptions(), task.getWorkingDirectory(),
            task.getEnvironmentVariables());
        testTasks.add(newTask);
      }
      Set<GradleRunTask> runTasks = new HashSet<>();
      for (GradleRunTask task : sourceSet.getRunTasks()) {
        List<File> classpath = new ArrayList<>();
        for (File file : task.getClasspath()) {
          // replace jar on classpath with source output on classpath
          List<File> sourceOutputDir = archivesToSourceOutput.get(file);
          if (sourceOutputDir == null) {
            classpath.add(file);
          } else {
            classpath.addAll(sourceOutputDir);
          }
        }
        GradleRunTask newTask = new DefaultGradleRunTask(task.getTaskPath(),
            classpath, task.getJvmOptions(), task.getWorkingDirectory(),
            task.getEnvironmentVariables(), task.getMainClass(), task.getArguments());
        runTasks.add(newTask);
      }
      if (sourceSet instanceof DefaultGradleSourceSet) {
        ((DefaultGradleSourceSet) sourceSet).setBuildTargetDependencies(dependencies);
        ((DefaultGradleSourceSet) sourceSet).setCompileClasspath(compileClasspath);
        ((DefaultGradleSourceSet) sourceSet).setRuntimeClasspath(runtimeClasspath);
        ((DefaultGradleSourceSet) sourceSet).setTestTasks(testTasks);
        ((DefaultGradleSourceSet) sourceSet).setRunTasks(runTasks);
      }
    }
  }
}