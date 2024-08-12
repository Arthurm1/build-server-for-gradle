// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model;

import java.util.List;

/**
 * The extension model for Kotlin language.
 */
public interface KotlinExtension extends LanguageExtension {

  String getKotlinLanguageVersion();
  
  String getKotlinApiVersion();
  
  List<String> getKotlincOptions();
  
  List<String> getKotlinAssociates();
}
