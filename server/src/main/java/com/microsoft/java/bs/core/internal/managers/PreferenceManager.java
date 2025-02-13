// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.managers;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import com.microsoft.java.bs.core.internal.model.Preferences;

/**
 * Manage the preferences of the build server.
 */
public class PreferenceManager {
  private Preferences preferences;

  /**
  * The root URI of the workspace.
  */
  private URI rootUri;

  private List<String> clientSupportedLanguages;

  /**
   * constructor.
   */
  public PreferenceManager() {
    this.clientSupportedLanguages = new LinkedList<>();
  }

  /**
   * set the client preferences.
   *
   * @param preferences BSP client preferences
   */
  public void setPreferences(Preferences preferences) {
    this.preferences = preferences;
  }

  /**
   * get the client preferences.
   *
   * @return BSP client preferences
   */
  public Preferences getPreferences() {
    return preferences;
  }

  /**
   * get the workspace uri.
   *
   * @return workspace uri
   */
  public URI getRootUri() {
    return rootUri;
  }

  /**
   * set the workspace uri.
   *
   * @param rootUri workspace uri
   */
  public void setRootUri(URI rootUri) {
    this.rootUri = rootUri;
  }

  /**
   * get the languages supported by the client.
   *
   * @return supported languages
   */
  public List<String> getClientSupportedLanguages() {
    return clientSupportedLanguages;
  }

  /**
   * set the languages supported by the client.
   *
   * @param clientSupportedLanguages supported languages
   */
  public void setClientSupportedLanguages(List<String> clientSupportedLanguages) {
    this.clientSupportedLanguages = clientSupportedLanguages;
  }
}
