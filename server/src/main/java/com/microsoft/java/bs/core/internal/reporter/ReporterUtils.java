package com.microsoft.java.bs.core.internal.reporter;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;

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
}
