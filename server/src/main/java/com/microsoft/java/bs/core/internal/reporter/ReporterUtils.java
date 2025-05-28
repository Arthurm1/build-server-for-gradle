package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.extended.TestName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.problems.AdditionalData;
import org.gradle.tooling.events.problems.Details;
import org.gradle.tooling.events.problems.DocumentationLink;
import org.gradle.tooling.events.problems.FileLocation;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.PluginIdLocation;
import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.events.problems.ProblemDefinition;
import org.gradle.tooling.events.problems.ProblemGroup;
import org.gradle.tooling.events.problems.ProblemId;
import org.gradle.tooling.events.problems.Severity;
import org.gradle.tooling.events.problems.SingleProblemEvent;
import org.gradle.tooling.events.problems.Solution;
import org.gradle.tooling.events.problems.TaskPathLocation;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;

/**
 * Methods to help with event listeners.
 */
public class ReporterUtils {

  public static String toString(ProgressEvent event) {
    if (event == null) {
      return "null";
    }
    if (event instanceof SingleProblemEvent spe) {
      return toString(spe);
    }
    return event.getClass().toString();
  }

  public static String toString(SingleProblemEvent event) {
    return "Problem: " + toString("\n  ", event.getProblem());
  }

  private static String indent(String indent) {
    return indent + "  ";
  }

  private static <T> String toString(String indent, List<T> list,
      BiFunction<String, T, String> toString) {
    return list.stream()
        .map(f -> toString.apply(indent(indent), f))
        .collect(Collectors.joining(","));
  }

  private static String toString(String indent, Map<String, Object> map) {
    return map.entrySet().stream()
        .map(f -> indent + f.getKey() + " -> " + f.getValue())
        .collect(Collectors.joining(","));
  }

  private static String toString(String indent, Problem problem) {
    String newIndent = indent(indent);
    return indent + "OriginLocations: " + toString(newIndent, problem.getOriginLocations(), ReporterUtils::toString)
        + indent + "ContextualLocations: " + toString(newIndent, problem.getContextualLocations(), ReporterUtils::toString)
        + indent + "Details: " + toString(newIndent, problem.getDetails())
        + indent + "Definition: " + toString(newIndent, problem.getDefinition())
        + indent + "Failure: " + toString(newIndent, problem.getFailure())
        + indent + "AdditionalData: " + toString(newIndent, problem.getAdditionalData())
        + indent + "Solutions: " + toString(newIndent, problem.getSolutions(), ReporterUtils::toString);
  }

  private static String toString(String indent, Location location) {
    if (location instanceof PluginIdLocation pluginIdLocation) {
      return toString(indent, pluginIdLocation);
    } else if (location instanceof FileLocation fileLocation) {
      return toString(indent, fileLocation);
    } else if (location instanceof TaskPathLocation taskPathLocation) {
      return toString(indent, taskPathLocation);
    }
    return indent + location;
  }

  private static String toString(String indent, PluginIdLocation location) {
    if (location == null) {
      return "null";
    }
    return indent + "PluginId: " + location.getPluginId();
  }

  private static String toString(String indent, FileLocation location) {
    if (location == null) {
      return "null";
    }
    return indent + "Path: " + location.getPath();
  }

  private static String toString(String indent, TaskPathLocation location) {
    if (location == null) {
      return "null";
    }
    return indent + "BuildTreePath: " + location.getBuildTreePath();
  }

  private static String toString(String indent, Details details) {
    if (details == null) {
      return "null";
    }
    return indent + details.getDetails();
  }

  private static String toString(String indent, ProblemGroup problemGroup) {
    if (problemGroup == null) {
      return "null";
    }
    String newIndent = indent(indent);
    return indent + "Name: " + problemGroup.getName()
        + indent + "DisplayName: " + problemGroup.getDisplayName()
        + indent + "Parent: " + toString(newIndent, problemGroup.getParent());
  }

  private static String toString(String indent, ProblemId problemId) {
    if (problemId == null) {
      return "null";
    }
    String newIndent = indent(indent);
    return indent + "Group: " + toString(newIndent, problemId.getGroup())
        + indent + "Name: " + problemId.getName()
        + indent + "DisplayName: " + problemId.getDisplayName();
  }

  private static String toString(String indent, Severity severity) {
    if (severity == null) {
      return "null";
    }
    String level;
    if (severity.getSeverity() == Severity.ADVICE.getSeverity()) {
      level = "Advice";
    } else if (severity.getSeverity() == Severity.WARNING.getSeverity()) {
      level = "Warning";
    } else if (severity.getSeverity() == Severity.ERROR.getSeverity()) {
      level = "Error";
    } else {
      level = "Unknown: " + severity.getSeverity();
    }
    return indent + "isKnown: " + severity.isKnown()
        + indent + "Severity: " + level;
  }

  private static String toString(String indent, DocumentationLink documentationLink) {
    if (documentationLink == null) {
      return "null";
    }
    return indent + "Url: " + documentationLink.getUrl();
  }

  private static String toString(String indent, ProblemDefinition problemDefinition) {
    if (problemDefinition == null) {
      return "null";
    }
    String newIndent = indent(indent);
    return indent + "Id: " + toString(newIndent, problemDefinition.getId())
        + indent + "Severity: " + toString(newIndent, problemDefinition.getSeverity())
        + indent + "DocumentationLink: " + toString(newIndent, problemDefinition.getDocumentationLink());
  }

  private static String toString(String indent, Failure failure) {
    if (failure == null) {
      return "null";
    }
    String newIndent = indent(indent);
    return indent + "Message: " + failure.getMessage()
        + indent + "Description: " + failure.getDescription()
        + indent + "Causes: " + toString(newIndent, failure.getCauses(), ReporterUtils::toString)
        + indent + "Problems: " + toString(newIndent, failure.getProblems(), ReporterUtils::toString);
  }

  private static String toString(String indent, AdditionalData additionalData) {
    if (additionalData == null) {
      return "null";
    }
    return toString(indent, additionalData.getAsMap());
  }

  private static String toString(String indent, Solution solution) {
    if (solution == null) {
      return "null";
    }
    return indent + "Solution: " + solution.getSolution();
  }

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
