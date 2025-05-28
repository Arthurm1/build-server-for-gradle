package com.microsoft.java.bs.core.internal.managers;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.Diagnostic;
import ch.epfl.scala.bsp4j.DiagnosticSeverity;
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Problem state has to be maintained for BSP. Gradle only reports warnings
 * on the first compile, then it reports nothing.
 */
public class ProblemsManager {

  // map of taskPath to problems
  private final Map<String, List<Problem>> currentProblems;
  private final Map<String, List<Problem>> oldProblems;

  /**
   * constructor.
   */
  public ProblemsManager() {
    currentProblems = new HashMap<>();
    oldProblems = new HashMap<>();
  }

  // for testing
  Map<String, List<Problem>> getCurrentProblems() {
    return currentProblems;
  }

  // for testing
  Map<String, List<Problem>> getOldProblems() {
    return oldProblems;
  }

  private int getSeverityCount(String taskPath, DiagnosticSeverity severity) {
    int count = 0;
    for (List<Problem> problems : currentProblems.values()) {
      for (Problem problem : problems) {
        if (problem.taskPath().equals(taskPath)) {
          if (problem.diagnostic().getSeverity() == severity) {
            count++;
          }
        }
      }
    }
    return count;
  }

  /**
   * Get the error count for this task path.
   *
   * @param taskPath compile task path
   * @return number of error diagnostics
   */
  public int getErrorCount(String taskPath) {
    return getSeverityCount(taskPath, DiagnosticSeverity.ERROR);
  }

  /**
   * Get the warning count for this task path.
   *
   * @param taskPath compile task path
   * @return number of warning diagnostics
   */
  public int getWarningCount(String taskPath) {
    return getSeverityCount(taskPath, DiagnosticSeverity.WARNING);
  }

  /**
   * add new diagnostics to the state.
   *
   * @param problem problem for a single target/file combo
   */
  public void addDiagnostics(Problem problem) {
    currentProblems.computeIfAbsent(problem.taskPath(), k -> new ArrayList<>())
        .add(problem);
  }

  /**
   * notify the manager that a clean task has started.
   *
   * @param targets targets that clean has been called for
   * @param taskPathMap map of task paths to targets
   */
  public void targetsClean(Collection<BuildTargetIdentifier> targets,
      Map<String, Set<BuildTargetIdentifier>> taskPathMap) {
    // TODO the mapping of clean taskpath to compile taskpaths could be cached in BuildTargetManager
    Set<String> taskPaths = new HashSet<>();
    for (BuildTargetIdentifier btId : targets) {
      for (Map.Entry<String, Set<BuildTargetIdentifier>> entry : taskPathMap.entrySet()) {
        if (entry.getValue().contains(btId)) {
          taskPaths.add(entry.getKey());
        }
      }
    }
    for (String taskPath : taskPaths) {
      // shift problems from current to old
      List<Problem> problems = currentProblems.remove(taskPath);
      if (problems != null) {
        oldProblems.put(taskPath, problems);
      }
    }
  }

  /**
   * notify the manager that a compile task has started.
   *
   * @param taskPath compile task
   */
  public void targetsCompile(String taskPath) {
    // shift problems from current to old
    List<Problem> problems = currentProblems.remove(taskPath);
    if (problems != null) {
      if (oldProblems.put(taskPath, problems) != null) {
        throw new IllegalStateException("Diagnostics recording error");
      }
    }
  }

  private Map<TaskPathDoc, OriginProblems> group(String taskPath,
      List<Problem> problems) {
    Map<TaskPathDoc, OriginProblems> taskPathDocProblems = new HashMap<>();
    if (problems != null) {
      for (Problem problem : problems) {
        TaskPathDoc buildTargetDoc = new TaskPathDoc(taskPath, problem);
        OriginProblems originProblems = taskPathDocProblems.computeIfAbsent(
            buildTargetDoc, k -> new OriginProblems());
        originProblems.addProblem(problem);
      }
    }
    return taskPathDocProblems;
  }

  private List<PublishDiagnosticsParams> collate(
      Map<TaskPathDoc, OriginProblems> taskPathDocProblems,
      Map<String, Set<BuildTargetIdentifier>> taskPathMap) {
    List<PublishDiagnosticsParams> result = new ArrayList<>();
    if (taskPathDocProblems != null) {
      for (Map.Entry<TaskPathDoc, OriginProblems> entry : taskPathDocProblems.entrySet()) {
        // collate by taskPath/textDoc
        TaskPathDoc taskPathDoc = entry.getKey();
        Set<BuildTargetIdentifier> targets = taskPathMap.get(taskPathDoc.taskPath());
        for (BuildTargetIdentifier btId : targets) {
          boolean isFirst = true;
          for (Map.Entry<String, List<Problem>> problem : entry.getValue().getProblems()) {
            // only reset the first diagnostic per target/textDoc - origin is irrelevant
            List<Diagnostic> bspDiagnostics = problem.getValue()
                .stream().map(Problem::diagnostic)
                .collect(Collectors.toList());
            PublishDiagnosticsParams newParams = new PublishDiagnosticsParams(
                taskPathDoc.textDocId(), btId, bspDiagnostics, isFirst);
            isFirst = false;
            newParams.setOriginId(problem.getKey());
            result.add(newParams);
          }
        }
      }
    }
    return result;
  }

  /**
   * Collate the stored problems for this task. This should be called once
   * a clean or compile is finished. Only call once - it mutates the state.
   *
   * @param taskPath task to get the problems for
   * @param taskPathMap map of task paths to targets
   * @return diagnostics to be sent to the bsp client
   */
  public List<PublishDiagnosticsParams> collateDiagnostics(String taskPath,
      Map<String, Set<BuildTargetIdentifier>> taskPathMap) {
    // It's not possible to get noop from Gradle events. I've left this here in case it ever is.
    return collateDiagnostics(taskPath, taskPathMap, false);
  }

  /**
   * Collate the stored problems for this task. This should be called once
   * a clean or compile is finished. Only call once - it mutates the state.
   *
   * @param taskPath task to get the problems for
   * @param taskPathMap map of task paths to targets
   * @param noop whether compile was a noop
   * @return diagnostics to be sent to the bsp client
   */
  public List<PublishDiagnosticsParams> collateDiagnostics(String taskPath,
      Map<String, Set<BuildTargetIdentifier>> taskPathMap, boolean noop) {

    // remove old problems
    List<Problem> originalProblems = oldProblems.remove(taskPath);

    Map<TaskPathDoc, OriginProblems> results;
    if (noop) {
      // Gradle does not replay problems when tasks are up to date so we restore them here
      // put back old problems into current problems
      if (originalProblems != null) {
        currentProblems.put(taskPath, originalProblems);
        results = group(taskPath, currentProblems.get(taskPath));
      } else {
        results = null;
      }
    } else {
      results = group(taskPath, currentProblems.get(taskPath));
      Map<TaskPathDoc, OriginProblems> oldGroup = group(taskPath, originalProblems);
      // add (as blank) any previous problems that have now been fixed
      for (Map.Entry<TaskPathDoc, OriginProblems> oldTaskPathDoc : oldGroup.entrySet()) {
        if (!results.containsKey(oldTaskPathDoc.getKey())) {
          OriginProblems blankProblems = oldTaskPathDoc.getValue().withEmptyProblems();
          results.put(oldTaskPathDoc.getKey(), blankProblems);
        }
      }
    }
    // TODO - CHECK AGAINST PREVIOUS RESULTS AND ONLY SEND DIFFS
    return collate(results, taskPathMap);
  }
}
