// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.PrintParams;
import ch.epfl.scala.bsp4j.TaskId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Class to pass on system err from Gradle to Build Client.
 */
public class SysErrOutputStream extends OutputStream {

  private final BuildClient client;
  private final String originId;
  private final TaskId taskId;
  private final ByteArrayOutputStream out;

  /**
   * Instantiates a {@link SysErrOutputStream}.
   *
   * @param client BSP client to report to.
   * @param originId id of the BSP client message.
   * @param taskId taskId for the BSP request.
   */
  public SysErrOutputStream(BuildClient client, String originId, TaskId taskId) {
    super();
    this.client = client;
    this.originId = originId;
    this.taskId = taskId;
    out = new ByteArrayOutputStream();
  }

  private void sendErr(String str) {
    // for now send a log message
    if (client != null) {
      PrintParams params = new PrintParams(originId, str);
      params.setTask(taskId);
      client.onRunPrintStderr(params);
    }
  }

  @Override
  public void write(final byte[] buf, final int off, final int len) {
    out.write(buf, off, len);
    sendErr(out.toString());
    out.reset();
  }

  @Override
  public void write(final int integer) {
    out.write(integer);
    sendErr(out.toString());
    out.reset();
  }

  @Override
  public void close() throws IOException {
    out.close();
  }
}