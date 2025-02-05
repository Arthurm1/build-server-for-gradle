// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSet;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSets;
import com.microsoft.java.bs.gradle.model.impl.DefaultJavaExtension;
import org.junit.jupiter.api.Test;

import com.microsoft.java.bs.core.internal.log.BuildTargetChangeInfo;
import com.microsoft.java.bs.core.internal.model.GradleBuildTarget;
import com.microsoft.java.bs.gradle.model.BuildTargetDependency;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.model.impl.DefaultBuildTargetDependency;

import ch.epfl.scala.bsp4j.BuildTarget;
import ch.epfl.scala.bsp4j.JvmBuildTarget;
import ch.epfl.scala.bsp4j.extended.JvmBuildTargetEx;

class BuildTargetManagerTest {

  @Test
  void testStore() {
    DefaultGradleSourceSet gradleSourceSet = getTestGradleSourceSet();
    gradleSourceSet.setSourceSetName("test");
    gradleSourceSet.setProjectName("name");
    gradleSourceSet.setHasTests(true);
    DefaultGradleSourceSets gradleSourceSets =
        new DefaultGradleSourceSets(List.of(gradleSourceSet));
    BuildTargetManager manager = new BuildTargetManager();
    manager.store(gradleSourceSets);

    List<GradleBuildTarget> list = manager.getAllGradleBuildTargets();
    BuildTarget buildTarget = list.get(0).getBuildTarget();
    assertTrue(buildTarget.getTags().contains("test"));
    assertTrue(buildTarget.getId().getUri().contains("?sourceset=test"));
    assertEquals("name [test]", buildTarget.getDisplayName());
  }

  @Test
  void testJvmExtension() {
    DefaultGradleSourceSet gradleSourceSet = getTestGradleSourceSet();
    DefaultGradleSourceSets gradleSourceSets =
        new DefaultGradleSourceSets(List.of(gradleSourceSet));
    
    BuildTargetManager manager = new BuildTargetManager();
    manager.store(gradleSourceSets);

    List<GradleBuildTarget> list = manager.getAllGradleBuildTargets();
    BuildTarget buildTarget = list.get(0).getBuildTarget();

    assertEquals("jvm", buildTarget.getDataKind());
    JvmBuildTarget jvmBt = (JvmBuildTarget) buildTarget.getData();
    assertEquals("17", jvmBt.getJavaVersion());
  }

  @Test
  void testJvmExtensionEx() {
    DefaultGradleSourceSet gradleSourceSet = getTestGradleSourceSet();
    DefaultGradleSourceSets gradleSourceSets =
        new DefaultGradleSourceSets(List.of(gradleSourceSet));
    
    BuildTargetManager manager = new BuildTargetManager();
    manager.store(gradleSourceSets);

    List<GradleBuildTarget> list = manager.getAllGradleBuildTargets();
    BuildTarget buildTarget = list.get(0).getBuildTarget();

    assertEquals("jvm", buildTarget.getDataKind());
    JvmBuildTargetEx jvmBt = (JvmBuildTargetEx) buildTarget.getData();
    assertEquals("8.0", jvmBt.getGradleVersion());
    assertEquals("17", jvmBt.getSourceCompatibility());
    assertEquals("17", jvmBt.getTargetCompatibility());
  }

  @Test
  void testBuildTargetDependency() {
    File fooProjectDir = new File("foo");
    String fooSourceSetName = "main";
    DefaultGradleSourceSet gradleSourceSetFoo = getTestGradleSourceSet();
    gradleSourceSetFoo.setProjectPath(":foo");
    gradleSourceSetFoo.setProjectDir(fooProjectDir);
    gradleSourceSetFoo.setSourceSetName(fooSourceSetName);

    BuildTargetDependency buildTargetDependency = new DefaultBuildTargetDependency(
        fooProjectDir.getAbsolutePath(), fooSourceSetName);
    Set<BuildTargetDependency> dependencies = new HashSet<>();
    dependencies.add(buildTargetDependency);
    DefaultGradleSourceSet gradleSourceSetBar = getTestGradleSourceSet();
    gradleSourceSetBar.setProjectPath(":bar");
    gradleSourceSetBar.setProjectDir(new File("bar"));
    gradleSourceSetBar.setBuildTargetDependencies(dependencies);

    DefaultGradleSourceSets gradleSourceSets = new DefaultGradleSourceSets(
        List.of(gradleSourceSetFoo, gradleSourceSetBar));

    BuildTargetManager manager = new BuildTargetManager();
    manager.store(gradleSourceSets);

    List<GradleBuildTarget> list = manager.getAllGradleBuildTargets();
    BuildTarget buildTargetFoo = list.stream()
        .filter(bt -> bt.getBuildTarget().getId().getUri().contains("foo"))
        .findFirst()
        .get()
        .getBuildTarget();
    BuildTarget buildTargetBar = list.stream()
        .filter(bt -> bt.getBuildTarget().getId().getUri().contains("bar"))
        .findFirst()
        .get()
        .getBuildTarget();

    assertTrue(buildTargetBar.getDependencies().contains(buildTargetFoo.getId()));
  }

  @Test
  void testDidChange() {
    DefaultGradleSourceSet sourceSet1 = getTestGradleSourceSet();
    DefaultGradleSourceSet sourceSet2 = getTestGradleSourceSet();
    sourceSet2.setProjectDir(new File("was test"));
    BuildTargetChangeInfo change = new BuildTargetChangeInfo(null, sourceSet1, sourceSet2);
    assertEquals("GradleSourceSet: (ProjectDir: (test -> was test))", change.getDifference());
    DefaultJavaExtension javaExt2 = getTestJavaExtension();
    javaExt2.setSourceCompatibility("9");
    sourceSet2.getExtensions().put(SupportedLanguages.JAVA.getBspName(), javaExt2);
    assertEquals("GradleSourceSet: (ProjectDir: (test -> was test), Extensions: (0: (key:java"
        + " value: (SourceCompatibility: (17 -> 9)))))", change.getDifference());
  }

  private DefaultJavaExtension getTestJavaExtension() {
    DefaultJavaExtension javaExtension = new DefaultJavaExtension();
    javaExtension.setJavaVersion("17");
    javaExtension.setSourceCompatibility("17");
    javaExtension.setTargetCompatibility("17");
    return javaExtension;
  }

  private DefaultGradleSourceSet getTestGradleSourceSet() {
    DefaultGradleSourceSet sourceSet = new DefaultGradleSourceSet();
    sourceSet.setGradleVersion("8.0");
    sourceSet.setProjectDir(new File("test"));
    sourceSet.setRootDir(new File("test"));
    sourceSet.setSourceSetName("main");
    sourceSet.setSourceDirs(Collections.emptySet());
    sourceSet.setGeneratedSourceDirs(Collections.emptySet());
    sourceSet.setResourceDirs(Collections.emptySet());
    sourceSet.setModuleDependencies(Collections.emptySet());
    sourceSet.setBuildTargetDependencies(Collections.emptySet());
    DefaultJavaExtension javaExtension = getTestJavaExtension();
    Map<String, LanguageExtension> extensions = new HashMap<>();
    extensions.put(SupportedLanguages.JAVA.getBspName(), javaExtension);
    sourceSet.setExtensions(extensions);
    return sourceSet;
  }


}
