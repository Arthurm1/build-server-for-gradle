package com.microsoft.java.bs.gradle.plugin.utils;

import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.plugin.AntlrLanguageModelBuilder;
import com.microsoft.java.bs.gradle.plugin.GroovyLanguageModelBuilder;
import com.microsoft.java.bs.gradle.plugin.JavaLanguageModelBuilder;
import com.microsoft.java.bs.gradle.plugin.KotlinLanguageModelBuilder;
import com.microsoft.java.bs.gradle.plugin.LanguageModelBuilder;
import com.microsoft.java.bs.gradle.plugin.ScalaLanguageModelBuilder;

import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for common source set operations.
 */
public class SourceSetUtils {

  private SourceSetUtils() {
  }

  /**
   * Return a project task name - [project path]:[task].
   *
   * @param modulePath path of project module
   * @param taskName name of gradle task
   */
  public static String getFullTaskName(String modulePath, String taskName) {
    if (taskName == null) {
      return null;
    }
    if (taskName.isEmpty()) {
      return taskName;
    }

    if (modulePath == null || modulePath.equals(":")) {
      // must be prefixed with ":" as taskPaths are reported back like that in progress messages
      return ":" + taskName;
    }
    return modulePath + ":" + taskName;
  }

  /**
   * Returns a list of LanguageModelBuilder for the supported languages.
   */
  public static List<LanguageModelBuilder> getSupportedLanguageModelBuilders() {
    List<LanguageModelBuilder> results = new LinkedList<>();
    for (String language : getSupportedLanguages()) {
      if (language.equalsIgnoreCase(SupportedLanguages.JAVA.getBspName())) {
        results.add(new JavaLanguageModelBuilder());
      } else if (language.equalsIgnoreCase(SupportedLanguages.SCALA.getBspName())) {
        results.add(new ScalaLanguageModelBuilder());
      } else if (language.equalsIgnoreCase(SupportedLanguages.GROOVY.getBspName())) {
        results.add(new GroovyLanguageModelBuilder());
      } else if (language.equalsIgnoreCase(SupportedLanguages.KOTLIN.getBspName())) {
        results.add(new KotlinLanguageModelBuilder());
      } else if (language.equalsIgnoreCase(SupportedLanguages.ANTLR.getBspName())) {
        results.add(new AntlrLanguageModelBuilder());
      }
    }
    return results;
  }

  /**
   * Returns a list of bsp names for the supported languages.
   */
  public static String[] getSupportedLanguages() {
    String supportedLanguagesProps = System.getProperty("bsp.gradle.supportedLanguages");
    if (supportedLanguagesProps != null) {
      return supportedLanguagesProps.split(",");
    }
    return new String[]{};
  }

}
