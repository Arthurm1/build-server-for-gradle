// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core;

/**
 * Constants.
 */
public class Constants {
  /**
   * The name of the build Server.
   */
  public static final String SERVER_NAME = "gradle-build-server";
  /**
   * The version of the build server.
   */
  public static final String SERVER_VERSION = Constants.class.getPackage()
      .getImplementationVersion();
  /**
   * The version of the BSP specification.
   * Keep in sync with server:build.gradle dependency
   */
  public static final String BSP_VERSION = "2.2.0-M2";

  private Constants() {}
}
