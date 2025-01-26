/*
 * Copyright 2015-2022 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package com.example.project;

import java.util.Map;

public class Calculator {

  public int add(int a, int b) {
    return a + b;
  }

  public static void main(String[] args) {
    if (args.length != 1 || !args[0].equals("firstArg")) {
      System.out.println("Requires 1 arg");
      System.exit(1);
    }
    Map<String, String> envVars = System.getenv();
    //String envVar = System.getProperty("testEnv");
    String envVar = envVars.get("testEnv");
    if (envVar == null || !envVar.equals("testing")) {
      System.out.println("testEnv system envVar not set " + envVar);
      System.out.println(envVars);
      System.exit(1);
    }
    System.out.println("Sysout test");
    System.err.println("Syserr test");
    // TODO wait until upgrade of BSP 2.2 and support for OnRunReadStdin then uncomment
    /*try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
      String input = br.readLine();
      String requiredInput = args[0];
      if (requiredInput.equals(input)) {
        System.out.println("OK!");
        System.err.println("No error");
      } else  {
        System.err.println("Expected [" + requiredInput + "] but got [" + input + "]");
      }
    } catch (IOException e) {
      System.err.println("I/O Error getting string" + e);
      System.exit(1);
    }*/
  }
}
