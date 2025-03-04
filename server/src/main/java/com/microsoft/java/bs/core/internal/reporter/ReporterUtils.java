package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.extended.TestName;
import java.util.ArrayList;
import java.util.List;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;

public class ReporterUtils {

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
