// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.gradle;

import com.microsoft.java.bs.core.BuildInfo;
import com.microsoft.java.bs.core.internal.managers.PreferenceManager;
import com.microsoft.java.bs.core.internal.model.Preferences;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.ConfigurableLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.TestLauncher;
import org.gradle.util.GradleVersion;

/**
 * Gradle Tooling API utils.
 */
public class Utils {
  private Utils() {}

  /**
   * The environment variable for Gradle home.
   */
  private static final String GRADLE_HOME = "GRADLE_HOME";

  /**
   * The environment variable for Gradle user home.
   */
  private static final String GRADLE_USER_HOME = "GRADLE_USER_HOME";

  /**
   * Is the OS Windows.
   */
  public static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  /**
   * What is the platform dependent name for the Java executable.
   */
  public static String getJavaExeName() {
    return isWindows() ? "java.exe" : "java";
  }

  /**
   * Returns the gradle project path without the initial {@code :}.
   *
   * @param projectPath project path to operate upon
   */
  private static String stripPathPrefix(String projectPath) {
    if (projectPath != null && projectPath.startsWith(":")) {
      return projectPath.substring(1);
    }
    return projectPath;
  }

  /**
   * Create a Build target display name of the format `projectName [sourceSetName]`.
   *
   * @param sourceSet Gradle source set
   * @return display name for build target
   */
  private static String bracketDisplayNaming(GradleSourceSet sourceSet) {
    return displayNaming(sourceSet,
        (projectName, sourceSetName) -> projectName + " [" + sourceSetName + ']');
  }

  /**
   * Create a Build target display name of the format `projectName` + delimiter + `sourceSetName`.
   *
   * @param sourceSet Gradle source set
   * @param delimiter delimiter
   * @return display name for build target
   */
  private static String delimiterDisplayNaming(GradleSourceSet sourceSet, char delimiter) {
    return displayNaming(sourceSet,
        (projectName, sourceSetName) -> projectName + delimiter + sourceSetName);
  }

  /**
   * Create a Build target display name of the format `projectName.sourceSetName`.
   *
   * @param sourceSet Gradle source set
   * @return display name for build target
   */
  private static String dotDisplayNaming(GradleSourceSet sourceSet) {
    return delimiterDisplayNaming(sourceSet, '.');
  }

  /**
   * Create a Build target display name of the format `projectName-sourceSetName`.
   *
   * @param sourceSet Gradle source set
   * @return display name for build target
   */
  private static String dashDisplayNaming(GradleSourceSet sourceSet) {
    return delimiterDisplayNaming(sourceSet, '-');
  }

  /**
   * Create a Build target display name of the format `projectName sourceSetName`.
   *
   * @param sourceSet Gradle source set
   * @return display name for build target
   */
  private static String spaceDisplayNaming(GradleSourceSet sourceSet) {
    return delimiterDisplayNaming(sourceSet, ' ');
  }

  /**
   * Create a Build target display name based on the function passed.
   *
   * @param converter function to convert project name + sourceset name to display name
   * @return display name for build target
   */
  private static String displayNaming(GradleSourceSet sourceSet,
      BiFunction<String, String, String> converter) {
    String projectName = stripPathPrefix(sourceSet.getProjectPath());
    if (projectName == null || projectName.isEmpty()) {
      projectName = sourceSet.getProjectName();
    }
    String sourceSetName = sourceSet.getSourceSetName();
    String displayName = converter.apply(projectName, sourceSetName);
    return displayName.replace(":", " ");
  }

  /**
   * Create a function that creates a display name for a build target based on the BSP client.
   *
   * @param displayNaming display naming type
   * @return function to create a display name
   */
  public static Function<GradleSourceSet, String> getDisplayNameMaker(String displayNaming) {
    if (Preferences.DOT_DISPLAY_NAMING.equals(displayNaming)) {
      return Utils::dotDisplayNaming;
    } else if (Preferences.DASH_DISPLAY_NAMING.equals(displayNaming)) {
      return Utils::dashDisplayNaming;
    } else if (Preferences.SPACE_DISPLAY_NAMING.equals(displayNaming)) {
      return Utils::spaceDisplayNaming;
    } else {
      return Utils::bracketDisplayNaming;
    }
  }

  /**
   * Create a function that creates a display name for a build target based on the BSP client.
   *
   * @param preferences BSP client preferences
   * @return function to create a display name
   */
  public static Function<GradleSourceSet, String> getDisplayNameMaker(Preferences preferences) {
    return getDisplayNameMaker(preferences.getDisplayNaming());
  }

  /**
   * Get the Gradle connector for the project.
   *
   * @param project The project.
   * @param preferences User preferences.
   * @return a connector to the Gradle API
   */
  public static GradleConnector getProjectConnector(File project, Preferences preferences) {
    GradleConnector connector = GradleConnector.newConnector()
        .forProjectDirectory(project);

    File gradleUserHome = getGradleUserHomeFile(preferences.getGradleUserHome());
    if (gradleUserHome != null && gradleUserHome.exists()) {
      connector.useGradleUserHomeDir(gradleUserHome);
    }

    switch (getEffectiveBuildKind(project, preferences)) {
      case SPECIFIED_VERSION:
        connector.useGradleVersion(preferences.getGradleVersion());
        break;
      case SPECIFIED_INSTALLATION:
        connector.useInstallation(getGradleHome(preferences.getGradleHome()));
        break;
      default:
        connector.useBuildDistribution();
        break;
    }

    return connector;
  }

  /**
   * Get the build action executer for the given project connection.
   *
   * @param <T> the result type
   * @param connection The project connection.
   * @param preferences The preferences.
   * @param action The build action.
   * @param cancellationToken Gradle cancellation token.
   * @return a build action executer for the Gradle API
   */
  public static <T> BuildActionExecuter<T> getBuildActionExecuter(ProjectConnection connection,
      Preferences preferences, BuildAction<T> action, CancellationToken cancellationToken) {
    return setLauncherProperties(connection.action(action), preferences, cancellationToken);
  }

  /**
   * Get the model builder for the given project connection.
   *
   * @param <T> the result type
   * @param connection The project connection.
   * @param preferences The preferences.
   * @param clazz The model class.
   * @param cancellationToken Gradle cancellation token.
   * @return a model builder for the Gradle API
   */
  public static <T> ModelBuilder<T> getModelBuilder(ProjectConnection connection,
      Preferences preferences, Class<T> clazz, CancellationToken cancellationToken) {
    return setLauncherProperties(connection.model(clazz), preferences, cancellationToken);
  }

  /**
   * Get the Build Launcher.
   *
   * @param connection The project connection.
   * @param preferences The preferences.
   * @param cancellationToken Gradle cancellation token.
   * @return a build launcher for the Gradle API
   */
  public static BuildLauncher getBuildLauncher(ProjectConnection connection,
      Preferences preferences, CancellationToken cancellationToken) {
    return setLauncherProperties(connection.newBuild(), preferences, cancellationToken);
  }

  /**
   * Get the Test Launcher.
   *
   * @param connection The project connection.
   * @param preferences The preferences.
   * @param cancellationToken Gradle cancellation token.
   * @return a test launcher for the Gradle API
   */
  public static TestLauncher getTestLauncher(ProjectConnection connection,
      Preferences preferences, CancellationToken cancellationToken) {
    return setLauncherProperties(connection.newTestLauncher(), preferences, cancellationToken);
  }

  /**
   * Set the Launcher properties.
   *
   * @param <T> the type of launcher
   * @param launcher The launcher.
   * @param preferences The preferences.
   * @param cancellationToken Gradle cancellation token.
   * @return the launcher passed in
   */
  public static <T extends ConfigurableLauncher<T>> T setLauncherProperties(T launcher,
      Preferences preferences, CancellationToken cancellationToken) {

    File gradleJavaHomeFile = getGradleJavaHomeFile(preferences.getGradleJavaHome());
    if (gradleJavaHomeFile != null && gradleJavaHomeFile.exists()) {
      launcher.setJavaHome(gradleJavaHomeFile);
    }

    List<String> gradleJvmArguments = preferences.getGradleJvmArguments();
    if (gradleJvmArguments != null && !gradleJvmArguments.isEmpty()) {
      launcher.setJvmArguments(gradleJvmArguments);
    }

    List<String> gradleArguments = preferences.getGradleArguments();
    if (gradleArguments != null && !gradleArguments.isEmpty()) {
      launcher.withArguments(gradleArguments);
    }

    if (cancellationToken != null) {
      launcher.withCancellationToken(cancellationToken);
    }
    return launcher;
  }

  /**
   * Get the latest compatible Java version for the current Gradle version, according
   * to <a href="https://docs.gradle.org/current/userguide/compatibility.html">
   * compatibility matrix</a>
   *
   * <p>If a compatible Java versions is not found, an empty string will be returned.
   *
   * @param gradleVersion the gradle version in String form
   * @return the latest compatible java version in String form
   */
  public static String getLatestCompatibleJavaVersion(String gradleVersion) {
    GradleVersion version = GradleVersion.version(gradleVersion);
    if (version.compareTo(GradleVersion.version("8.10")) >= 0) {
      return "23";
    } else if (version.compareTo(GradleVersion.version("8.8")) >= 0) {
      return "22";
    } else if (version.compareTo(GradleVersion.version("8.5")) >= 0) {
      return "21";
    } else if (version.compareTo(GradleVersion.version("8.3")) >= 0) {
      return "20";
    } else if (version.compareTo(GradleVersion.version("7.6")) >= 0) {
      return "19";
    } else if (version.compareTo(GradleVersion.version("7.5")) >= 0) {
      return "18";
    } else if (version.compareTo(GradleVersion.version("7.3")) >= 0) {
      return "17";
    } else if (version.compareTo(GradleVersion.version("7.0")) >= 0) {
      return "16";
    } else if (version.compareTo(GradleVersion.version("6.7")) >= 0) {
      return "15";
    } else if (version.compareTo(GradleVersion.version("6.3")) >= 0) {
      return "14";
    } else if (version.compareTo(GradleVersion.version("6.0")) >= 0) {
      return "13";
    } else if (version.compareTo(GradleVersion.version("5.4")) >= 0) {
      return "12";
    } else if (version.compareTo(GradleVersion.version("5.0")) >= 0) {
      return "11";
    } else if (version.compareTo(GradleVersion.version("4.7")) >= 0) {
      return "10";
    } else if (version.compareTo(GradleVersion.version("4.3")) >= 0) {
      return "9";
    } else if (version.compareTo(GradleVersion.version("2.0")) >= 0) {
      return "1.8";
    }

    return "";
  }

  /**
   * Get the oldest compatible Java version for the current Gradle version.
   *
   * @return the oldest supported Java version
   */
  public static String getOldestCompatibleJavaVersion() {
    return "1.8";
  }

  static File getGradleUserHomeFile(String gradleUserHome) {
    if (StringUtils.isNotBlank(gradleUserHome)) {
      return new File(gradleUserHome);
    }

    return getFileFromEnvOrProperty(GRADLE_USER_HOME);
  }

  /**
   * Infer the Gradle Home.
   * TODO: simplify the method.
   */
  static File getGradleHome(String gradleHome) {
    File gradleHomeFolder = null;
    if (StringUtils.isNotBlank(gradleHome)) {
      gradleHomeFolder = new File(gradleHome);
    } else {
      // find if there is a gradle executable in PATH
      String path = System.getenv("PATH");
      if (StringUtils.isNotBlank(path)) {
        for (String p : path.split(File.pathSeparator)) {
          File gradle = new File(p, "gradle");
          if (gradle.exists() && gradle.isFile()) {
            File gradleBinFolder = gradle.getParentFile();
            if (gradleBinFolder != null && gradleBinFolder.isDirectory()
                && gradleBinFolder.getName().equals("bin")) {
              File gradleLibFolder = new File(gradleBinFolder.getParent(), "lib");
              if (gradleLibFolder.isDirectory()) {
                File[] files = gradleLibFolder.listFiles();
                if (files != null) {
                  Optional<File> gradleLauncherJar = Arrays.stream(files)
                      .filter(file -> file.isFile() && file.getName().startsWith("gradle-launcher-")
                          && file.getName().endsWith(".jar"))
                      .findFirst();
                  if (gradleLauncherJar.isPresent()) {
                    gradleHomeFolder = gradleBinFolder.getParentFile();
                    break;
                  }
                }
              }
            }
          }
        }
      }
    }

    if (gradleHomeFolder == null) {
      gradleHomeFolder = getFileFromEnvOrProperty(GRADLE_HOME);
    }

    if (gradleHomeFolder != null && gradleHomeFolder.isDirectory()) {
      return gradleHomeFolder;
    }

    return null;
  }

  /**
   * return a collection as a String, limited to a number of entries.
   *
   * @param <T> type of collection entry
   * @param collection collection
   * @param maxEntries maximum array items to include in the String
   * @return String version of array
   */
  public static <T> String collectionAsStr(Collection<T> collection, int maxEntries) {
    if (collection == null) {
      return "null";
    }

    if (collection.isEmpty()) {
      return "[]";
    }

    StringBuilder sb = new StringBuilder();
    sb.append('[');
    int idx = 0;
    Iterator<T> iter = collection.iterator();
    while(iter.hasNext() && idx < maxEntries) {
      if (idx > 0) {
        sb.append(", ");
      }
      sb.append(iter.next());
      idx++;
    }
    if (iter.hasNext()) {
      sb.append("... and ");
      sb.append(collection.size() - maxEntries);
      sb.append(" more");
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * return an array as a String, limited to a number of entries.
   *
   * @param <T> type of array entry
   * @param array array of data
   * @param maxEntries maximum array items to include in the String
   * @return String version of array
   */
  public static <T> String arrayAsStr(T[] array, int maxEntries) {
    if (array == null) {
      return "null";
    }

    int length = Math.min(array.length, maxEntries);
    if (length == -1) {
      return "[]";
    }

    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int idx = 0; idx < length; idx++) {
      if (idx > 0) {
        sb.append(", ");
      }
      sb.append(array[idx]);
    }
    if (length < array.length) {
      sb.append("... and ");
      sb.append(array.length - length);
      sb.append(" more");
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * Get the path specified by the key from environment variables or system properties.
   * If the path is not empty, an <code>File</code> instance will be returned.
   * Otherwise, <code>null</code> will be returned.
   */
  static File getFileFromEnvOrProperty(String key) {
    String value = System.getenv().get(key);
    if (StringUtils.isBlank(value)) {
      value = System.getProperties().getProperty(key);
    }
    if (StringUtils.isNotBlank(value)) {
      return new File(value);
    }

    return null;
  }

  static File getGradleJavaHomeFile(String gradleJavaHome) {
    if (StringUtils.isNotBlank(gradleJavaHome)) {
      File file = new File(gradleJavaHome);
      if (file.isDirectory()) {
        return file;
      }
    }
    return null;
  }

  /**
   * Get the effective Gradle build kind according to the preferences.
   *
   * @param projectRoot Root path of the project.
   * @param preferences The preferences.
   * @return the build kind
   */
  public static GradleBuildKind getEffectiveBuildKind(File projectRoot, Preferences preferences) {
    if (preferences.isWrapperEnabled()) {
      File wrapperProperties = Paths.get(projectRoot.getAbsolutePath(), "gradle", "wrapper",
          "gradle-wrapper.properties").toFile();
      if (wrapperProperties.exists()) {
        return GradleBuildKind.WRAPPER;
      }
    }

    if (StringUtils.isNotBlank(preferences.getGradleVersion())) {
      return GradleBuildKind.SPECIFIED_VERSION;
    }

    File gradleHome = getGradleHome(preferences.getGradleHome());
    if (gradleHome != null && gradleHome.exists()) {
      return GradleBuildKind.SPECIFIED_INSTALLATION;
    }

    return GradleBuildKind.TAPI;
  }

  /**
   * Create a temporary init.gradle script.  If contents is null then file isn't created.
   * Caller is responsible for file deletion.
   *
   * @param prefix filename prefix
   * @param contents contents of script.
   * @return the init.gradle file or null if contents are empty.
   */
  public static File createInitScriptFile(String prefix, String contents) {
    if (contents == null || contents.isEmpty()) {
      return null;
    }
    try {
      File initScriptFile = File.createTempFile(prefix, ".gradle");
      if (!initScriptFile.getParentFile().exists()) {
        initScriptFile.getParentFile().mkdirs();
      }
      Files.write(initScriptFile.toPath(), contents.getBytes());
      return initScriptFile;
    } catch (IOException e) {
      throw new IllegalStateException("Error creating init file", e);
    }
  }

  /**
   * create a script for changing Gradle test tasks to execute a dry run.
   *
   * @return init script contents to setup dry run
   */
  public static String createTestTaskScript() {
    // can't pass arguments to tasks e.g. "--test-dry-run"
    // so manipulate test tasks using init script.
    // dryRun supported >= 8.3
    // don't specify by task name here as number of tasks can get huge and the script won't compile
    return """
        gradle.afterProject { p ->
          p.tasks.withType(Test.class).configureEach {
            dryRun = true
          }
        }""";
  }

  /**
   * create a script for creating a Gradle JavaExec task to run a Java mainclass.
   *
   * @param projectPath path to Gradle project
   * @param sourceSetName source set of project - for classpath
   * @param taskName new task name (must be unique to the project)
   * @param className java class to run
   * @param arguments arguments to pass to main class
   * @param environmentVariables additional environment variables to set
   * @param jvmOptions jvm options for running main class
   * @return init script contents to create task
   */
  public static String createJavaExecTaskScript(String projectPath,
        String sourceSetName, String taskName, String className,
        Collection<String> arguments, Map<String, String> environmentVariables,
        Collection<String> jvmOptions) {

    String argsSetup;
    if (arguments != null && !arguments.isEmpty()) {
      String args = arguments.stream().collect(Collectors.joining("','", "['", "']"));
      argsSetup = "      args = " + args;
    } else {
      argsSetup = "";
    }

    String envVarSetup;
    if (environmentVariables != null && !environmentVariables.isEmpty()) {
      String envVars = environmentVariables.entrySet().stream()
              .map(entry -> "'" + entry.getKey() + "':'" + entry.getValue() + "'")
              .collect(Collectors.joining(",", "[", "]"));
      envVarSetup = "      environment = " + envVars;
    } else {
      envVarSetup = "";
    }

    String jvmOptionSetup;
    if (jvmOptions != null && !jvmOptions.isEmpty()) {
      String jvmArgs = jvmOptions.stream().collect(Collectors.joining("','", "['", "']"));
      jvmOptionSetup = "      jvmArgs = " + jvmArgs;
    } else {
      jvmOptionSetup = "";
    }
    return """
        gradle.projectsEvaluated {
          Project proj = rootProject.findProject('$projectPath')
          if (proj != null) {
            proj.getTasks().create('$taskName', JavaExec.class, {
              classpath = proj.sourceSets.$sourceSetName.runtimeClasspath
              mainClass = '$className'
              $argsSetup
              $envVarSetup
              $jvmOptionSetup
            })
          }
        }"""
        .replace("$projectPath", projectPath)
        .replace("$taskName", taskName)
        .replace("$sourceSetName", sourceSetName)
        .replace("$className", className)
        .replace("$argsSetup", argsSetup)
        .replace("$envVarSetup", envVarSetup)
        .replace("$jvmOptionSetup", jvmOptionSetup);
  }

  /**
   * Create a Gradle init script to apply the BSP plugin to all projects.
   *
   * @param workspaceDir the root dir of all the projects
   * @param javaSemanticDbVersion version of the java semanticdb jar
   * @param scalaSemanticDbVersion version of the scala semanticdb jar
   * @param kotlinSemanticDbVersion version of the kotlin semanticdb jar
   * @return the text for an init script to apply the BSP plugin.
   */
  public static String createPluginScript(File workspaceDir, String javaSemanticDbVersion,
      String scalaSemanticDbVersion, String kotlinSemanticDbVersion) {
    return createInitScript(workspaceDir, javaSemanticDbVersion, scalaSemanticDbVersion,
        kotlinSemanticDbVersion, true);
  }

  /**
   * Create a Gradle init script to alter the compiler options to
   * include the semantic db plugins.
   *
   * @param workspaceDir the root dir of all the projects
   * @param preferenceManager preference manager
   * @return the text for an init script to alter the compiler options.
   */
  public static String createCompilerOptionsScript(File workspaceDir,
        PreferenceManager preferenceManager) {
    return createInitScript(workspaceDir,
        preferenceManager.getPreferences().getJavaSemanticdbVersion(),
        preferenceManager.getPreferences().getScalaSemanticdbVersion(),
        preferenceManager.getPreferences().getKotlinSemanticdbVersion(), false);
  }

  /**
   * Create a Gradle init script to alter thecompiler options to
   * include the semantic db plugins.
   *
   * @param workspaceDir the root dir of all the projects
   * @param javaSemanticDbVersion version of the java semanticdb jar
   * @param scalaSemanticDbVersion version of the scala semanticdb jar
   * @param kotlinSemanticDbVersion version of the kotlin semanticdb jar
   * @return the text for an init script to alter the compiler options.
   */
  public static String createCompilerOptionsScript(File workspaceDir, String javaSemanticDbVersion,
      String scalaSemanticDbVersion, String kotlinSemanticDbVersion) {
    return createInitScript(workspaceDir, javaSemanticDbVersion, scalaSemanticDbVersion,
        kotlinSemanticDbVersion, false);
  }

  /**
   * Create a Gradle init script to apply plugins and settings.
   *
   * @param workspaceDir the root dir of all the projects
   * @param javaSemanticDbVersion version of the java semanticdb jar
   * @param scalaSemanticDbVersion version of the scala semanticdb jar
   * @param kotlinSemanticDbVersion version of the kotlin semanticdb jar
   * @param includeBspPlugin whether to apply the BSP plugin
   * @return the text for the init script.
   */
  private static String createInitScript(File workspaceDir, String javaSemanticDbVersion,
        String scalaSemanticDbVersion, String kotlinSemanticDbVersion, boolean includeBspPlugin) {
    if (javaSemanticDbVersion == null && scalaSemanticDbVersion == null
        && kotlinSemanticDbVersion == null && !includeBspPlugin) {
      return null;
    }
    String bspPluginSetup = getBspPluginSetup(includeBspPlugin);
    String semanticDbPluginSetup = getSemanticDbPluginSetup(workspaceDir, javaSemanticDbVersion,
        scalaSemanticDbVersion, kotlinSemanticDbVersion);

    return """
        initscript {
          repositories {
            mavenLocal() // included so tests run and users can publish their own version
            mavenCentral()
            maven {
              url = 'https://repo.gradle.org/gradle/libs-releases'
            }
          }

          dependencies {
            classpath '$group:$artifact:$version'
          }
        }
        allprojects { proj ->
          $bspPluginSetup

          $semanticDbPluginSetup
        }"""
        .replace("$group", BuildInfo.groupId)
        .replace("$artifact", BuildInfo.pluginArtifactId)
        .replace("$version", BuildInfo.version)
        .replace("$bspPluginSetup", bspPluginSetup)
        .replace("$semanticDbPluginSetup", semanticDbPluginSetup);
  }

  private static String getBspPluginSetup(boolean includeBspPlugin) {
    if (includeBspPlugin) {
      return """
          // apply plugin so config can be extracted
          apply plugin: com.microsoft.java.bs.gradle.plugin.GradleBuildServerPlugin
          """;
    }
    return "";
  }

  private static String getSemanticDbPluginSetup(File workspaceDir,
      String javaSemanticDbVersion, String scalaSemanticDbVersion,
      String kotlinSemanticDbVersion) {
    String javaSemanticDbSetup = javaSemanticDbVersion != null
        && !javaSemanticDbVersion.isEmpty()
        ? "    javaSemanticDbVersion = '" + javaSemanticDbVersion + "'\n" : "";
    String scalaSemanticDbSetup = scalaSemanticDbVersion != null
        && !scalaSemanticDbVersion.isEmpty()
        ? "    scalaSemanticDbVersion = '" + scalaSemanticDbVersion + "'\n" : "";
    String kotlinSemanticDbSetup = kotlinSemanticDbVersion != null
        && !kotlinSemanticDbVersion.isEmpty()
        ? "    kotlinSemanticDbVersion = '" + kotlinSemanticDbVersion + "'\n" : "";
    if (!javaSemanticDbSetup.isEmpty() || !scalaSemanticDbSetup.isEmpty()
        || !kotlinSemanticDbSetup.isEmpty()) {
      String workspace = workspaceDir.toString().replace("\\", "\\\\").replace("'", "\\'");
      return """
          // apply plugin to setup semanticdb info
          apply plugin: com.microsoft.java.bs.gradle.plugin.MetalsBspPlugin

          MetalsBspPlugin {
            sourceRoot = file('$workspace')
            $javaSemanticDbSetup
            $scalaSemanticDbSetup
            $kotlinSemanticDbSetup
          }
        """
        .replace("$workspace", workspace)
        .replace("$javaSemanticDbSetup", javaSemanticDbSetup)
        .replace("$scalaSemanticDbSetup", scalaSemanticDbSetup)
        .replace("$kotlinSemanticDbSetup", kotlinSemanticDbSetup);
    }
    return "";
  }
}
