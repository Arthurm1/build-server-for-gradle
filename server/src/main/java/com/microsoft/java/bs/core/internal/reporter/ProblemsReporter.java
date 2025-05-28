package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.CodeDescription;
import ch.epfl.scala.bsp4j.Diagnostic;
import ch.epfl.scala.bsp4j.DiagnosticSeverity;
import ch.epfl.scala.bsp4j.DiagnosticTag;
import ch.epfl.scala.bsp4j.Position;
import ch.epfl.scala.bsp4j.Range;
import ch.epfl.scala.bsp4j.TextDocumentIdentifier;
import com.microsoft.java.bs.core.internal.managers.Problem;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.problems.DocumentationLink;
import org.gradle.tooling.events.problems.FileLocation;
import org.gradle.tooling.events.problems.LineInFileLocation;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.OffsetInFileLocation;
import org.gradle.tooling.events.problems.PluginIdLocation;
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

  public Problem convertToDiagnostic(String taskPath, ProgressEvent event,
      Map<BuildTargetIdentifier, File> buildFileMap) {
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
          documentationLink != null ? documentationLink.getUrl() : null,
          buildFileMap);
    }
    return null;
  }

  private Problem convertToDiagnostic(String taskPath,
      Severity severity, List<Location> locations, String errorCode, String message,
      List<Solution> solutions, String documentationLink,
      Map<BuildTargetIdentifier, File> buildFileMap) {

    // TODO this won't handle the issue with tabs https://github.com/gradle/gradle/issues/28230
    // TODO this won't handle the issue with multiline diagnostics as Java does
    //      not return the end line.  Only way to do this is examine the file.
    Range range = null;
    String path = null;
    boolean isPluginDiagnostic = false;
    for (Location location : locations) {
      if (location instanceof LineInFileLocation) {
        LineInFileLocation lineInFileLocation = (LineInFileLocation) location;
        range = getRange(lineInFileLocation);
        path = lineInFileLocation.getPath();
        // LineInFileLocation gives all info so no need for more
        break;
      } else if (location instanceof OffsetInFileLocation) {
        OffsetInFileLocation offsetInFileLocation = (OffsetInFileLocation) location;
        range = getRange(offsetInFileLocation);
        path = offsetInFileLocation.getPath();
      } else if (location instanceof FileLocation) {
        FileLocation fileOnlyLocation = (FileLocation) location;
        range = getZeroRange();
        path = fileOnlyLocation.getPath();
      } else if (location instanceof PluginIdLocation) {
        isPluginDiagnostic = true;
      }
    }
    // don't report diagnostics that are internal to plugins
    if (path == null && !isPluginDiagnostic && buildFileMap != null) {
      // associate any locations not specified with a build file
      Optional<File> buildFile = buildFileMap.values().stream().filter(Objects::nonNull).findAny();
      if (buildFile.isPresent()) {
        range = getZeroRange();
        path = buildFile.get().toPath().toString();
      }
    }
    if (path != null) {
      DiagnosticSeverity diagnosticSeverity = getSeverity(severity);
      return convertToDiagnostic(taskPath, path, range, diagnosticSeverity,
          errorCode, message, solutions, documentationLink);
    }
    return null;
  }

  private String locationToUri(String location) {
    // currently Gradle can return a path of the form `build file 'proper path'`
    if (location.length() >= 13 && location.startsWith("build file '") && location.endsWith("'")) {
      location = location.substring(12, location.length() - 1);
    }
    try {
      return Path.of(location).toUri().toString();
    } catch (Exception e) {
      throw new IllegalStateException("Error translating " + location, e);
    }
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
    /*if (solutions != null && !solutions.isEmpty()) {
      // `scala` is currently the only data kind type and allows passing back of ScalaActions, but
      // we don't have the info to create them so just pass back suggestions
      diagnostic.setDataKind("GradleSolutions");
      List<String> solutionsAsStrs = solutions.stream()
          .map(Solution::getSolution)
          .collect(Collectors.toList());
      diagnostic.setData(solutionsAsStrs);
    } else {
      diagnostic.setDataKind("TaskPath");
      diagnostic.setData(taskPath);
    }*/
    String uri = locationToUri(location);
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

  private Range getRange(OffsetInFileLocation offsetInFileLocation) {
    // TODO only way to discover line numbers is to open the file and count back newlines
    // TODO same issue as multi-line diagnostics. Use start of file for now.
    return getZeroRange();
  }

  private Range getRange(LineInFileLocation lineInFileLocation) {
    int startLine = lineInFileLocation.getLine() > 0 ? lineInFileLocation.getLine() - 1 : 0;
    int startCol = lineInFileLocation.getColumn() > 0 ? lineInFileLocation.getColumn() - 1 : 0;
    Position start = new Position(startLine, startCol);
    // TODO - doesn't work for multi-line diagnostics - currently assume it's the same line
    Position end;
    if (lineInFileLocation.getLength() > 0) {
      end = new Position(startLine, startCol + lineInFileLocation.getLength());
    } else {
      end = start;
    }
    return new Range(start, end);
  }

  private Range getZeroRange() {
    Position position = new Position(0, 0);
    return new Range(position, position);
  }
}
