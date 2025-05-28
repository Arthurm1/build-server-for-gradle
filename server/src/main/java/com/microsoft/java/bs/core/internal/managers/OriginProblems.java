package com.microsoft.java.bs.core.internal.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class OriginProblems {

  // map of originId to problems
  private final Map<String, List<Problem>> originProblems;

  OriginProblems(Map<String, List<Problem>> originProblems) {
    this.originProblems = originProblems;
  }

  OriginProblems() {
    this(new HashMap<>());
  }

  public OriginProblems withEmptyProblems() {
    if (originProblems.isEmpty()) {
      return this;
    }
    Map<String, List<Problem>> blankProblems = new HashMap<>();
    for (String originId : originProblems.keySet()) {
      blankProblems.put(originId, Collections.emptyList());
    }
    return new OriginProblems(blankProblems);
  }

  public void addProblem(Problem problem) {
    String originId = problem.originId() == null ? "null" : problem.originId();
    List<Problem> problems = originProblems.computeIfAbsent(originId,
        k -> new ArrayList<>());
    problems.add(problem);
  }

  public Set<Map.Entry<String, List<Problem>>> getProblems() {
    return originProblems.entrySet();
  }

  @Override
  public int hashCode() {
    return Objects.hash(originProblems);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    OriginProblems other = (OriginProblems) obj;
    return Objects.equals(originProblems, other.originProblems);
  }

  @Override
  public String toString() {
    return "OriginProblems: " + originProblems;
  }
}