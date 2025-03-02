// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.model;

import java.util.Set;
import java.util.Objects;

import com.microsoft.java.bs.gradle.model.GradleTestTask;

/**
 * Contains test information relating to a Gradle Test task.
 * And the test classes 
 */
public class GradleTestEntity {

  private final GradleTestTask gradleTestTask;
  private final Set<String> testClasses;

  /**
   * Initialize the test information.
   *
   * @param gradleTestTask the test environment
   * @param testClasses the jvm test class names 
   */
  public GradleTestEntity(GradleTestTask gradleTestTask, Set<String> testClasses) {
    this.gradleTestTask = gradleTestTask;
    this.testClasses = testClasses;
  }

  /**
   * get the test environment.
   *
   * @return the test environment
   */
  public GradleTestTask getGradleTestTask() {
    return gradleTestTask;
  }

  /**
   * get the jvm test class names.
   *
   * @return the jvm test class names 
   */
  public Set<String> getTestClasses() {
    return testClasses;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(gradleTestTask, testClasses);
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
    GradleTestEntity other = (GradleTestEntity) obj;
    return Objects.equals(gradleTestTask, other.gradleTestTask)
        && Objects.equals(testClasses, other.testClasses);
  }
}
