// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.reporter;

import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.StartEvent;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.StatusCode;
import ch.epfl.scala.bsp4j.TaskFinishParams;
import ch.epfl.scala.bsp4j.TaskId;
import ch.epfl.scala.bsp4j.TaskProgressParams;
import ch.epfl.scala.bsp4j.TaskStartParams;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An implementation of {@link ProgressReporter} to report on main class output.
 */
public class AppRunReporter extends ProgressReporter implements Closeable {

  private final String taskName;
  private final SysOutOutputStream stdOut;
  private final SysErrOutputStream stdErr;

  /**
   * Instantiates a {@link AppRunReporter}.
   *
   * @param client BSP client to report to.
   * @param originId BSP client origin Id.
   * @param taskName the name of the task that runs the app.
   */
  public AppRunReporter(BuildClient client, String originId, String taskName) {
    super(client, originId);
    this.taskName = taskName;
    stdOut = new SysOutOutputStream(client, originId, taskId);
    stdErr = new SysErrOutputStream(client, originId, taskId);
  }

  public OutputStream getStdOut() {
    return stdOut;
  }

  public OutputStream getStdErr() {
    return stdErr;
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    if (client != null) {
      String taskPath = getTaskPath(event.getDescriptor());
      if (taskPath != null && taskPath.contains(taskName)) {
        TaskId taskId = getTaskId(taskPath);
        if (event instanceof StartEvent) {
          taskStarted(taskId, event.getDisplayName());
        } else if (event instanceof FinishEvent) {
          OperationResult result = ((FinishEvent) event).getResult();
          StatusCode status = result instanceof FailureResult ? StatusCode.ERROR : StatusCode.OK;
          taskFinished(taskId, event.getDisplayName(), status);
        } else {
          taskInProgress(taskId, event.getDisplayName());
        }
      }
    }
  }

  private void taskStarted(TaskId taskId, String message) {
    TaskStartParams startParam = new TaskStartParams(taskId);
    startParam.setEventTime(System.currentTimeMillis());
    startParam.setMessage(message);
    client.onBuildTaskStart(startParam);
  }

  private void taskInProgress(TaskId taskId, String message) {
    TaskProgressParams progressParam = new TaskProgressParams(taskId);
    progressParam.setEventTime(System.currentTimeMillis());
    progressParam.setMessage(message);
    client.onBuildTaskProgress(progressParam);
  }

  private void taskFinished(TaskId taskId, String message,
      StatusCode statusCode) {
    TaskFinishParams endParam = new TaskFinishParams(taskId, statusCode);
    endParam.setEventTime(System.currentTimeMillis());
    endParam.setMessage(message);
    client.onBuildTaskFinish(endParam);
  }

  @Override
  public void close() throws IOException {
    stdOut.close();
    stdErr.close();
  }
}
