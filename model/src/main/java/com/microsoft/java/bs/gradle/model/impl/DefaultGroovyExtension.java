// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.impl;

import java.io.File;
import java.util.Objects;
import java.util.Set;

import com.microsoft.java.bs.gradle.model.GroovyExtension;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;

/**
 * Default implementation of {@link GroovyExtension}.
 */
public class DefaultGroovyExtension implements GroovyExtension {
  private static final long serialVersionUID = 1L;
  
  private Set<File> sourceDirs;

  private Set<File> generatedSourceDirs;

  private String compileTaskName;

  private File classesDir;

  public DefaultGroovyExtension() {}

  /**
   * Copy constructor.
   *
   * @param groovyExtension the GroovyExtension to copy from.
   */
  public DefaultGroovyExtension(GroovyExtension groovyExtension) {
    this.sourceDirs = groovyExtension.getSourceDirs();
    this.generatedSourceDirs = groovyExtension.getGeneratedSourceDirs();
    this.compileTaskName = groovyExtension.getCompileTaskName();
    this.classesDir = groovyExtension.getClassesDir();
  }

  @Override
  public Set<File> getSourceDirs() {
    return sourceDirs;
  }

  public void setSourceDirs(Set<File> sourceDirs) {
    this.sourceDirs = sourceDirs;
  }

  @Override
  public Set<File> getGeneratedSourceDirs() {
    return generatedSourceDirs;
  }

  public void setGeneratedSourceDirs(Set<File> generatedSourceDirs) {
    this.generatedSourceDirs = generatedSourceDirs;
  }

  @Override
  public String getCompileTaskName() {
    return compileTaskName;
  }

  public void setCompileTaskName(String compileTaskName) {
    this.compileTaskName = compileTaskName;
  }

  @Override
  public File getClassesDir() {
    return classesDir;
  }

  public void setClassesDir(File classesDir) {
    this.classesDir = classesDir;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceDirs, generatedSourceDirs, compileTaskName, classesDir);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DefaultGroovyExtension other = (DefaultGroovyExtension) obj;
    return Objects.equals(sourceDirs, other.sourceDirs)
            && Objects.equals(generatedSourceDirs, other.generatedSourceDirs)
            && Objects.equals(compileTaskName, other.compileTaskName)
            && Objects.equals(classesDir, other.classesDir);
  }

  @Override
  public String toString() {
    return "DefaultGroovyExtension: SourceDirs:" + sourceDirs
        + " GeneratedSourceDirs:" + generatedSourceDirs
        + " CompileTaskName:" + compileTaskName
        + " ClassesDir:" + classesDir;
  }

  @Override
  public boolean isJavaExtension() {
    return false;
  }

  @Override
  public boolean isScalaExtension() {
    return false;
  }

  @Override
  public boolean isGroovyExtension() {
    return true;
  }

  @Override
  public boolean isKotlinExtension() {
    return false;
  }

  @Override
  public JavaExtension getAsJavaExtension() {
    return null;
  }

  @Override
  public ScalaExtension getAsScalaExtension() {
    return null;
  }

  @Override
  public GroovyExtension getAsGroovyExtension() {
    return this;
  }

  @Override
  public KotlinExtension getAsKotlinExtension() {
    return null;
  }
}
