// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.impl;

import com.microsoft.java.bs.gradle.model.AntlrExtension;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;

import java.util.Map;

/**
 * Default Antlr implementation of {@link SupportedLanguage}.
 */
public class DefaultAntlrLanguage implements SupportedLanguage<AntlrExtension> {
  @Override
  public String getBspName() {
    return "antlr";
  }

  @Override
  public String getGradleName() {
    return "antlr";
  }

  @Override
  public AntlrExtension getExtension(Map<String, LanguageExtension> extensions) {
    LanguageExtension extension = extensions.get(getBspName());
    if (extension == null) {
      return null;
    }
    if (extension.isAntlrExtension()) {
      return extension.getAsAntlrExtension();
    }
    throw new IllegalArgumentException(
        "LanguageExtension: " + extension + " is not a AntlrExtension."
    );
  }
}