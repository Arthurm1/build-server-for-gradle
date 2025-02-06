// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;

/**
 * The customized Gradle plugin to apply the semanticdb settings.
 */
public class MetalsBspPlugin implements Plugin<Project> {

  // A unique configuration to
  private static final String scala2ConfigName = "BspPlugin";

  private void addDependency(Project project, String configName, String dependency) {
    String suffixConfigName = configName.substring(0, 1).toUpperCase() + configName.substring(1);
    project.getConfigurations().forEach(config -> {
      addDependency(configName, suffixConfigName, project.getDependencies(), config, dependency);
    });
  }

  private void addDependency(String configName, String suffixConfigName,
      DependencyHandler dependencies, Configuration config, String dependency) {
    if (config.getName().equals(configName)
        || config.getName().endsWith(suffixConfigName)) {
      dependencies.add(config.getName(), dependency);
    }
  }

  private void applyJavaSemanticDbDependency(Project project, String version) {
    // Unsure how to know if annotation processing is used so add to
    // both compile and annotation processor configurations
    if (project.getPlugins().hasPlugin("java")) {
      String dependency = "com.sourcegraph:semanticdb-javac:" + version;
      addDependency(project, "compileOnly", dependency);
      addDependency(project, "annotationProcessor", dependency);
    }
  }

  // semanticdb plugin isn't needed on the classpath, but the location is needed and the jar must
  // exist so add it as a dependency to a new configuration and Gradle will download it.
  // There are no transitive dependencies - the plugin only requires the scala library.
  private void applyScalaSemanticDbDependency(Project project, String scalaVersion,
      String version) {
    if (project.getPlugins().hasPlugin("scala")) {
      // config only needs to be added for the first source set for the project
      if (project.getConfigurations().findByName(scala2ConfigName) == null) {
        project.getConfigurations().create(scala2ConfigName, config -> {
          config.setVisible(false);
          config.setCanBeConsumed(false);
          config.setCanBeDeclared(true);
          config.setCanBeResolved(true);
          config.setDescription("Semanticdb dependencies.");
          config.defaultDependencies(dependencies -> {
            String dependency = "org.scalameta:semanticdb-scalac_" + scalaVersion + ':' + version;
            dependencies.add(project.getDependencies().create(dependency));
          });
        });
      }
    }
  }

  private String extractScalaVersion(FileCollection classpath) {
    for (File file : classpath.getFiles()) {
      String filename = file.getName();
      if (filename.endsWith(".jar")) {
        if (filename.startsWith("scala3-library_3-") && filename.length() > 21) {
          // format "scala3-library_3-3.6.3.jar"
          return filename.substring(17, filename.length() - 4);

        } else if (filename.startsWith("scala-library-") && filename.length() > 18) {
          // format "scala-library-2.13.16.jar"
          return filename.substring(14, filename.length() - 4);
        }
      }
    }
    return "";
  }

  private File extractSemanticDbJar(Project project, String scalaVersion, String version) {
    Configuration config = project.getConfigurations().getByName(scala2ConfigName);
    String jarName = "semanticdb-scalac_" + scalaVersion + '-' + version + ".jar";
    for (File file : config.getFiles()) {
      if (file.getName().equals(jarName)) {
        return file;
      }
    }
    throw new IllegalStateException("Cannot find " + jarName + " in " + config.getFiles());
  }

  @Override
  public void apply(Project project) {

    // get user defined settings
    MetalsBspPluginExtension extension = MetalsBspPluginExtension.createExtension(project);

    project.afterEvaluate(proj -> {
      // Can't use rootProject dir for sourceroot because it will change for included builds
      // so supply it from the extension.
      String sourceRoot = extension.getSourceRoot().get().toString();
      String targetRoot = proj.getLayout().getBuildDirectory().get()
          .dir("semanticdb-targetroot").getAsFile().toString();

      // setup semanticdb plugin in Java
      if (extension.getJavaSemanticDbVersion().isPresent()) {
        applyJavaSemanticDbDependency(proj, extension.getJavaSemanticDbVersion().get());
        proj.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
          javaCompile.getOptions().getCompilerArgs()
            .add("-Xplugin:semanticdb -sourceroot:" + sourceRoot + " -targetroot:" + targetRoot);
        });
      }

      // setup semanticdb plugin in Scala
      String[] scalaVersion = new String[1];
      scalaVersion[0] = "";
      if (extension.getScalaSemanticDbVersion().isPresent()) {
        proj.getTasks().withType(ScalaCompile.class).configureEach(scalaCompile -> {

          scalaVersion[0] = extractScalaVersion(scalaCompile.getClasspath());

          applyScalaSemanticDbDependency(proj, scalaVersion[0],
                  extension.getScalaSemanticDbVersion().get());

          List<String> params = scalaCompile.getScalaCompileOptions().getAdditionalParameters();
          if (scalaVersion[0].startsWith("3")) {
            params.add("-Xsemanticdb");
            params.add("-sourceroot");
            params.add(sourceRoot);
            params.add("-semanticdb-target");
            params.add(targetRoot);
          } else {
            File pluginPath = extractSemanticDbJar(proj, scalaVersion[0],
                    extension.getScalaSemanticDbVersion().get());
            scalaCompile.getScalaCompileOptions().getAdditionalParameters()
                    .add("-Xplugin:" + pluginPath.toString().replace("\\", "\\\\"));
            params.add("-P:semanticdb:sourceroot:" + sourceRoot);
            params.add("-P:semanticdb:targetroot:" + targetRoot);
            params.add("-P:semanticdb:failures:warning");
            params.add("-P:semanticdb:synthetics:on");
            params.add("-Xplugin-require:semanticdb");
            params.add("-Yrangepos");
          }
        });
      }
    });
  }
}