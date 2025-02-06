// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

/**
 * settings for the MetalsBspPlugin.
 * Use:
 * MetalsBspPlugin {
 *   javaSemanticDbVersion = "2.1"
 *   scalaSemanticDbVersion = "3.1"
 *   sourceRoot = File('/myRepos/myProject')
 * }
 */
public interface MetalsBspPluginExtension {

  Property<File> getSourceRoot();

  Property<String> getJavaSemanticDbVersion();

  Property<String> getScalaSemanticDbVersion();

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

      // default is no semantic db settings
      extension.getJavaSemanticDbVersion().unsetConvention();
      extension.getScalaSemanticDbVersion().unsetConvention();
      extension.getSourceRoot().unsetConvention();
    }
    return extension;
  }
}
