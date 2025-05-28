package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.extended.TestName;
import java.util.ArrayList;
import java.util.List;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.model.ProjectIdentifier;

/**
 * Methods to help with event listeners.
 */
public class ReporterUtils {

  /**
   * Create a fake task path from the operation descriptor.
   * Recurses through parents to create full task path.
   *
   * @param operationDescriptor descriptor
   * @return fake task path
   */
  public static String createFakeTaskPath(OperationDescriptor operationDescriptor) {
    if (operationDescriptor == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    OperationDescriptor descriptor = operationDescriptor;
    while (descriptor != null) {
      if (!sb.isEmpty()) {
        sb.append('\n');
      }
      sb.append(descriptor.getName());
      descriptor = descriptor.getParent();
    }
    return sb.toString();
  }

  /**
   * get the gradle task path from the operation descriptor.
   * Recurses through parents to find a non-null task path.
   *
   * @param operationDescriptor descriptor
   * @return task path
   */
  public static String getTaskPath(OperationDescriptor operationDescriptor) {
    if (operationDescriptor == null) {
      return null;
    } else if (operationDescriptor instanceof TaskOperationDescriptor) {
      return ((TaskOperationDescriptor) operationDescriptor).getTaskPath();
    } else {
      OperationDescriptor parent = operationDescriptor.getParent();
      if (parent != operationDescriptor) {
        return getTaskPath(operationDescriptor.getParent());
      } else {
        return null;
      }
    }
  }

  /**
   * get the test name from the event descriptor.
   * Recurses through parents to fully describe the test name.
   *
   * @param eventDescriptor descriptor
   * @return the test name
   */
  public static TestName getTestName(JvmTestOperationDescriptor eventDescriptor) {
    List<JvmTestOperationDescriptor> fullStack = new ArrayList<>();
    fullStack.add(eventDescriptor);
    OperationDescriptor descriptor = eventDescriptor.getParent();
    while (descriptor != null) {
      if (descriptor instanceof JvmTestOperationDescriptor jvmTestOperationDescriptor) {
        fullStack.add(jvmTestOperationDescriptor);
      }
      descriptor = descriptor.getParent();
    }
    // Gradle can have blank classnames on dynamic tests even though the test is still
    // within the class, so search until classname disappears completely.
    int i = fullStack.size() - 1;
    boolean classNameFound = false;
    while (i >= 0 && !classNameFound) {
      if (fullStack.get(i).getClassName() != null) {
        classNameFound = true;
      } else {
        i--;
      }
    }
    // earlier check means that classname will always be found so can't have i < 0
    // reverse list order
    TestName testName = null;
    while (i >= 0) {
      JvmTestOperationDescriptor desc = fullStack.get(i);
      String displayName;
      try {
        displayName = desc.getTestDisplayName();
      } catch (NoSuchMethodError | AbstractMethodError e) {
        displayName = desc.getDisplayName();
      }
      TestName currentTestName = new TestName(displayName, desc.getSuiteName(),
          desc.getClassName(), desc.getMethodName());
      currentTestName.setParent(testName);
      testName = currentTestName;
      i--;
    }
    return testName;
  }
}
