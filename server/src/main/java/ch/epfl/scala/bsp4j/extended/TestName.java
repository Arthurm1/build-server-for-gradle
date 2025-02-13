// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package ch.epfl.scala.bsp4j.extended;

import java.util.Objects;

import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

/**
 * BSP TestName, which contains the test name and the test hierarchy
 * e.g. method/class/suite
 */
public class TestName {
  
  private String displayName;
  
  private String suiteName;

  private String className;

  private String methodName;

  private TestName parent;

  /**
   * Create a new instance of {@link TestName}.
   *
   * @param displayName test display name
   * @param suiteName test suite name
   * @param className test class name
   * @param methodName test method name
   */
  public TestName(@NonNull String displayName, String suiteName,
      String className, String methodName) {
    this.displayName = displayName;
    this.suiteName = suiteName;
    this.className = className;
    this.methodName = methodName;
  }

  /**
   * get the test display name.
   *
   * @return the test display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * set the test display name.
   *
   * @param displayName the test display name
   */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /**
   * get the test suite name.
   *
   * @return the test suite name
   */
  public String getSuiteName() {
    return suiteName;
  }

  /**
   * set the test suite name.
   *
   * @param suiteName the test suite name
   */
  public void setSuiteName(String suiteName) {
    this.suiteName = suiteName;
  }

  /**
   * get the test class name.
   *
   * @return the test class name
   */
  public String getClassName() {
    return className;
  }

  /**
   * set the test class name.
   *
   * @param className the test class name
   */
  public void setClassName(String className) {
    this.className = className;
  }

  /**
   * get the test method name.
   *
   * @return the test method name
   */
  public String getMethodName() {
    return methodName;
  }

  /**
   * set the test method name.
   *
   * @param methodName the test method name
   */
  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  /**
   * get the test parent.
   *
   * @return the test parent
   */
  public TestName getParent() {
    return parent;
  }

  /**
   * set the test parent.
   *
   * @param parent the test parent
   */
  public void setParent(TestName parent) {
    this.parent = parent;
  }

  @Override
  @Pure
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    b.add("displayName", this.displayName);
    b.add("suiteName", this.suiteName);
    b.add("className", this.className);
    b.add("methodName", this.methodName);
    b.add("parent", this.parent);
    return b.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(displayName, suiteName, className, methodName, parent);
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
    TestName other = (TestName) obj;
    return Objects.equals(displayName, other.displayName)
        && Objects.equals(suiteName, other.suiteName)
        && Objects.equals(className, other.className)
        && Objects.equals(methodName, other.methodName)
        && Objects.equals(parent, other.parent);
  }
}