// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import org.gradle.api.Project;

/**
 * settings for the GradleBuildServerPluginExtension.
 * String with `|` to separate languages.
 * `String` instead of Set of String to maintain backwards compatibility
 * Use:
 * GradleBuildServerPlugin {
 *   languages = "java|scala"
 * }
 */
public class GradleBuildServerPluginExtension {

  private String languages;

  public String getLanguages() {
    return languages;
  }

  public void setLanguages(String languages) {
    this.languages = languages;
  }

  /**
   * create the extension with defaults.
   *
   * @param project Gradle project.
   * @return instance of the extension.
   */
  static GradleBuildServerPluginExtension createExtension(Project project) {
    GradleBuildServerPluginExtension extension = project.getExtensions()
        .findByType(GradleBuildServerPluginExtension.class);
    if (extension == null) {
      extension = project.getExtensions().create("GradleBuildServerPlugin",
          GradleBuildServerPluginExtension.class);
    }
    return extension;
  }
}
