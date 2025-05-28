plugins {
  `java-gradle-plugin`
}

gradlePlugin {
  plugins {
    register("bspCommon") {
      id = "com.microsoft.java.bs.checkstyle"
      implementationClass = "com.microsoft.java.bs.CheckStyleConventionPlugin"
    }
  }
}