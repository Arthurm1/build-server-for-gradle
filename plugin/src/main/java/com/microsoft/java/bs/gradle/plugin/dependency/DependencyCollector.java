// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin.dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;

import com.microsoft.java.bs.gradle.model.Artifact;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.impl.DefaultArtifact;
import com.microsoft.java.bs.gradle.model.impl.DefaultGradleModuleDependency;
import org.gradle.util.GradleVersion;

/**
 * Collects dependencies from a {@link SourceSet}.
 */
public class DependencyCollector {

  private static final String UNKNOWN = "unknown";

  private static final List<Class<? extends org.gradle.api.component.Artifact>> artifactTypes;
  static {
    artifactTypes = new ArrayList<>();
    artifactTypes.add(JavadocArtifact.class);
    artifactTypes.add(SourcesArtifact.class);
  }

  /**
   * Resolve and collect dependencies from a collection of {@link Configuration}.
   */
  public static Set<GradleModuleDependency> getModuleDependencies(DependencyHandler dependencies,
      Collection<Configuration> configurations) {
    if (GradleVersion.current().compareTo(GradleVersion.version("4.0")) < 0) {
      try {
        List<ResolvedConfiguration> configs = configurations.stream()
            .map(Configuration::getResolvedConfiguration)
            .collect(Collectors.toList());
        Stream<DefaultGradleModuleDependency> moduleDependencies = configs.stream()
            .flatMap(config -> config.getResolvedArtifacts().stream())
            .map(artifact -> getArtifact(dependencies, artifact.getId(), artifact.getFile()));

        // add as individual files for direct dependencies on jars
        Stream<DefaultGradleModuleDependency> directDependencies = configs.stream()
            .flatMap(config -> config.getFiles(Specs.satisfyAll()).stream())
            .map(DependencyCollector::getFileDependency);
        return Stream.concat(moduleDependencies, directDependencies)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      } catch (GradleException ex) {
        // handle build with unresolvable dependencies e.g. missing repository
        return new HashSet<>();
      }
    } else {
      return configurations.stream()
        .filter(Configuration::isCanBeResolved)
        .flatMap(configuration -> getConfigurationArtifacts(configuration).stream())
        .map(artifactResult -> getArtifact(dependencies, artifactResult.getId(),
            artifactResult.getFile()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    }
  }

  /**
   * Create Dependency for each input dependency based on extracted jar name and version
   * @param dependencyHandler Gradle DependencyHandler
   * @param dependencies Gradle built-in jar only dependencies
   * @return Maven dependencies
   */
  private static Set<Dependency> getAsDependencies(DependencyHandler dependencyHandler,
      Set<GradleModuleDependency> dependencies) {

    Pattern filePattern = Pattern.compile("(\\w*(-.+?)?)-(\\d.+?)\\.jar");
    Pattern versionPattern = Pattern.compile("(\\d+?)\\..*");
    return dependencies.stream().flatMap(dep ->
            dep.getArtifacts().stream().flatMap(artifact -> {
              Matcher fileMatcher = filePattern.matcher(new File(artifact.getUri()).getName());
              if (!fileMatcher.matches()) {
                return Stream.empty();
              }
              String version = fileMatcher.group(3);
              Matcher versionMatcher = versionPattern.matcher(version);
              if (!versionMatcher.matches()) {
                return Stream.empty();
              }
              String module = fileMatcher.group(1);
              String group;
              if (module.startsWith("groovy")) {
                try {
                  int fileVersion = Integer.parseInt(versionMatcher.group(1));
                  group = getGroovyGroupName(fileVersion);
                } catch (Exception e) {
                  group = getGroovyGroupName(1);
                }
              } else if (module.startsWith("kotlin")) {
                group = "org.jetbrains.kotlin";
              } else if (module.equals("gradle-api")
                || module.equals("gradle-test-kit")) {
                // these seem to be a version behind so the latest Gradle may not be present
                group = "dev.gradleplugins";
              } else if (module.equals("javaparser-core")) {
                group = "com.github.javaparser";
              } else {
                return Stream.empty();
              }
              String dependency = group + ':' + module + ':' + version;

              return Stream.of(dependencyHandler.create(dependency));
            }))
        .collect(Collectors.toSet());
  }

  /**
   * Takes a collection of dependencies and attempts to download missing build-in Gradle sources.
   * Returns original collection if no built-in sources are missing
   * @param configurationContainer configuration container
   * @param repositoryHandler project repositoryHandler
   * @param dependencyHandler project dependencyHandler
   * @param moduleDependencies set of dependencies that potentially have missing Gradle build-in sources
   * @return full set of dependencies with the sources if download is required/possible
   */
  public static Set<GradleModuleDependency> downloadGroovySources(
      ConfigurationContainer configurationContainer, RepositoryHandler repositoryHandler,
      DependencyHandler dependencyHandler, Set<GradleModuleDependency> moduleDependencies) {

    // check if any are missing source artifacts and if they are built-in Gradle artifacts
    Set<GradleModuleDependency> missingSources = moduleDependencies.stream()
        .filter(dep -> dep.getArtifacts().stream()
            .noneMatch(art -> "sources".equals(art.getClassifier())))
        .filter(dep -> isBuildInGradleSource(dep.getModule()))
        .collect(Collectors.toSet());

    if (missingSources.isEmpty()) {
      return moduleDependencies;
    }

    Set<Dependency> missingDependencies = getAsDependencies(dependencyHandler, missingSources);

    if (missingDependencies.isEmpty()) {
      return moduleDependencies;
    }

    // create a detached config to download the dependencies
    Configuration detachedConfig = configurationContainer.detachedConfiguration(
        missingDependencies.toArray(new Dependency[0]));
    detachedConfig.setTransitive(false);

    repositoryHandler.add(repositoryHandler.mavenCentral());

    Set<GradleModuleDependency> missingDeps = getModuleDependencies(dependencyHandler,
        Collections.singletonList(detachedConfig));

    // merge existing and downloaded dependencies - only jar name will be the same.
    return moduleDependencies.stream()
        .map(dep -> {
          // don't replace if sources exist
          if (dep.getArtifacts().stream().anyMatch(art -> "sources".equals(art.getClassifier()))) {
            return dep;
          }
          // don't replace if original jar isn't specified
          Optional<Artifact> jarArtifact = dep.getArtifacts().stream()
              .filter(art -> art.getClassifier() == null).findAny();
          if (!jarArtifact.isPresent()) {
            return dep;
          }
          // find replacement by matching the jar file only
          String originalJarName = new File(jarArtifact.get().getUri()).getName();
          Artifact nullClassifedNewArtifact = null;
          GradleModuleDependency matchingDep = null;
          for (GradleModuleDependency newDep : missingDeps) {
            for (Artifact artifact : newDep.getArtifacts()) {
              if (artifact.getClassifier() == null) {
                if (originalJarName.equals(new File(artifact.getUri()).getName())) {
                  nullClassifedNewArtifact = artifact;
                  break;
                }
              }
            }
            if (nullClassifedNewArtifact != null) {
              matchingDep = newDep;
              break;
            }
          }
          if (matchingDep == null) {
            return dep;
          }
          // create a set of new artifacts using the original nullClassified jar (essential to
          // match the one in the classpath) and the new sources/javadocs artifact
          List<Artifact> newArtifacts = new ArrayList<>();
          newArtifacts.add(jarArtifact.get());
          for (Artifact artifact : matchingDep.getArtifacts()) {
            if (!artifact.equals(nullClassifedNewArtifact)) {
              newArtifacts.add(artifact);
            }
          }
          // create a new module using the non-null group + version and proper name
          return new DefaultGradleModuleDependency(matchingDep.getGroup(), matchingDep.getModule(),
              matchingDep.getVersion(), newArtifacts);
        })
        .collect(Collectors.toSet());
  }

  private static boolean isBuildInGradleSource(String moduleName) {
    return moduleName != null
        && (moduleName.contains("Gradle API")
        || moduleName.contains("Gradle TestKit")
        || moduleName.contains("Gradle Kotlin DSL")
        || moduleName.contains("Local Groovy"));
  }

  private static String getGroovyGroupName(int version) {
    if (version > 4) {
      return "org.apache.groovy";
    } else {
      return "org.codehaus.groovy";
    }
  }

  private static DefaultGradleModuleDependency getArtifact(DependencyHandler dependencies,
      ComponentArtifactIdentifier id, File artifactFile) {
    if (id instanceof ModuleComponentArtifactIdentifier) {
      return getModuleArtifactDependency(dependencies, (ModuleComponentArtifactIdentifier) id,
        artifactFile);
    }
    if (id instanceof OpaqueComponentArtifactIdentifier) {
      return getFileArtifactDependency((OpaqueComponentArtifactIdentifier) id, artifactFile);
    }
    if (id instanceof ComponentFileArtifactIdentifier) {
      return getFileArtifactDependency((ComponentFileArtifactIdentifier) id, artifactFile);
    }
    return null;
  }

  private static List<ResolvedArtifactResult> getConfigurationArtifacts(Configuration config) {
    return new ArrayList<>(config.getIncoming()
        .artifactView(viewConfiguration -> {
          viewConfiguration.lenient(true);
          viewConfiguration.componentFilter(Specs.satisfyAll());
        })
        .getArtifacts() // get ArtifactCollection from ArtifactView.
        .getArtifacts());
  }

  private static DefaultGradleModuleDependency getModuleArtifactDependency(
      DependencyHandler dependencies, ModuleComponentArtifactIdentifier artifactIdentifier,
      File resolvedArtifactFile) {

    ArtifactResolutionQuery query = dependencies
        .createArtifactResolutionQuery()
        .forComponents(artifactIdentifier.getComponentIdentifier());

    if (GradleVersion.current().compareTo(GradleVersion.version("4.5")) >= 0) {
      @SuppressWarnings({"UnstableApiUsage"})
      ArtifactResolutionQuery withArtifacts = query.withArtifacts(JvmLibrary.class, artifactTypes);
      query = withArtifacts;
    } else {
      @SuppressWarnings({"UnstableApiUsage", "unchecked"})
      ArtifactResolutionQuery withArtifacts = query.withArtifacts(JvmLibrary.class,
          JavadocArtifact.class, SourcesArtifact.class);
      query = withArtifacts;
    }

    ArtifactResolutionResult resolutionResult = query.execute();

    List<Artifact> artifacts = new LinkedList<>();
    if (resolvedArtifactFile != null) {
      artifacts.add(new DefaultArtifact(resolvedArtifactFile.toPath().toUri(), null));
    }

    Set<ComponentArtifactsResult> resolvedComponents = resolutionResult.getResolvedComponents();
    File sourceJar = getNonClassesArtifact(resolvedComponents, SourcesArtifact.class);
    if (sourceJar != null) {
      artifacts.add(new DefaultArtifact(sourceJar.toPath().toUri(), "sources"));
    }

    File javaDocJar = getNonClassesArtifact(resolvedComponents, JavadocArtifact.class);
    if (javaDocJar != null) {
      artifacts.add(new DefaultArtifact(javaDocJar.toPath().toUri(), "javadoc"));
    }

    return new DefaultGradleModuleDependency(
        artifactIdentifier.getComponentIdentifier().getGroup(),
        artifactIdentifier.getComponentIdentifier().getModule(),
        artifactIdentifier.getComponentIdentifier().getVersion(),
        artifacts
    );
  }

  private static File getNonClassesArtifact(Set<ComponentArtifactsResult> resolvedComponents,
      Class<? extends org.gradle.api.component.Artifact> artifactClass) {
    for (ComponentArtifactsResult component : resolvedComponents) {
      Set<ArtifactResult> artifacts = component.getArtifacts(artifactClass);
      for (ArtifactResult artifact : artifacts) {
        if (artifact instanceof ResolvedArtifactResult) {
          // TODO: only return the first found result, might be wrong!
          return ((ResolvedArtifactResult) artifact).getFile();
        }
      }
    }
    return null;
  }

  private static DefaultGradleModuleDependency getFileDependency(File resolvedArtifactFile) {
    return getFileArtifactDependency(
            resolvedArtifactFile.getName(),
            resolvedArtifactFile
    );
  }

  private static DefaultGradleModuleDependency getFileArtifactDependency(
      ComponentFileArtifactIdentifier artifactIdentifier, File resolvedArtifactFile) {
    return getFileArtifactDependency(
        artifactIdentifier.getCapitalizedDisplayName(),
        resolvedArtifactFile
    );
  }

  private static DefaultGradleModuleDependency getFileArtifactDependency(
      OpaqueComponentArtifactIdentifier artifactIdentifier, File resolvedArtifactFile) {
    return getFileArtifactDependency(
        artifactIdentifier.getCapitalizedDisplayName(),
        resolvedArtifactFile
    );
  }

  private static DefaultGradleModuleDependency getFileArtifactDependency(String displayName,
      File resolvedArtifactFile) {
    List<Artifact> artifacts = new LinkedList<>();
    if (resolvedArtifactFile != null) {
      artifacts.add(new DefaultArtifact(resolvedArtifactFile.toPath().toUri(), null));
    }
  
    return new DefaultGradleModuleDependency(
        UNKNOWN,
        displayName,
        UNKNOWN,
        artifacts
    );
  }
}
