// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import com.microsoft.java.bs.core.internal.gradle.GradleApiConnector;
import com.microsoft.java.bs.core.internal.log.LogHandler;
import com.microsoft.java.bs.core.internal.log.TelemetryHandler;
import com.microsoft.java.bs.core.internal.managers.BuildTargetManager;
import com.microsoft.java.bs.core.internal.managers.PreferenceManager;
import com.microsoft.java.bs.core.internal.server.GradleBuildServer;
import com.microsoft.java.bs.core.internal.services.BuildTargetService;
import com.microsoft.java.bs.core.internal.services.LifecycleService;
import com.microsoft.java.bs.core.internal.transport.NamedPipeStream;

import ch.epfl.scala.bsp4j.BuildClient;
import org.apache.commons.lang3.StringUtils;

/**
 * Main entry point for the BSP server.
 */
public class Launcher {

  public static final Logger LOGGER = Logger.getLogger("GradleBuildServerLogger");

  /**
   * Main entry point.
   */
  public static void main(String[] args) {

    org.eclipse.lsp4j.jsonrpc.Launcher<BuildClient> launcher;
    Map<String, String> params = parseArgs(args);
    String pipePath = params.get("pipe");
    if (StringUtils.isNotBlank(pipePath)) {
      launcher = createLauncherUsingPipe(pipePath);
    } else {
      launcher = createLauncherUsingStdIo();
    }

    setupLoggers(launcher.getRemoteProxy());
    launcher.startListening();
  }

  private static PrintWriter getTracer() {
    String traceLocation = System.getProperty("bsp.gradle.traceMessagesFile");
    if (traceLocation != null) {
      Path tracePath = Paths.get(traceLocation);
      return getTracer(tracePath);
    }
    return null;
  }

  /**
   * Create a PrintWriter to log BSP messages to file.
   *
   * @param tracePath path of log file
   * @return writer to pass to Launcher or null if file creation failed.
   */
  public static PrintWriter getTracer(Path tracePath) {
    try {
      OutputStream outputStream = Files.newOutputStream(
          tracePath,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);

      return new PrintWriter(outputStream);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Error setting up trace file", e);
      return null;
    }
  }

  private static org.eclipse.lsp4j.jsonrpc.Launcher<BuildClient>
      createLauncherUsingPipe(String pipePath) {
    NamedPipeStream pipeStream = new NamedPipeStream(pipePath);
    try {
      return createLauncher(pipeStream.getOutputStream(), pipeStream.getInputStream());
    } catch (Exception e) {
      throw new IllegalStateException("Error initializing the named pipe", e);
    }
  }

  private static org.eclipse.lsp4j.jsonrpc.Launcher<BuildClient> createLauncherUsingStdIo() {
    return createLauncher(System.out, System.in);
  }

  private static org.eclipse.lsp4j.jsonrpc.Launcher<BuildClient> 
      createLauncher(OutputStream outputStream, InputStream inputStream) {
    return createLauncher(outputStream, inputStream, Executors.newCachedThreadPool(), getTracer());
  }

  /**
   * create a BSP Server Launcher.
   *
   * @param outputStream output stream to send outgoing messages
   * @param inputStream input stream to listen for incoming messages
   * @param threadPool the executor service used to start threads
   * @param tracer message tracer for all BSP messages.  Set to null for no message tracing.
   * @return new launcher
   */
  public static org.eclipse.lsp4j.jsonrpc.Launcher<BuildClient> createLauncher(
      OutputStream outputStream, InputStream inputStream, ExecutorService threadPool,
      PrintWriter tracer) {
    BuildTargetManager buildTargetManager = new BuildTargetManager();
    PreferenceManager preferenceManager = new PreferenceManager();
    GradleApiConnector connector = new GradleApiConnector(preferenceManager);
    LifecycleService lifecycleService = new LifecycleService(connector, preferenceManager);
    BuildTargetService buildTargetService = new BuildTargetService(buildTargetManager,
        connector, preferenceManager);
    GradleBuildServer gradleBuildServer = new GradleBuildServer(lifecycleService,
        buildTargetService);
    org.eclipse.lsp4j.jsonrpc.Launcher<BuildClient> launcher = new
        org.eclipse.lsp4j.jsonrpc.Launcher.Builder<BuildClient>()
        .setOutput(outputStream)
        .setInput(inputStream)
        .traceMessages(tracer)
        .setLocalService(gradleBuildServer)
        .setRemoteInterface(BuildClient.class)
        .setExecutorService(threadPool)
        .create();
    BuildClient client = launcher.getRemoteProxy();
    lifecycleService.setClient(client);
    buildTargetService.setClient(client);
    return launcher;
  }

  private static void setupLoggers(BuildClient client) {
    LOGGER.setUseParentHandlers(false);
    LogHandler logHandler = new LogHandler(client);
    logHandler.setLevel(Level.FINE);
    LOGGER.addHandler(logHandler);

    if (System.getProperty("disableServerTelemetry") == null) {
      TelemetryHandler telemetryHandler = new TelemetryHandler(client);
      telemetryHandler.setLevel(Level.INFO);
      LOGGER.addHandler(telemetryHandler);
    }
  }

  /**
   * Parse the arguments and return a map of key-value pairs.
   */
  public static Map<String, String> parseArgs(String[] args) {
    Map<String, String> paramMap = new HashMap<>();
    for (String arg : args) {
      if (arg.startsWith("--")) {
        int index = arg.indexOf('=');
        if (index != -1) {
          String key = arg.substring(2, index);
          String value = arg.substring(index + 1);
          paramMap.put(key, value);
        }
      }
    }
    return paramMap;
  }
}
