package com.microsoft.java.bs.gradle.plugin.utils;

import com.microsoft.java.bs.gradle.model.Artifact;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.impl.DefaultJavaExtension;
import com.microsoft.java.bs.gradle.model.impl.DefaultArtifact;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleModuleDependency;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSet;
import com.microsoft.java.bs.gradle.plugin.JavaLanguageModelBuilder;
import com.microsoft.java.bs.gradle.plugin.dependency.DependencyCollector;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for android related operations.
 */
public class AndroidUtils {

  private AndroidUtils() {
  }

  /**
   * Checks if the given project is an Android project.
   *
   * @param project Gradle project to check
   */
  public static boolean isAndroidProject(Project project) {
    return getAndroidExtension(project) != null;
  }

  /**
   * Extracts build variants from the given Android project and converts
   * them into list of GradleSourceSets.
   *
   * @param project Gradle project for extracting the build variants
   */
  public static List<GradleSourceSet> getBuildVariantsAsGradleSourceSets(Project project) {

    List<GradleSourceSet> sourceSets = new LinkedList<>();

    Object androidExtension = getAndroidExtension(project);
    if (androidExtension == null) {
      return sourceSets;
    }

    AndroidProjectType androidProjectType = getProjectType(project);
    if (androidProjectType == null) {
      return sourceSets;
    }

    List<Object> variants = new LinkedList<>();
    switch (androidProjectType) {
      case APPLICATION:
      case DYNAMIC_FEATURE:
        variants = getVariants(androidExtension, "getApplicationVariants", "getTestVariants");
        break;
      case LIBRARY:
        variants = getVariants(androidExtension, "getLibraryVariants", "getTestVariants");
        break;
      case INSTANT_APP_FEATURE:
        variants = getVariants(androidExtension, "getFeatureVariants", "getTestVariants");
        break;
      case ANDROID_TEST:
        variants = getVariants(androidExtension, "getTestVariants");
        break;
      default:
    }

    for (Object variant : variants) {
      GradleSourceSet sourceSet = convertVariantToGradleSourceSet(project, variant, false);
      if (sourceSet != null) {
        sourceSets.add(sourceSet);
      }
    }

    if (androidProjectType != AndroidProjectType.ANDROID_TEST) {
      for (Object variant : getVariants(androidExtension, "getUnitTestVariants")) {
        GradleSourceSet sourceSet = convertVariantToGradleSourceSet(project, variant, true);
        if (sourceSet != null) {
          sourceSets.add(sourceSet);
        }
      }
    }

    return sourceSets;

  }

  /**
   * Returns a list of variants extracted with the listed method names from the given
   * android extension.
   *
   * @param androidExtension AndroidExtension object from which the variants are to be extracted.
   * @param methodNames name of different methods to invoke to get all the variants.
   */
  private static List<Object> getVariants(Object androidExtension, String... methodNames) {
    List<Object> variants = new LinkedList<>();
    for (String methodName : methodNames) {
      try {
        variants.addAll(Utils.invokeMethod(androidExtension, methodName));
      } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
        // do nothing
      }
    }
    return variants;
  }

  /**
   * Returns a GradleSourceSet which has been populated with respective
   * Android build variant data.
   *
   * @param project Gradle project to populate GradleSourceSet properties
   * @param variant Android Build Variant object to populate GradleSourceSet properties
   * @param isUnitTest Indicates if the given variant is a unit test variant
   */
  private static GradleSourceSet convertVariantToGradleSourceSet(
      Project project,
      Object variant,
      boolean isUnitTest
  ) {

    try {

      DefaultGradleSourceSet gradleSourceSet = new DefaultGradleSourceSet();
      gradleSourceSet.setBuildTargetDependencies(new HashSet<>());

      gradleSourceSet.setGradleVersion(project.getGradle().getGradleVersion());
      gradleSourceSet.setProjectName(project.getName());
      String projectPath = project.getPath();
      gradleSourceSet.setProjectPath(projectPath);
      gradleSourceSet.setProjectDir(project.getProjectDir());
      gradleSourceSet.setRootDir(project.getRootDir());

      String variantName = Utils.invokeMethod(variant, "getName");
      gradleSourceSet.setSourceSetName(variantName);

      // classes task equivalent in android (assembleRelease)
      gradleSourceSet.setClassesTaskName(
          SourceSetUtils.getFullTaskName(projectPath, "assemble" + capitalize(variantName))
      );

      gradleSourceSet.setCleanTaskName(SourceSetUtils.getFullTaskName(projectPath, "clean"));

      // compile task in android (compileReleaseJavaWithJavac)
      HashSet<String> tasks = new HashSet<>();
      String compileTaskName = "compile" + capitalize(variantName) + "JavaWithJavac";
      tasks.add(SourceSetUtils.getFullTaskName(projectPath, compileTaskName));
      gradleSourceSet.setTaskNames(tasks);

      // module dependencies
      addModuleDependencies(gradleSourceSet, project, variant);

      // source and resource
      addSourceAndResources(gradleSourceSet, variant, isUnitTest);

      // resource outputs
      addResourceOutputs(gradleSourceSet, variant, isUnitTest);

      List<String> compilerArgs = new ArrayList<>();

      // generated sources and source outputs
      addGeneratedSourceAndSourceOutputs(gradleSourceSet, variant, compilerArgs);

      // classpath
      addClasspath(gradleSourceSet, variant);

      // Archive output dirs (not relevant in case of android build variants)
      gradleSourceSet.setArchiveOutputFiles(new HashMap<>());

      // has tests
      gradleSourceSet.setHasTests(hasProperty(variant, "testedVariant"));

      // extensions
      addExtensions(gradleSourceSet, compilerArgs);

      return gradleSourceSet;

    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
      // do nothing
    }

    return null;
  }

  /**
   * Add module dependencies to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param project Instance of Project
   * @param variant Instance of Build Variant
   */
  private static void addModuleDependencies(
      DefaultGradleSourceSet gradleSourceSet,
      Project project,
      Object variant
  ) {
    Set<GradleModuleDependency> moduleDependencies = new HashSet<>();

    try {
      // compile and runtime libraries
      Configuration compileConfiguration = getProperty(variant, "compileConfiguration");
      Configuration runtimeConfiguration = getProperty(variant, "runtimeConfiguration");

      List<Configuration> configs = new ArrayList<>();
      configs.add(compileConfiguration);
      configs.add(runtimeConfiguration);
      moduleDependencies.addAll(DependencyCollector.getModuleDependencies(project, configs));
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
      // do nothing
    }

    try {
      // add Android SDK
      Object androidComponents = getAndroidComponentExtension(project);
      if (androidComponents != null) {
        Object sdkComponents = getProperty(androidComponents, "sdkComponents");
        Object bootClasspath =
            ((Provider<?>) getProperty(sdkComponents, "bootclasspathProvider")).get();
        try {
          List<RegularFile> bootClasspathFiles = Utils.invokeMethod(bootClasspath, "get");
          List<File> sdkClasspath =
              bootClasspathFiles.stream().map(RegularFile::getAsFile).collect(Collectors.toList());
          for (File file : sdkClasspath) {
            moduleDependencies.add(mockModuleDependency(file.toURI()));
          }
        } catch (IllegalStateException | InvocationTargetException e) {
          // failed to retrieve android sdk classpath
          // do nothing
        }
      }
      // add R.jar file
      String taskName = "process" + capitalize(gradleSourceSet.getSourceSetName()) + "Resources";
      Task processResourcesTask = project.getTasks().findByName(taskName);
      if (processResourcesTask != null) {
        Object output = Utils.invokeMethod(processResourcesTask, "getRClassOutputJar");
        RegularFile file = Utils.invokeMethod(output, "get");
        File jarFile = file.getAsFile();
        if (jarFile.exists()) {
          moduleDependencies.add(mockModuleDependency(jarFile.toURI()));
        }
      }
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
      // do nothing
    }

    gradleSourceSet.setModuleDependencies(moduleDependencies);
  }

  /**
   * Add source and resource directories to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param variant Instance of Build Variant
   * @param isUnitTest Indicates if the given variant is a unit test variant
   */
  private static void addSourceAndResources(
      DefaultGradleSourceSet gradleSourceSet,
      Object variant,
      boolean isUnitTest
  ) {

    Set<File> sourceDirs = new HashSet<>();
    Set<File> resourceDirs = new HashSet<>();

    try {
      Object sourceSets = getProperty(variant, "sourceSets");
      if (sourceSets instanceof Iterable) {
        for (Object sourceSet : (Iterable<?>) sourceSets) {
          Set<File> javaDirectories = getProperty(sourceSet, "javaDirectories");
          sourceDirs.addAll(javaDirectories);
          if (!isUnitTest) {
            resourceDirs.addAll(getProperty(sourceSet, "resDirectories"));
          }
          resourceDirs.addAll(getProperty(sourceSet, "resourcesDirectories"));
        }
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      // do nothing
    }

    gradleSourceSet.setSourceDirs(sourceDirs);
    gradleSourceSet.setResourceDirs(resourceDirs);

  }

  /**
   * Add resource output directories to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param variant Instance of Build Variant
   * @param isUnitTest Indicates if the given variant is a unit test variant
   */
  private static void addResourceOutputs(
      DefaultGradleSourceSet gradleSourceSet,
      Object variant,
      boolean isUnitTest
  ) {

    Set<File> resourceOutputs = new HashSet<>();

    try {
      Provider<Task> resourceProvider = getProperty(variant, "processJavaResourcesProvider");
      if (resourceProvider != null) {
        Task resTask = resourceProvider.get();
        File outputDir = Utils.invokeMethod(resTask, "getDestinationDir");
        resourceOutputs.add(outputDir);
      }

      if (!isUnitTest) {
        Provider<Task> resProvider = getProperty(variant, "mergeResourcesProvider");
        if (resProvider != null) {
          Task resTask = resProvider.get();
          Object outputDir = Utils.invokeMethod(resTask, "getOutputDir");
          Provider<File> fileProvider = Utils.invokeMethod(outputDir, "getAsFile");
          File output = fileProvider.get();
          resourceOutputs.add(output);
        }
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      // do nothing
    }

    gradleSourceSet.setResourceOutputDirs(resourceOutputs);

  }

  /**
   * Add source output and generated source output directories to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param variant Instance of Build Variant
   * @param compilerArgs List to be populated from the java compiler arguments.
   */
  private static void addGeneratedSourceAndSourceOutputs(
      DefaultGradleSourceSet gradleSourceSet,
      Object variant,
      List<String> compilerArgs
  ) {

    Set<File> generatedSources = new HashSet<>();
    Set<File> sourceOutputs = new HashSet<>();

    try {
      Provider<Task> javaCompileProvider = getProperty(variant, "javaCompileProvider");
      if (javaCompileProvider != null) {
        Task javaCompileTask = javaCompileProvider.get();

        compilerArgs.addAll(
            JavaLanguageModelBuilder.getCompilerArgs((JavaCompile) javaCompileTask));

        File outputDir = Utils.invokeMethod(javaCompileTask, "getDestinationDir");
        sourceOutputs.add(outputDir);

        Object source = Utils.invokeMethod(javaCompileTask, "getSource");
        Set<File> compileSources = Utils.invokeMethod(source, "getFiles");

        // generated = compile source - source
        for (File compileSource : compileSources) {
          boolean inSourceDir = gradleSourceSet.getSourceDirs().stream()
              .anyMatch(dir -> compileSource.getAbsolutePath().startsWith(dir.getAbsolutePath()));
          if (inSourceDir) {
            continue;
          }
          boolean inGeneratedSourceDir = generatedSources.stream()
              .anyMatch(dir -> compileSource.getAbsolutePath().startsWith(dir.getAbsolutePath()));
          if (inGeneratedSourceDir) {
            continue;
          }
          generatedSources.add(compileSource);
        }
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      // do nothing
    }

    gradleSourceSet.setGeneratedSourceDirs(generatedSources);
    gradleSourceSet.setSourceOutputDirs(sourceOutputs);

  }

  /**
   * Add classpath files to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param variant Instance of Build Variant
   */
  private static void addClasspath(DefaultGradleSourceSet gradleSourceSet, Object variant) {

    Set<File> compileClasspathFiles = new HashSet<>();
    try {
      Object compileConfig = Utils.invokeMethod(variant, "getCompileConfiguration");
      compileClasspathFiles.addAll(Utils.invokeMethod(compileConfig, "getFiles"));
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      // do nothing
    }
    gradleSourceSet.setCompileClasspath(new LinkedList<>(compileClasspathFiles));

    gradleSourceSet.setRuntimeClasspath(new LinkedList<>());
  }

  /**
   * Add language extension to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param compilerArgs List of compiler arguments needed to build the language extension.
   */
  private static void addExtensions(
      DefaultGradleSourceSet gradleSourceSet,
      List<String> compilerArgs
  ) {
    Map<String, LanguageExtension> extensions = new HashMap<>();
    boolean isJavaSupported = Arrays.stream(SourceSetUtils.getSupportedLanguages())
        .anyMatch(l -> Objects.equals(l, SupportedLanguages.JAVA.getBspName()));
    if (isJavaSupported) {
      DefaultJavaExtension extension = new DefaultJavaExtension();

      extension.setCompilerArgs(compilerArgs);
      extension.setSourceCompatibility(
          JavaLanguageModelBuilder.getSourceCompatibility(compilerArgs));
      extension.setTargetCompatibility(
          JavaLanguageModelBuilder.getTargetCompatibility(compilerArgs));

      extensions.put(SupportedLanguages.JAVA.getBspName(), extension);
    }
    gradleSourceSet.setExtensions(extensions);
  }

  /**
   * Extracts the AndroidExtension from the given project.
   *
   * @param project Gradle project to extract the AndroidExtension object.
   */
  private static Object getAndroidExtension(Project project) {
    return getExtension(project, "android");
  }

  /**
   * Extracts the AndroidComponentsExtension from the given project.
   *
   * @param project Gradle project to extract the AndroidComponentsExtension object.
   */
  private static Object getAndroidComponentExtension(Project project) {
    return getExtension(project, "androidComponents");
  }

  /**
   * Extracts the given extension from the given project.
   *
   * @param project Gradle project to extract the extension object.
   * @param extensionName Name of the extension to extract.
   */
  private static Object getExtension(Project project, String extensionName) {
    Object extension = null;

    try {
      Object convention = Utils.invokeMethod(project, "getConvention");
      Object extensionMap = Utils.invokeMethod(convention, "getAsMap");
      extension = extensionMap.getClass()
          .getMethod("get", Object.class).invoke(extensionMap, extensionName);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      // do nothing
    }

    return extension;
  }

  /**
   * Returns the AndroidProjectType based on the plugin applied to the given project.
   *
   * @param project Gradle project to check for plugin and return the corresponding project type.
   */
  private static AndroidProjectType getProjectType(Project project) {

    if (getAndroidExtension(project) == null) {
      return null;
    }

    AndroidProjectType projectType = null;

    if (project.getPluginManager().hasPlugin("com.android.application")) {
      projectType = AndroidProjectType.APPLICATION;
    } else if (project.getPluginManager().hasPlugin("com.android.library")) {
      projectType = AndroidProjectType.LIBRARY;
    } else if (project.getPluginManager().hasPlugin("com.android.dynamic-feature")) {
      projectType = AndroidProjectType.DYNAMIC_FEATURE;
    } else if (project.getPluginManager().hasPlugin("com.android.feature")) {
      projectType = AndroidProjectType.INSTANT_APP_FEATURE;
    } else if (project.getPluginManager().hasPlugin("com.android.test")) {
      projectType = AndroidProjectType.ANDROID_TEST;
    }

    return projectType;

  }

  /**
   * Extracts the given property from the given object with {@code getProperty} method.
   *
   * @param obj object from which the property is to be extracted
   * @param propertyName name of the property to be extracted
   */
  public static <A> A getProperty(Object obj, String propertyName)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    return Utils.invokeMethod(obj, new Class<?>[] { String.class }, "getProperty",
      new Object[] { propertyName });
  }

  /**
   * Checks if the given property exists in the given object with {@code hasProperty} method.
   *
   * @param obj object from which the property is to be extracted
   * @param propertyName name of the property to be extracted
   */
  public static boolean hasProperty(Object obj, String propertyName)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    return Utils.invokeMethod(obj, new Class<?>[] { String.class }, "hasProperty",
      new Object[] { propertyName });
  }

  /**
   * Enum class representing different types of Android projects.
   */
  private enum AndroidProjectType {
    APPLICATION,
    LIBRARY,
    DYNAMIC_FEATURE,
    INSTANT_APP_FEATURE,
    ANDROID_TEST
  }

  /**
   * Returns the given string with its first letter capitalized.
   *
   * @param s String to capitalize
   */
  private static String capitalize(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  /**
   * Mocks GradleModuleDependency with a single artifact.
   *
   * @param jarUri Uri for the artifact to include in the ModuleDependency object.
   */
  private static GradleModuleDependency mockModuleDependency(URI jarUri) {

    final String unknown = "UNKNOWN";

    List<Artifact> artifacts = new LinkedList<>();
    artifacts.add(new DefaultArtifact(jarUri, null));

    return new DefaultGradleModuleDependency(
        unknown,
        unknown,
        unknown,
        artifacts
    );

  }
}
