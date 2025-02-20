// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.gradle.plugin.dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
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

  private static List<Class<? extends org.gradle.api.component.Artifact>> artifactTypes;
  static {
    artifactTypes = new ArrayList<>();
    artifactTypes.add(JavadocArtifact.class);
    artifactTypes.add(SourcesArtifact.class);
  }

  /**
   * Resolve and collect dependencies from a collection of Configuration names.
   */
  public static Set<GradleModuleDependency> getModuleDependencies(Project project,
      Set<String> configurationNames) {
    List<Configuration> configurations = project.getConfigurations()
        .stream()
        .filter(configuration -> configurationNames.contains(configuration.getName()))
        .collect(Collectors.toList());
    return getModuleDependencies(project.getDependencies(), configurations);
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
            .map(artifact -> getArtifact(dependencies, artifact));

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
        .map(artifactResult -> getArtifact(dependencies, artifactResult))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    }
  }

  private static DefaultGradleModuleDependency getArtifact(DependencyHandler dependencies,
      ResolvedArtifactResult artifactResult) {
    ComponentArtifactIdentifier id = artifactResult.getId();
    return getArtifact(dependencies, id, artifactResult.getFile());
  }

  private static DefaultGradleModuleDependency getArtifact(DependencyHandler dependencies,
      ResolvedArtifact resolvedArtifact) {
    ComponentArtifactIdentifier id = resolvedArtifact.getId();
    return getArtifact(dependencies, id, resolvedArtifact.getFile());
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
      if (isGradleAPIDependency(id, artifactFile)) {
        System.err.println("Component " + artifactFile + " " + id.getClass());
      }
      return getFileArtifactDependency((ComponentFileArtifactIdentifier) id, artifactFile);
    }
    return null;
  }

  private static boolean isGradleAPIDependency(ComponentArtifactIdentifier id, File artifactFile) {
    String componentIdentifier = id.getComponentIdentifier().getDisplayName();
    return (componentIdentifier.equals("Gradle API")
        || componentIdentifier.equals("Gradle Kotlin DSL")
        || componentIdentifier.equals("Local Groovy"))
        && artifactFile.getName().startsWith("groovy-");
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

    @SuppressWarnings({"UnstableApiUsage"})
    ArtifactResolutionResult resolutionResult = dependencies
        .createArtifactResolutionQuery()
        .forComponents(artifactIdentifier.getComponentIdentifier())
        .withArtifacts(JvmLibrary.class, artifactTypes)
        .execute();

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
