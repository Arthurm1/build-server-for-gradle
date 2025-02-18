package com.microsoft.java.bs.core.internal.server;

import ch.epfl.scala.bsp4j.BuildClientCapabilities;
import ch.epfl.scala.bsp4j.InitializeBuildParams;
import ch.epfl.scala.bsp4j.InitializeBuildResult;
import ch.epfl.scala.bsp4j.MessageType;
import ch.epfl.scala.bsp4j.ShowMessageParams;
import com.microsoft.java.bs.core.internal.model.Preferences;
import com.microsoft.java.bs.core.internal.utils.JsonUtils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleServiceIntegrationTest extends IntegrationTest {

  private static InitializeBuildParams getInitializedBuildParamsWithJdk(
      String projectDir,
      String jdkVersion,
      String gradleJavaVersionPath
  ) {

    Preferences preferences = new Preferences();
    var jdks = new HashMap<String, String>();
    jdks.put(jdkVersion, "file:///tmp/nonexistent_file.txt");
    preferences.setJdks(jdks);
    preferences.setGradleJavaHome(gradleJavaVersionPath);

    InitializeBuildParams initParams = getInitializeBuildParams(projectDir);
    initParams.setData(preferences);
    return initParams;
  }

  private void withConnection(BiConsumer<TestClient, TestServer> consumer) {
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
        consumer.accept(client, testServer);
      } finally {
        testServer.buildShutdown().join();
        threadPool.shutdown();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error closing streams", e);
    }
  }

  @Test
  void testCompatibleDefaultJavaHomeProjectServer() {
    withConnection((client, server) -> {
      try {
        InitializeBuildParams initParams = getInitializeBuildParams("legacy-gradle");
        Preferences preferences = new Preferences();
        preferences.setSemanticdbVersion("1.0");
        preferences.setJavaSemanticdbVersion("2.0");
        initParams.setData(preferences);
        InitializeBuildResult initResult = server.buildInitialize(initParams).join();
        assertEquals("BSP-Preferences", initResult.getDataKind());
        assertNotNull(initResult.getData());
        Preferences resultPrefs = JsonUtils.toModel(initResult.getData(), Preferences.class);
        assertNotNull(resultPrefs);
        assertEquals("1.0", resultPrefs.getSemanticdbVersion());
        assertEquals("2.0", resultPrefs.getJavaSemanticdbVersion());

        // Wait for the configuration logics to complete
        synchronized (this) {
          wait(5000L);
        }

        assertEquals(0, client.showMessages.size());
      } catch (InterruptedException e) {
        throw new RuntimeException("Thread interrupted during wait.");
      }
    });
  }

  @Test
  void testCompatibleUserJavaHomeProjectServer() {
    withConnection((client, server) -> {
      InitializeBuildParams initParams =
          getInitializedBuildParamsWithJdk("Non-Existent Project", "1.8", null);

      server.buildInitialize(initParams).join();
      client.waitOnShowMessages(1);
      ShowMessageParams param = client.showMessages.get(0);
      assertEquals(MessageType.INFO, param.getType());
      assertTrue(param.getMessage()
          .startsWith("Default JDK wasn't compatible with current gradle version"));
      server.onBuildInitialized();
      client.clearMessages();
    });
  }

  @Test
  void testIncompatibleUserJavaHomeProjectServer() {
    withConnection((client, server) -> {
      InitializeBuildParams initParams =
          getInitializedBuildParamsWithJdk(
              "Non-Existent Project",
              "99.0.0",
              "file:///tmp/nonexistent_file.txt"
          );

      server.buildInitialize(initParams).join();
      client.waitOnShowMessages(1);
      ShowMessageParams param = client.showMessages.get(0);
      assertEquals(MessageType.ERROR, param.getType());
      assertTrue(param.getMessage()
          .startsWith("Failed to find a JDK compatible with current gradle version"));
      server.onBuildInitialized();
      client.clearMessages();
    });
  }

  @Test
  void testBuildInitialize() {
    withConnection((client, server) -> {
      List<String> languageIds = Collections.emptyList();
      BuildClientCapabilities capabilities = new BuildClientCapabilities(languageIds);
      Path testProject = getTestPath("legacy-gradle");
      InitializeBuildParams initParams = new InitializeBuildParams("Scala Steward",
          "0.28.0-75-cf26eca2-20250217-2201-SNAPSHOT", "2.1.1", testProject.toUri().toString(),
          capabilities);
      server.buildInitialize(initParams).join();
      client.waitOnShowMessages(1);
      ShowMessageParams param = client.showMessages.get(0);
      assertEquals(MessageType.INFO, param.getType());
      server.onBuildInitialized();
      client.clearMessages();
    });
  }
}
