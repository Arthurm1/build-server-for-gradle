// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Contains run information relating to a Gradle Run task.
 */
public interface GradleRunTask extends Serializable {

  String getTaskPath();

  List<File> getClasspath();

  List<String> getJvmOptions();

  File getWorkingDirectory();

  Map<String, String> getEnvironmentVariables();

  String getMainClass();

  List<String> getArguments();
}
