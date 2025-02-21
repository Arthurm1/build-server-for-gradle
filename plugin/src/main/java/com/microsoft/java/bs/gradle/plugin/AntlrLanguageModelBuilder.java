// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.AntlrExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultAntlrExtension;

import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.plugins.antlr.AntlrSourceDirectorySet;
import org.gradle.util.GradleVersion;

import com.microsoft.java.bs.gradle.model.SupportedLanguages;

/**
 * The language model builder for Antlr language.
 */
public class AntlrLanguageModelBuilder extends LanguageModelBuilder {

  @Override
  public SupportedLanguage<AntlrExtension> getLanguage() {
    return SupportedLanguages.ANTLR;
  }

  private Set<File> getSourceFolders(SourceSet sourceSet) {
    if (GradleVersion.current().compareTo(GradleVersion.version("7.1")) >= 0) {
      SourceDirectorySet sourceDirectorySet = sourceSet.getExtensions()
          .findByType(AntlrSourceDirectorySet.class);
      return sourceDirectorySet == null ? Collections.emptySet() : sourceDirectorySet.getSrcDirs();
    } else {
      // there is no way pre-Gradle 7.1 to get the antlr source dirs separately from other
      // languages.  Luckily source dirs from all languages are jumbled together in BSP,
      // so we can just reply with all.
      // resource dirs must be removed.
      Set<File> allSource = sourceSet.getAllSource().getSrcDirs();
      Set<File> allResource = sourceSet.getResources().getSrcDirs();
      return allSource.stream().filter(dir -> !allResource.contains(dir))
          .collect(Collectors.toSet());
    }
  }

  @Override
  public DefaultAntlrExtension getExtensionFor(Project project, SourceSet sourceSet,
                                                Set<GradleModuleDependency> moduleDependencies) {
    Set<File> source = getSourceFolders(sourceSet);
    if (source == null) {
      return null;
    }
    DefaultAntlrExtension extension = new DefaultAntlrExtension();
    extension.setSourceDirs(source);
    extension.setGeneratedSourceDirs(new HashSet<>());
    // is that all there is for ANTLR?
  /*
    SourceSetOutput sourceSetOutput = sourceSet.getOutput();
    Set<File> classesDirs = sourceSetOutput.getClassesDirs().getFiles();
    if (!classesDirs.isEmpty()) {
      extension.setClassesDir(classesDirs.iterator().next());
    }*/

    return extension;
  }
}