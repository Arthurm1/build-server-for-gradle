// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin.utils;

import org.gradle.api.Project;
import org.gradle.api.Task;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper functions.
 */
public class Utils {

  /**
   * Invoke a method by reflection and pass back any exceptions thrown.
   * Method takes no parameters.
   *
   * @param <A> expected return type
   * @param object instance to invoke method on.
   * @param methodName name of method to invoke.
   * @return result of method called.
   */
  @SuppressWarnings("unchecked")
  public static <A> A invokeMethod(Object object, String methodName) {
    try {
      return (A) object.getClass().getMethod(methodName).invoke(object);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Error in reflection", e);
    }
  }

  /**
   * Invoke a method by reflection and return null if method cannot be called.
   * Method takes no parameters.
   *
   * @param <A> expected return type
   * @param object instance to invoke method on.
   * @param methodName name of method to invoke.
   * @return result of method called.
   */
  public static <A> A invokeMethodIgnoreFail(Object object, String methodName) {
    try {
      return invokeMethod(object, methodName);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * get a set of tasks by type.
   *
   * @param project Gradle project
   * @param clazz type of task
   * @return set of tasks of type `T`
   */
  public static <T extends Task> Set<T> tasksWithType(Project project, Class<T> clazz) {
    // Gradle gives concurrentmodification exceptions if multiple threads resolve
    // the tasks concurrently, which happens on multi-project builds
    synchronized (project.getRootProject()) {
      return new HashSet<>(project.getTasks().withType(clazz));
    }
  }

  /**
   * get a task by name.
   *
   * @param project Gradle project
   * @return the task (if it exists) or null if it does not.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Task> T taskByName(Project project, String name) {
    // Gradle gives concurrentmodification exceptions if multiple threads resolve
    // the tasks concurrently, which happens on multi-project builds
    synchronized (project.getRootProject()) {
      return (T) project.getTasks().findByName(name);
    }
  }
}
