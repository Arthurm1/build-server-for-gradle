// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultKotlinExtension;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.GradleVersion;

import com.microsoft.java.bs.gradle.model.SupportedLanguages;

/**
 * The language model builder for Kotlin language.
 * Kotlin plugin is not built-in to Gradle so Reflection is used to query info.
 */
public class KotlinLanguageModelBuilder extends LanguageModelBuilder {

  @Override
  public SupportedLanguage<KotlinExtension> getLanguage() {
    return SupportedLanguages.KOTLIN;
  }

  private Set<File> getSourceFolders(SourceSet sourceSet) {
    if (GradleVersion.current().compareTo(GradleVersion.version("7.1")) >= 0) {
      SourceDirectorySet sourceDirectorySet = (SourceDirectorySet)
          sourceSet.getExtensions().findByName("kotlin");
      return sourceDirectorySet == null ? Collections.emptySet() : sourceDirectorySet.getSrcDirs();
    } else {
      // there is no way pre-Gradle 7.1 to get the kotlin source dirs separately from other
      // languages.  Luckily source dirs from all languages are jumbled together in BSP,
      // so we can just reply with all.
      // resource dirs must be removed.
      Set<File> allSource = sourceSet.getAllSource().getSrcDirs();
      Set<File> allResource = sourceSet.getResources().getSrcDirs();
      return allSource.stream().filter(dir -> !allResource.contains(dir))
        .collect(Collectors.toSet());
    }
  }

  private Task getKotlinCompileTask(Project project, SourceSet sourceSet) {
    return getLanguageCompileTask(project, sourceSet);
  }

  @Override
  public DefaultKotlinExtension getExtensionFor(Project project, SourceSet sourceSet,
                                 Set<GradleModuleDependency> moduleDependencies) {
    Task kotlinCompile = getKotlinCompileTask(project, sourceSet);
    if (kotlinCompile != null) {
      DefaultKotlinExtension extension = new DefaultKotlinExtension();

      extension.setCompileTaskName(kotlinCompile.getName());

      extension.setSourceDirs(getSourceFolders(sourceSet));
      extension.setGeneratedSourceDirs(Collections.emptySet());
      extension.setClassesDir(getClassesDir(kotlinCompile, sourceSet));

      extension.setKotlinApiVersion(getKotlinApiVersion(kotlinCompile));
      extension.setKotlinLanguageVersion(getKotlinLanguageVersion(kotlinCompile));
      extension.setKotlincOptions(getKotlinOptions(kotlinCompile));
      // TODO - how to set this?
      // gradleSourceSet.setKotlinAssociates(null);
      return extension;
    }
    return null;
  }

  private String getKotlinApiVersion(Task kotlinCompile) {
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/tasks/KotlinCompile.kt
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/dsl/KotlinCommonCompilerOptions.kt
    try {
      Method getCompilerOptionsMethod = kotlinCompile.getClass().getMethod("getCompilerOptions");
      Object compilerOptions = getCompilerOptionsMethod.invoke(kotlinCompile);
      Method getApiVersionMethod = compilerOptions.getClass().getMethod("getApiVersion");
      Object apiVersionProviderObject = getApiVersionMethod.invoke(compilerOptions);
      if (apiVersionProviderObject instanceof Provider) {
        Provider<?> apiVersionProvider = (Provider<?>) apiVersionProviderObject;
        if (apiVersionProvider.isPresent()) {
          Object apiVersion = apiVersionProvider.get();
          if (apiVersion != null) {
            Method versionMethod = apiVersion.getClass().getMethod("getVersion");
            Object versionMethodObject = versionMethod.invoke(apiVersion);
            if (versionMethodObject != null) {
              return versionMethodObject.toString();
            }
          }
        }
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
             | IllegalArgumentException | InvocationTargetException e) {
      // ignore
    }
    return "";
  }

  private String getKotlinLanguageVersion(Task kotlinCompile) {
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/tasks/KotlinCompile.kt
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/dsl/KotlinCommonCompilerOptions.kt
    try {
      Method getCompilerOptionsMethod = kotlinCompile.getClass().getMethod("getCompilerOptions");
      Object compilerOptions = getCompilerOptionsMethod.invoke(kotlinCompile);
      Method getLanguageVersionMethod = compilerOptions.getClass().getMethod("getLanguageVersion");
      Object languageVersionProviderObject = getLanguageVersionMethod.invoke(compilerOptions);
      if (languageVersionProviderObject instanceof Provider) {
        Provider<?> languageVersionProvider = (Provider<?>) languageVersionProviderObject;
        if (languageVersionProvider.isPresent()) {
          Object languageVersion = languageVersionProvider.get();
          if (languageVersion != null) {
            Method versionMethod = languageVersion.getClass().getMethod("getVersion");
            Object versionMethodObject = versionMethod.invoke(languageVersion);
            if (versionMethodObject != null) {
              return versionMethodObject.toString();
            }
          }
        }
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
             | IllegalArgumentException | InvocationTargetException e) {
      // ignore
    }
    return "";
  }

  private List<String> getKotlinOptions(Task kotlinCompile) {
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/tasks/KotlinCompile.kt
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/dsl/KotlinCommonCompilerOptions.kt
    try {
      Method getCompilerOptionsMethod = kotlinCompile.getClass().getMethod("getCompilerOptions");
      Object compilerOptions = getCompilerOptionsMethod.invoke(kotlinCompile);
      Method getLanguageVersionMethod = compilerOptions.getClass().getMethod("getFreeCompilerArgs");
      Object freeCompilerArgsProviderObject = getLanguageVersionMethod.invoke(compilerOptions);
      if (freeCompilerArgsProviderObject instanceof Provider) {
        Provider<?> freeCompilerArgsProvider = (Provider<?>) freeCompilerArgsProviderObject;
        if (freeCompilerArgsProvider.isPresent()) {
          Object freeCompilerArgs = freeCompilerArgsProvider.get();
          if (freeCompilerArgs instanceof List) {
            return ((List<?>) freeCompilerArgs).stream()
              .map(Object::toString).collect(Collectors.toList());
          }
        }
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
             | IllegalArgumentException | InvocationTargetException e) {
      // ignore
    }
    return null;
  }

  private File getClassesDir(Task kotlinCompile, SourceSet sourceSet) {
    if (GradleVersion.current().compareTo(GradleVersion.version("4.2")) >= 0) {
      // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/tasks/KotlinCompile.kt
      try {
        Method getDestinationDirectoryMethod = kotlinCompile.getClass().getMethod("getDestinationDirectory");
        Object destinationDirectory = getDestinationDirectoryMethod.invoke(kotlinCompile);
        Method getAsFileMethod = destinationDirectory.getClass().getMethod("getAsFile");
        Object fileProviderObject = getAsFileMethod.invoke(destinationDirectory);
        if (fileProviderObject instanceof Provider) {
          Provider<?> fileProvider = (Provider<?>) fileProviderObject;
          if (fileProvider.isPresent()) {
            Object file = fileProvider.get();
            if (file instanceof File) {
              return (File) file;
            }
          }
        }
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException
              | IllegalArgumentException | InvocationTargetException e) {
        // ignore
      }
    }
    return null;
  }
}
