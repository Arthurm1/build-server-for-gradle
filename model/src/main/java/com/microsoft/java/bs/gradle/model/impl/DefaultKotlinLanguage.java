// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model.impl;

import com.microsoft.java.bs.gradle.model.KotlinExtension;
import com.microsoft.java.bs.gradle.model.LanguageExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguage;

import java.util.Map;

/**
 * Default Kotlin implementation of {@link SupportedLanguage}.
 */
public class DefaultKotlinLanguage implements SupportedLanguage<KotlinExtension> {

  @Override
  public String getBspName() {
    return "kotlin";
  }

  @Override
  public String getGradleName() {
    return "kotlin";
  }

  @Override
  public KotlinExtension getExtension(Map<String, LanguageExtension> extensions) {
    LanguageExtension extension = extensions.get(getBspName());
    if (extension == null) {
      return null;
    }
    if (extension.isKotlinExtension()) {
      return extension.getAsKotlinExtension();
    }
    throw new IllegalArgumentException(
        "LanguageExtension: " + extension + " is not a KotlinExtension."
    );
  }
}