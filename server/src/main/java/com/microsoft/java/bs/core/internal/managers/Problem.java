package com.microsoft.java.bs.core.internal.managers;

import ch.epfl.scala.bsp4j.Diagnostic;
import ch.epfl.scala.bsp4j.TextDocumentIdentifier;

/**
 * Diagnostic info for errors and warnings.
 */
public record Problem(String taskPath,
                      String originId,
                      TextDocumentIdentifier textDocument,
                      Diagnostic diagnostic) {
}
