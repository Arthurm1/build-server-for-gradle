// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.impl.DefaultJavaExtension;
import com.microsoft.java.bs.gradle.plugin.utils.Utils;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;
import org.gradle.util.GradleVersion;

/**
 * The language model builder for Java language.
 */
public class JavaLanguageModelBuilder extends LanguageModelBuilder {

  @Override
  public SupportedLanguage<JavaExtension> getLanguage() {
    return SupportedLanguages.JAVA;
  }

  @Override
  public DefaultJavaExtension getExtensionFor(Project project, SourceSet sourceSet,
      Set<GradleModuleDependency> moduleDependencies) {
    JavaCompile javaCompile = getJavaCompileTask(project, sourceSet);
    if (javaCompile != null) {

      Set<File> allSourceDirs = new HashSet<>(sourceSet.getJava().getSrcDirs());
      return getExtension(project, javaCompile, allSourceDirs);
    }
    return null;
  }

  /**
   * Extract a JavaExtension from the project information.
   *
   * @param project Gradle project
   * @param javaCompile java compile task
   * @param allSourceDirs all the source directories for the sourceset/variant
   * @return the Java information
   */
  public DefaultJavaExtension getExtension(Project project, JavaCompile javaCompile,
      Set<File> allSourceDirs) {

    DefaultJavaExtension extension = new DefaultJavaExtension();

    // jdk
    extension.setJavaHome(DefaultInstalledJdk.current().getJavaHome());
    extension.setJavaVersion(DefaultInstalledJdk.current().getJavaVersion().getMajorVersion());

    extension.setCompileTaskName(javaCompile.getName());

    Set<File> generatedSrcDirs = new HashSet<>();
    File annotationProcessingDir = getAnnotationProcessingDir(javaCompile);
    if (annotationProcessingDir != null) {
      generatedSrcDirs.add(annotationProcessingDir);
    }
    Set<File> sourceDirs = new HashSet<>(allSourceDirs);
    File buildDir;
    // Even though ProjectLayout was added in 4.1, DirectoryProperty wasn't added til 4.3
    if (GradleVersion.current().compareTo(GradleVersion.version("4.3")) >= 0) {
      buildDir = project.getLayout().getBuildDirectory().map(Directory::getAsFile).getOrNull();
    } else {
      buildDir = Utils.invokeMethodIgnoreFail(project, "getBuildDir");
    }
    // generated sources aren't marked as such so work out if sources are in the build dir
    if (buildDir != null) {
      Set<File> sourceDirsInOutputDir = getFilesWithThisParent(buildDir.toPath(), sourceDirs);
      generatedSrcDirs.addAll(sourceDirsInOutputDir);
    }
    sourceDirs.removeAll(generatedSrcDirs);
    extension.setSourceDirs(sourceDirs);
    addGeneratedSourceDirs(javaCompile, extension.getSourceDirs(), generatedSrcDirs);
    extension.setGeneratedSourceDirs(generatedSrcDirs);

    extension.setCompilerArgs(getCompilerArgs(javaCompile));
    extension.setClassesDir(getClassesDir(javaCompile));

    // Ignore options and get source/target compatibility directly from javaCompile.
    extension.setSourceCompatibility(javaCompile.getSourceCompatibility());
    extension.setTargetCompatibility(javaCompile.getTargetCompatibility());

    return extension;
  }

  private Set<File> getFilesWithThisParent(Path parent, Set<File> files) {
    Set<File> results = new HashSet<>();
    for (File file : files) {
      if (file.toPath().startsWith(parent)) {
        results.add(file);
      }
    }
    return results;
  }

  private JavaCompile getJavaCompileTask(Project project, SourceSet sourceSet) {
    return (JavaCompile) getLanguageCompileTask(project, sourceSet);
  }

  private File getAnnotationProcessingDir(JavaCompile javaCompile) {
    CompileOptions options = javaCompile.getOptions();
    return getAnnotationProcessingDir(options);
  }

  /**
   * get the annotation processing path if it exists.
   *
   * @param options compile options
   * @return path to annotation processing output dir or null
   */
  public static File getAnnotationProcessingDir(CompileOptions options) {
    if (GradleVersion.current().compareTo(GradleVersion.version("6.3")) >= 0) {
      return options.getGeneratedSourceOutputDirectory().getAsFile().getOrNull();
    } else if (GradleVersion.current().compareTo(GradleVersion.version("4.3")) >= 0) {
      return Utils.invokeMethodIgnoreFail(options,
          "getAnnotationProcessorGeneratedSourcesDirectory");
    }
    return null;
  }


  private void addGeneratedSourceDirs(JavaCompile javaCompile, Set<File> srcDirs,
      Set<File> generatedSrcDirs) {
    Set<File> filesToCompile = javaCompile.getSource().getFiles();
    for (File file : filesToCompile) {
      if (canSkipInferSourceRoot(file, srcDirs, generatedSrcDirs)) {
        continue;
      }

      // the file is not in the source directories, so it must be a generated file.
      // we need to find the source directory for the generated file.
      File srcDir = findSourceDirForGeneratedFile(file);
      if (srcDir != null) {
        generatedSrcDirs.add(srcDir);
      }
    }
  }

  /**
   * Skip the source root inference if:
   * <ul>
   * <li>File is not a Java file.</li>
   * <li>File already belongs to srcDirs.</li>
   * <li>File already belongs to generatedSrcDirs.</li>
   * </ul>
   * Return <code>true</code> if the source root inference can be skipped.
   */
  private boolean canSkipInferSourceRoot(File sourceFile, Set<File> srcDirs,
      Set<File> generatedSrcDirs) {
    if (!sourceFile.isFile() || !sourceFile.exists() || !sourceFile.getName().endsWith(".java")) {
      return true;
    }

    if (srcDirs.stream().anyMatch(dir -> sourceFile.getAbsolutePath()
        .startsWith(dir.getAbsolutePath()))) {
      return true;
    }

    return generatedSrcDirs.stream().anyMatch(dir -> sourceFile.getAbsolutePath()
        .startsWith(dir.getAbsolutePath()));
  }

  /**
   * read the file content and find the package declaration.
   * Then find the source directory that contains the package declaration.
   * If the package declaration is not found, then return <code>null</code>.
   */
  private File findSourceDirForGeneratedFile(File file) {
    Pattern packagePattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;\\s*$");
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        Matcher matcher = packagePattern.matcher(line);
        if (matcher.matches()) {
          String packageName = matcher.group(1);
          String relativeToRoot = packageName.replace(".", File.separator)
              .concat(File.separator).concat(file.getName());
          String absolutePath = file.getAbsolutePath();
          if (!absolutePath.endsWith(relativeToRoot)) {
            return null;
          }
          return new File(absolutePath.substring(
                0, absolutePath.length() - relativeToRoot.length()));
        }
      }
    } catch (IOException e) {
      return null;
    }

    return null;
  }

  private static DefaultJavaCompileSpec getJavaCompileSpec(JavaCompile javaCompile) {
    CompileOptions options = javaCompile.getOptions();

    DefaultJavaCompileSpec specs = new DefaultJavaCompileSpec();
    try {
      specs.setCompileOptions(options);
    } catch (Exception e1) {
      // Android will throw exceptions here.  Populate as many options manually as possible
      try {
        options.getCompilerArgumentProviders().clear();
        specs.setCompileOptions(options);
      } catch (Exception e2) {
        // do nothing
      }
    }

    // check the project hasn't already got the target or source defined in the
    // compiler args so they're not overwritten below
    List<String> originalArgs = options.getCompilerArgs();
    String argsSourceCompatibility = getSourceCompatibility(originalArgs);
    String argsTargetCompatibility = getTargetCompatibility(originalArgs);

    if (!argsSourceCompatibility.isEmpty() && !argsTargetCompatibility.isEmpty()) {
      return specs;
    }

    if (GradleVersion.current().compareTo(GradleVersion.version("6.6")) >= 0) {
      if (options.getRelease().isPresent()) {
        specs.setRelease(options.getRelease().get());
        return specs;
      }
    }
    if (argsSourceCompatibility.isEmpty() && specs.getSourceCompatibility() == null) {
      String sourceCompatibility = javaCompile.getSourceCompatibility();
      if (sourceCompatibility != null) {
        specs.setSourceCompatibility(sourceCompatibility);
      }
    }
    if (argsTargetCompatibility.isEmpty() && specs.getTargetCompatibility() == null) {
      String targetCompatibility = javaCompile.getTargetCompatibility();
      if (targetCompatibility != null) {
        specs.setTargetCompatibility(targetCompatibility);
      }
    }
    return specs;
  }

  /**
   * Get the compilation arguments of the source set.
   */
  public static List<String> getCompilerArgs(JavaCompile javaCompile) {
    CompileOptions options = javaCompile.getOptions();

    try {
      DefaultJavaCompileSpec specs = getJavaCompileSpec(javaCompile);

      JavaCompilerArgumentsBuilder builder = new JavaCompilerArgumentsBuilder(specs)
              .includeMainOptions(true)
              .includeClasspath(false)
              .includeSourceFiles(false)
              .includeLauncherOptions(false);
      return builder.build();
    } catch (Exception e) {
      // DefaultJavaCompileSpec and JavaCompilerArgumentsBuilder are internal so may not exist.
      // Fallback to returning just the compiler arguments the build has specified.
      // This will miss a lot of arguments derived from the CompileOptions e.g. sourceCompatibility
      // Arguments must be cast and converted to String because Groovy can use GStringImpl
      // which then throws IllegalArgumentException when passed back over the tooling connection.
      List<Object> compilerArgs = new LinkedList<>(options.getCompilerArgs());
      return compilerArgs
          .stream()
          .map(Object::toString)
          .collect(Collectors.toList());
    }
  }

  /**
   * Get the source compatibility level of the source set.
   */
  public static String getSourceCompatibility(List<String> compilerArgs) {
    return findFirstCompilerArgMatch(compilerArgs,
      Stream.of("-source", "--source", "--release"))
      .orElse("");
  }

  /**
   * Get the target compatibility level of the source set.
   */
  public static String getTargetCompatibility(List<String> compilerArgs) {
    return findFirstCompilerArgMatch(compilerArgs,
      Stream.of("-target", "--target", "--release"))
      .orElse("");
  }

  private static Optional<String> findCompilerArg(List<String> compilerArgs, String arg) {
    int idx = compilerArgs.indexOf(arg);
    if (idx >= 0 && idx < compilerArgs.size() - 1) {
      return Optional.of(compilerArgs.get(idx + 1));
    }
    return Optional.empty();
  }

  private static Optional<String> findFirstCompilerArgMatch(List<String> compilerArgs,
      Stream<String> args) {
    return args.map(arg -> findCompilerArg(compilerArgs, arg))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .findFirst();
  }

  private File getClassesDir(AbstractCompile compile) {
    if (GradleVersion.current().compareTo(GradleVersion.version("6.1")) >= 0) {
      return compile.getDestinationDirectory().get().getAsFile();
    } else {
      return Utils.invokeMethodIgnoreFail(compile, "getDestinationDir");
    }
  }
}