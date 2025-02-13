// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.log;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.microsoft.java.bs.gradle.model.Artifact;
import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GroovyExtension;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;

/**
 * Class to report on build target changes.
 */
public class BuildTargetChangeInfo {

  private final BuildTargetIdentifier btId;
  private final GradleSourceSet oldSourceSet;
  private final GradleSourceSet newSourceSet;

  /**
   * Constructor.
   *
   * @param btId build target (new or old)
   * @param oldSourceSet old build target setup
   * @param newSourceSet new build target setup
   */
  public BuildTargetChangeInfo(BuildTargetIdentifier btId, GradleSourceSet oldSourceSet,
      GradleSourceSet newSourceSet) {
    this.btId = btId;
    this.oldSourceSet = oldSourceSet;
    this.newSourceSet = newSourceSet;
  }

  /**
   * has the build target config changed.
   *
   * @return flag indicating change.
   */
  public boolean hasChanged() {
    return oldSourceSet != null && newSourceSet != null;
  }

  /**
   * is the build target new.
   *
   * @return flag indicating new.
   */
  public boolean isAdded() {
    return oldSourceSet == null && newSourceSet != null;
  }

  /**
   * has the build target been deleted.
   *
   * @return flag indicating deletion.
   */
  public boolean isRemoved() {
    return oldSourceSet != null && newSourceSet == null;
  }

  /**
   * Get the build target id.
   *
   * @return build target id
   */
  public BuildTargetIdentifier getBtId() {
    return btId;
  }

  private static class Difference<S, T> {
    private final String name;
    private final Function<S, T> accessor;

    Difference(String name, Function<S, T> accessor) {
      this.name = name;
      this.accessor = accessor;
    }

    static <S, T> Difference<S, T> of(String name, Function<S, T> accessor) {
      return new Difference<>(name, accessor);
    }

    @SuppressWarnings("unchecked")
    T apply(Object value) {
      return accessor.apply((S) value);
    }

    String getName() {
      return name;
    }
  }

  private static class Differences<S> {
    private final Class<S> clazz;
    private final List<Difference<S, ?>> differences;

    Differences(Class<S> clazz, List<Difference<S, ?>> differences) {
      this.clazz = clazz;
      this.differences = differences;
    }

    static <S> Differences<S> of(Class<S> clazz, List<Difference<S, ?>> list) {
      return new Differences<>(clazz, list);
    }
  }

  private static class DifferenceMap {
    private final List<Differences<?>> diffs;

    DifferenceMap(List<Differences<?>> diffs) {
      this.diffs = diffs;
    }

    <S> List<? extends Difference<?, ?>> getDifferences(Class<S> clazz) {
      for (Differences<?> diff : diffs) {
        if (diff.clazz.isAssignableFrom(clazz)) {
          return diff.differences;
        }
      }
      return null;
    }
  }

  private static String getCollectionDiff(Iterator<?> oldIter, Iterator<?> newIter,
        DifferenceMap diffMap) {
    StringBuilder diffs = new StringBuilder();
    int i = 0;
    boolean first = true;
    while (oldIter.hasNext() && newIter.hasNext()) {
      Object oldObj = oldIter.next();
      Object newObj = newIter.next();
      String diff = getDifference(Integer.toString(i), oldObj, newObj, diffMap);
      if (diff != null) {
        if (!first) {
          diffs.append(", ");
        }
        diffs.append(diff);
        first = false;
      }
      i++;
    }
    return diffs.toString();
  }

  private static String getDifference(String name, Object oldObject, Object newObject,
      DifferenceMap diffMap) {

    if (!Objects.equals(oldObject, newObject)) {
      final String diff;
      if (oldObject == null || newObject == null) {
        diff = oldObject + " -> " + newObject;
      } else {
        List<? extends Difference<?, ?>> diffs = diffMap.getDifferences(oldObject.getClass());
        if (diffs == null) {
          if (newObject instanceof Map<?, ?>) {
            oldObject = ((Map<?, ?>) oldObject).entrySet();
            newObject = ((Map<?, ?>) newObject).entrySet();
          }
          if (newObject instanceof Collection<?>) {
            Collection<?> oldCollection = (Collection<?>) oldObject;
            Collection<?> newCollection = (Collection<?>) newObject;
            if (oldCollection.size() != newCollection.size()) {
              return oldObject + " -> " + newObject;
            }
            diff = getCollectionDiff(oldCollection.iterator(), newCollection.iterator(), diffMap);
          } else if (newObject instanceof Map.Entry<?, ?>) {
            Map.Entry<?, ?> oldNode = (Map.Entry<?, ?>) oldObject;
            Map.Entry<?, ?> newNode = (Map.Entry<?, ?>) newObject;
            String keyDiff = getDifference("key:", oldNode.getKey(), newNode.getKey(), diffMap);
            if (keyDiff != null) {
              diff = keyDiff;
            } else {
              diff = getDifference("key:" + oldNode.getKey() + " value",
                  oldNode.getValue(), newNode.getValue(), diffMap);
            }
          } else {
            diff = oldObject + " -> " + newObject;
          }
        } else {
          final Object oldValue = oldObject;
          final Object newValue = newObject;
          diff = diffs.stream()
              .map(difference -> {
                Object oldResult = difference.apply(oldValue);
                Object newResult = difference.apply(newValue);
                return getDifference(difference.getName(), oldResult, newResult, diffMap);
              })
              .filter(Objects::nonNull)
              .collect(Collectors.joining(", "));
        }
      }
      if (diff == null || diff.isEmpty()) {
        String oldInfo = oldObject.getClass() + " " + oldObject;
        String newInfo = newObject.getClass() + " " + newObject;
        return name + " (Failed to find diff " + oldInfo + " -> " + newInfo + ")";
      }
      return name + ": (" + diff + ")";
    }
    return null;
  }

  /**
   * Get the minimal differences of the 2 source sets.
   *
   * @return a String representing just the fields of what's different
   */
  public String getDifference() {
    if (oldSourceSet == null || newSourceSet == null) {
      return null;
    }
    List<Differences<?>> diffs = new ArrayList<>();
    diffs.add(Differences.of(GradleSourceSet.class, List.of(
        Difference.of("GradleVersion", GradleSourceSet::getGradleVersion),
        Difference.of("ProjectName", GradleSourceSet::getProjectName),
        Difference.of("ProjectPath", GradleSourceSet::getProjectPath),
        Difference.of("ProjectDir", GradleSourceSet::getProjectDir),
        Difference.of("RootDir", GradleSourceSet::getRootDir),
        Difference.of("SourceSetName", GradleSourceSet::getSourceSetName),
        Difference.of("ClassesTaskName", GradleSourceSet::getClassesTaskName),
        Difference.of("CleanTaskName", GradleSourceSet::getCleanTaskName),
        Difference.of("TaskNames", GradleSourceSet::getTaskNames),
        Difference.of("SourceDirs", GradleSourceSet::getSourceDirs),
        Difference.of("GeneratedSourceDirs", GradleSourceSet::getGeneratedSourceDirs),
        Difference.of("ResourceDirs", GradleSourceSet::getResourceDirs),
        Difference.of("SourceOutputDirs", GradleSourceSet::getSourceOutputDirs),
        Difference.of("ResourceOutputDirs", GradleSourceSet::getResourceOutputDirs),
        Difference.of("ArchiveOutputFiles", GradleSourceSet::getArchiveOutputFiles),
        Difference.of("CompileClasspath", GradleSourceSet::getCompileClasspath),
        Difference.of("RuntimeClasspath", GradleSourceSet::getRuntimeClasspath),
        Difference.of("ModuleDependencies", GradleSourceSet::getModuleDependencies),
        Difference.of("BuildTargetDependencies", GradleSourceSet::getBuildTargetDependencies),
        Difference.of("HasTests", GradleSourceSet::hasTests),
        Difference.of("Extensions", GradleSourceSet::getExtensions))));

    diffs.add(Differences.of(GroovyExtension.class, List.of(
        Difference.of("SourceDirs", GroovyExtension::getSourceDirs),
        Difference.of("GeneratedSourceDirs", GroovyExtension::getGeneratedSourceDirs),
        Difference.of("ClassesDir", GroovyExtension::getClassesDir),
        Difference.of("CompileTaskName", GroovyExtension::getCompileTaskName))));

    diffs.add(Differences.of(KotlinExtension.class, List.of(
        Difference.of("SourceDirs", KotlinExtension::getSourceDirs),
        Difference.of("GeneratedSourceDirs", KotlinExtension::getGeneratedSourceDirs),
        Difference.of("ClassesDir", KotlinExtension::getClassesDir),
        Difference.of("CompileTaskName", KotlinExtension::getCompileTaskName),
        Difference.of("KotlinLanguageVersion", KotlinExtension::getKotlinLanguageVersion),
        Difference.of("KotlinApiVersion", KotlinExtension::getKotlinApiVersion),
        Difference.of("KotlincOptions", KotlinExtension::getKotlincOptions),
        Difference.of("KotlinAssociates", KotlinExtension::getKotlinAssociates))));

    diffs.add(Differences.of(JavaExtension.class, List.of(
        Difference.of("SourceDirs", JavaExtension::getSourceDirs),
        Difference.of("GeneratedSourceDirs", JavaExtension::getGeneratedSourceDirs),
        Difference.of("ClassesDir", JavaExtension::getClassesDir),
        Difference.of("CompileTaskName", JavaExtension::getCompileTaskName),
        Difference.of("JavaHome", JavaExtension::getJavaHome),
        Difference.of("JavaVersion", JavaExtension::getJavaVersion),
        Difference.of("SourceCompatibility", JavaExtension::getSourceCompatibility),
        Difference.of("TargetCompatibility", JavaExtension::getTargetCompatibility),
        Difference.of("CompilerArgs", JavaExtension::getCompilerArgs))));

    diffs.add(Differences.of(ScalaExtension.class, List.of(
        Difference.of("SourceDirs", ScalaExtension::getSourceDirs),
        Difference.of("GeneratedSourceDirs", ScalaExtension::getGeneratedSourceDirs),
        Difference.of("ClassesDir", ScalaExtension::getClassesDir),
        Difference.of("CompileTaskName", ScalaExtension::getCompileTaskName),
        Difference.of("ScalaCompilerArgs", ScalaExtension::getScalaCompilerArgs),
        Difference.of("ScalaOrganization", ScalaExtension::getScalaOrganization),
        Difference.of("ScalaVersion", ScalaExtension::getScalaVersion),
        Difference.of("ScalaBinaryVersion", ScalaExtension::getScalaBinaryVersion),
        Difference.of("ScalaJars", ScalaExtension::getScalaJars))));

    diffs.add(Differences.of(BuildTargetDependency.class, List.of(
        Difference.of("ProjectDir", BuildTargetDependency::getProjectDir),
        Difference.of("SourceSetName", BuildTargetDependency::getSourceSetName))));

    diffs.add(Differences.of(GradleModuleDependency.class, List.of(
        Difference.of("Group", GradleModuleDependency::getGroup),
        Difference.of("Module", GradleModuleDependency::getModule),
        Difference.of("Version", GradleModuleDependency::getVersion),
        Difference.of("Artifacts", GradleModuleDependency::getArtifacts))));

    diffs.add(Differences.of(Artifact.class, List.of(
        Difference.of("Uri", Artifact::getUri),
        Difference.of("Classifier", Artifact::getClassifier))));

    DifferenceMap diffMap = new DifferenceMap(diffs);
    return getDifference("GradleSourceSet", oldSourceSet, newSourceSet, diffMap);
  }
}
