// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.ScalaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultScalaExtension;
import com.microsoft.java.bs.gradle.plugin.utils.Utils;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.ScalaSourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.util.GradleVersion;

import com.microsoft.java.bs.gradle.model.SupportedLanguages;

/**
 * The language model builder for Scala language.
 */
public class ScalaLanguageModelBuilder extends LanguageModelBuilder {

  @Override
  public SupportedLanguage<ScalaExtension> getLanguage() {
    return SupportedLanguages.SCALA;
  }

  private Set<File> getSourceFolders(SourceSet sourceSet) {
    if (GradleVersion.current().compareTo(GradleVersion.version("7.1")) >= 0) {
      SourceDirectorySet sourceDirectorySet = sourceSet.getExtensions()
              .findByType(ScalaSourceDirectorySet.class);
      return sourceDirectorySet == null ? Collections.emptySet() : sourceDirectorySet.getSrcDirs();
    } else {
      // there appears to be no way pre-Gradle 7.1 to get the scala source dirs separately
      // from other languages.  Source dirs from all languages are jumbled together in BSP,
      // so we can just reply with all.
      // resource dirs must be removed.
      Set<File> allSource = sourceSet.getAllSource().getSrcDirs();
      Set<File> allResource = sourceSet.getResources().getSrcDirs();
      return allSource.stream().filter(dir -> !allResource.contains(dir))
        .collect(Collectors.toSet());
    }
  }

  private ScalaCompile getScalaCompileTask(Project project, SourceSet sourceSet) {
    return (ScalaCompile) getLanguageCompileTask(project, sourceSet);
  }

  @Override
  public DefaultScalaExtension getExtensionFor(Project project, SourceSet sourceSet,
      Set<GradleModuleDependency> moduleDependencies) {
    ScalaCompile scalaCompile = getScalaCompileTask(project, sourceSet);
    GradleModuleDependency scalaLibraryDependency = getScalaLibraryDependency(moduleDependencies);
    if (scalaCompile != null && scalaLibraryDependency != null) {
      DefaultScalaExtension extension = new DefaultScalaExtension();

      extension.setCompileTaskName(scalaCompile.getName());

      extension.setSourceDirs(getSourceFolders(sourceSet));
      Set<File> generatedDirs = new HashSet<>();
      File annotationProcessorDir = JavaLanguageModelBuilder.getAnnotationProcessingDir(
          scalaCompile.getOptions());
      if (annotationProcessorDir != null) {
        generatedDirs.add(annotationProcessorDir);
      }
      extension.setGeneratedSourceDirs(generatedDirs);
      extension.setClassesDir(getClassesDir(scalaCompile));

      extension.setScalaOrganization(getScalaOrganization(scalaLibraryDependency));
      extension.setScalaVersion(getScalaVersion(scalaLibraryDependency));
      extension.setScalaBinaryVersion(getScalaBinaryVersion(scalaLibraryDependency));
      extension.setScalaCompilerArgs(getScalaCompilerArgs(scalaCompile));
      extension.setScalaJars(getScalaJars(scalaCompile));
      extension.setClassesDir(getClassesDir(scalaCompile));
      return extension;
    }
    return null;
  }

  private GradleModuleDependency findModule(String name,
                                            Set<GradleModuleDependency> moduleDependencies) {
    Optional<GradleModuleDependency> module = moduleDependencies.stream()
            .filter(f -> f.getModule().equals(name))
            .findAny();
    return module.orElse(null);
  }

  private GradleModuleDependency getScalaLibraryDependency(
          Set<GradleModuleDependency> moduleDependencies) {
    // scala 3 library takes precedence as scala 2 library can also be present.
    GradleModuleDependency scala3Library = findModule("scala3-library_3", moduleDependencies);
    if (scala3Library != null) {
      return scala3Library;
    }
    return findModule("scala-library", moduleDependencies);
  }

  private String getScalaOrganization(GradleModuleDependency scalaLibraryDependency) {
    return scalaLibraryDependency.getGroup();
  }

  private String getScalaVersion(GradleModuleDependency scalaLibraryDependency) {
    return scalaLibraryDependency.getVersion();
  }

  private String getScalaBinaryVersion(GradleModuleDependency scalaLibraryDependency) {
    String version = scalaLibraryDependency.getVersion();
    int idx1 = version.indexOf('.');
    int idx2 = version.indexOf('.', idx1 + 1);
    return version.substring(0, idx2);
  }

  private List<File> getScalaJars(ScalaCompile scalaCompile) {
    return new LinkedList<>(scalaCompile.getScalaClasspath().getFiles());
  }

  private void addIfNotExists(String option, Set<String> usedArgs, List<String> args) {
    if (!usedArgs.contains(option)) {
      args.add(option);
    }
  }

  private List<String> getScalaCompilerArgs(ScalaCompile scalaCompile) {
    // Gradle changes internal implementation for options handling after 7.0 so manually setup
    ScalaCompileOptions options = scalaCompile.getScalaCompileOptions();

    List<String> additionalArgs = new ArrayList<>();
    if (options.getAdditionalParameters() != null) {
      additionalArgs.addAll(options.getAdditionalParameters());
    }
    Set<String> usedArgs = new HashSet<>(additionalArgs);
    List<String> args = new LinkedList<>();
    if (options.isDeprecation()) {
      addIfNotExists("-deprecation", usedArgs, args);
    }
    if (options.isUnchecked()) {
      addIfNotExists("-unchecked", usedArgs, args);
    }
    if (options.getDebugLevel() != null && !options.getDebugLevel().isEmpty()) {
      addIfNotExists("-g:" + options.getDebugLevel(), usedArgs, args);
    }
    if (options.isOptimize()) {
      addIfNotExists("-optimise", usedArgs, args);
    }
    if (options.getEncoding() != null && !options.getEncoding().isEmpty()) {
      if (!usedArgs.contains("-encoding")) {
        args.add("-encoding");
        args.add(options.getEncoding());
      }
    }
    if (options.getLoggingLevel() != null) {
      if ("verbose".equalsIgnoreCase(options.getLoggingLevel())) {
        addIfNotExists("-verbose", usedArgs, args);
      } else if ("debug".equalsIgnoreCase(options.getLoggingLevel())) {
        addIfNotExists("-Ydebug", usedArgs, args);
      }
    }
    if (options.getLoggingPhases() != null && !options.getLoggingPhases().isEmpty()) {
      for (String phase : options.getLoggingPhases()) {
        addIfNotExists("-Ylog:" + phase, usedArgs, args);
      }
    }
    args.addAll(additionalArgs);

    // scalaCompilerPlugins was added in Gradle 6.4
    try {
      Method getScalaCompilerPlugins = ScalaCompile.class
          .getDeclaredMethod("getScalaCompilerPlugins");
      FileCollection fileCollection = (FileCollection) getScalaCompilerPlugins.invoke(scalaCompile);
      for (File file : fileCollection) {
        args.add("-Xplugin:" + file.getPath());
      }
    } catch (NoSuchMethodException | InvocationTargetException
        | IllegalArgumentException | IllegalAccessException e) {
      // do nothing
    }

    return args;
  }

  private File getClassesDir(AbstractCompile compile) {
    if (compile != null) {
      if (GradleVersion.current().compareTo(GradleVersion.version("6.1")) >= 0) {
        return compile.getDestinationDirectory().get().getAsFile();
      } else {
        return Utils.invokeMethodIgnoreFail(compile, "getDestinationDir");
      }
    }

    return null;
  }
}
