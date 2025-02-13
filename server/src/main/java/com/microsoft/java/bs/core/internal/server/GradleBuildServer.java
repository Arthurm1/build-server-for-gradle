// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.server;

import static com.microsoft.java.bs.core.Launcher.LOGGER;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;

import ch.epfl.scala.bsp4j.JvmCompileClasspathParams;
import ch.epfl.scala.bsp4j.JvmCompileClasspathResult;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;

import com.microsoft.java.bs.core.internal.log.BspTraceEntity;
import com.microsoft.java.bs.core.internal.services.BuildTargetService;
import com.microsoft.java.bs.core.internal.services.LifecycleService;
import com.microsoft.java.bs.core.internal.utils.concurrent.CancellableFuture;

import ch.epfl.scala.bsp4j.BuildServer;
import ch.epfl.scala.bsp4j.CleanCacheParams;
import ch.epfl.scala.bsp4j.CleanCacheResult;
import ch.epfl.scala.bsp4j.CompileParams;
import ch.epfl.scala.bsp4j.CompileResult;
import ch.epfl.scala.bsp4j.DebugSessionAddress;
import ch.epfl.scala.bsp4j.DebugSessionParams;
import ch.epfl.scala.bsp4j.DependencyModulesParams;
import ch.epfl.scala.bsp4j.DependencyModulesResult;
import ch.epfl.scala.bsp4j.DependencySourcesParams;
import ch.epfl.scala.bsp4j.DependencySourcesResult;
import ch.epfl.scala.bsp4j.InitializeBuildParams;
import ch.epfl.scala.bsp4j.InitializeBuildResult;
import ch.epfl.scala.bsp4j.InverseSourcesParams;
import ch.epfl.scala.bsp4j.InverseSourcesResult;
import ch.epfl.scala.bsp4j.JavaBuildServer;
import ch.epfl.scala.bsp4j.JavacOptionsParams;
import ch.epfl.scala.bsp4j.JavacOptionsResult;
import ch.epfl.scala.bsp4j.JvmBuildServer;
import ch.epfl.scala.bsp4j.JvmRunEnvironmentParams;
import ch.epfl.scala.bsp4j.JvmRunEnvironmentResult;
import ch.epfl.scala.bsp4j.JvmTestEnvironmentParams;
import ch.epfl.scala.bsp4j.JvmTestEnvironmentResult;
import ch.epfl.scala.bsp4j.OutputPathsParams;
import ch.epfl.scala.bsp4j.OutputPathsResult;
import ch.epfl.scala.bsp4j.ReadParams;
import ch.epfl.scala.bsp4j.ResourcesParams;
import ch.epfl.scala.bsp4j.ResourcesResult;
import ch.epfl.scala.bsp4j.RunParams;
import ch.epfl.scala.bsp4j.RunResult;
import ch.epfl.scala.bsp4j.ScalaBuildServer;
import ch.epfl.scala.bsp4j.ScalaMainClassesItem;
import ch.epfl.scala.bsp4j.ScalaMainClassesParams;
import ch.epfl.scala.bsp4j.ScalaMainClassesResult;
import ch.epfl.scala.bsp4j.ScalaTestClassesItem;
import ch.epfl.scala.bsp4j.ScalaTestClassesParams;
import ch.epfl.scala.bsp4j.ScalaTestClassesResult;
import ch.epfl.scala.bsp4j.ScalacOptionsParams;
import ch.epfl.scala.bsp4j.ScalacOptionsResult;
import ch.epfl.scala.bsp4j.SourcesParams;
import ch.epfl.scala.bsp4j.SourcesResult;
import ch.epfl.scala.bsp4j.TestParams;
import ch.epfl.scala.bsp4j.TestResult;
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsResult;

/**
 * The implementation of the Build Server Protocol.
 */
public class GradleBuildServer implements BuildServer, JavaBuildServer, ScalaBuildServer,
    JvmBuildServer {

  private final LifecycleService lifecycleService;

  private final BuildTargetService buildTargetService;

  public GradleBuildServer(LifecycleService lifecycleService,
      BuildTargetService buildTargetService) {
    this.lifecycleService = lifecycleService;
    this.buildTargetService = buildTargetService;
  }

  @Override
  public CompletableFuture<InitializeBuildResult> buildInitialize(InitializeBuildParams params) {
    return handleRequest("build/initialize", cancelToken ->
        lifecycleService.initializeServer(params, cancelToken));
  }

  @Override
  public void onBuildInitialized() {
    handleNotification("build/initialized", lifecycleService::onBuildInitialized, true /*async*/);
  }

  @Override
  public CompletableFuture<Object> buildShutdown() {
    return handleRequest("build/shutdown", cancelToken ->
        lifecycleService.shutdown());
  }

  @Override
  public void onBuildExit() {
    handleNotification("build/exit", lifecycleService::exit, false /*async*/);
  }

  @Override
  public CompletableFuture<WorkspaceBuildTargetsResult> workspaceBuildTargets() {
    return handleRequest("workspace/buildTargets", cancelToken ->
        buildTargetService.getWorkspaceBuildTargets(cancelToken));
  }

  @Override
  public CompletableFuture<Object> workspaceReload() {
    return handleRequest("workspace/reload", cancelToken -> {
      buildTargetService.reloadWorkspace(cancelToken);
      return null;
    });
  }

  @Override
  public CompletableFuture<SourcesResult> buildTargetSources(SourcesParams params) {
    return handleRequest("buildTarget/sources", cancelToken ->
        buildTargetService.getBuildTargetSources(params, cancelToken));
  }

  @Override
  public CompletableFuture<InverseSourcesResult> buildTargetInverseSources(
      InverseSourcesParams params) {
    return handleRequest("buildTarget/inverseSources", cancelToken ->
        buildTargetService.getBuildTargetInverseSources(params, cancelToken));
  }

  @Override
  public CompletableFuture<DependencySourcesResult> buildTargetDependencySources(
      DependencySourcesParams params) {
    return handleRequest("buildTarget/dependencySources", cancelToken ->
        buildTargetService.getBuildTargetDependencySources(params, cancelToken));
  }

  @Override
  public CompletableFuture<ResourcesResult> buildTargetResources(ResourcesParams params) {
    return handleRequest("buildTarget/resources", cancelToken ->
        buildTargetService.getBuildTargetResources(params, cancelToken));
  }

  @Override
  public CompletableFuture<OutputPathsResult> buildTargetOutputPaths(OutputPathsParams params) {
    return handleRequest("buildTarget/outputPaths", cancelToken ->
        buildTargetService.getBuildTargetOutputPaths(params, cancelToken));
  }

  @Override
  public CompletableFuture<CompileResult> buildTargetCompile(CompileParams params) {
    return handleRequest("buildTarget/compile", cancelToken ->
       buildTargetService.compile(params, cancelToken));
  }

  @Override
  public CompletableFuture<JvmRunEnvironmentResult> buildTargetJvmRunEnvironment(
      JvmRunEnvironmentParams params) {
    return handleRequest("buildTarget/jvmRunEnvironment", cancelToken ->
        buildTargetService.getBuildTargetJvmRunEnvironment(params, cancelToken));
  }

  @Override
  public CompletableFuture<JvmTestEnvironmentResult> buildTargetJvmTestEnvironment(
      JvmTestEnvironmentParams params) {
    return handleRequest("buildTarget/jvmTestEnvironment", cancelToken ->
        buildTargetService.getBuildTargetJvmTestEnvironment(params, cancelToken));
  }

  @Override
  public CompletableFuture<JvmCompileClasspathResult> buildTargetJvmCompileClasspath(
      JvmCompileClasspathParams params) {
    return handleRequest("buildTarget/jvmCompileClasspath", cancelToken ->
        buildTargetService.getBuildTargetJvmCompileClasspath(params, cancelToken));
  }

  @Override
  public CompletableFuture<TestResult> buildTargetTest(TestParams params) {
    return handleRequest("buildTarget/test", cancelToken ->
       buildTargetService.buildTargetTest(params, cancelToken));
  }

  @Override
  public CompletableFuture<RunResult> buildTargetRun(RunParams params) {
    return handleRequest("buildTarget/run", cancelToken ->
       buildTargetService.buildTargetRun(params, cancelToken));
  }

  @Override
  public CompletableFuture<DebugSessionAddress> debugSessionStart(DebugSessionParams params) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'debugSessionStart'");
  }

  @Override
  public void onRunReadStdin(ReadParams params) {
    // TODO Auto-generated method stub
    // TODO how does BSP indicate which running main class or test should receive this?
    throw new UnsupportedOperationException("Unimplemented method 'onRunReadStdin'");
  }

  @Override
  public CompletableFuture<CleanCacheResult> buildTargetCleanCache(CleanCacheParams params) {
    return handleRequest("buildTarget/cleanCache", cancelToken ->
        buildTargetService.cleanCache(params, cancelToken));
  }

  @Override
  public CompletableFuture<DependencyModulesResult> buildTargetDependencyModules(
      DependencyModulesParams params) {
    return handleRequest("buildTarget/dependencyModules", cancelToken ->
        buildTargetService.getBuildTargetDependencyModules(params, cancelToken));
  }

  @Override
  public CompletableFuture<JavacOptionsResult> buildTargetJavacOptions(JavacOptionsParams params) {
    return handleRequest("buildTarget/javacOptions", cancelToken ->
        buildTargetService.getBuildTargetJavacOptions(params, cancelToken));
  }

  @Override
  public CompletableFuture<ScalacOptionsResult> buildTargetScalacOptions(
      ScalacOptionsParams params) {
    return handleRequest("buildTarget/scalacOptions", cancelToken ->
        buildTargetService.getBuildTargetScalacOptions(params, cancelToken));
  }

  @Override
  public CompletableFuture<ScalaTestClassesResult> buildTargetScalaTestClasses(
      ScalaTestClassesParams params) {
    // There is no `buildTargetScalaTestClassesProvider` flag for the client to
    // know if this is supported
    // Rather than sending exceptions back, just send an error message.
    LOGGER.warning("'buildTarget/ScalaTestClasses' not supported");
    List<ScalaTestClassesItem> items = new ArrayList<>();
    ScalaTestClassesResult result = new ScalaTestClassesResult(items);
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<ScalaMainClassesResult> buildTargetScalaMainClasses(
      ScalaMainClassesParams params) {
    // There is no `buildTargetScalaMainClassesProvider` flag for the client to
    // know if this is supported
    // Rather than sending exceptions back, just send an error message.
    LOGGER.warning("'buildTarget/ScalaMainClasses' not supported");
    List<ScalaMainClassesItem> items = new ArrayList<>();
    ScalaMainClassesResult result = new ScalaMainClassesResult(items);
    return CompletableFuture.completedFuture(result);
  }

  private void handleNotification(String methodName, Runnable runnable, boolean async) {
    BspTraceEntity entity = new BspTraceEntity.Builder()
        .operationName(escapeMethodName(methodName))
        .build();
    LOGGER.log(Level.FINE, "Received notification '" + methodName + "'.", entity);
    if (async) {
      CompletableFuture.runAsync(runnable);
    } else {
      runnable.run();
    }
  }

  private <R> CompletableFuture<R> handleRequest(String methodName,
      Function<CancellationToken, R> request) {
    long startTime = System.nanoTime();
    // create an empty future that will be completed further down
    CompletableFuture<CancellationToken> cancelTokenFuture = new CompletableFuture<>();
    // define async run request and handle errors
    CompletableFuture<R> result = cancelTokenFuture
        .thenApplyAsync(request)
        .thenApply(Either::<Throwable, R>forRight)
        .thenCompose(either -> {
          long elapsedTime = getElapsedTime(startTime);
          return either.isLeft()
            ? failure(methodName, either.getLeft(), elapsedTime)
            : success(methodName, either.getRight(), elapsedTime);
        });
    // create a Gradle cancellation token
    CancellationTokenSource cancelTokenSource = GradleConnector.newCancellationTokenSource();
    CancellationToken cancelToken = cancelTokenSource.token();
    // ensure Gradle is cancelled when the very last future in the chain is cancelled.
    Runnable cancel = () -> {
      cancelTokenSource.cancel();
      long elapsedTime = getElapsedTime(startTime);
      logCancelMessage(methodName, elapsedTime);
    };
    CancellableFuture<R> wrappedFuture = CancellableFuture.from(result, cancel);
    // start chain
    cancelTokenFuture.complete(cancelToken);
    return wrappedFuture;
  }

  private long getElapsedTime(long startTime) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
  }

  private BspTraceEntity.Builder createMessageBuilder(String methodName, long elapsedTime) {
    return new BspTraceEntity.Builder()
      .operationName(escapeMethodName(methodName))
      .duration(String.valueOf(elapsedTime));
  }

  private void logCancelMessage(String methodName, long elapsedTime) {
    BspTraceEntity entity = createMessageBuilder(methodName, elapsedTime).build();
    String message = String.format("Request cancelled '%s'. Processing request took %d ms.",
        methodName, elapsedTime);
    LOGGER.log(Level.INFO, message, entity);
  }

  private <T> CompletableFuture<T> success(String methodName, T response, long elapsedTime) {
    BspTraceEntity entity = createMessageBuilder(methodName, elapsedTime).build();
    String message = String.format("Sending response '%s'. Processing request took %d ms.",
        methodName, elapsedTime);
    LOGGER.log(Level.FINE, message, entity);
    return CompletableFuture.completedFuture(response);
  }

  private <T> CompletableFuture<T> failure(String methodName, Throwable throwable,
      long elapsedTime) {
    String stackTrace = ExceptionUtils.getStackTrace(throwable);
    Throwable rootCause = ExceptionUtils.getRootCause(throwable);
    String rootCauseMessage = rootCause != null ? rootCause.getMessage() : null;
    BspTraceEntity entity = createMessageBuilder(methodName, elapsedTime)
        .trace(stackTrace)
        .rootCauseMessage(rootCauseMessage)
        .build();
    String message = String.format("Failed to process '%s': %s", methodName, stackTrace);
    LOGGER.log(Level.SEVERE, message, entity);
    if (throwable instanceof ResponseErrorException) {
      return CompletableFuture.failedFuture(throwable);
    }
    return CompletableFuture.failedFuture(
        new ResponseErrorException(
            new ResponseError(ResponseErrorCode.InternalError,
            rootCauseMessage == null ? throwable.getMessage() : rootCauseMessage, null)));
  }

  private String escapeMethodName(String name) {
    return name.replace('/', '-');
  }
}
