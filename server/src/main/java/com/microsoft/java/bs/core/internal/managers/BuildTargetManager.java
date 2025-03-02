// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.managers;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import ch.epfl.scala.bsp4j.JvmBuildTarget;
import ch.epfl.scala.bsp4j.ScalaBuildTarget;
import ch.epfl.scala.bsp4j.ScalaPlatform;
import ch.epfl.scala.bsp4j.extended.KotlinBuildTarget;

import com.microsoft.java.bs.core.internal.log.BuildTargetChangeInfo;
import com.microsoft.java.bs.core.internal.model.GradleBuildTarget;
import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.impl.DefaultBuildTargetDependency;

import ch.epfl.scala.bsp4j.BuildTarget;
import ch.epfl.scala.bsp4j.BuildTargetCapabilities;
import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.BuildTargetTag;
import ch.epfl.scala.bsp4j.extended.JvmBuildTargetEx;

/**
 * Build targets manager.
 */
public class BuildTargetManager {

  private volatile Map<BuildTargetIdentifier, GradleBuildTarget> cache;

  private volatile Map<Path, BuildTargetIdentifier> sourceDirsMap;

  /**
   * constructor.
   */
  public BuildTargetManager() {
    this.cache = new HashMap<>();
    this.sourceDirsMap = new HashMap<>();
  }

  /**
   * Store the Gradle source sets.
   *
   * @param gradleSourceSets the new source sets.
   * @return A list containing identifiers of changed build targets.
   */
  public List<BuildTargetChangeInfo> store(GradleSourceSets gradleSourceSets,
      Function<GradleSourceSet, String> displayNameMaker) {
    Map<BuildTargetIdentifier, GradleBuildTarget> newCache = new HashMap<>();
    Map<BuildTargetDependency, BuildTargetIdentifier> dependencyToBuildTargetId = new HashMap<>();
    for (GradleSourceSet sourceSet : gradleSourceSets.getGradleSourceSets()) {
      String sourceSetName = sourceSet.getSourceSetName();
      URI uri = getBuildTargetUri(sourceSet.getProjectDir().toPath().toUri(), sourceSetName);
      List<String> tags = getBuildTargetTags(sourceSet.hasTests());
      BuildTargetIdentifier btId = new BuildTargetIdentifier(uri.toString());
      List<String> languages = new LinkedList<>(sourceSet.getExtensions().keySet());
      BuildTargetCapabilities buildTargetCapabilities = new BuildTargetCapabilities();
      buildTargetCapabilities.setCanCompile(true);
      buildTargetCapabilities.setCanTest(true);
      buildTargetCapabilities.setCanRun(true);
      BuildTarget bt = new BuildTarget(
          btId,
          tags,
          languages,
          Collections.emptyList(),
          buildTargetCapabilities
      );
      bt.setBaseDirectory(sourceSet.getRootDir().toPath().toUri().toString());

      setBuildTarget(sourceSet, bt);

      GradleBuildTarget buildTarget = new GradleBuildTarget(bt, sourceSet);
      newCache.put(btId, buildTarget);
      // Store the relationship between the project/sourceset and the build target id.
      BuildTargetDependency dependency = new DefaultBuildTargetDependency(sourceSet);
      dependencyToBuildTargetId.put(dependency, btId);
    }
    makeDisplayNameUnique(newCache.values(), displayNameMaker);
    updateBuildTargetDependencies(newCache.values(), dependencyToBuildTargetId);

    this.sourceDirsMap = calculateSourceDirsMap(newCache.values());

    Map<BuildTargetIdentifier, GradleBuildTarget> oldCache = cache;
    this.cache = newCache;
    return calculateChanges(oldCache, newCache);
  }

  /**
   * If the build target data has changed in any way then the BSP client needs to be told.
   *
   * @param oldCache all the current build target info
   * @param newCache all the new build target info
   * @return a list of adds/deletes/changes
   */
  private static List<BuildTargetChangeInfo> calculateChanges(
      Map<BuildTargetIdentifier, GradleBuildTarget> oldCache,
      Map<BuildTargetIdentifier, GradleBuildTarget> newCache) {

    List<BuildTargetChangeInfo> changedTargets = new LinkedList<>();

    // any changed or added targets?
    for (BuildTargetIdentifier newBtId : newCache.keySet()) {
      GradleBuildTarget newTarget = newCache.get(newBtId);
      GradleBuildTarget oldTarget = oldCache.get(newBtId);
      // only compare the source set instance, which is the result
      // returned from the gradle plugin.
      if (oldTarget == null) {
        BuildTargetChangeInfo changeInfo = new BuildTargetChangeInfo(newBtId,
            null, newTarget.getSourceSet());
        changedTargets.add(changeInfo);
      } else if (!Objects.equals(oldTarget.getSourceSet(), newTarget.getSourceSet())) {
        BuildTargetChangeInfo changeInfo = new BuildTargetChangeInfo(newBtId,
            oldTarget.getSourceSet(), newTarget.getSourceSet());
        changedTargets.add(changeInfo);
      }
    }

    // any deleted targets?
    for (BuildTargetIdentifier oldBtId : oldCache.keySet()) {
      if (!newCache.containsKey(oldBtId)) {
        GradleSourceSet oldSourceSet = oldCache.get(oldBtId).getSourceSet();
        BuildTargetChangeInfo changeInfo = new BuildTargetChangeInfo(oldBtId,
            oldSourceSet, null);
        changedTargets.add(changeInfo);
      }
    }
    return changedTargets;
  }

  /**
   * Make project display names unique.
   *
   * @param buildTargets all the build targets
   */
  private void makeDisplayNameUnique(Collection<GradleBuildTarget> buildTargets,
      Function<GradleSourceSet, String> displayNameMaker) {
    for (GradleBuildTarget buildTarget : buildTargets) {
      String displayName = displayNameMaker.apply(buildTarget.getSourceSet());
      buildTarget.getBuildTarget().setDisplayName(displayName);
    }
  }

  /**
   * get the build target data for the specified build target id.
   *
   * @param buildTargetId the build target id
   * @return the build target data or null if doesn't exist in the cache
   */
  public GradleBuildTarget getGradleBuildTarget(BuildTargetIdentifier buildTargetId) {
    return cache.get(buildTargetId);
  }

  /**
   * get all the build targets.
   *
   * @return all the build target data in the cache
   */
  public List<GradleBuildTarget> getAllGradleBuildTargets() {
    return new ArrayList<>(cache.values());
  }

  /**
   * get the map of build target source dirs to their build target.
   *
   * @return map of source dirs to respective build target
   */
  public Map<Path, BuildTargetIdentifier> getSourceDirsMap() {
    return new HashMap<>(sourceDirsMap);
  }

  private URI getBuildTargetUri(URI projectUri, String sourceSetName) {
    return URI.create(projectUri.toString() + "?sourceset=" + sourceSetName);
  }

  private List<String> getBuildTargetTags(boolean hasTests) {
    List<String> tags = new ArrayList<>();
    if (hasTests) {
      tags.add(BuildTargetTag.TEST);
    }
    return tags;
  }

  private void setBuildTarget(GradleSourceSet sourceSet, BuildTarget bt) {
    // currently BSP can't support Kotlin + Scala, only Scala + kotlin or Java + Kotlin
    // The next version should output a map of BuildTargets
    KotlinExtension kotlinExtension = SupportedLanguages.KOTLIN.getExtension(sourceSet);
    ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(sourceSet);
    JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(sourceSet);
    if (scalaExtension != null) {
      setScalaBuildTarget(sourceSet, scalaExtension, javaExtension, bt);
    } else if (kotlinExtension != null) {
      setKotlinBuildTarget(sourceSet, kotlinExtension, javaExtension, bt);
    } else if (javaExtension != null) {
      setJvmBuildTarget(sourceSet, javaExtension, bt);
    }
  }

  private JvmBuildTarget getJvmBuildTarget(GradleSourceSet sourceSet, JavaExtension javaExtension) {
    // See: https://build-server-protocol.github.io/docs/extensions/jvm#jvmbuildtarget
    JvmBuildTargetEx buildTarget = new JvmBuildTargetEx();

    buildTarget.setJavaHome(javaExtension.getJavaHome() == null ? ""
        : javaExtension.getJavaHome().toPath().toUri().toString());
    buildTarget.setJavaVersion(javaExtension.getJavaVersion() == null ? ""
        : javaExtension.getJavaVersion());
    buildTarget.setGradleVersion(sourceSet.getGradleVersion() == null ? ""
        : sourceSet.getGradleVersion());
    buildTarget.setSourceCompatibility(javaExtension.getSourceCompatibility() == null ? ""
        : javaExtension.getSourceCompatibility());
    buildTarget.setTargetCompatibility(javaExtension.getTargetCompatibility() == null ? ""
        : javaExtension.getTargetCompatibility());

    return buildTarget;
  }

  private void setJvmBuildTarget(GradleSourceSet sourceSet, JavaExtension javaExtension,
      BuildTarget bt) {
    bt.setDataKind("jvm");
    bt.setData(getJvmBuildTarget(sourceSet, javaExtension));
  }

  private ScalaBuildTarget getScalaBuildTarget(GradleSourceSet sourceSet,
      ScalaExtension scalaExtension, JavaExtension javaExtension) {
    // See: https://build-server-protocol.github.io/docs/extensions/scala#scalabuildtarget
    JvmBuildTarget jvmBuildTarget = getJvmBuildTarget(sourceSet, javaExtension);
    List<String> scalaJars = scalaExtension.getScalaJars().stream()
          .map(file -> file.toPath().toUri().toString())
          .collect(Collectors.toList());
    ScalaBuildTarget scalaBuildTarget = new ScalaBuildTarget(
        scalaExtension.getScalaOrganization() == null ? "" : scalaExtension.getScalaOrganization(),
        scalaExtension.getScalaVersion() == null ? "" : scalaExtension.getScalaVersion(),
        scalaExtension.getScalaBinaryVersion() == null ? ""
            : scalaExtension.getScalaBinaryVersion(),
        ScalaPlatform.JVM,
        scalaJars
    );
    scalaBuildTarget.setJvmBuildTarget(jvmBuildTarget);
    return scalaBuildTarget;
  }

  private void setScalaBuildTarget(GradleSourceSet sourceSet, ScalaExtension scalaExtension,
      JavaExtension javaExtension, BuildTarget bt) {
    bt.setDataKind("scala");
    bt.setData(getScalaBuildTarget(sourceSet, scalaExtension, javaExtension));
  }

  private KotlinBuildTarget getKotlinBuildTarget(GradleSourceSet sourceSet,
        KotlinExtension kotlinExtension, JavaExtension javaExtension) {
    JvmBuildTarget jvmBuildTarget = getJvmBuildTarget(sourceSet, javaExtension);
    // TODO set associates from kotlinExtension.getKotlinAssociates() once it implemented.
    List<BuildTargetIdentifier> associates = Collections.emptyList();

    KotlinBuildTarget kotlinBuildTarget = new KotlinBuildTarget(
            kotlinExtension.getKotlinLanguageVersion() == null ? ""
                : kotlinExtension.getKotlinLanguageVersion(),
            kotlinExtension.getKotlinApiVersion() == null ? ""
                : kotlinExtension.getKotlinApiVersion(),
            kotlinExtension.getKotlincOptions(),
            associates,
            jvmBuildTarget
    );
    kotlinBuildTarget.setJvmBuildTarget(jvmBuildTarget);
    return kotlinBuildTarget;
  }

  private void setKotlinBuildTarget(GradleSourceSet sourceSet, KotlinExtension kotlinExtension,
                                   JavaExtension javaExtension, BuildTarget bt) {
    bt.setDataKind("kotlin");
    bt.setData(getKotlinBuildTarget(sourceSet, kotlinExtension, javaExtension));
  }

  /**
   * Iterate all the gradle build targets, and update their dependencies with
   * the help of 'build target to id' mapping.
   */
  private void updateBuildTargetDependencies(
      Collection<GradleBuildTarget> gradleBuildTargets,
      Map<BuildTargetDependency, BuildTargetIdentifier> dependencyToBuildTargetId
  ) {
    for (GradleBuildTarget gradleBuildTarget : gradleBuildTargets) {
      Set<BuildTargetDependency> buildTargetDependencies =
          gradleBuildTarget.getSourceSet().getBuildTargetDependencies();
      if (buildTargetDependencies != null) {
        List<BuildTargetIdentifier> btDependencies = buildTargetDependencies.stream()
            .map(dependencyToBuildTargetId::get)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        gradleBuildTarget.getBuildTarget().setDependencies(btDependencies);
      }
    }
  }

  /**
   * create a map of all known sourceDirs to the build target they belong to.
   */
  private Map<Path, BuildTargetIdentifier> calculateSourceDirsMap(
      Collection<GradleBuildTarget> buildTargets) {
    Map<Path, BuildTargetIdentifier> sourceDirsMap = new HashMap<>();
    for (GradleBuildTarget buildTarget : buildTargets) {
      Set<File> sourceDirs = buildTarget.getSourceSet().getSourceDirs();
      BuildTargetIdentifier btId = buildTarget.getBuildTarget().getId();
      for (File sourceDir : sourceDirs) {
        sourceDirsMap.put(sourceDir.toPath(), btId);
      }
    }
    return sourceDirsMap;
  }
}
