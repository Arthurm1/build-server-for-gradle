package com.microsoft.java.bs.core;

import ch.epfl.scala.bsp4j.BspConnectionDetails;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * class to create a `.bsp/gradle-bsp.json` file that can start the server.
 * See <a href="https://build-server-protocol.github.io/docs/overview/server-discovery#the-bsp-connection-details">Build Discovery</a>
 */
public class BspFileCreator {

  private static final String BSP_DIRNAME = ".bsp";
  private static final String BSP_FILENAME = "gradle-bsp.json";
  /**
   * Main entry point.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    Path projectPath = Paths.get(System.getProperty("user.dir"));
    new BspFileCreator().run(projectPath.toAbsolutePath());
  }

  private void run(Path projectPath) {
    Path bloopPath = projectPath.resolve(BSP_DIRNAME);
    File bloopDir = bloopPath.toFile();
    bloopDir.mkdirs();
    Path targetPath = bloopPath.resolve(BSP_FILENAME);

    String name = BuildInfo.serverName;
    String version = BuildInfo.version;
    String bspVersion = BuildInfo.bspVersion;
    List<String> languages = SupportedLanguages.allBspNames;
    String javaHome = System.getProperty("java.home");
    Path javaExe = Path.of(javaHome, "bin", "java.exe");
    String classpath = System.getProperty("java.class.path");
    List<String> argv = List.of(
        javaExe.toString(),
        "-cp",
        classpath,
        "com.microsoft.java.bs.core.Launcher"
    );
    BspConnectionDetails details = new BspConnectionDetails(name, argv, version, bspVersion, languages);

    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    String json = gson.toJson(details);
    try {
      Files.writeString(targetPath, json);
    } catch (IOException e) {
      throw new IllegalStateException("Error writing to file " + targetPath, e);
    }
  }
}
