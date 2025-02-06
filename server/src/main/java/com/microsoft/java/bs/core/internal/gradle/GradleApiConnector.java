// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.gradle;

import static com.microsoft.java.bs.core.Launcher.LOGGER;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSets;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;

import com.microsoft.java.bs.core.internal.managers.PreferenceManager;
import com.microsoft.java.bs.core.internal.model.GradleTestEntity;
import com.microsoft.java.bs.core.internal.reporter.AppRunReporter;
import com.microsoft.java.bs.core.internal.reporter.CompileProgressReporter;
import com.microsoft.java.bs.core.internal.reporter.DefaultProgressReporter;
import com.microsoft.java.bs.core.internal.reporter.ProgressReporter;
import com.microsoft.java.bs.core.internal.reporter.TestNameRecorder;
import com.microsoft.java.bs.core.internal.reporter.TestReportReporter;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.GradleTestTask;
import com.microsoft.java.bs.gradle.model.actions.GetSourceSetsAction;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.StatusCode;

/**
 * Connect to Gradle Daemon via Gradle Tooling API.
 */
public class GradleApiConnector {
  // cancellations especially can lead to concurrent calls to `connectors#computeIfAbsent`
  private final ConcurrentHashMap<File, GradleConnector> connectors;
  private final PreferenceManager preferenceManager;

  private static final String UNSUPPORTED_BUILD_ENVIRONMENT_MESSAGE =
      "Could not create an instance of Tooling API implementation "
      + "using the specified Gradle distribution";

  public GradleApiConnector(PreferenceManager preferenceManager) {
    this.preferenceManager = preferenceManager;
    connectors = new ConcurrentHashMap<>();
  }

  /**
   * Extracts the GradleVersion for the given project.
   *
   * @param projectUri URI of the project used to fetch the gradle version.
   * @return Gradle version of the project or empty string upon failure.
   */
  public String getGradleVersion(URI projectUri, CancellationToken cancellationToken) {
    try (ProjectConnection connection = getGradleConnector(projectUri).connect()) {
      return getBuildEnvironment(connection, cancellationToken).getGradle().getGradleVersion();
    } catch (BuildException e) {
      LOGGER.severe("Failed to get Gradle version: " + e.getMessage());
      return "";
    }
  }

  /**
   * Extracts the GradleVersion for the given project connection.
   *
   * @param connection ProjectConnection used to fetch the gradle version.
   * @return Gradle version of the project or empty string upon failure.
   */
  public String getGradleVersion(ProjectConnection connection,
      CancellationToken cancellationToken) {
    try {
      return getBuildEnvironment(connection, cancellationToken).getGradle().getGradleVersion();
    } catch (BuildException e) {
      LOGGER.severe("Failed to get Gradle version: " + e.getMessage());
      return "";
    }
  }

  /**
   * Extracts the BuildEnvironment model for the given project.
   *
   * @param projectUri URI of the project used to fetch the gradle java home.
   * @return BuildEnvironment of the project or {@code null} upon failure.
   */
  public BuildEnvironment getBuildEnvironment(URI projectUri,
      CancellationToken cancellationToken) {
    try (ProjectConnection connection = getGradleConnector(projectUri).connect()) {
      return getBuildEnvironment(connection, cancellationToken);
    } catch (BuildException e) {
      LOGGER.severe("Failed to get Build Environment: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extracts the BuildEnvironment model for the given project.
   *
   * @param connection ProjectConnection used to fetch the gradle version.
   * @return BuildEnvironment of the project.
   */
  private BuildEnvironment getBuildEnvironment(ProjectConnection connection,
      CancellationToken cancellationToken) {
    return Utils
        .getModelBuilder(connection, preferenceManager.getPreferences(), BuildEnvironment.class,
           cancellationToken)
        .get();
  }

  /**
   * Runs a probe build to check if the build fails due to java home incompatibility.
   *
   * @param projectUri URI of the project for which the check needs to be performed.
   * @return true if the given project has compatible java home, false otherwise.
   */
  public boolean checkCompatibilityWithProbeBuild(URI projectUri,
      CancellationToken cancellationToken) {
    try (ProjectConnection connection = getGradleConnector(projectUri).connect()) {
      ModelBuilder<GradleBuild> modelBuilder = Utils.setLauncherProperties(
          connection.model(GradleBuild.class), preferenceManager.getPreferences(),
          cancellationToken);
      modelBuilder.get();
      return true;
    } catch (BuildException e) {
      return hasUnsupportedBuildEnvironmentMessage(e);
    }
  }

  private boolean hasUnsupportedBuildEnvironmentMessage(BuildException e) {
    Set<Throwable> seen = new HashSet<>();
    Throwable current = e;
    while (current != null) {
      if (current.getMessage().contains(UNSUPPORTED_BUILD_ENVIRONMENT_MESSAGE)) {
        return true;
      }
      if (!seen.add(current)) {
        break;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * Get the source sets of the Gradle project.
   *
   * @param projectUri uri of the project
   * @param client     connection to BSP client
   * @return an instance of {@link GradleSourceSets}
   */
  public GradleSourceSets getGradleSourceSets(URI projectUri, BuildClient client,
      CancellationToken cancellationToken) {
    ProgressReporter reporter = new DefaultProgressReporter(client);
    ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
    try (ProjectConnection connection = getGradleConnector(projectUri).connect();
         errorOut) {
      File workspaceDir = new File(projectUri);
      String pluginInitScript = Utils.createPluginScript(workspaceDir,
          preferenceManager.getPreferences().getJavaSemanticdbVersion(),
          preferenceManager.getPreferences().getScalaSemanticdbVersion());
      File initScript = Utils.createInitScriptFile("sourcesets", pluginInitScript);
      try {
        BuildActionExecuter<GradleSourceSets> buildExecutor =
            Utils.getBuildActionExecuter(connection, preferenceManager.getPreferences(),
              new GetSourceSetsAction(), cancellationToken)
            .addProgressListener(reporter,
                OperationType.FILE_DOWNLOAD, OperationType.PROJECT_CONFIGURATION)
            .setStandardError(errorOut);
        if (Boolean.getBoolean("bsp.plugin.debug.enabled")) {
          buildExecutor.addJvmArguments(
              "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
        }
        if (initScript != null) {
          buildExecutor.addArguments("--init-script", initScript.getAbsolutePath());
        }
        buildExecutor.addJvmArguments("-Dbsp.gradle.supportedLanguages="
            + String.join(",", preferenceManager.getClientSupportedLanguages()));
        // since the model returned from Gradle TAPI is a wrapped object, here we re-construct it
        // via a copy constructor and return as a POJO.
        return new DefaultGradleSourceSets(buildExecutor.run());
      } finally {
        if (initScript != null) {
          initScript.delete();
        }
      }
    } catch (GradleConnectionException | IllegalStateException | IOException e) {
      String summary = e.getMessage();
      if (errorOut.size() > 0) {
        summary += "\n" + errorOut;
      }
      reporter.sendError(summary);
      throw new IllegalStateException(e);
    }
  }

  /**
   * Request Gradle daemon to run the tasks.
   *
   * @param projectUri uri of the project
   * @param reporter   reporter on feedback from Gradle
   * @param tasks      tasks to run
   */
  public StatusCode runTasks(URI projectUri, ProgressReporter reporter,
      String[] tasks, CancellationToken cancellationToken) {
    // Don't issue a start progress update - the listener will pick that up automatically
    final ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
    StatusCode statusCode = StatusCode.OK;
    try (ProjectConnection connection = getGradleConnector(projectUri).connect();
         errorOut
    ) {
      File workspaceDir = new File(projectUri);
      String compilerOptionsScript = Utils.createCompilerOptionsScript(workspaceDir,
              preferenceManager.getPreferences().getJavaSemanticdbVersion(),
              preferenceManager.getPreferences().getScalaSemanticdbVersion());
      File initScript = Utils.createInitScriptFile("runTask", compilerOptionsScript);
      try {
        BuildLauncher launcher = Utils.getBuildLauncher(connection,
            preferenceManager.getPreferences(), cancellationToken);
        if (initScript != null) {
          launcher.addArguments("--init-script", initScript.getAbsolutePath());
        }
        // TODO: consider to use outputstream to capture the output.
        launcher.addProgressListener(reporter, OperationType.TASK)
            .setStandardError(errorOut)
            .forTasks(tasks)
            .run();
      } finally {
        if (initScript != null) {
          initScript.delete();
        }
      }
    } catch (IOException e) {
      // caused by close the output stream, just simply log the error.
      LOGGER.severe(e.getMessage());
    } catch (BuildException e) {
      String summary = e.getMessage();
      if (errorOut.size() > 0) {
        summary += "\n" + errorOut;
      }
      reporter.sendError(summary);
      statusCode = StatusCode.ERROR;
    }

    return statusCode;
  }

  /**
   * request Gradle to run tests.
   */
  public StatusCode runTests(
      URI projectUri,
      Map<BuildTargetIdentifier, Map<String, Set<String>>> testClassesMethodsMap,
      List<String> jvmOptions,
      List<String> args,
      Map<String, String> envVars,
      BuildClient client, String originId,
      CompileProgressReporter compileProgressReporter,
      CancellationToken cancellationToken) {

    StatusCode statusCode = StatusCode.OK;
    ProgressReporter reporter = new DefaultProgressReporter(client);
    try (ProjectConnection connection = getGradleConnector(projectUri).connect()) {
      String gradleVersion = getGradleVersion(connection, cancellationToken);
      if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.6")) < 0) {
        reporter.sendError("Error running test classes: Gradle version "
            + gradleVersion + " must be >= 2.6");
      } else if (envVars != null && !envVars.isEmpty()
          && GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("3.5")) < 0) {
        reporter.sendError("Error running test classes With Environment Variables: Gradle version "
            + gradleVersion + " must be >= 3.5");
      } else {
        for (Map.Entry<BuildTargetIdentifier, Map<String, Set<String>>> entry :
            testClassesMethodsMap.entrySet()) {
          TestReportReporter testReportReporter = new TestReportReporter(entry.getKey(),
              client, originId);
          final ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
          try (errorOut) {
            File workspaceDir = new File(projectUri);
            String compilerOptionsScript = Utils.createCompilerOptionsScript(workspaceDir,
                    preferenceManager.getPreferences().getJavaSemanticdbVersion(),
                    preferenceManager.getPreferences().getScalaSemanticdbVersion());
            File initScript = Utils.createInitScriptFile("runTest", compilerOptionsScript);
            try {
              TestLauncher launcher = Utils
                  .getTestLauncher(connection,
                     preferenceManager.getPreferences(), cancellationToken)
                  .setStandardError(errorOut)
                  .addProgressListener(testReportReporter, OperationType.TEST);
              if (initScript != null) {
                launcher.addArguments("--init-script", initScript.getAbsolutePath());
              }
              if (compileProgressReporter != null) {
                launcher.addProgressListener(compileProgressReporter, OperationType.TASK);
              }
              for (Map.Entry<String, Set<String>> classesMethods : entry.getValue().entrySet()) {
                if (classesMethods.getValue() != null && !classesMethods.getValue().isEmpty()) {
                  launcher.withJvmTestMethods(classesMethods.getKey() + '*',
                      classesMethods.getValue());
                } else {
                  launcher.withJvmTestClasses(classesMethods.getKey() + '*');
                }
              }
              launcher.withArguments(args);
              launcher.setJvmArguments(jvmOptions);
              // env vars requires Gradle >= 3.5
              if (envVars != null) {
                // Running Gradle tests on Windows seems to require the `SystemRoot` env var
                // Otherwise Windows complains "Unrecognized Windows Sockets error: 10106"
                // Assumption is that current env vars plus specified env vars are all wanted.
                Map<String, String> allEnvVars = new HashMap<>(System.getenv());
                allEnvVars.putAll(envVars);
                launcher.setEnvironmentVariables(allEnvVars);
              }
              launcher.run();
            } finally {
              if (initScript != null) {
                initScript.delete();
              }
            }
          } catch (IOException e) {
            // caused by close the output stream, just simply log the error.
            LOGGER.severe(e.getMessage());
          } catch (GradleConnectionException | IllegalStateException e) {
            String message = String.join("\n", ExceptionUtils.getRootCauseStackTraceList(e));
            if (errorOut.size() > 0) {
              message = message + '\n' + errorOut;
            }
            testReportReporter.addException(message);
            statusCode = StatusCode.ERROR;
          } finally {
            testReportReporter.sendResult();
          }
        }
      }
    } catch (GradleConnectionException | IllegalStateException e) {
      reporter.sendError("Error running test classes: " + e.getMessage());
      statusCode = StatusCode.ERROR;
    }

    return statusCode;
  }

  /**
   * request Gradle to return test classes.
   */
  public Map<BuildTargetIdentifier, List<GradleTestEntity>> getTestClasses(URI projectUri,
      Map<BuildTargetIdentifier, Set<GradleTestTask>> testTaskMap, BuildClient client,
      CompileProgressReporter compileProgressReporter, CancellationToken cancellationToken) {
 
    Map<BuildTargetIdentifier, List<GradleTestEntity>> results = new HashMap<>();
    DefaultProgressReporter reporter = new DefaultProgressReporter(client);

    try (ProjectConnection connection = getGradleConnector(projectUri).connect()) {
      String gradleVersion = getGradleVersion(connection, cancellationToken);
      // use --test-dry-run to discover tests.  Gradle version must be 8.3 or higher.
      if (GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("8.3")) < 0) {
        reporter.sendError("Error searching for test classes: Gradle version "
            + gradleVersion + " must be >= 8.3");
      } else {
        for (Map.Entry<BuildTargetIdentifier, Set<GradleTestTask>> entry :
            testTaskMap.entrySet()) {
          List<GradleTestEntity> gradleTestEntities = new LinkedList<>();
          for (GradleTestTask gradleTestTask : entry.getValue()) {
            // task can trigger compilation so add compiler options as well as
            // script to alter test task to execute dry-run
            String testScript = Utils.createTestTaskScript(gradleTestTask.getTaskPath());
            File workspaceDir = new File(projectUri);
            String compilerOptionsScript = Utils.createCompilerOptionsScript(workspaceDir,
                preferenceManager.getPreferences().getJavaSemanticdbVersion(),
                preferenceManager.getPreferences().getScalaSemanticdbVersion());
            String script = testScript + '\n' + compilerOptionsScript;
            File initScript = Utils.createInitScriptFile("testTask", script);
            try {
              TestNameRecorder testNameRecorder = new TestNameRecorder();
              try {
                TestLauncher launcher = Utils
                    .getTestLauncher(connection,
                        preferenceManager.getPreferences(), cancellationToken)
                    .forTasks(gradleTestTask.getTaskPath())
                    .addArguments("--init-script", initScript.getAbsolutePath())
                    .addProgressListener(testNameRecorder, OperationType.TEST)
                    .addProgressListener(reporter, OperationType.TASK);
                if (compileProgressReporter != null) {
                  launcher.addProgressListener(compileProgressReporter, OperationType.TASK);
                }
                launcher.run();
              } catch (GradleConnectionException | IllegalStateException e) {
                String message = String.join("\n", ExceptionUtils.getRootCauseStackTraceList(e));
                reporter.sendError("Error searching for test classes in " 
                    + gradleTestTask.getTaskPath() + " " + message);
              }
              Set<String> mainClasses = testNameRecorder.getMainClasses();
              GradleTestEntity gradleTestEntity = new GradleTestEntity(gradleTestTask, mainClasses);
              gradleTestEntities.add(gradleTestEntity);
            } finally {
              if (initScript != null) {
                initScript.delete();
              }
            }
          }

          results.put(entry.getKey(), gradleTestEntities);
        }
      }
    } catch (GradleConnectionException | IllegalStateException e) {
      reporter.sendError("Error searching for test classes: " + e.getMessage());
    }

    return results;
  }

  /**
   * request Gradle to run main class.
   */
  public StatusCode runMainClass(URI projectUri, String projectPath, String sourceSetName,
      String className, Map<String, String> environmentVariables, List<String> jvmOptions,
      List<String> arguments, BuildClient client, String originId,
      CompileProgressReporter compileProgressReporter, CancellationToken cancellationToken) {

    StatusCode statusCode = StatusCode.OK;
    String taskName = "buildServerRunApp";
    try (AppRunReporter reporter = new AppRunReporter(client, originId, taskName)) {
      try (ProjectConnection connection = getGradleConnector(projectUri).connect()) {
        // task can trigger compilation so add compiler options as well as
        // script to create a JavaExec task to run the main class
        String execScript = Utils.createJavaExecTaskScript(projectPath, sourceSetName, taskName,
            className, arguments, environmentVariables, jvmOptions);
        File workspaceDir = new File(projectUri);
        String compilerOptionsScript = Utils.createCompilerOptionsScript(workspaceDir,
            preferenceManager.getPreferences().getJavaSemanticdbVersion(),
            preferenceManager.getPreferences().getScalaSemanticdbVersion());
        String script = execScript + '\n' + compilerOptionsScript;
        File initScript = Utils.createInitScriptFile("runMain", script);
        try {
          BuildLauncher launcher = Utils
              .getBuildLauncher(connection,
                  preferenceManager.getPreferences(), cancellationToken)
              .forTasks(taskName)
              // TODO this is needed to feedback the main class stdOut/Err but it will also feedback
              // Gradle stdOut/Err so reporter will report on any compile messages etc.
              // Unsure how to filter those out.
              .setStandardOutput(reporter.getStdOut())
              .setStandardError(reporter.getStdErr())
              // TODO BSP `run/readStdin` - how to link which running task gets which StdIn data?
              //    .setStandardInput(reporter.getStdIn())
              .addProgressListener(reporter, OperationType.TASK);
          if (initScript != null) {
            launcher.addArguments("--init-script", initScript.getAbsolutePath());
          }
          if (compileProgressReporter != null) {
            launcher.addProgressListener(compileProgressReporter, OperationType.TASK);
          }
          // run method is blocking
          launcher.run();
        } finally {
          if (initScript != null) {
            initScript.delete();
          }
        }
      } catch (GradleConnectionException | IllegalStateException e) {
        String message = String.join("\n", ExceptionUtils.getRootCauseStackTraceList(e));
        reporter.sendError("Error running main class: " + message);
        statusCode = StatusCode.ERROR;
      }
    } catch (IOException e) {
      // caused by close the output stream, just simply log the error.
      LOGGER.severe(e.getMessage());
    }

    return statusCode;
  }

  public void shutdown() {
    connectors.values().forEach(GradleConnector::disconnect);
  }

  private GradleConnector getGradleConnector(URI projectUri) {
    return getGradleConnector(new File(projectUri));
  }

  private GradleConnector getGradleConnector(File project) {
    return connectors.computeIfAbsent(project,
        p -> Utils.getProjectConnector(p, preferenceManager.getPreferences()));
  }
}
