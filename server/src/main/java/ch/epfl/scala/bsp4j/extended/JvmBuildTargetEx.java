package ch.epfl.scala.bsp4j.extended;

import java.util.Objects;

import ch.epfl.scala.bsp4j.JvmBuildTarget;

/**
 * Extended {@link JvmBuildTarget}, which contains the Gradle version.
 * The client can use the Gradle version to find compatible JDKs according to
 * https://docs.gradle.org/current/userguide/compatibility.html.
 */
public class JvmBuildTargetEx extends JvmBuildTarget {

  private String gradleVersion;

  private String sourceCompatibility;

  private String targetCompatibility;

  /**
   * Create a new instance of {@link JvmBuildTargetEx}.
   */
  public JvmBuildTargetEx() {
    super();
  }

  /**
   * get the version of Gradle for the build target.
   *
   * @return the gradle version
   */
  public String getGradleVersion() {
    return gradleVersion;
  }

  /**
   * set the version of Gradle for the build target.
   *
   * @param gradleVersion the gradle version
   */
  public void setGradleVersion(String gradleVersion) {
    this.gradleVersion = gradleVersion;
  }

  /**
   * get the java source compatibility setting of the build target.
   *
   * @return the java source compatibility setting
   */
  public String getSourceCompatibility() {
    return sourceCompatibility;
  }

  /**
   * set the java source compatibility setting of the build target.
   *
   * @param sourceCompatibility the java source compatibility setting
   */
  public void setSourceCompatibility(String sourceCompatibility) {
    this.sourceCompatibility = sourceCompatibility;
  }

  /**
   * get the java target compatibility setting of the build target.
   *
   * @return the java target compatibility setting
   */
  public String getTargetCompatibility() {
    return targetCompatibility;
  }

  /**
   * set the java target compatibility setting of the build target.
   *
   * @param targetCompatibility the java target compatibility setting
   */
  public void setTargetCompatibility(String targetCompatibility) {
    this.targetCompatibility = targetCompatibility;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(gradleVersion, sourceCompatibility,
        targetCompatibility);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    JvmBuildTargetEx other = (JvmBuildTargetEx) obj;
    return Objects.equals(gradleVersion, other.gradleVersion)
        && Objects.equals(sourceCompatibility, other.sourceCompatibility)
        && Objects.equals(targetCompatibility, other.targetCompatibility);
  }
}
