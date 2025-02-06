// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GroovyExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultGroovyExtension;
import com.microsoft.java.bs.gradle.plugin.utils.Utils;

import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.util.GradleVersion;

import com.microsoft.java.bs.gradle.model.SupportedLanguages;

/**
 * The language model builder for Groovy language.
 */
public class GroovyLanguageModelBuilder extends LanguageModelBuilder {

  @Override
  public SupportedLanguage<GroovyExtension> getLanguage() {
    return SupportedLanguages.GROOVY;
  }

  private Set<File> getSourceFolders(SourceSet sourceSet) {
    if (GradleVersion.current().compareTo(GradleVersion.version("7.1")) >= 0) {
      SourceDirectorySet sourceDirectorySet = sourceSet.getExtensions()
              .findByType(GroovySourceDirectorySet.class);
      return sourceDirectorySet == null ? Collections.emptySet() : sourceDirectorySet.getSrcDirs();
    } else {
      // there is no way pre-Gradle 7.1 to get the groovy source dirs separately from other
      // languages.  Luckily source dirs from all languages are jumbled together in BSP,
      // so we can just reply with all.
      // resource dirs must be removed.
      Set<File> allSource = sourceSet.getAllSource().getSrcDirs();
      Set<File> allResource = sourceSet.getResources().getSrcDirs();
      return allSource.stream().filter(dir -> !allResource.contains(dir))
        .collect(Collectors.toSet());
    }
  }

  private GroovyCompile getGroovyCompileTask(Project project, SourceSet sourceSet) {
    return (GroovyCompile) getLanguageCompileTask(project, sourceSet);
  }

  @Override
  public DefaultGroovyExtension getExtensionFor(Project project, SourceSet sourceSet,
                                 Set<GradleModuleDependency> moduleDependencies) {
    GroovyCompile groovyCompile = getGroovyCompileTask(project, sourceSet);
    if (groovyCompile != null) {
      DefaultGroovyExtension extension = new DefaultGroovyExtension();

      extension.setCompileTaskName(groovyCompile.getName());

      extension.setSourceDirs(getSourceFolders(sourceSet));
      extension.setGeneratedSourceDirs(Collections.emptySet());
      extension.setClassesDir(getClassesDir(groovyCompile));

      return extension;
    }
    return null;
  }

  private File getClassesDir(AbstractCompile compile) {
    if (GradleVersion.current().compareTo(GradleVersion.version("6.1")) >= 0) {
      return compile.getDestinationDirectory().get().getAsFile();
    } else {
      return Utils.invokeMethodIgnoreFail(compile, "getDestinationDir");
    }
  }
}