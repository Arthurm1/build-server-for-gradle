// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import org.gradle.api.Project;

/**
 * settings for the MetalsBspPlugin.
 * Use:
 * MetalsBspPlugin {
 *   javaSemanticDbVersion = "2.1"
 *   scalaSemanticDbVersion = "3.1"
 *   sourceRoot = File('/myRepos/myProject')
 * }
 */
public class MetalsBspPluginExtension {

  private File sourceRoot;
  private String javaSemanticDbVersion;
  private String scalaSemanticDbVersion;

  public File getSourceRoot() {
    return sourceRoot;
  }

  public void setSourceRoot(File sourceRoot) {
    this.sourceRoot = sourceRoot;
  }

  public String getJavaSemanticDbVersion() {
    return javaSemanticDbVersion;
  }

  public void setJavaSemanticDbVersion(String javaSemanticDbVersion) {
    this.javaSemanticDbVersion = javaSemanticDbVersion;
  }

  public String getScalaSemanticDbVersion() {
    return scalaSemanticDbVersion;
  }

  public void setScalaSemanticDbVersion(String scalaSemanticDbVersion) {
    this.scalaSemanticDbVersion = scalaSemanticDbVersion;
  }

  /**
   * create the extension with defaults.
   *
   * @param project Gradle project.
   * @return instance of the extension.
   */
  static MetalsBspPluginExtension createExtension(Project project) {
    MetalsBspPluginExtension extension = project.getExtensions()
        .findByType(MetalsBspPluginExtension.class);
    if (extension == null) {
      extension = project.getExtensions()
        .create("MetalsBspPlugin", MetalsBspPluginExtension.class);
    }
    return extension;
  }
}
