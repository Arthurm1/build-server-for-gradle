package com.example.project;

import java.util.List;

public class AnnotatedSourceUse {
  public static void main(String... args) {
    AnnotatedSource value = AnnotatedSource.builder()
        .foo(2)
        .bar("Bar")
        .addBuz(1, 3, 4)
        .build();

    int foo = value.foo();

    List<Integer> buz = value.buz();
  }
}
