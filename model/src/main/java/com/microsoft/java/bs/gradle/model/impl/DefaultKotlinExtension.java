// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.impl;

import com.microsoft.java.bs.gradle.model.GroovyExtension;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation of {@link KotlinExtension}.
 */
public class DefaultKotlinExtension implements KotlinExtension {
  private static final long serialVersionUID = 1L;
  
  private Set<File> sourceDirs;

  private Set<File> generatedSourceDirs;

  private String compileTaskName;

  private File classesDir;

  private String kotlinLanguageVersion;

  private String kotlinApiVersion;

  private List<String> kotlincOptions;

  private List<String> kotlinAssociates;

  public DefaultKotlinExtension() {}

  /**
   * Copy constructor.
   *
   * @param kotlinExtension the KotlinExtension to copy from.
   */
  public DefaultKotlinExtension(KotlinExtension kotlinExtension) {
    this.sourceDirs = kotlinExtension.getSourceDirs();
    this.generatedSourceDirs = kotlinExtension.getGeneratedSourceDirs();
    this.compileTaskName = kotlinExtension.getCompileTaskName();
    this.classesDir = kotlinExtension.getClassesDir();
    this.kotlinLanguageVersion = kotlinExtension.getKotlinLanguageVersion();
    this.kotlinApiVersion = kotlinExtension.getKotlinApiVersion();
    this.kotlincOptions = kotlinExtension.getKotlincOptions();
    this.kotlinAssociates = kotlinExtension.getKotlinAssociates();
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
  public String getKotlinLanguageVersion() {
    return kotlinLanguageVersion;
  }

  public void setKotlinLanguageVersion(String kotlinLanguageVersion) {
    this.kotlinLanguageVersion = kotlinLanguageVersion;
  }

  @Override
  public String getKotlinApiVersion() {
    return kotlinApiVersion;
  }

  public void setKotlinApiVersion(String kotlinApiVersion) {
    this.kotlinApiVersion = kotlinApiVersion;
  }

  @Override
  public List<String> getKotlincOptions() {
    return kotlincOptions;
  }

  public void setKotlincOptions(List<String> kotlincOptions) {
    this.kotlincOptions = kotlincOptions;
  }

  @Override
  public List<String> getKotlinAssociates() {
    return kotlinAssociates;
  }

  public void setKotlinAssociates(List<String> kotlinAssociates) {
    this.kotlinAssociates = kotlinAssociates;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceDirs, generatedSourceDirs, compileTaskName, classesDir,
              kotlinLanguageVersion, kotlinApiVersion, kotlincOptions, kotlinAssociates);
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
    DefaultKotlinExtension other = (DefaultKotlinExtension) obj;
    return Objects.equals(sourceDirs, other.sourceDirs)
            && Objects.equals(generatedSourceDirs, other.generatedSourceDirs)
            && Objects.equals(compileTaskName, other.compileTaskName)
            && Objects.equals(classesDir, other.classesDir)
            && Objects.equals(kotlinLanguageVersion, other.kotlinLanguageVersion)
            && Objects.equals(kotlinApiVersion, other.kotlinApiVersion)
            && Objects.equals(kotlincOptions, other.kotlincOptions)
            && Objects.equals(kotlinAssociates, other.kotlinAssociates);
  }

  @Override
  public String toString() {
    return "DefaultKotlinExtension: SourceDirs:" + sourceDirs
        + " GeneratedSourceDirs:" + generatedSourceDirs
        + " CompileTaskName:" + compileTaskName
        + " ClassesDir:" + classesDir
        + " KotlinLanguageVersion:" + kotlinLanguageVersion
        + " KotlinApiVersion:" + kotlinApiVersion
        + " KotlincOptions:" + kotlincOptions
        + " KotlinAssociates:" + kotlinAssociates;
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
    return false;
  }

  @Override
  public boolean isKotlinExtension() {
    return true;
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
    return null;
  }

  @Override
  public KotlinExtension getAsKotlinExtension() {
    return this;
  }
}
