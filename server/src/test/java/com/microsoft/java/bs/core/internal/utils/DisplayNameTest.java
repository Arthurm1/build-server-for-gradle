// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.java.bs.core.internal.gradle.Utils;
import com.microsoft.java.bs.core.internal.model.Preferences;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleSourceSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

// display names must be unique and must be valid filenames

// Gradle project and sourceset name validation...
// https://github.com/gradle/gradle/blob/master/subprojects/core/src/main/java/org/gradle/util/internal/NameValidator.java
// So any except [/, \, :, <, >, ", ?, *, |]
// [.] appears to be invalid even though the class shows it's only invalid as leading or trailing
// The above covers valid filenames in  Linux, Windows, Mac
class DisplayNameTest {

  private GradleSourceSet sourceSet(String relativeDir, String projectPath, String projectName,
                                    String sourceSetName) {
    DefaultGradleSourceSet sourceSet = new DefaultGradleSourceSet();
    String tmpDir = System.getProperty("java.io.tmpdir");
    Path path = Paths.get(tmpDir, relativeDir);
    sourceSet.setProjectDir(path.toFile());
    sourceSet.setProjectPath(projectPath);
    sourceSet.setProjectName(projectName);
    sourceSet.setSourceSetName(sourceSetName);
    return sourceSet;
  }

  private Set<String> createDisplayNames(List<GradleSourceSet> sourceSets,
      String displayType) {
    Set<String> displayNames = new HashSet<>();
    Function<GradleSourceSet, String> displayNameMaker = Utils.getDisplayNameMaker(displayType);
    for (GradleSourceSet sourceSet : sourceSets) {
      String displayName = displayNameMaker.apply(sourceSet);
      displayNames.add(displayName);
    }
    return displayNames;
  }

  @Test
  void testValidDisplayNames1() {
    List<GradleSourceSet> sourceSets = new ArrayList<>();
    sourceSets.add(sourceSet("code/bar", ":code:bar", "bar", "main"));
    sourceSets.add(sourceSet("code/bar", ":code:bar", "bar", "test"));
    sourceSets.add(sourceSet("code/foo", ":code:foo", "foo", "main"));
    sourceSets.add(sourceSet("code/foo", ":code:foo", "foo", "test"));
    sourceSets.add(sourceSet("infra/foo", ":infra:foo", "foo", "main"));
    sourceSets.add(sourceSet("infra/foo", ":infra:foo", "foo", "test"));

    Set<String> dashNames = createDisplayNames(sourceSets, Preferences.DASH_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), dashNames.size());
    assertTrue(dashNames.contains("code bar-main"));
    assertTrue(dashNames.contains("code bar-test"));
    assertTrue(dashNames.contains("code foo-main"));
    assertTrue(dashNames.contains("code foo-test"));
    assertTrue(dashNames.contains("infra foo-main"));
    assertTrue(dashNames.contains("infra foo-test"));

    Set<String> bracketNames = createDisplayNames(sourceSets, Preferences.BRACKET_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), bracketNames.size());
    assertTrue(bracketNames.contains("code bar [main]"));
    assertTrue(bracketNames.contains("code bar [test]"));
    assertTrue(bracketNames.contains("code foo [main]"));
    assertTrue(bracketNames.contains("code foo [test]"));
    assertTrue(bracketNames.contains("infra foo [main]"));
    assertTrue(bracketNames.contains("infra foo [test]"));

    Set<String> dotNames = createDisplayNames(sourceSets, Preferences.DOT_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), dotNames.size());
    assertTrue(dotNames.contains("code bar.main"));
    assertTrue(dotNames.contains("code bar.test"));
    assertTrue(dotNames.contains("code foo.main"));
    assertTrue(dotNames.contains("code foo.test"));
    assertTrue(dotNames.contains("infra foo.main"));
    assertTrue(dotNames.contains("infra foo.test"));

    Set<String> spaceNames = createDisplayNames(sourceSets, Preferences.SPACE_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), spaceNames.size());
    assertTrue(spaceNames.contains("code bar main"));
    assertTrue(spaceNames.contains("code bar test"));
    assertTrue(spaceNames.contains("code foo main"));
    assertTrue(spaceNames.contains("code foo test"));
    assertTrue(spaceNames.contains("infra foo main"));
    assertTrue(spaceNames.contains("infra foo test"));
  }

  @Test
  void testValidDisplayNames2() {
    List<GradleSourceSet> sourceSets = new ArrayList<>();
    sourceSets.add(sourceSet("foo", ":foo", "foo", "main"));
    sourceSets.add(sourceSet("foo", ":foo", "foo", "test"));
    sourceSets.add(sourceSet("foo-test", ":foo-test", "foo-test", "main"));
    sourceSets.add(sourceSet("foo-test", ":foo-test", "foo-test", "test"));

    Set<String> dashNames = createDisplayNames(sourceSets, Preferences.DASH_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), dashNames.size());
    assertTrue(dashNames.contains("foo-main"));
    assertTrue(dashNames.contains("foo-test"));
    assertTrue(dashNames.contains("foo-test-main"));
    assertTrue(dashNames.contains("foo-test-test"));

    Set<String> bracketNames = createDisplayNames(sourceSets, Preferences.BRACKET_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), bracketNames.size());
    assertTrue(bracketNames.contains("foo [main]"));
    assertTrue(bracketNames.contains("foo [test]"));
    assertTrue(bracketNames.contains("foo-test [main]"));
    assertTrue(bracketNames.contains("foo-test [test]"));

    Set<String> dotNames = createDisplayNames(sourceSets, Preferences.DOT_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), dotNames.size());
    assertTrue(dotNames.contains("foo.main"));
    assertTrue(dotNames.contains("foo.test"));
    assertTrue(dotNames.contains("foo-test.main"));
    assertTrue(dotNames.contains("foo-test.test"));

    Set<String> spaceNames = createDisplayNames(sourceSets, Preferences.SPACE_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), spaceNames.size());
    assertTrue(spaceNames.contains("foo main"));
    assertTrue(spaceNames.contains("foo test"));
    assertTrue(spaceNames.contains("foo-test main"));
    assertTrue(spaceNames.contains("foo-test test"));
  }

  @Test
  void testValidDisplayNames3() {
    List<GradleSourceSet> sourceSets = new ArrayList<>();
    sourceSets.add(sourceSet("a/b/c/foo", ":a:b:c:foo", "foo", "main"));
    sourceSets.add(sourceSet("a/b/c/foo", ":a:b:c:foo", "foo", "test"));
    sourceSets.add(sourceSet("b/foo", ":b:foo", "foo", "main"));
    sourceSets.add(sourceSet("b/foo", ":b:foo", "foo", "test"));
    sourceSets.add(sourceSet("c/foo", ":c:foo", "foo", "main"));
    sourceSets.add(sourceSet("c/foo", ":c:foo", "foo", "test"));
    sourceSets.add(sourceSet("d/foo", ":d:foo", "foo", "main"));
    sourceSets.add(sourceSet("d/foo", ":d:foo", "foo", "test"));

    Set<String> dashNames = createDisplayNames(sourceSets, Preferences.DASH_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), dashNames.size());
    assertTrue(dashNames.contains("a b c foo-main"));
    assertTrue(dashNames.contains("a b c foo-test"));
    assertTrue(dashNames.contains("b foo-main"));
    assertTrue(dashNames.contains("b foo-test"));
    assertTrue(dashNames.contains("c foo-main"));
    assertTrue(dashNames.contains("c foo-test"));
    assertTrue(dashNames.contains("d foo-main"));
    assertTrue(dashNames.contains("d foo-test"));

    Set<String> bracketNames = createDisplayNames(sourceSets, Preferences.BRACKET_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), bracketNames.size());
    assertTrue(bracketNames.contains("a b c foo [main]"));
    assertTrue(bracketNames.contains("a b c foo [test]"));
    assertTrue(bracketNames.contains("b foo [main]"));
    assertTrue(bracketNames.contains("b foo [test]"));
    assertTrue(bracketNames.contains("c foo [main]"));
    assertTrue(bracketNames.contains("c foo [test]"));
    assertTrue(bracketNames.contains("d foo [main]"));
    assertTrue(bracketNames.contains("d foo [test]"));

    Set<String> dotNames = createDisplayNames(sourceSets, Preferences.DOT_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), dotNames.size());
    assertTrue(dotNames.contains("a b c foo.main"));
    assertTrue(dotNames.contains("a b c foo.test"));
    assertTrue(dotNames.contains("b foo.main"));
    assertTrue(dotNames.contains("b foo.test"));
    assertTrue(dotNames.contains("c foo.main"));
    assertTrue(dotNames.contains("c foo.test"));
    assertTrue(dotNames.contains("d foo.main"));
    assertTrue(dotNames.contains("d foo.test"));

    Set<String> spaceNames = createDisplayNames(sourceSets, Preferences.SPACE_DISPLAY_NAMING);
    assertEquals(sourceSets.size(), spaceNames.size());
    assertTrue(spaceNames.contains("a b c foo main"));
    assertTrue(spaceNames.contains("a b c foo test"));
    assertTrue(spaceNames.contains("b foo main"));
    assertTrue(spaceNames.contains("b foo test"));
    assertTrue(spaceNames.contains("c foo main"));
    assertTrue(spaceNames.contains("c foo test"));
    assertTrue(spaceNames.contains("d foo main"));
    assertTrue(spaceNames.contains("d foo test"));
  }
}
