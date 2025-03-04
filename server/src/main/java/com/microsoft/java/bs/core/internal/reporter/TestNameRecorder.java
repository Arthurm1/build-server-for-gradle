// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.extended.TestName;
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
  private final Map<String, List<TestName>> tests;

  /**
   * constructor.
   */
  public TestNameRecorder() {
    tests = new HashMap<>();
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    if (event instanceof StartEvent
        && event.getDescriptor() instanceof JvmTestOperationDescriptor descriptor) {
      String taskPath = ReporterUtils.getTaskPath(descriptor);
      if (taskPath != null) {
        TestName testName = ReporterUtils.getTestName(descriptor);
        // do not send reports on Gradle internal test tasks
        if (testName != null) {
          tests.computeIfAbsent(taskPath, k -> new ArrayList<>()).add(testName);
        }
      }
    }
  }

  /**
   * get the set of tests retrieved by the test dry-run.
   *
   * @return map of task path to tests
   */
  public Map<String, List<TestName>> getTests() {
    return tests;
  }
}
