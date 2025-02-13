// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.log;

import com.microsoft.java.bs.core.BuildInfo;

/**
 * The Object passed to the logger.
 */
public class BspTraceEntity {
  private final String kind;
  private final String schemaVersion;
  private final String buildServerVersion;
  private final String operationName;
  private final String duration;
  private final String trace;
  private final String rootCauseMessage;

  private BspTraceEntity(Builder builder) {
    this.kind = "bsptrace";
    this.schemaVersion = "1.0";
    this.buildServerVersion = BuildInfo.version;
    this.operationName = builder.operationName;
    this.duration = builder.duration;
    this.trace = builder.trace;
    this.rootCauseMessage = builder.rootCauseMessage;
  }

  /**
   * get the exception's root cause message.
   *
   * @return root cause message
   */
  public String getRootCauseMessage() {
    return rootCauseMessage;
  }

  /**
   * get the exception's stack trace.
   *
   * @return exception's stack trace
   */
  public String getTrace() {
    return trace;
  }

  /**
   * get the BSP's message request name.
   *
   * @return BSP's message request name.
   */
  public String getOperationName() {
    return operationName;
  }

  /**
   * get the build server version.
   *
   * @return build server version
   */
  public String getBuildServerVersion() {
    return buildServerVersion;
  }

  /**
   * get the request call's duration in milliseconds.
   *
   * @return the request call's duration in milliseconds
   */
  public String getDuration() {
    return duration;
  }

  /**
   * Builder.
   */
  public static class Builder {
    private String rootCauseMessage;
    private String trace;
    private String operationName;
    private String duration;

    /**
     * set the exception's root cause message.
     *
     * @param rootCauseMessage the exception's root cause message.
     * @return the builder
     */
    public Builder rootCauseMessage(String rootCauseMessage) {
      this.rootCauseMessage = rootCauseMessage;
      return this;
    }

    /**
     * set the exception's stack trace.
     *
     * @param trace exception's stack trace
     * @return the builder
     */
    public Builder trace(String trace) {
      this.trace = trace;
      return this;
    }

    /**
     * set the BSP's message request name..
     *
     * @param operationName the BSP's message request name.
     * @return the builder
     */
    public Builder operationName(String operationName) {
      this.operationName = operationName;
      return this;
    }

    /**
     * set the request call's duration in milliseconds.
     *
     * @param duration the request call's duration in milliseconds
     * @return the builder
     */
    public Builder duration(String duration) {
      this.duration = duration;
      return this;
    }

    /**
     * create the trace entity.
     *
     * @return the trace entity
     */
    public BspTraceEntity build() {
      return new BspTraceEntity(this);
    }
  }
}
