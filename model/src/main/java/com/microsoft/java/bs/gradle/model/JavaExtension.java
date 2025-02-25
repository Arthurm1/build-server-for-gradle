// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model;

import java.io.File;
import java.util.List;

/**
 * The extension model for Java language.
 */
public interface JavaExtension extends LanguageExtension {

  /**
   * JDK home file location.
   */
  File getJavaHome();

  /**
   * The java version this target is supposed to use.
   */
  String getJavaVersion();

  /**
   * The source compatibility of the source set.
   */
  String getSourceCompatibility();

  /**
   * The target compatibility of the source set.
   */
  String getTargetCompatibility();

  /**
   * The list of compiler arguments.
   */
  List<String> getCompilerArgs();
}
