package com.microsoft.java.bs.core.internal.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.Diagnostic;
import ch.epfl.scala.bsp4j.Position;
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams;
import ch.epfl.scala.bsp4j.Range;
import ch.epfl.scala.bsp4j.TextDocumentIdentifier;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestProblemsManager {

  private TextDocumentIdentifier getTextDocumentIdentifier() {
    return new TextDocumentIdentifier(UUID.randomUUID().toString());
  }

  private Range getRange() {
    Position start = new Position(1, 2);
    Position end = new Position(3, 4);
    return new Range(start, end);
  }

  private Diagnostic getDiagnostic() {
    final Range range = getRange();
    return new Diagnostic(range, "Some Diagnostic");
  }

  private Problem getProblem(String taskPath) {
    TextDocumentIdentifier tdId = getTextDocumentIdentifier();
    Diagnostic diagnostic = getDiagnostic();
    return new Problem(taskPath, null, tdId, diagnostic);
  }

  @Test
  void testProblemsManager() {
    BuildTargetIdentifier fakeId = new BuildTargetIdentifier("fake");
    ProblemsManager manager = new ProblemsManager(fakeId);
    BuildTargetIdentifier fooId = new BuildTargetIdentifier("foo");
    BuildTargetIdentifier barId = new BuildTargetIdentifier("bar");
    String taskPathFoo = "Task:Foo";
    String taskPathBar = "Task:Bar";
    Map<String, Set<BuildTargetIdentifier>> taskPathMap = new HashMap<>();
    taskPathMap.put(taskPathFoo, Set.of(fooId));
    taskPathMap.put(taskPathBar, Set.of(barId));

    // initial state
    assertEquals(0, manager.getErrorCount(taskPathFoo));
    assertEquals(0, manager.getWarningCount(taskPathFoo));
    assertEquals(0, manager.getErrorCount(taskPathBar));
    assertEquals(0, manager.getWarningCount(taskPathBar));

    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());

    // clean
    List<BuildTargetIdentifier> targets = List.of(fooId, barId);
    manager.targetsClean(targets, taskPathMap);
    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());
    manager.collateDiagnostics(taskPathFoo, taskPathMap);
    manager.collateDiagnostics(taskPathBar, taskPathMap);
    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());

    // compile
    manager.targetsCompile(taskPathFoo);
    manager.targetsCompile(taskPathBar);
    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());
    manager.collateDiagnostics(taskPathFoo, taskPathMap);
    manager.collateDiagnostics(taskPathBar, taskPathMap);
    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());

    // compile - should be noop
    manager.targetsCompile(taskPathFoo);
    manager.targetsCompile(taskPathBar);
    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());
    manager.collateDiagnostics(taskPathFoo, taskPathMap);
    manager.collateDiagnostics(taskPathBar, taskPathMap);
    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());

    // compile warning
    manager.targetsCompile(taskPathFoo);
    manager.targetsCompile(taskPathBar);
    assertTrue(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());
    Problem fooProblem = getProblem(taskPathFoo);
    manager.addDiagnostics(fooProblem);
    // collate Foo
    List<PublishDiagnosticsParams> fooParams1 = manager
        .collateDiagnostics(taskPathFoo, taskPathMap);
    assertEquals(1, fooParams1.size());
    PublishDiagnosticsParams fooParam1 = fooParams1.iterator().next();
    assertTrue(fooParam1.getReset());
    assertEquals(1, fooParam1.getDiagnostics().size());
    // collate Bar
    List<PublishDiagnosticsParams> barParams1 = manager
        .collateDiagnostics(taskPathBar, taskPathMap);
    assertTrue(barParams1.isEmpty());

    // compile warning noop
    manager.targetsCompile(taskPathFoo);
    manager.targetsCompile(taskPathBar);
    assertFalse(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());
    // collate Foo
    List<PublishDiagnosticsParams> fooParams2 = manager
        .collateDiagnostics(taskPathFoo, taskPathMap);
    assertEquals(1, fooParams2.size());
    PublishDiagnosticsParams fooParam2 = fooParams2.iterator().next();
    assertTrue(fooParam2.getReset());
    assertEquals(1, fooParam2.getDiagnostics().size());
    // collate Bar
    List<PublishDiagnosticsParams> barParams2 = manager
        .collateDiagnostics(taskPathBar, taskPathMap);
    assertTrue(barParams2.isEmpty());
    // TODO Gradle will eventually replay warnings
    // https://github.com/gradle/gradle/issues/31233

    // compile warning - repeat
    manager.targetsCompile(taskPathFoo);
    manager.targetsCompile(taskPathBar);
    assertFalse(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());
    manager.addDiagnostics(fooProblem);
    // collate Foo
    List<PublishDiagnosticsParams> fooParams3 = manager
        .collateDiagnostics(taskPathFoo, taskPathMap);
    assertEquals(1, fooParams3.size());
    PublishDiagnosticsParams fooParam3 = fooParams3.iterator().next();
    assertTrue(fooParam3.getReset());
    assertEquals(1, fooParam3.getDiagnostics().size());
    // collate Bar
    List<PublishDiagnosticsParams> barParams3 = manager
        .collateDiagnostics(taskPathBar, taskPathMap);
    assertTrue(barParams3.isEmpty());

    // compile warning gone
    manager.targetsCompile(taskPathFoo);
    manager.targetsCompile(taskPathBar);
    assertFalse(manager.getOldProblems().isEmpty());
    assertTrue(manager.getCurrentProblems().isEmpty());
    // collate Foo
    List<PublishDiagnosticsParams> fooParams4 = manager
        .collateDiagnostics(taskPathFoo, taskPathMap);
    assertEquals(1, fooParams4.size());
    PublishDiagnosticsParams fooParam4 = fooParams4.iterator().next();
    assertTrue(fooParam4.getReset());
    assertTrue(fooParam4.getDiagnostics().isEmpty());
    // collate Bar
    List<PublishDiagnosticsParams> barParams4 = manager
        .collateDiagnostics(taskPathBar, taskPathMap);
    assertTrue(barParams4.isEmpty());
  }
}
