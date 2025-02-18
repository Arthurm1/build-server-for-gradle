package com.microsoft.java.bs.core.internal.server;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.BuildClientCapabilities;
import ch.epfl.scala.bsp4j.BuildServer;
import ch.epfl.scala.bsp4j.BuildServerCapabilities;
import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.BuildTarget;
import ch.epfl.scala.bsp4j.CompileReport;
import ch.epfl.scala.bsp4j.CompileTask;
import ch.epfl.scala.bsp4j.DidChangeBuildTarget;
import ch.epfl.scala.bsp4j.InitializeBuildParams;
import ch.epfl.scala.bsp4j.InitializeBuildResult;
import ch.epfl.scala.bsp4j.JavaBuildServer;
import ch.epfl.scala.bsp4j.JvmBuildServer;
import ch.epfl.scala.bsp4j.LogMessageParams;
import ch.epfl.scala.bsp4j.PrintParams;
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams;
import ch.epfl.scala.bsp4j.ShowMessageParams;
import ch.epfl.scala.bsp4j.StatusCode;
import ch.epfl.scala.bsp4j.TaskFinishParams;
import ch.epfl.scala.bsp4j.TaskProgressParams;
import ch.epfl.scala.bsp4j.TestReport;
import ch.epfl.scala.bsp4j.TaskStartParams;
import ch.epfl.scala.bsp4j.extended.TestFinishEx;
import ch.epfl.scala.bsp4j.extended.TestStartEx;
import com.microsoft.java.bs.core.Launcher;
import com.microsoft.java.bs.core.internal.utils.JsonUtils;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

abstract class IntegrationTest {

  protected interface TestServer extends BuildServer, JavaBuildServer, JvmBuildServer {
  }

  protected static class TestClient implements BuildClient {

    protected final List<TaskStartParams> startReports = new ArrayList<>();
    protected final List<TaskFinishParams> finishReports = new ArrayList<>();
    protected final List<CompileReport> compileReports = new ArrayList<>();
    protected final List<CompileTask> compileTasks = new ArrayList<>();
    protected final List<PrintParams> stdOut = new ArrayList<>();
    protected final List<PrintParams> stdErr = new ArrayList<>();
    protected final List<LogMessageParams> logMessages = new ArrayList<>();
    protected final List<ShowMessageParams> showMessages = new ArrayList<>();
    protected final List<TestReport> testReports = new ArrayList<>();
    protected final List<TestStartEx> testStarts = new ArrayList<>();
    protected final List<TestFinishEx> testFinishes = new ArrayList<>();
    protected final List<PublishDiagnosticsParams> diagnostics = new ArrayList<>();

    protected void clearMessages() {
      startReports.clear();
      finishReports.clear();
      compileReports.clear();
      compileTasks.clear();
      logMessages.clear();
      stdOut.clear();
      stdErr.clear();
      showMessages.clear();
      testReports.clear();
      testStarts.clear();
      testFinishes.clear();
      diagnostics.clear();
    }

    protected void waitOnStartReports(int size) {
      waitOnMessages("Start Reports", size, startReports::size);
    }

    protected void waitOnFinishReports(int size) {
      waitOnMessages("Finish Reports", size, finishReports::size);
    }

    protected void waitOnCompileReports(int size) {
      waitOnMessages("Compile Reports", size, compileReports::size);
    }

    protected void waitOnCompileTasks(int size) {
      waitOnMessages("Compile Tasks", size, compileTasks::size);
    }

    protected void waitOnLogMessages(int size) {
      waitOnMessages("Log Messages", size, logMessages::size);
    }

    protected void waitOnStdOut(int size) {
      waitOnMessages("Std Out", size, stdOut::size);
    }

    protected void waitOnStdErr(int size) {
      waitOnMessages("Std Err", size, stdErr::size);
    }

    protected void waitOnShowMessages(int size) {
      waitOnMessages("Show Messages", size, showMessages::size);
    }

    protected void waitOnTestReports(int size) {
      waitOnMessages("Test Reports", size, testReports::size);
    }

    protected void waitOnTestStarts(int size) {
      waitOnMessages("Test Starts", size, testStarts::size);
    }

    protected void waitOnTestFinishes(int size) {
      waitOnMessages("Test Finishes", size, testFinishes::size);
    }

    void waitOnDiagnostics(int size) {
      waitOnMessages("Diagnostics", size, diagnostics::size);
    }

    protected long finishReportErrorCount() {
      return finishReports.stream()
          .filter(report -> report.getStatus() == StatusCode.ERROR)
          .count();
    }

    private boolean waitOnSupplier(Supplier<Boolean> supplier) {
      // set to 5000ms because it seems reasonable
      long timeoutMs = 5000;
      long endTime = System.currentTimeMillis() + timeoutMs;
      while (!supplier.get() && System.currentTimeMillis() < endTime) {
        synchronized (this) {
          long waitTime = endTime - System.currentTimeMillis();
          if (waitTime > 0) {
            try {
              wait(waitTime);
            } catch (InterruptedException e) {
              // do nothing
            }
          }
        }
      }
      return supplier.get();
    }

    private void waitOnMessages(String errorMessage, int size, IntSupplier sizeSupplier) {
      waitOnSupplier(() -> sizeSupplier.getAsInt() >= size);
      assertEquals(size, sizeSupplier.getAsInt(), errorMessage + " count error");
    }

    @Override
    public void onBuildShowMessage(ShowMessageParams params) {
      showMessages.add(params);
      synchronized (this) {
        notify();
      }
    }

    @Override
    public void onBuildLogMessage(LogMessageParams params) {
      logMessages.add(params);
      synchronized (this) {
        notify();
      }
    }

    @Override
    public void onBuildTaskStart(TaskStartParams params) {
      if (params.getDataKind() != null) {
        if (params.getDataKind().equals("compile-task")) {
          compileTasks.add(JsonUtils.toModel(params.getData(), CompileTask.class));
        } else if (params.getDataKind().equals("test-start")) {
          testStarts.add(JsonUtils.toModel(params.getData(), TestStartEx.class));
        } else {
          fail("Task Start kind not handled " + params.getDataKind());
        }
      }
      startReports.add(params);
      synchronized (this) {
        notify();
      }
    }

    @Override
    public void onBuildTaskProgress(TaskProgressParams params) {
      // do nothing
    }

    @Override
    public void onBuildTaskFinish(TaskFinishParams params) {
      if (params.getDataKind() != null) {
        if (params.getDataKind().equals("compile-report")) {
          compileReports.add(JsonUtils.toModel(params.getData(), CompileReport.class));
        } else if (params.getDataKind().equals("test-report")) {
          testReports.add(JsonUtils.toModel(params.getData(), TestReport.class));
        } else if (params.getDataKind().equals("test-finish")) {
          testFinishes.add(JsonUtils.toModel(params.getData(), TestFinishEx.class));
        } else {
          fail("Task Finish kind not handled " + params.getDataKind());
        }
      }
      finishReports.add(params);
      synchronized (this) {
        notify();
      }
    }

    @Override
    public void onBuildPublishDiagnostics(PublishDiagnosticsParams params) {
      diagnostics.add(params);
      synchronized (this) {
        notify();
      }
    }

    @Override
    public void onBuildTargetDidChange(DidChangeBuildTarget params) {
      // do nothing
    }

    @Override
    public void onRunPrintStdout(PrintParams params) {
      System.out.print(params.getMessage());
      stdOut.add(params);
      synchronized (this) {
        notify();
      }
    }

    @Override
    public void onRunPrintStderr(PrintParams params) {
      System.err.print(params.getMessage());
      stdErr.add(params);
      synchronized (this) {
        notify();
      }
    }
  }

  @BeforeAll
  static void beforeClass() {
    System.setProperty("bsp.plugin.reloadworkspace.disabled", "true");
  }

  @AfterAll
  static void afterClass() {
    System.clearProperty("bsp.plugin.reloadworkspace.disabled");
  }

  protected static Path getTestPath(String projectDir) {
    return Paths.get(
        System.getProperty("user.dir"),
        "..",
        "testProjects",
        projectDir);
  }

  protected static InitializeBuildParams getInitializeBuildParams(String projectDir) {
    BuildClientCapabilities capabilities =
        new BuildClientCapabilities(SupportedLanguages.allBspNames);
    return new InitializeBuildParams(
        "test-client",
        "0.1.0",
        "0.1.0",
        getTestPath(projectDir).toUri().toString(),
        capabilities);
  }

  protected static Pair<TestClient, TestServer> setupClientServer(
      PipedInputStream clientIn,
      PipedOutputStream clientOut,
      PipedInputStream serverIn,
      PipedOutputStream serverOut,
      ExecutorService threadPool
  ) {
    // server
    // set `tracer` to `new PrintWriter(System.out)` for JSON message logging.
    // or use `Launcher.getTracer(Path)` to log to file.
    PrintWriter tracer = null;
    org.eclipse.lsp4j.jsonrpc.Launcher<BuildClient> serverLauncher =
        Launcher.createLauncher(serverOut, serverIn, threadPool, tracer);
    // client
    TestClient client = new TestClient();
    org.eclipse.lsp4j.jsonrpc.Launcher<TestServer> clientLauncher =
        new org.eclipse.lsp4j.jsonrpc.Launcher.Builder<TestServer>()
            .setLocalService(client)
            .setRemoteInterface(TestServer.class)
            .setInput(clientIn)
            .setOutput(clientOut)
            .setExecutorService(threadPool)
            .create();
    // start
    clientLauncher.startListening();
    serverLauncher.startListening();
    TestServer testServer = clientLauncher.getRemoteProxy();
    return Pair.of(client, testServer);
  }

  protected static void withNewTestServer(
      String project,
      BiConsumer<TestServer, TestClient> consumer
  ) {
    ExecutorService threadPool = Executors.newCachedThreadPool();
    try (PipedInputStream clientIn = new PipedInputStream();
         PipedOutputStream clientOut = new PipedOutputStream();
         PipedInputStream serverIn = new PipedInputStream();
         PipedOutputStream serverOut = new PipedOutputStream()) {
      try {
        clientIn.connect(serverOut);
        clientOut.connect(serverIn);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot setup streams", e);
      }
      var pair = setupClientServer(clientIn, clientOut, serverIn, serverOut, threadPool);
      TestClient client = pair.getLeft();
      TestServer testServer = pair.getRight();
      try {
        InitializeBuildParams params = getInitializeBuildParams(project);
        InitializeBuildResult result = testServer.buildInitialize(params).join();
        BuildServerCapabilities capabilities = result.getCapabilities();
        assertFalse(capabilities.getCompileProvider().getLanguageIds().isEmpty());
        assertFalse(capabilities.getTestProvider().getLanguageIds().isEmpty());
        assertFalse(capabilities.getRunProvider().getLanguageIds().isEmpty());
        assertNull(capabilities.getDebugProvider());
        assertTrue(capabilities.getInverseSourcesProvider());
        assertTrue(capabilities.getDependencySourcesProvider());
        assertTrue(capabilities.getDependencyModulesProvider());
        assertTrue(capabilities.getResourcesProvider());
        assertTrue(capabilities.getOutputPathsProvider());
        assertTrue(capabilities.getBuildTargetChangedProvider());
        assertTrue(capabilities.getJvmRunEnvironmentProvider());
        assertTrue(capabilities.getJvmTestEnvironmentProvider());
        assertFalse(capabilities.getCargoFeaturesProvider());
        assertTrue(capabilities.getCanReload());
        assertTrue(capabilities.getJvmCompileClasspathProvider());
        testServer.onBuildInitialized();
        consumer.accept(testServer, client);
      } finally {
        try {
          testServer.buildShutdown().get(10000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
          // do nothing
        }
        threadPool.shutdown();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error closing streams", e);
    }
  }

  protected static BuildTargetIdentifier findTarget(
      List<BuildTarget> targets,
      String displayName
  ) {
    Optional<BuildTarget> matchingTargets = targets.stream()
        .filter(res -> displayName.equals(res.getDisplayName()))
        .findAny();
    assertFalse(matchingTargets.isEmpty(), () -> {
      List<String> targetNames = targets.stream()
          .map(BuildTarget::getDisplayName)
          .collect(Collectors.toList());
      return "Target " + displayName + " not found in " + targetNames;
    });
    return matchingTargets.get().getId();
  }
}
