package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.CodeDescription;
import ch.epfl.scala.bsp4j.Diagnostic;
import ch.epfl.scala.bsp4j.DiagnosticSeverity;
import ch.epfl.scala.bsp4j.DiagnosticTag;
import ch.epfl.scala.bsp4j.Position;
import ch.epfl.scala.bsp4j.Range;
import ch.epfl.scala.bsp4j.ScalaAction;
import ch.epfl.scala.bsp4j.TextDocumentIdentifier;
import com.microsoft.java.bs.core.internal.managers.Problem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.problems.DocumentationLink;
import org.gradle.tooling.events.problems.FileLocation;
import org.gradle.tooling.events.problems.LineInFileLocation;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.OffsetInFileLocation;
import org.gradle.tooling.events.problems.ProblemDefinition;
import org.gradle.tooling.events.problems.Severity;
import org.gradle.tooling.events.problems.SingleProblemEvent;
import org.gradle.tooling.events.problems.Solution;

/**
 * Convert Gradle problem events to BSP diagnostics.
 */
class ProblemsReporter {

  private final String originId;

  ProblemsReporter(String originId) {
    this.originId = originId;
  }

  public Problem convertToDiagnostic(String taskPath, ProgressEvent event) {
    if (event instanceof SingleProblemEvent) {
      SingleProblemEvent singleProblemEvent = (SingleProblemEvent) event;
      org.gradle.tooling.events.problems.Problem problem = singleProblemEvent.getProblem();
      ProblemDefinition problemDefinition = problem.getDefinition();
      DocumentationLink documentationLink = problemDefinition.getDocumentationLink();

      return convertToDiagnostic(
          taskPath,
          problemDefinition.getSeverity(),
          problem.getOriginLocations(),
          problemDefinition.getId().getName(),
          problem.getContextualLabel().getContextualLabel(),
          problem.getSolutions(),
          documentationLink != null ? documentationLink.getUrl() : null);
    }
    return null;
  }

  private Problem convertToDiagnostic(String taskPath,
      Severity severity, List<Location> locations, String errorCode, String message,
      List<Solution> solutions, String documentationLink) {

    // TODO this won't handle the issue with tabs https://github.com/gradle/gradle/issues/28230
    // TODO this won't handle the issue with multiline diagnostics as Java does
    //      not return the end line.  Only way to do this is examine the file.
    OffsetInFileLocation offsetInFileLocation = null;
    LineInFileLocation lineInFileLocation = null;
    FileLocation fileOnlyLocation = null;
    for (Location location : locations) {
      if (location instanceof LineInFileLocation) {
        lineInFileLocation = (LineInFileLocation) location;
        // LineInFileLocation gives all info so no need for more
        break;
      } else if (location instanceof OffsetInFileLocation) {
        offsetInFileLocation = (OffsetInFileLocation) location;
      } else if (location instanceof FileLocation) {
        fileOnlyLocation = (FileLocation) location;
      }
    }
    if (offsetInFileLocation != null || lineInFileLocation != null || fileOnlyLocation != null) {
      final Range range;
      final String location;
      if (lineInFileLocation != null) {
        range = getRange(lineInFileLocation);
        location = lineInFileLocation.getPath();
      } else if (offsetInFileLocation != null) {
        // TODO only way to discover line numbers is to open the file and count back newlines
        // TODO same issue as multi-line diagnostics. Use start of file for now.
        range = getZeroRange();
        location = offsetInFileLocation.getPath();
      } else {
        range = getZeroRange();
        location = fileOnlyLocation.getPath();
      }
      DiagnosticSeverity diagnosticSeverity = getSeverity(severity);
      return convertToDiagnostic(taskPath, location, range, diagnosticSeverity,
            errorCode, message, solutions, documentationLink);
    }
    return null;
  }

  private Problem convertToDiagnostic(String taskPath, String location, Range range,
      DiagnosticSeverity severity, String errorCode, String message, List<Solution> solutions,
      String documentationLink) {
    Diagnostic diagnostic = new Diagnostic(range, message);
    diagnostic.setSeverity(severity);
    diagnostic.setSource("Gradle");
    diagnostic.setCode(errorCode);
    diagnostic.setCodeDescription(getCodeDescription(documentationLink));
    diagnostic.setTags(getTags(errorCode));
    if (solutions != null && !solutions.isEmpty()) {
      diagnostic.setDataKind("scala");
      List<ScalaAction> actions = solutions.stream()
          .map(solution -> new ScalaAction(solution.getSolution()))
          .collect(Collectors.toList());
      diagnostic.setData(actions);
    }
    // currently Gradle can return a path of the form `build file 'proper path'`
    if (location.length() >= 13 && location.startsWith("build file '") && location.endsWith("'")) {
      location = location.substring(12, location.length() - 1);
    }
    String uri;
    try {
      uri = Path.of(location).toUri().toString();
    } catch (Exception e) {
      throw new IllegalStateException("Error translating " + location, e);
    }
    TextDocumentIdentifier textDocument = new TextDocumentIdentifier(uri);
    return new Problem(taskPath, originId, textDocument, diagnostic);
  }

  private DiagnosticSeverity getSeverity(Severity severity) {
    if (severity.getSeverity() == Severity.ERROR.getSeverity()) {
      return DiagnosticSeverity.ERROR;
    } else if (severity.getSeverity() == Severity.WARNING.getSeverity()) {
      return DiagnosticSeverity.WARNING;
    } else if (severity.getSeverity() == Severity.ADVICE.getSeverity()) {
      return DiagnosticSeverity.INFORMATION;
    } else {
      return null;
    }
  }

  private CodeDescription getCodeDescription(String documentationLink) {
    if (documentationLink != null) {
      return new CodeDescription(documentationLink);
    }
    return null;
  }

  private List<Integer> getTags(String errorCode) {
    if (errorCode != null) {
      String code = errorCode.toLowerCase();
      List<Integer> tags = new ArrayList<>();
      if (code.contains("deprecated")) {
        tags.add(DiagnosticTag.DEPRECATED);
      }
      if (code.contains("unused")) {
        tags.add(DiagnosticTag.UNNECESSARY);
      }
      if (!tags.isEmpty()) {
        return tags;
      }
    }
    return null;
  }

  private Range getRange(LineInFileLocation lineInFileLocation) {
    int startLine = lineInFileLocation.getLine() >= 0 ? lineInFileLocation.getLine() - 1 : -1;
    int startCol = lineInFileLocation.getColumn() >= 0 ? lineInFileLocation.getColumn() - 1 : -1;
    Position start = new Position(startLine, startCol);
    // TODO - doesn't work for multi-line diagnostics - currently assume it's the same line
    Position end = new Position(startLine, startCol + lineInFileLocation.getLength());
    return new Range(start, end);
  }

  private Range getZeroRange() {
    Position position = new Position(0, 0);
    return new Range(position, position);
  }
}
