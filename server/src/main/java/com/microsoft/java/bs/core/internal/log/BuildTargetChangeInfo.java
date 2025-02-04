// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.log;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;

/**
 * Class to report on build target changes.
 */
public class BuildTargetChangeInfo {

  private final BuildTargetIdentifier btId;
  private final String oldSourceSet;
  private final String newSourceSet;

  /**
   * Constructor.
   *
   * @param btId build target (new or old)
   * @param oldSourceSet old build target setup
   * @param newSourceSet new build target setup
   */
  public BuildTargetChangeInfo(BuildTargetIdentifier btId, GradleSourceSet oldSourceSet,
      GradleSourceSet newSourceSet) {
    this.btId = btId;
    this.oldSourceSet = oldSourceSet != null ? oldSourceSet.toString() : null;
    this.newSourceSet = newSourceSet != null ? newSourceSet.toString() : null;
  }

  public boolean hasChanged() {
    return oldSourceSet != null && newSourceSet != null;
  }

  public boolean isAdded() {
    return oldSourceSet == null && newSourceSet != null;
  }

  public boolean isRemoved() {
    return oldSourceSet != null && newSourceSet == null;
  }

  public BuildTargetIdentifier getBtId() {
    return btId;
  }
}
