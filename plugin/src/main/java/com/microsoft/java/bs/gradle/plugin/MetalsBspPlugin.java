// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * The customized Gradle plugin to apply the semanticdb settings.
 */
public class MetalsBspPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {

    // get user defined settings
    MetalsBspPluginExtension extension = MetalsBspPluginExtension.createExtension(project);

    project.afterEvaluate(proj -> {
      // Can't use rootProject dir for sourceroot because it will change for included builds
      // so supply it from the extension.
      String sourceRoot = extension.getSourceRoot().toString();

      // setup semanticdb plugin in Java
      String javaSemanticDbVersion = extension.getJavaSemanticDbVersion();
      if (javaSemanticDbVersion != null) {
        JavaLanguageModelBuilder.configureSemanticDb(proj, sourceRoot, javaSemanticDbVersion);
      }

      // setup semanticdb plugin in kotlin
      String kotlinSemanticDbVersion = extension.getKotlinSemanticDbVersion();
      if (kotlinSemanticDbVersion != null) {
        KotlinLanguageModelBuilder.configureSemanticDb(proj, sourceRoot, kotlinSemanticDbVersion);
      }

      // setup semanticdb plugin in Scala
      String scalaSemanticDbVersion = extension.getScalaSemanticDbVersion();
      if (scalaSemanticDbVersion != null) {
        ScalaLanguageModelBuilder.configureSemanticDb(proj, sourceRoot, scalaSemanticDbVersion);
      }
    });
  }
}