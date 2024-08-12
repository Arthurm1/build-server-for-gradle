// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.impl;

import com.microsoft.java.bs.gradle.model.GroovyExtension;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;

import java.util.Map;

/**
 * Default Groovy implementation of {@link SupportedLanguage}.
 */
public class DefaultGroovyLanguage implements SupportedLanguage<GroovyExtension> {
  @Override
  public String getBspName() {
    return "groovy";
  }

  @Override
  public String getGradleName() {
    return "groovy";
  }

  @Override
  public GroovyExtension getExtension(Map<String, LanguageExtension> extensions) {
    LanguageExtension extension = extensions.get(getBspName());
    if (extension == null) {
      return null;
    }
    if (extension.isGroovyExtension()) {
      return extension.getAsGroovyExtension();
    }
    throw new IllegalArgumentException(
        "LanguageExtension: " + extension + " is not a GroovyExtension."
    );
  }
}