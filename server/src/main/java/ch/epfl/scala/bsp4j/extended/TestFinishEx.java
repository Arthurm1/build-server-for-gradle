// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package ch.epfl.scala.bsp4j.extended;

import java.util.Objects;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

import ch.epfl.scala.bsp4j.TestFinish;
import ch.epfl.scala.bsp4j.TestStatus;

/**
 * Extended {@link TestFinish}, which contains the Suite, class, method.
 * {@link TestFinish} only contains file location which Gradle doesn't have.
 */
public class TestFinishEx extends TestFinish {

  private TestName testName;

  private String stackTrace;

  /**
   * Create a new instance of {@link TestFinishEx}.
   *
   * @param displayName the test display name
   * @param status the test status
   * @param testName the test name
   */
  public TestFinishEx(@NonNull String displayName, @NonNull TestStatus status,
      @NonNull TestName testName) {
    super(displayName, status);
    this.testName = testName;
  }

  /**
   * get the test name.
   *
   * @return the test name
   */
  public TestName getTestName() {
    return testName;
  }

  /**
   * set the test name.
   *
   * @param testName the test name
   */
  public void setTestName(TestName testName) {
    this.testName = testName;
  }

  /**
   * get the error stack trace.
   *
   * @return the error stack trace
   */
  public String getStackTrace() {
    return stackTrace;
  }

  /**
   * set the error stack trace.
   *
   * @param stackTrace the error stack trace
   */
  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  @Override
  @Pure
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    b.add(super.toString());
    b.add("testName", this.testName);
    b.add("stackTrace", this.stackTrace);
    return b.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(testName, stackTrace);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TestFinishEx other = (TestFinishEx) obj;
    return Objects.equals(testName, other.testName)
        && Objects.equals(stackTrace, other.stackTrace);
  }
}