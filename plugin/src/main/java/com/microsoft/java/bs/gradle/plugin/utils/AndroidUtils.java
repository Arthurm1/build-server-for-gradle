package com.microsoft.java.bs.gradle.plugin.utils;

import com.microsoft.java.bs.gradle.model.Artifact;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.impl.DefaultArtifact;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleModuleDependency;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSet;
import com.microsoft.java.bs.gradle.model.impl.DefaultKotlinExtension;
import com.microsoft.java.bs.gradle.plugin.JavaLanguageModelBuilder;
import com.microsoft.java.bs.gradle.plugin.SourceSetsModelBuilder;
import com.microsoft.java.bs.gradle.plugin.dependency.DependencyCollector;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for android related operations.
 * See https://developer.android.com/reference/tools/gradle-api for API
 */
public class AndroidUtils {

  private AndroidUtils() {
  }

  /**
   * Extracts build variants from the given Android project and converts
   * them into list of GradleSourceSets.
   *
   * @param project Gradle project for extracting the build variants
   */
  public static List<GradleSourceSet> getBuildVariantsAsGradleSourceSets(Project project) {

    Object androidExtension = getAndroidExtension(project);
    if (androidExtension == null) {
      return Collections.emptyList();
    }

    return Stream.of("getApplicationVariants",
            "getLibraryVariants",
            "getFeatureVariants",
            "getTestVariants",
            "getUnitTestVariants")
        .flatMap(name -> getVariant(androidExtension, name).stream())
        .map(variant -> convertVariantToGradleSourceSet(project, variant))
        .collect(Collectors.toList());
  }

  /**
   * Returns a list of variants extracted with the method name from the given android extension.
   *
   * @param androidExtension AndroidExtension object from which the variants are to be extracted.
   * @param methodName name of method to invoke to get all the variants.
   */
  private static List<Object> getVariant(Object androidExtension, String methodName) {
    Collection<Object> variants = Utils.invokeMethodIgnoreFail(androidExtension, methodName);
    if (variants != null) {
      return new LinkedList<>(variants);
    }
    return Collections.emptyList();
  }

  /**
   * Returns a GradleSourceSet which has been populated with respective
   * Android build variant data.
   *
   * @param project Gradle project to populate GradleSourceSet properties
   * @param variant Android Build Variant object to populate GradleSourceSet properties
   */
  private static GradleSourceSet convertVariantToGradleSourceSet(
      Project project,
      Object variant
  ) {

    DefaultGradleSourceSet gradleSourceSet = new DefaultGradleSourceSet();
    gradleSourceSet.setBuildTargetDependencies(new HashSet<>());

    gradleSourceSet.setGradleVersion(project.getGradle().getGradleVersion());
    gradleSourceSet.setProjectName(project.getName());
    String projectPath = project.getPath();
    gradleSourceSet.setProjectPath(projectPath);
    gradleSourceSet.setProjectDir(project.getProjectDir());
    gradleSourceSet.setRootDir(project.getRootDir());

    // variant class should be com.android.build.gradle.api.BaseVariant or child class
    String variantName = Utils.invokeMethod(variant, "getName");
    gradleSourceSet.setSourceSetName(variantName);

    // classes task equivalent in android (assembleRelease)
    Provider<Task> assembleTask = Utils.invokeMethod(variant, "getAssembleProvider");
    gradleSourceSet.setClassesTaskName(
        SourceSetUtils.getFullTaskName(projectPath, assembleTask.get().getName())
    );

    gradleSourceSet.setCleanTaskName(SourceSetUtils.getFullTaskName(projectPath, "clean"));

    // compile task in android (compileReleaseJavaWithJavac)
    HashSet<String> tasks = new HashSet<>();
    Provider<JavaCompile> javaCompileProvider =
        Utils.invokeMethod(variant, "getJavaCompileProvider");
    JavaCompile javaCompile = javaCompileProvider.get();
    tasks.add(SourceSetUtils.getFullTaskName(projectPath, javaCompile.getName()));
    gradleSourceSet.setTaskNames(tasks);

    // extensions
    Map<String, LanguageExtension> extensions = new HashMap<>();
    Set<File> javaSourceDirs = getDirs(variant, "getJavaDirectories");
    addJavaExtension(extensions, project, javaCompile, javaSourceDirs);
    Set<File> kotlinSourceDirs = getDirs(variant, "getKotlinDirectories");
    addKotlinExtension(extensions, kotlinSourceDirs);
    gradleSourceSet.setExtensions(extensions);

    // compile and runtime configurations
    Configuration compileConfig = Utils.invokeMethod(variant, "getCompileConfiguration");
    Configuration runtimeConfig = Utils.invokeMethod(variant, "getRuntimeConfiguration");

    // classpath
    addClasspath(gradleSourceSet, compileConfig, runtimeConfig, javaCompile);

    // module dependencies
    addModuleDependencies(gradleSourceSet, project, compileConfig, runtimeConfig);

    // source and resource
    addSourceAndResources(gradleSourceSet, variant);

    // resource outputs
    addResourceOutputs(gradleSourceSet, variant);

    // generated sources and source outputs
    addGeneratedSourceAndSourceOutputs(gradleSourceSet, extensions.values());

    // Archive output dirs. TODO
    // the `.aar` files produced by ArchiveTasks contain a `classes.jar` file.
    // the `classes.jar` inputs should tell us what project is being referenced
    // But can't figure out how to get hold of the task that produces the classes.jar
    // to find out the inputs. :shrug:
    // the Android tests don't contain any inter project dependencies
    gradleSourceSet.setArchiveOutputFiles(new HashMap<>());

    // tests
    gradleSourceSet.setTestTasks(SourceSetsModelBuilder.getTestTasks(project,
        gradleSourceSet.getSourceOutputDirs()));

    // run tasks
    gradleSourceSet.setRunTasks(SourceSetsModelBuilder.getRunTasks(project,
        gradleSourceSet.getRuntimeClasspath()));

    return gradleSourceSet;
  }

  /**
   * Add module dependencies to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param project Instance of Project
   * @param compileConfig Compile time configuration
   * @param runtimeConfig Runtime time configuration
   */
  private static void addModuleDependencies(DefaultGradleSourceSet gradleSourceSet,
      Project project, Configuration compileConfig, Configuration runtimeConfig
  ) {
    List<Configuration> configs = new ArrayList<>();
    configs.add(compileConfig);
    configs.add(runtimeConfig);
    Set<GradleModuleDependency> moduleDependencies = new HashSet<>(
        DependencyCollector.getModuleDependencies(project.getDependencies(), configs));

    // add Android SDK
    Object androidComponents = getAndroidComponentExtension(project);
    if (androidComponents != null) {
      Object sdkComponents = Utils.invokeMethod(androidComponents, "getSdkComponents");
      Provider<List<RegularFile>> bootClasspath =
          Utils.invokeMethod(sdkComponents, "getBootClasspath");
      List<RegularFile> bootClasspathFiles = bootClasspath.get();
      bootClasspathFiles.stream().map(RegularFile::getAsFile)
          .forEach(file -> moduleDependencies.add(mockModuleDependency(file)));
    }
    // add R.jar file
    String taskName = "process" + capitalize(gradleSourceSet.getSourceSetName()) + "Resources";
    Task processResourcesTask = Utils.taskByName(project, taskName);
    if (processResourcesTask != null) {
      Provider<RegularFile> outputProvider = Utils.invokeMethod(processResourcesTask,
          "getRClassOutputJar");
      if (outputProvider.isPresent()) {
        RegularFile file = outputProvider.get();
        File jarFile = file.getAsFile();
        if (jarFile.exists()) {
          moduleDependencies.add(mockModuleDependency(jarFile));
        }
      }
    }

    gradleSourceSet.setModuleDependencies(moduleDependencies);
  }

  /**
   * Get specific type of input dirs for the given GradleSourceSet.
   *
   * @param variant Instance of Build Variant
   * @param methodName name of method to return relevante dirs
   */
  private static Set<File> getDirs(
      Object variant,
      String methodName
  ) {
    Set<File> dirs = new HashSet<>();
    Object sourceSets = Utils.invokeMethod(variant, "getSourceSets");
    if (sourceSets instanceof Iterable) {
      for (Object sourceSet : (Iterable<?>) sourceSets) {
        dirs.addAll(Utils.invokeMethod(sourceSet, methodName));
      }
    }
    return dirs;
  }

  /**
   * Add source and resource directories to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param variant Instance of Build Variant
   */
  private static void addSourceAndResources(
      DefaultGradleSourceSet gradleSourceSet,
      Object variant
  ) {
    Set<File> sourceDirs = new HashSet<>();
    sourceDirs.addAll(getDirs(variant, "getJavaDirectories"));
    sourceDirs.addAll(getDirs(variant, "getKotlinDirectories"));
    gradleSourceSet.setSourceDirs(sourceDirs);
    Set<File> resourceDirs = new HashSet<>();
    resourceDirs.addAll(getDirs(variant, "getResDirectories"));
    resourceDirs.addAll(getDirs(variant, "getResourcesDirectories"));
    gradleSourceSet.setResourceDirs(resourceDirs);
  }

  /**
   * Add resource output directories to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param variant Instance of Build Variant
   */
  private static void addResourceOutputs(
      DefaultGradleSourceSet gradleSourceSet,
      Object variant
  ) {

    Set<File> resourceOutputs = new HashSet<>();

    Provider<Task> resourceProvider = Utils.invokeMethod(variant,
        "getProcessJavaResourcesProvider");
    if (resourceProvider != null) {
      Task resTask = resourceProvider.get();
      File outputDir = Utils.invokeMethod(resTask, "getDestinationDir");
      resourceOutputs.add(outputDir);
    }

    Provider<Task> resProvider;
    try {
      resProvider = Utils.invokeMethod(variant, "getMergeResourcesProvider");
    } catch (Exception e) {
      // mergeResourcesProvider isn't initialized on all variants
      resProvider = null;
    }
    if (resProvider != null) {
      Task resTask = resProvider.get();
      Provider<FileSystemLocation> outputDir = Utils.invokeMethod(resTask, "getOutputDir");
      File output = outputDir.get().getAsFile();
      resourceOutputs.add(output);
    }

    gradleSourceSet.setResourceOutputDirs(resourceOutputs);
  }

  /**
   * Add source output and generated source output directories to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   */
  private static void addGeneratedSourceAndSourceOutputs(
      DefaultGradleSourceSet gradleSourceSet,
      Collection<LanguageExtension> languageExtensions
  ) {
    Set<File> generatedSources = new HashSet<>();
    Set<File> sourceOutputs = new HashSet<>();

    for (LanguageExtension extension : languageExtensions) {
      if (extension.getClassesDir() != null) {
        sourceOutputs.add(extension.getClassesDir());
      }
      if (extension.getGeneratedSourceDirs() != null) {
        generatedSources.addAll(extension.getGeneratedSourceDirs());
      }
    }

    gradleSourceSet.setGeneratedSourceDirs(generatedSources);
    gradleSourceSet.setSourceOutputDirs(sourceOutputs);
  }

  /**
   * Add classpath files to the given GradleSourceSet.
   *
   * @param gradleSourceSet Instance of DefaultGradleSourceSet
   * @param compileConfig Compile time configuration
   * @param runtimeConfig Runtime time configuration
   */
  private static void addClasspath(DefaultGradleSourceSet gradleSourceSet,
       Configuration compileConfig, Configuration runtimeConfig, JavaCompile javaCompile) {

    List<File> compileClasspathFiles = new LinkedList<>();
    try {
      compileClasspathFiles.addAll(compileConfig.getFiles());
    } catch (Exception e1) {
      // attempt to get classpath through java compiler
      try {
        compileClasspathFiles.addAll(javaCompile.getClasspath().getFiles());
      } catch (Exception e2) {
        throw new IllegalStateException("Error getting compile path for "
            + gradleSourceSet.getProjectPath() + " "
            + gradleSourceSet.getSourceSetName() + " "
            + gradleSourceSet.getExtensions() + " "
            + compileConfig, e2);
      }
    }
    gradleSourceSet.setCompileClasspath(compileClasspathFiles);

    List<File> runtimeClasspathFiles = new LinkedList<>();
    try {
      runtimeClasspathFiles.addAll(runtimeConfig.getFiles());
    } catch (Exception e) {
      System.err.println("Ignoring error getting runtime path for " + runtimeConfig);
    }
    gradleSourceSet.setRuntimeClasspath(runtimeClasspathFiles);
  }

  /**
   * Add Java language extension to the given map.
   *
   * @param extensions map of language extensions to populate
   * @param project Gradle project
   * @param javaCompile Gradle's Java compile task
   * @param javaSourceDirs source dirs for java compilation
   */
  private static void addJavaExtension(
      Map<String, LanguageExtension> extensions,
      Project project,
      JavaCompile javaCompile,
      Set<File> javaSourceDirs
  ) {
    Set<String> languages = Arrays.stream(SourceSetUtils.getSupportedLanguages())
        .collect(Collectors.toSet());

    if (languages.contains(SupportedLanguages.JAVA.getBspName())) {
      JavaLanguageModelBuilder builder = new JavaLanguageModelBuilder();
      LanguageExtension extension = builder.getExtension(project, javaCompile, javaSourceDirs);
      extensions.put(SupportedLanguages.JAVA.getBspName(), extension);
    }
  }

  /**
   * Add Kotlin language extension to the given map.
   *
   * @param extensions map of language extensions to populate
   * @param kotlinSourceDirs source dirs for kotlin compilation
   */
  private static void addKotlinExtension(
      Map<String, LanguageExtension> extensions,
      Set<File> kotlinSourceDirs
  ) {
    Set<String> languages = Arrays.stream(SourceSetUtils.getSupportedLanguages())
        .collect(Collectors.toSet());

    if (languages.contains(SupportedLanguages.KOTLIN.getBspName())) {
      // TODO flesh this out once we know how to extract kotlin setup.
      DefaultKotlinExtension extension = new DefaultKotlinExtension();
      extension.setSourceDirs(kotlinSourceDirs);
      extensions.put(SupportedLanguages.KOTLIN.getBspName(), extension);
    }
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
   * @param file file for the artifact to include in the ModuleDependency object.
   */
  private static GradleModuleDependency mockModuleDependency(File file) {

    final String unknown = "UNKNOWN";

    List<Artifact> artifacts = new LinkedList<>();
    artifacts.add(new DefaultArtifact(file.toPath().toUri(), null));

    return new DefaultGradleModuleDependency(
        unknown,
        unknown,
        unknown,
        artifacts
    );

  }
}
