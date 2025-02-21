// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.model;

import com.microsoft.java.bs.gradle.model.impl.DefaultAntlrLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultJavaLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultKotlinLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultScalaLanguage;
import com.microsoft.java.bs.gradle.model.impl.DefaultGroovyLanguage;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The supported languages.
 */
public class SupportedLanguages {
  public static final DefaultAntlrLanguage ANTLR = new DefaultAntlrLanguage();
  public static final DefaultJavaLanguage JAVA = new DefaultJavaLanguage();
  public static final DefaultScalaLanguage SCALA = new DefaultScalaLanguage();
  public static final DefaultGroovyLanguage GROOVY = new DefaultGroovyLanguage();
  public static final DefaultKotlinLanguage KOTLIN = new DefaultKotlinLanguage();

  public static final List<String> allBspNames;

  static {
    List<SupportedLanguage<? extends LanguageExtension>> all = new LinkedList<>();
    all.add(ANTLR);
    all.add(JAVA);
    all.add(SCALA);
    all.add(GROOVY);
    all.add(KOTLIN);
    allBspNames = all.stream().map(SupportedLanguage::getBspName).collect(Collectors.toList());
  }
}
