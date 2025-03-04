// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.LogMessageParams;
import ch.epfl.scala.bsp4j.MessageType;
import ch.epfl.scala.bsp4j.TaskId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.gradle.tooling.events.ProgressListener;

/**
 * An extension of {@link ProgressListener} that allows sending errors.
 */
public abstract class ProgressReporter implements ProgressListener {

  protected final BuildClient client;
  protected final String originId;
  protected final TaskId taskId;
  protected final List<String> taskIds;

  /**
   * Instantiates a {@link ProgressReporter}.
   *
   * @param client BSP client to report to.
   * @param originId client supplied originId.
   */
  public ProgressReporter(BuildClient client, String originId) {
    this.client = client;
    this.originId = originId;
    taskId = new TaskId(UUID.randomUUID().toString());
    taskIds = new ArrayList<>();
    taskIds.add(taskId.getId());
  }

  /**
   * Notify the client of an error.
   *
   * @param error the error message.
   */
  public void sendError(String error) {
    if (client != null) {
      LogMessageParams messageParam = new LogMessageParams(MessageType.ERROR, error);
      messageParam.setOriginId(originId);
      messageParam.setTask(taskId);
      client.onBuildLogMessage(messageParam);
    }
  }

  protected TaskId getTaskId(String taskPath) {
    TaskId taskId = new TaskId(taskPath == null ? "null" : taskPath);
    taskId.setParents(taskIds);
    return taskId;
  }
}
