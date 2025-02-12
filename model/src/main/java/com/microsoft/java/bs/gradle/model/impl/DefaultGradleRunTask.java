// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.impl;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.microsoft.java.bs.gradle.model.GradleRunTask;

/**
 * Contains run information relating to a Gradle Run task.
 */
public class DefaultGradleRunTask implements GradleRunTask {

  private static final long serialVersionUID = 1L;

  private final String taskPath;
  private final List<File> classpath;
  private final List<String> jvmOptions;
  private final File workingDirectory;
  private final Map<String, String> environmentVariables;
  private final String mainClass;
  private final List<String> arguments;

  /**
   * Initialize the run information.
   */
  public DefaultGradleRunTask(String taskPath, List<File> classpath,
      List<String> jvmOptions, File workingDirectory,
      Map<String, String> environmentVariables, String mainClass, List<String> arguments) {
    this.taskPath = taskPath;
    this.classpath = classpath;
    this.jvmOptions = jvmOptions;
    this.workingDirectory = workingDirectory;
    this.environmentVariables = environmentVariables;
    this.mainClass = mainClass;
    this.arguments = arguments;
  }

  /**
   * copy constructor.
   */
  public DefaultGradleRunTask(GradleRunTask gradleRunTask) {
    this.taskPath = gradleRunTask.getTaskPath();
    this.classpath = gradleRunTask.getClasspath();
    this.jvmOptions = gradleRunTask.getJvmOptions();
    this.workingDirectory = gradleRunTask.getWorkingDirectory();
    this.environmentVariables = gradleRunTask.getEnvironmentVariables();
    this.mainClass = gradleRunTask.getMainClass();
    this.arguments = gradleRunTask.getArguments();
  }

  @Override
  public String getTaskPath() {
    return taskPath;
  }

  @Override
  public List<File> getClasspath() {
    return classpath;
  }

  @Override
  public List<String> getJvmOptions() {
    return jvmOptions;
  }

  @Override
  public File getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  @Override
  public String getMainClass() {
    return mainClass;
  }

  @Override
  public List<String> getArguments() {
    return arguments;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(taskPath, classpath, jvmOptions, workingDirectory,
        environmentVariables, mainClass, arguments);
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
    DefaultGradleRunTask other = (DefaultGradleRunTask) obj;
    return Objects.equals(taskPath, other.taskPath)
        && Objects.equals(classpath, other.classpath)
        && Objects.equals(jvmOptions, other.jvmOptions)
        && Objects.equals(workingDirectory, other.workingDirectory)
        && Objects.equals(environmentVariables, other.environmentVariables)
        && Objects.equals(mainClass, other.mainClass)
        && Objects.equals(arguments, other.arguments);
  }

  @Override
  public String toString() {
    return "DefaultGradleRunTask: TaskPath:" + taskPath
        + " Classpath:" + classpath
        + " JvmOptions:" + jvmOptions
        + " WorkingDirectory:" + workingDirectory
        + " EnvironmentVariables:" + environmentVariables
        + " MainClass:" + mainClass
        + " Arguments:" + arguments;
  }
}
