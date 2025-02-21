// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.impl;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleRunTask;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleTestTask;
import com.microsoft.java.bs.gradle.model.LanguageExtension;

/**
 * Default implementation of {@link GradleSourceSet}.
 */
public class DefaultGradleSourceSet implements GradleSourceSet {
  private static final long serialVersionUID = 1L;

  private String gradleVersion;

  private String projectName;

  private String projectPath;

  private File projectDir;

  private File rootDir;

  private String sourceSetName;

  private String classesTaskName;

  private String cleanTaskName;

  private Set<String> taskNames;

  private Set<File> sourceDirs;

  private Set<File> generatedSourceDirs;

  private Set<File> sourceOutputDirs;

  private Set<File> resourceDirs;

  private Set<File> resourceOutputDirs;

  private Map<File, List<File>> archiveOutputFiles;

  private List<File> compileClasspath;

  private List<File> runtimeClasspath;

  private Set<GradleModuleDependency> moduleDependencies;

  private Set<BuildTargetDependency> buildTargetDependencies;

  private Set<GradleTestTask> testTasks;

  private Set<GradleRunTask> runTasks;

  private Map<String, LanguageExtension> extensions;

  public DefaultGradleSourceSet() {}

  /**
   * Copy constructor.
   *
   * @param gradleSourceSet the source set to copy from.
   */
  public DefaultGradleSourceSet(GradleSourceSet gradleSourceSet) {
    this.gradleVersion = gradleSourceSet.getGradleVersion();
    this.projectName = gradleSourceSet.getProjectName();
    this.projectPath = gradleSourceSet.getProjectPath();
    this.projectDir = gradleSourceSet.getProjectDir();
    this.rootDir = gradleSourceSet.getRootDir();
    this.sourceSetName = gradleSourceSet.getSourceSetName();
    this.classesTaskName = gradleSourceSet.getClassesTaskName();
    this.cleanTaskName = gradleSourceSet.getCleanTaskName();
    this.taskNames = gradleSourceSet.getTaskNames();
    this.sourceDirs = gradleSourceSet.getSourceDirs();
    this.generatedSourceDirs = gradleSourceSet.getGeneratedSourceDirs();
    this.sourceOutputDirs = gradleSourceSet.getSourceOutputDirs();
    this.resourceDirs = gradleSourceSet.getResourceDirs();
    this.resourceOutputDirs = gradleSourceSet.getResourceOutputDirs();
    this.archiveOutputFiles = gradleSourceSet.getArchiveOutputFiles();
    this.compileClasspath = gradleSourceSet.getCompileClasspath();
    this.runtimeClasspath = gradleSourceSet.getRuntimeClasspath();
    this.moduleDependencies = gradleSourceSet.getModuleDependencies().stream()
            .map(DefaultGradleModuleDependency::new).collect(Collectors.toSet());
    this.buildTargetDependencies = gradleSourceSet.getBuildTargetDependencies().stream()
            .map(DefaultBuildTargetDependency::new).collect(Collectors.toSet());
    this.testTasks = gradleSourceSet.getTestTasks().stream()
            .map(DefaultGradleTestTask::new).collect(Collectors.toSet());
    this.runTasks = gradleSourceSet.getRunTasks().stream()
            .map(DefaultGradleRunTask::new).collect(Collectors.toSet());
    this.extensions = gradleSourceSet.getExtensions().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertLanguageExtension(e.getValue())));
  }

  private LanguageExtension convertLanguageExtension(LanguageExtension object) {
    if (object.isJavaExtension()) {
      return new DefaultJavaExtension(object.getAsJavaExtension());
    }

    if (object.isScalaExtension()) {
      return new DefaultScalaExtension(object.getAsScalaExtension());
    }

    if (object.isGroovyExtension()) {
      return new DefaultGroovyExtension(object.getAsGroovyExtension());
    }

    if (object.isKotlinExtension()) {
      return new DefaultKotlinExtension(object.getAsKotlinExtension());
    }

    if (object.isAntlrExtension()) {
      return new DefaultAntlrExtension(object.getAsAntlrExtension());
    }

    throw new IllegalArgumentException("No conversion methods defined for object: " + object);
  }

  @Override
  public String getGradleVersion() {
    return gradleVersion;
  }

  public void setGradleVersion(String gradleVersion) {
    this.gradleVersion = gradleVersion;
  }

  @Override
  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  @Override
  public String getProjectPath() {
    return projectPath;
  }

  public void setProjectPath(String projectPath) {
    this.projectPath = projectPath;
  }

  @Override
  public File getProjectDir() {
    return projectDir;
  }

  public void setProjectDir(File projectDir) {
    this.projectDir = projectDir;
  }

  @Override
  public File getRootDir() {
    return rootDir;
  }

  public void setRootDir(File rootDir) {
    this.rootDir = rootDir;
  }

  @Override
  public String getSourceSetName() {
    return sourceSetName;
  }

  public void setSourceSetName(String sourceSetName) {
    this.sourceSetName = sourceSetName;
  }

  @Override
  public String getClassesTaskName() {
    return classesTaskName;
  }

  public void setClassesTaskName(String classesTaskName) {
    this.classesTaskName = classesTaskName;
  }

  public String getCleanTaskName() {
    return cleanTaskName;
  }

  public void setCleanTaskName(String cleanTaskName) {
    this.cleanTaskName = cleanTaskName;
  }

  public void setTaskNames(Set<String> taskNames) {
    this.taskNames = taskNames;
  }

  public Set<String> getTaskNames() {
    return taskNames;
  }

  public Set<File> getSourceDirs() {
    return sourceDirs;
  }

  public void setSourceDirs(Set<File> sourceDirs) {
    this.sourceDirs = sourceDirs;
  }

  @Override
  public Set<File> getGeneratedSourceDirs() {
    return generatedSourceDirs;
  }

  public void setGeneratedSourceDirs(Set<File> generatedSourceDirs) {
    this.generatedSourceDirs = generatedSourceDirs;
  }

  @Override
  public Set<File> getSourceOutputDirs() {
    return sourceOutputDirs;
  }

  public void setSourceOutputDirs(Set<File> sourceOutputDirs) {
    this.sourceOutputDirs = sourceOutputDirs;
  }

  @Override
  public Set<File> getResourceDirs() {
    return resourceDirs;
  }

  public void setResourceDirs(Set<File> resourceDirs) {
    this.resourceDirs = resourceDirs;
  }

  @Override
  public Set<File> getResourceOutputDirs() {
    return resourceOutputDirs;
  }

  public void setResourceOutputDirs(Set<File> resourceOutputDirs) {
    this.resourceOutputDirs = resourceOutputDirs;
  }

  @Override
  public Map<File, List<File>> getArchiveOutputFiles() {
    return archiveOutputFiles;
  }

  public void setArchiveOutputFiles(Map<File, List<File>> archiveOutputFiles) {
    this.archiveOutputFiles = archiveOutputFiles;
  }

  @Override
  public List<File> getCompileClasspath() {
    return compileClasspath;
  }

  public void setCompileClasspath(List<File> compileClasspath) {
    this.compileClasspath = compileClasspath;
  }

  @Override
  public List<File> getRuntimeClasspath() {
    return runtimeClasspath;
  }

  public void setRuntimeClasspath(List<File> runtimeClasspath) {
    this.runtimeClasspath = runtimeClasspath;
  }

  @Override
  public Set<GradleModuleDependency> getModuleDependencies() {
    return moduleDependencies;
  }

  public void setModuleDependencies(Set<GradleModuleDependency> moduleDependencies) {
    this.moduleDependencies = moduleDependencies;
  }

  @Override
  public Set<BuildTargetDependency> getBuildTargetDependencies() {
    return buildTargetDependencies;
  }

  public void setBuildTargetDependencies(Set<BuildTargetDependency> buildTargetDependencies) {
    this.buildTargetDependencies = buildTargetDependencies;
  }

  @Override
  public boolean hasTests() {
    return testTasks != null && !testTasks.isEmpty();
  }

  @Override
  public Set<GradleTestTask> getTestTasks() {
    return testTasks;
  }

  public void setTestTasks(Set<GradleTestTask> testTasks) {
    this.testTasks = testTasks;
  }

  @Override
  public Set<GradleRunTask> getRunTasks() {
    return runTasks;
  }

  public void setRunTasks(Set<GradleRunTask> runTasks) {
    this.runTasks = runTasks;
  }

  @Override
  public Map<String, LanguageExtension> getExtensions() {
    return extensions;
  }

  public void setExtensions(Map<String, LanguageExtension> extensions) {
    this.extensions = extensions;
  }

  @Override
  public int hashCode() {
    return Objects.hash(gradleVersion, projectName, projectPath,
        projectDir, rootDir, sourceSetName, classesTaskName, cleanTaskName, taskNames, sourceDirs,
        generatedSourceDirs, sourceOutputDirs, resourceDirs, resourceOutputDirs, archiveOutputFiles,
        compileClasspath, runtimeClasspath, moduleDependencies, buildTargetDependencies,
        testTasks, runTasks, extensions);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DefaultGradleSourceSet other = (DefaultGradleSourceSet) obj;
    return Objects.equals(gradleVersion, other.gradleVersion)
            && Objects.equals(projectName, other.projectName)
            && Objects.equals(projectPath, other.projectPath)
            && Objects.equals(projectDir, other.projectDir)
            && Objects.equals(rootDir, other.rootDir)
            && Objects.equals(sourceSetName, other.sourceSetName)
            && Objects.equals(classesTaskName, other.classesTaskName)
            && Objects.equals(cleanTaskName, other.cleanTaskName)
            && Objects.equals(taskNames, other.taskNames)
            && Objects.equals(sourceDirs, other.sourceDirs)
            && Objects.equals(generatedSourceDirs, other.generatedSourceDirs)
            && Objects.equals(sourceOutputDirs, other.sourceOutputDirs)
            && Objects.equals(resourceDirs, other.resourceDirs)
            && Objects.equals(resourceOutputDirs, other.resourceOutputDirs)
            && Objects.equals(archiveOutputFiles, other.archiveOutputFiles)
            && Objects.equals(compileClasspath, other.compileClasspath)
            && Objects.equals(runtimeClasspath, other.runtimeClasspath)
            && Objects.equals(moduleDependencies, other.moduleDependencies)
            && Objects.equals(buildTargetDependencies, other.buildTargetDependencies)
            && Objects.equals(testTasks, other.testTasks)
            && Objects.equals(runTasks, other.runTasks)
            && Objects.equals(extensions, other.extensions);
  }

  @Override
  public String toString() {
    return "DefaultGradleSourceSet: GradleVersion:" + gradleVersion
        + " ProjectName:" + projectName
        + " projectPath:" + projectPath
        + " sourceSetName:" + sourceSetName
        + " projectDir:" + projectDir
        + " rootDir:" + rootDir
        + " classesTaskName:" + classesTaskName
        + " cleanTaskName:" + cleanTaskName
        + " taskNames:" + taskNames
        + " sourceDirs:" + sourceDirs
        + " generatedSourceDirs:" + generatedSourceDirs
        + " sourceOutputDirs:" + sourceOutputDirs
        + " resourceDirs:" + resourceDirs
        + " resourceOutputDirs:" + resourceOutputDirs
        + " archiveOutputFiles:" + archiveOutputFiles
        + " compileClasspath:" + compileClasspath
        + " runtimeClasspath:" + runtimeClasspath
        + " moduleDependencies:" + moduleDependencies
        + " buildTargetDependencies:" + buildTargetDependencies
        + " testTasks:" + testTasks
        + " runTasks:" + runTasks
        + " extensions:" + extensions;
  }
}
