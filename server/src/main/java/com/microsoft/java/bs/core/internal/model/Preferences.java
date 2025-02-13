// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.model;

import java.util.List;
import java.util.Map;

/**
 * The preferences sent from 'build/initialize' request.
 */
public class Preferences {

  /**
   * The location to the JVM used to run the Gradle daemon.
   */
  private String gradleJavaHome;

  /**
   * Whether to use Gradle from the 'gradle-wrapper.properties' file.
   */
  private boolean isWrapperEnabled;

  /**
   * Use Gradle from the specific version if the Gradle wrapper is missing or disabled.
   */
  private String gradleVersion;

  /**
   * Use Gradle from the specified local installation directory or GRADLE_HOME if the
   * Gradle wrapper is missing or disabled and no 'gradleVersion' is blank.
   */
  private String gradleHome;

  /**
   * Setting for GRADLE_USER_HOME.
   */
  private String gradleUserHome;

  /**
   * The arguments to pass to the Gradle daemon.
   */
  private List<String> gradleArguments;

  /**
   * The JVM arguments to pass to the Gradle daemon.
   */
  private List<String> gradleJvmArguments;

  /**
   * A map of the JDKs on the machine. The key is the major JDK version,
   * for example: "1.8", "17", etc. The value is the installation path of the
   * JDK. When this preference is available, the Build Server will find the
   * most matched JDK to launch the Gradle daemon according to the Gradle version.
   * See: https://docs.gradle.org/current/userguide/compatibility.html#java
   */
  private Map<String, String> jdks;

  /**
   * Java semanticdb plugin version to use.
   */
  private String javaSemanticdbVersion;

  /**
   * Scala semanticdb plugin version to use.
   */
  private String semanticdbVersion;

  /**
   * Initialize the preferences.
   */
  public Preferences() {
    isWrapperEnabled = true;
  }

  /**
   * get the home dir of the JVM Gradle is running under.
   *
   * @return Gradle Java Home
   */
  public String getGradleJavaHome() {
    return gradleJavaHome;
  }

  /**
   * set the home dir of the JVM Gradle is running under.
   *
   * @param gradleJavaHome Gradle Java Home
   */
  public void setGradleJavaHome(String gradleJavaHome) {
    this.gradleJavaHome = gradleJavaHome;
  }

  /**
   * is the Gradle wrapper used.
   *
   * @return flag indicating Gradle wrapper is being used
   */
  public boolean isWrapperEnabled() {
    return isWrapperEnabled;
  }

  /**
   * set whether the gradle wrapper is to be used.
   *
   * @param isWrapperEnabled flag indicating Gradle wrapper is being used
   */
  public void setWrapperEnabled(boolean isWrapperEnabled) {
    this.isWrapperEnabled = isWrapperEnabled;
  }

  /**
   * get the version of Gradle to use.
   *
   * @return gradle version
   */
  public String getGradleVersion() {
    return gradleVersion;
  }

  /**
   * set the version of Gradle to use.
   *
   * @param gradleVersion gradle version
   */
  public void setGradleVersion(String gradleVersion) {
    this.gradleVersion = gradleVersion;
  }

  /**
   * get the Gradle installation directory.
   *
   * @return gradle installation dir
   */
  public String getGradleHome() {
    return gradleHome;
  }

  /**
   * set the Gradle installation directory.
   *
   * @param gradleHome gradle installation dir
   */
  public void setGradleHome(String gradleHome) {
    this.gradleHome = gradleHome;
  }

  /**
   * get the Gradle user home directory.
   *
   * @return Gradle user home directory
   */
  public String getGradleUserHome() {
    return gradleUserHome;
  }

  /**
   * set the Gradle user home directory.
   *
   * @param gradleUserHome Gradle user home directory
   */
  public void setGradleUserHome(String gradleUserHome) {
    this.gradleUserHome = gradleUserHome;
  }

  /**
   * get the arguments to pass to the gradle launcher API.
   *
   * @return Gradle arguments for launchers
   */
  public List<String> getGradleArguments() {
    return gradleArguments;
  }

  /**
   * set the arguments to pass to the gradle launcher API.
   *
   * @param gradleArguments Gradle arguments for launchers
   */
  public void setGradleArguments(List<String> gradleArguments) {
    this.gradleArguments = gradleArguments;
  }

  /**
   * get the Gradle JVM arguments to pass to the Gradle launcher API.
   *
   * @return Gradle JVM arguments for launchers
   */
  public List<String> getGradleJvmArguments() {
    return gradleJvmArguments;
  }

  /**
   * set the Gradle JVM arguments to pass to the Gradle launcher API.
   *
   * @param gradleJvmArguments Gradle JVM arguments for launchers
   */
  public void setGradleJvmArguments(List<String> gradleJvmArguments) {
    this.gradleJvmArguments = gradleJvmArguments;
  }

  /**
   * get the available JDKs for Gradle to run under.
   *
   * @return available JDKs
   */
  public Map<String, String> getJdks() {
    return jdks;
  }

  /**
   * set the available JDKs for Gradle to run under.
   *
   * @param jdks available JDKs
   */
  public void setJdks(Map<String, String> jdks) {
    this.jdks = jdks;
  }

  /**
   * get the version of the Java Semantic DB library to use - if at all.
   *
   * @return java semantic db library version or null
   */
  public String getJavaSemanticdbVersion() {
    return javaSemanticdbVersion;
  }

  /**
   * set the version of the Java Semantic DB library to use - if at all.
   *
   * @param javaSemanticdbVersion java semantic db library version or null
   */
  public void setJavaSemanticdbVersion(String javaSemanticdbVersion) {
    this.javaSemanticdbVersion = javaSemanticdbVersion;
  }

  /**
   * get the version of the Scala Semantic DB library to use - if at all.
   *
   * @return scala semantic db library version or null
   */
  public String getSemanticdbVersion() {
    return semanticdbVersion;
  }

  /**
   * get the version of the Scala Semantic DB library to use - if at all.
   *
   * @return scala semantic db library version or null
   */
  public String getScalaSemanticdbVersion() {
    return semanticdbVersion;
  }

  /**
   * set the version of the Scala Semantic DB library to use - if at all.
   *
   * @param semanticdbVersion scala semantic db library version or null
   */
  public void setSemanticdbVersion(String semanticdbVersion) {
    this.semanticdbVersion = semanticdbVersion;
  }
}
