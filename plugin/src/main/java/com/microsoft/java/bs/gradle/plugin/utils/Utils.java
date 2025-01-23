// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin.utils;

import java.lang.reflect.InvocationTargetException;

/**
 * Helper functions.
 */
public class Utils {

  /**
   * Invoke a method by reflection and pass back any exceptions thrown.
   *
   * @param <A> expected return type
   * @param object instance to invoke method on.
   * @param types method parameter types
   * @param methodName name of method to invoke.
   * @param args method arguments
   * @return result of method called.
   */
  @SuppressWarnings("unchecked")
  public static <A> A invokeMethod(Object object, Class<?>[] types, String methodName,
      Object[] args)
          throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    return (A) object.getClass().getMethod(methodName, types).invoke(object, args);
  }

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
  public static <A> A invokeMethod(Object object, String methodName)
          throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    return (A) object.getClass().getMethod(methodName).invoke(object);
  }

  /**
   * Invoke a method by reflection and return null if method cannot be called.
   *
   * @param <A> expected return type
   * @param object instance to invoke method on.
   * @param types method parameter types
   * @param methodName name of method to invoke.
   * @param args method arguments
   * @return result of method called.
   */
  public static <A> A invokeMethodIgnoreFail(Object object, Class<?>[] types, String methodName,
      Object[] args) {
    try {
      return invokeMethod(object, types, methodName, args);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      return null;
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
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      return null;
    }
  }
}
