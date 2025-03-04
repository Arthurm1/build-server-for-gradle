// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.reporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.StartEvent;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;

/**
 * Implements {@link ProgressListener} that listens to the progress of gradle test tasks,
 * and records the names of the tests.
 */
public class TestNameRecorder implements ProgressListener {

  // map of task path to test classes
  private final Map<String, List<String>> testClasses;

  /**
   * constructor.
   */
  public TestNameRecorder() {
    testClasses = new HashMap<>();
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    if (event instanceof StartEvent && event.getDescriptor() instanceof JvmTestOperationDescriptor) {
      JvmTestOperationDescriptor descriptor = (JvmTestOperationDescriptor) event.getDescriptor();
      if (descriptor.getClassName() != null && descriptor.getMethodName() == null) {
        String taskPath = ReporterUtils.getTaskPath(descriptor);
        if (taskPath != null) {
          testClasses.computeIfAbsent(taskPath, k -> new ArrayList<>())
              .add(descriptor.getClassName());
        }
      }
    }
  }

  /**
   * get the set of test classes retrieved by the test dry-run.
   *
   * @return map of task path to test classes
   */
  public Map<String, List<String>> getTestClasses() {
    return testClasses;
  }
}
