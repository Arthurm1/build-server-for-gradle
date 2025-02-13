// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.model;

import java.util.Objects;

import com.microsoft.java.bs.gradle.model.GradleSourceSet;

import ch.epfl.scala.bsp4j.BuildTarget;

/**
 * Represents a Gradle build target.
 */
public class GradleBuildTarget {

  /**
   * constructor.
   *
   * @param buildTarget the build target information
   * @param sourceSet the Gradle source set information.
   */
  public GradleBuildTarget(BuildTarget buildTarget, GradleSourceSet sourceSet) {
    this.buildTarget = buildTarget;
    this.sourceSet = sourceSet;
  }

  private BuildTarget buildTarget;

  private GradleSourceSet sourceSet;

  /**
   * get the build target information.
   *
   * @return build target
   */
  public BuildTarget getBuildTarget() {
    return buildTarget;
  }

  /**
   * set the build target information.
   *
   * @param buildTarget build target
   */
  public void setBuildTarget(BuildTarget buildTarget) {
    this.buildTarget = buildTarget;
  }

  /**
   * get the Gradle source set information.
   *
   * @return Gradle source set
   */
  public GradleSourceSet getSourceSet() {
    return sourceSet;
  }

  /**
   * set the Gradle source set information.
   *
   * @param sourceSet Gradle source set
   */
  public void setSourceSet(GradleSourceSet sourceSet) {
    this.sourceSet = sourceSet;
  }

  @Override
  public int hashCode() {
    return Objects.hash(buildTarget, sourceSet);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    GradleBuildTarget other = (GradleBuildTarget) obj;
    return Objects.equals(buildTarget, other.buildTarget)
        && Objects.equals(sourceSet, other.sourceSet);
  }
}
