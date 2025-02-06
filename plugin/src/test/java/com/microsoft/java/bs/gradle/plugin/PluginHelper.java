// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helpers for plugin tests.
 */
public class PluginHelper {
  private PluginHelper() {}

  /**
   * Returns the init script contents for applying the plugin.
   */
  public static String getInitScriptContents() {
    Path pluginProjectPath = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    if (!pluginProjectPath.endsWith("plugin")) {
      throw new IllegalStateException("Tests not run from plugin folder " + pluginProjectPath);
    }
    Path modelProjectPath = pluginProjectPath.getParent().resolve("model");

    Path classesPath = Paths.get("build", "classes", "java", "main");
    Path pluginClassesPath = pluginProjectPath.resolve(classesPath);
    Path modelClassesPath = modelProjectPath.resolve(classesPath);

    if (!Files.exists(pluginClassesPath)) {
      throw new IllegalStateException("Plugin classes path doesn't exist.  Ensure project is built "
          + pluginClassesPath);
    }
    if (!Files.exists(modelClassesPath)) {
      throw new IllegalStateException("Model classes path doesn't exist.  Ensure project is built "
          + modelClassesPath);
    }

    String pluginPath = pluginClassesPath.toString().replace("\\", "/");
    String modelPath = modelClassesPath.toString().replace("\\", "/");

    String initScript = "initscript {\n"
        + "  dependencies {\n"
        + "    classpath files('$pluginPath', '$modelPath')\n"
        + "  }\n"
        + "}\n"
        + "allprojects {\n"
        + "  apply plugin: com.microsoft.java.bs.gradle.plugin.GradleBuildServerPlugin\n"
        + "}\n";
    return initScript
        .replace("$pluginPath", pluginPath)
        .replace("$modelPath", modelPath);
  }

  /**
   * Returns the init script file.
   * Caller responsible for deletion
   */
  public static File getInitScript(String contents) throws IOException {
    File initScriptFile = File.createTempFile("init", ".gradle");
    if (!initScriptFile.getParentFile().exists()) {
      initScriptFile.getParentFile().mkdirs();
    }
    Files.write(initScriptFile.toPath(), contents.getBytes());
    return initScriptFile;
  }
}
