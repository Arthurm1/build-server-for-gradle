package com.microsoft.java.bs.core.internal.managers;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.Diagnostic;
import ch.epfl.scala.bsp4j.DiagnosticSeverity;
import ch.epfl.scala.bsp4j.Position;
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams;
import ch.epfl.scala.bsp4j.Range;
import ch.epfl.scala.bsp4j.TextDocumentIdentifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Problem state has to be maintained for BSP. Gradle only reports warnings
 * on the first compile, then it reports nothing.
 */
public class ProblemsManager {

  // map of taskPath to problems
  private final Map<String, List<Problem>> currentProblems;
  private final Map<String, List<Problem>> oldProblems;
  private final BuildTargetIdentifier catchAllBt;

  /**
   * constructor.
   */
  public ProblemsManager(BuildTargetIdentifier catchAllBt) {
    currentProblems = new HashMap<>();
    oldProblems = new HashMap<>();
    this.catchAllBt = catchAllBt;
  }

  public BuildTargetIdentifier getCatchAllBt() {
    return catchAllBt;
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
        if (Objects.equals(problem.taskPath(), taskPath)) {
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
    if (targets == null) {
      return;
    }
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
        if (targets == null || targets.isEmpty()) {
          // we could associate these with targets if the diagnostic was on a build file,
          // but we get these through on the first configure before we know about build targets.
          targets = Set.of(catchAllBt);
        }
        for (BuildTargetIdentifier btId : targets) {
          boolean isFirst = true;
          for (Map.Entry<String, List<Problem>> problem : entry.getValue().getProblems()) {
            // only reset the first diagnostic per target/textDoc - origin is irrelevant
            List<Diagnostic> bspDiagnostics = problem.getValue()
                .stream()
                .map(Problem::diagnostic)
                .distinct()
                .collect(Collectors.toList());
          /*  if (bspDiagnostics.isEmpty()) {
              // TODO remove this when we understand how it's being triggered
              Position position = new Position(0, 0);
              Range range = new Range(position, position);
              Diagnostic diag = new Diagnostic(range, "taskPath: " + taskPathDoc.taskPath());
              bspDiagnostics = List.of(diag);
            }*/
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

    // remove old problems - only for this task path
    List<Problem> originalProblems = oldProblems.remove(taskPath);
    // get the current problems
    List<Problem> problems = currentProblems.get(taskPath);
    if (problems != null) {
      Set<TextDocumentIdentifier> documents = problems.stream().map(Problem::textDocument)
          .collect(Collectors.toSet());
      // get any problems that aren't this task path but have the same text document id
      // TODO iterating through list is not optimal
      List<Problem> otherTaskPathProblems = Stream.of(currentProblems.values().stream(), oldProblems.values().stream())
          .flatMap(f -> f)
          .flatMap(Collection::stream)
          .filter(problem -> !Objects.equals(problem.taskPath(), taskPath))
          .filter(problem -> documents.contains(problem.textDocument()))
          .collect(Collectors.toList());
      if (!otherTaskPathProblems.isEmpty()) {
        problems = new ArrayList<>(problems);
        problems.addAll(otherTaskPathProblems);
      }
    }

    Map<TaskPathDoc, OriginProblems> results = group(taskPath, problems);
    Map<TaskPathDoc, OriginProblems> oldGroup = group(taskPath, originalProblems);
    // add (as blank) any previous problems that have now been fixed
    for (Map.Entry<TaskPathDoc, OriginProblems> oldTaskPathDoc : oldGroup.entrySet()) {
      if (!results.containsKey(oldTaskPathDoc.getKey())) {
        OriginProblems blankProblems = oldTaskPathDoc.getValue().withEmptyProblems();
        results.put(oldTaskPathDoc.getKey(), blankProblems);
      }
    }
    // TODO - CHECK AGAINST PREVIOUS RESULTS AND ONLY SEND DIFFS
    return collate(results, taskPathMap);
  }
}
