package com.microsoft.java.bs.core.internal.managers;

import ch.epfl.scala.bsp4j.TextDocumentIdentifier;

record TaskPathDoc(String taskPath,
                   TextDocumentIdentifier textDocId) {

  TaskPathDoc(String taskPath, Problem problem) {
    this(taskPath, problem.textDocument());
  }
}