// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin;

import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;
import com.microsoft.java.bs.gradle.plugin.utils.Utils;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.tasks.SourceSet;

/**
 * The language model builder for different languages.
 */
public abstract class LanguageModelBuilder {

  public abstract SupportedLanguage<?> getLanguage();

  public final String getLanguageId() {
    return getLanguage().getBspName();
  }

  public abstract LanguageExtension getExtensionFor(Project project, SourceSet sourceSet,
      Set<GradleModuleDependency> moduleDependencies);

  protected final Task getLanguageCompileTask(Project project, SourceSet sourceSet) {
    String taskName = sourceSet.getCompileTaskName(getLanguage().getGradleName());
    try {
      return Utils.taskByName(project, taskName);
    } catch (UnknownTaskException e) {
      return null;
    }
  }

  /**
   * Returns a list of LanguageModelBuilder for the supported languages.
   *
   * @param languages languages to fetch the model builders for
   * @return supported model builders
   */
  public static List<LanguageModelBuilder> getSupportedLanguageModelBuilders(
      Collection<String> languages) {
    List<LanguageModelBuilder> results = new LinkedList<>();
    for (String language : languages) {
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
}
