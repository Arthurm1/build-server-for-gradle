// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.reporter;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.CompileReport;
import ch.epfl.scala.bsp4j.CompileTask;
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams;
import ch.epfl.scala.bsp4j.StatusCode;
import ch.epfl.scala.bsp4j.TaskFinishDataKind;
import ch.epfl.scala.bsp4j.TaskFinishParams;
import ch.epfl.scala.bsp4j.TaskId;
import ch.epfl.scala.bsp4j.TaskProgressParams;
import ch.epfl.scala.bsp4j.TaskStartDataKind;
import ch.epfl.scala.bsp4j.TaskStartParams;
import com.microsoft.java.bs.core.internal.managers.Problem;
import com.microsoft.java.bs.core.internal.managers.ProblemsManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.gradle.tooling.events.FailureResult;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.StartEvent;
import org.gradle.tooling.events.task.TaskSkippedResult;
import org.gradle.tooling.events.task.TaskSuccessResult;

/**
 * A default implementation of {@link ProgressReporter}.
 */
public class DefaultProgressReporter extends ProgressReporter {

  private final Map<String, Set<BuildTargetIdentifier>> taskPathMap;
  private final Map<String, Long> startTimes;
  private final Set<String> cleanTasks;
  private final Set<String> compilingTasks;
  private final ProblemsManager problemsManager;
  private ProblemsReporter problemsReporter;

  /**
   * Instantiates a {@link DefaultProgressReporter}.
   *
   * @param client BSP client to report to.
   * @param originId id of the BSP client message.
   * @param taskPathMap all know task paths to their build targets.
   * @param cleanTasks set of all names of clean task
   * @param compilingTasks set of all names of compile task
   * @param problemsManager state of all BSP diagnostics
   */
  public DefaultProgressReporter(BuildClient client, String originId,
      Map<String, Set<BuildTargetIdentifier>> taskPathMap, Set<String> cleanTasks,
      Set<String> compilingTasks, ProblemsManager problemsManager) {
    super(client, originId);
    this.taskPathMap = taskPathMap;
    this.cleanTasks = cleanTasks;
    this.compilingTasks = compilingTasks;
    this.problemsManager = problemsManager;
    startTimes = new HashMap<>();
    problemsReporter = null;
  }

  public void useProblemReporter() {
    problemsReporter = new ProblemsReporter(originId);
  }

  @Override
  public void statusChanged(ProgressEvent event) {
    if (client != null) {
      String taskPath = ReporterUtils.getTaskPath(event.getDescriptor());
      boolean isCleaning = cleanTasks != null && cleanTasks.contains(taskPath);
      boolean isCompiling = compilingTasks != null && compilingTasks.contains(taskPath);
      boolean isCompileTask = isCleaning || isCompiling;
      TaskId taskId = getTaskId(taskPath);
      Set<BuildTargetIdentifier> targets = taskPathMap != null ? taskPathMap.get(taskPath) : null;
      if (event instanceof StartEvent) {
        if (taskPath != null) {
          startTimes.put(taskPath, event.getEventTime());
        }
        if (isCleaning) {
          problemsManager.targetsClean(targets, taskPathMap);
        } else if (isCompiling) {
          problemsManager.targetsCompile(taskPath);
        }
        taskStarted(taskId, isCompileTask, targets, event.getDisplayName());
      } else if (event instanceof FinishEvent) {
        // collate and send diagnostics before finish is called
        if (isCompileTask) {
          for (PublishDiagnosticsParams params : problemsManager.collateDiagnostics(taskPath,
              taskPathMap)) {
            client.onBuildPublishDiagnostics(params);
          }
        }

        Long compileTimeDuration;
        if (taskPath != null) {
          Long compileStartTime = startTimes.get(taskPath);
          compileTimeDuration = compileStartTime == null ? null
              : event.getEventTime() - compileStartTime;
        } else {
          compileTimeDuration = null;
        }
        OperationResult result = ((FinishEvent) event).getResult();
        boolean upToDate = result instanceof TaskSuccessResult
            && ((TaskSuccessResult) result).isUpToDate();
        boolean skipped = result instanceof TaskSkippedResult;
        StatusCode status = result instanceof FailureResult ? StatusCode.ERROR : StatusCode.OK;
        boolean noop = skipped || upToDate;

        taskFinished(taskId, taskPath, isCompileTask, targets, event.getDisplayName(),
            compileTimeDuration, status, noop);
      } else {
        if (problemsReporter != null) {
          Problem problem = problemsReporter.convertToDiagnostic(taskPath, event);
          if (problem != null) {
            problemsManager.addDiagnostics(problem);
          }
        }
        taskInProgress(taskId, isCompileTask, targets, event.getDisplayName());
      }
    }
  }

  private void taskStarted(TaskId taskId, boolean isCompileTask, Set<BuildTargetIdentifier> targets,
      String message) {
    long eventTime = System.currentTimeMillis();
    if (targets == null || targets.isEmpty()) {
      TaskStartParams startParam = new TaskStartParams(taskId);
      startParam.setEventTime(eventTime);
      startParam.setMessage(message);
      client.onBuildTaskStart(startParam);
    } else {
      for (BuildTargetIdentifier btId : targets) {
        TaskStartParams startParam = new TaskStartParams(taskId);
        startParam.setEventTime(eventTime);
        startParam.setMessage(message);
        if (isCompileTask) {
          startParam.setDataKind(TaskStartDataKind.COMPILE_TASK);
          startParam.setData(new CompileTask(btId));
        }
        client.onBuildTaskStart(startParam);
      }
    }
  }

  private void taskInProgress(TaskId taskId, boolean isCompileTask,
      Set<BuildTargetIdentifier> targets, String message) {
    long eventTime = System.currentTimeMillis();
    if (targets == null || targets.isEmpty()) {
      TaskProgressParams progressParam = new TaskProgressParams(taskId);
      progressParam.setEventTime(eventTime);
      progressParam.setMessage(message);
      client.onBuildTaskProgress(progressParam);
    } else {
      for (BuildTargetIdentifier btId : targets) {
        TaskProgressParams progressParam = new TaskProgressParams(taskId);
        progressParam.setEventTime(eventTime);
        progressParam.setMessage(message);
        if (isCompileTask) {
          // TODO there appears to be no TaskProgressDataKind.COMPILE_TASK
          progressParam.setDataKind(TaskStartDataKind.COMPILE_TASK);
          progressParam.setData(new CompileTask(btId));
        }
        client.onBuildTaskProgress(progressParam);
      }
    }
  }

  private void taskFinished(TaskId taskId, String taskPath, boolean isCompileTask,
      Set<BuildTargetIdentifier> targets, String message, Long compileTimeDuration,
      StatusCode statusCode, boolean noOp) {
    long eventTime = System.currentTimeMillis();
    if (targets == null || targets.isEmpty()) {
      TaskFinishParams endParam = new TaskFinishParams(taskId, statusCode);
      endParam.setEventTime(eventTime);
      endParam.setMessage(message);
      client.onBuildTaskFinish(endParam);
    } else {
      int errors;
      int warnings;
      if (isCompileTask) {
        errors = problemsManager.getErrorCount(taskPath);
        warnings = problemsManager.getWarningCount(taskPath);
      } else {
        errors = 0;
        warnings = 0;
      }
      // only send the finished task once
      // GradleApiConnector calls this for all tasks to make sure finished is sent in error cases
      // TODO put condition check back
      // if (tasksFinished.add(taskPath)) {
      for (BuildTargetIdentifier btId : targets) {
        TaskFinishParams endParam = new TaskFinishParams(taskId, statusCode);
        endParam.setEventTime(eventTime);
        endParam.setMessage(message);
        if (isCompileTask) {
          CompileReport compileReport = new CompileReport(btId, errors, warnings);
          compileReport.setNoOp(noOp);
          compileReport.setOriginId(originId);
          compileReport.setTime(compileTimeDuration);
          endParam.setDataKind(TaskFinishDataKind.COMPILE_REPORT);
          endParam.setData(compileReport);
        }
        client.onBuildTaskFinish(endParam);
      }
    }
  }
}
