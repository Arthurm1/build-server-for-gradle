// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.impl.DefaultKotlinExtension;
import com.microsoft.java.bs.gradle.plugin.utils.Utils;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.GradleVersion;

/**
 * The language model builder for Kotlin language.
 * Kotlin plugin is not built-in to Gradle so Reflection is used to query info.
 */
public class KotlinLanguageModelBuilder extends LanguageModelBuilder {

  // Configuration used to retrieve the semantic db libraries
  private static final String kotlinConfigName = "KotlinBspPlugin";

  @Override
  public SupportedLanguage<KotlinExtension> getLanguage() {
    return SupportedLanguages.KOTLIN;
  }

  /**
   * apply the semantic db plugin.
   *
   * @param project Gradle project
   * @param sourceRoot root of all source files
   * @param semanticDbVersion semantic db library version
   */
  @SuppressWarnings("unchecked")
  public static void configureSemanticDb(Project project, String sourceRoot,
      String semanticDbVersion) {
    // https://github.com/sourcegraph/scip-kotlin
    // TODO get this in a better way than findByPath as it only returns the main compile
    // TODO there are 2 different versions, 1.8 and 1.9.  How to support these?
    Task kotlinCompile = project.getTasks().findByPath(":compileKotlin");
    if (kotlinCompile != null) {
      applySemanticDbDependency(project, semanticDbVersion);
      File classesDir = getClassesDir(kotlinCompile);

      File pluginPath = extractSemanticDbJar(project, semanticDbVersion);
      List<String> params = new ArrayList<>();
      params.add("-Xplugin=" + pluginPath.toString().replace("\\", "\\\\"));
      params.add("-P plugin:semanticdb-kotlinc:sourceroot=" + sourceRoot);
      params.add("-P plugin:semanticdb-kotlinc:targetroot=" + classesDir);
      Object compilerOptions = Utils.invokeMethod(kotlinCompile, "getCompilerOptions");
      Provider<List<?>> freeCompilerArgsProvider =
          Utils.invokeMethod(compilerOptions, "getFreeCompilerArgs");
      try {
        Method addAll = freeCompilerArgsProvider.getClass().getMethod("addAll", Iterable.class);
        addAll.invoke(freeCompilerArgsProvider, params);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        // do nothing
      }
    }
  }

  // semanticdb plugin isn't needed on the classpath, but the location is needed and the jar must
  // exist so add it as a dependency to a new configuration and Gradle will download it.
  // There are no transitive dependencies.
  private static void applySemanticDbDependency(Project project, String version) {
    if (project.getPlugins().hasPlugin("kotlin")) {
      // config only needs to be added for the first source set for the project
      if (project.getConfigurations().findByName(kotlinConfigName) == null) {
        project.getConfigurations().create(kotlinConfigName, config -> {
          config.setVisible(false);
          config.setCanBeConsumed(false);
          config.setCanBeDeclared(true);
          config.setCanBeResolved(true);
          config.setDescription("Semanticdb Kotlin dependencies.");
          config.defaultDependencies(dependencies -> {
            String dependency = "com.sourcegraph:semanticdb-kotlinc:" + version;
            dependencies.add(project.getDependencies().create(dependency));
          });
        });
      }
    }
  }

  private static File extractSemanticDbJar(Project project, String version) {
    Configuration config = project.getConfigurations().getByName(kotlinConfigName);
    String jarName = "semanticdb-kotlinc-" + version + ".jar";
    for (File file : config.getFiles()) {
      if (file.getName().equals(jarName)) {
        return file;
      }
    }
    throw new IllegalStateException("Cannot find " + jarName + " in " + config.getFiles());
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
      extension.setClassesDir(getClassesDir(kotlinCompile));

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
    Object compilerOptions = Utils.invokeMethod(kotlinCompile, "getCompilerOptions");
    Provider<?> apiVersionProvider = Utils.invokeMethod(compilerOptions, "getApiVersion");
    if (apiVersionProvider.isPresent()) {
      Object apiVersion = apiVersionProvider.get();
      if (apiVersion != null) {
        Object versionMethodObject = Utils.invokeMethod(apiVersion, "getVersion");
        if (versionMethodObject != null) {
          return versionMethodObject.toString();
        }
      }
    }
    return "";
  }

  private String getKotlinLanguageVersion(Task kotlinCompile) {
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/tasks/KotlinCompile.kt
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/dsl/KotlinCommonCompilerOptions.kt
    Object compilerOptions = Utils.invokeMethod(kotlinCompile, "getCompilerOptions");
    Provider<?> languageVersionProvider =
        Utils.invokeMethod(compilerOptions, "getLanguageVersion");
    if (languageVersionProvider.isPresent()) {
      Object languageVersion = languageVersionProvider.get();
      if (languageVersion != null) {
        Object versionMethodObject = Utils.invokeMethod(languageVersion, "getVersion");
        if (versionMethodObject != null) {
          return versionMethodObject.toString();
        }
      }
    }
    return "";
  }

  private List<String> getKotlinOptions(Task kotlinCompile) {
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/tasks/KotlinCompile.kt
    // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/dsl/KotlinCommonCompilerOptions.kt
    Object compilerOptions = Utils.invokeMethod(kotlinCompile, "getCompilerOptions");
    Provider<List<?>> freeCompilerArgsProvider =
        Utils.invokeMethod(compilerOptions, "getFreeCompilerArgs");
    if (freeCompilerArgsProvider.isPresent()) {
      List<?> freeCompilerArgs = freeCompilerArgsProvider.get();
      return freeCompilerArgs.stream()
        .map(Object::toString).collect(Collectors.toList());
    }
    return null;
  }

  private static File getClassesDir(Task kotlinCompile) {
    if (GradleVersion.current().compareTo(GradleVersion.version("4.2")) >= 0) {
      // https://github.com/JetBrains/kotlin/blob/master/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/tasks/KotlinCompile.kt
      Object destinationDirectory = Utils.invokeMethod(kotlinCompile, "getDestinationDirectory");
      Provider<File> fileProvider = Utils.invokeMethod(destinationDirectory, "getAsFile");
      if (fileProvider.isPresent()) {
        return fileProvider.get();
      }
    }
    return null;
  }
}
