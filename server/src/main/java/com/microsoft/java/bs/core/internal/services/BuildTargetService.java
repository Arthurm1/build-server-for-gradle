// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.services;

import static com.microsoft.java.bs.core.Launcher.LOGGER;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.java.bs.core.internal.gradle.GradleApiConnector;
import com.microsoft.java.bs.core.internal.gradle.Utils;
import com.microsoft.java.bs.core.internal.log.BuildTargetChangeInfo;
import com.microsoft.java.bs.core.internal.managers.BuildTargetManager;
import com.microsoft.java.bs.core.internal.managers.PreferenceManager;
import com.microsoft.java.bs.core.internal.model.GradleBuildTarget;
import com.microsoft.java.bs.core.internal.model.GradleTestEntity;
import com.microsoft.java.bs.core.internal.reporter.CompileProgressReporter;
import com.microsoft.java.bs.core.internal.reporter.DefaultProgressReporter;
import com.microsoft.java.bs.core.internal.reporter.ProgressReporter;
import com.microsoft.java.bs.core.internal.utils.JsonUtils;
import com.microsoft.java.bs.core.internal.utils.TelemetryUtils;
import com.microsoft.java.bs.core.internal.utils.UriUtils;
import com.microsoft.java.bs.gradle.model.GradleModuleDependency;
import com.microsoft.java.bs.gradle.model.GradleRunTask;
import com.microsoft.java.bs.gradle.model.GradleSourceSet;
import com.microsoft.java.bs.gradle.model.GradleSourceSets;
import com.microsoft.java.bs.gradle.model.GradleTestTask;
import com.microsoft.java.bs.gradle.model.JavaExtension;
import com.microsoft.java.bs.gradle.model.ScalaExtension;
import com.microsoft.java.bs.gradle.model.SupportedLanguages;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.BuildTarget;
import ch.epfl.scala.bsp4j.BuildTargetEvent;
import ch.epfl.scala.bsp4j.BuildTargetEventKind;
import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.CleanCacheParams;
import ch.epfl.scala.bsp4j.CleanCacheResult;
import ch.epfl.scala.bsp4j.CompileParams;
import ch.epfl.scala.bsp4j.CompileResult;
import ch.epfl.scala.bsp4j.DependencyModule;
import ch.epfl.scala.bsp4j.DependencyModulesItem;
import ch.epfl.scala.bsp4j.DependencyModulesParams;
import ch.epfl.scala.bsp4j.DependencyModulesResult;
import ch.epfl.scala.bsp4j.DependencySourcesItem;
import ch.epfl.scala.bsp4j.DependencySourcesParams;
import ch.epfl.scala.bsp4j.DependencySourcesResult;
import ch.epfl.scala.bsp4j.JavacOptionsItem;
import ch.epfl.scala.bsp4j.JavacOptionsParams;
import ch.epfl.scala.bsp4j.JavacOptionsResult;
import ch.epfl.scala.bsp4j.JvmCompileClasspathItem;
import ch.epfl.scala.bsp4j.JvmCompileClasspathParams;
import ch.epfl.scala.bsp4j.JvmCompileClasspathResult;
import ch.epfl.scala.bsp4j.JvmEnvironmentItem;
import ch.epfl.scala.bsp4j.JvmMainClass;
import ch.epfl.scala.bsp4j.JvmRunEnvironmentParams;
import ch.epfl.scala.bsp4j.JvmRunEnvironmentResult;
import ch.epfl.scala.bsp4j.JvmTestEnvironmentParams;
import ch.epfl.scala.bsp4j.JvmTestEnvironmentResult;
import ch.epfl.scala.bsp4j.DidChangeBuildTarget;
import ch.epfl.scala.bsp4j.InverseSourcesParams;
import ch.epfl.scala.bsp4j.InverseSourcesResult;
import ch.epfl.scala.bsp4j.MavenDependencyModule;
import ch.epfl.scala.bsp4j.MavenDependencyModuleArtifact;
import ch.epfl.scala.bsp4j.OutputPathItem;
import ch.epfl.scala.bsp4j.OutputPathItemKind;
import ch.epfl.scala.bsp4j.OutputPathsItem;
import ch.epfl.scala.bsp4j.OutputPathsParams;
import ch.epfl.scala.bsp4j.OutputPathsResult;
import ch.epfl.scala.bsp4j.ResourcesItem;
import ch.epfl.scala.bsp4j.ResourcesParams;
import ch.epfl.scala.bsp4j.ResourcesResult;
import ch.epfl.scala.bsp4j.RunParams;
import ch.epfl.scala.bsp4j.RunParamsDataKind;
import ch.epfl.scala.bsp4j.RunResult;
import ch.epfl.scala.bsp4j.ScalaMainClass;
import ch.epfl.scala.bsp4j.ScalaTestClassesItem;
import ch.epfl.scala.bsp4j.ScalaTestParams;
import ch.epfl.scala.bsp4j.ScalaTestSuiteSelection;
import ch.epfl.scala.bsp4j.ScalaTestSuites;
import ch.epfl.scala.bsp4j.ScalacOptionsItem;
import ch.epfl.scala.bsp4j.ScalacOptionsParams;
import ch.epfl.scala.bsp4j.ScalacOptionsResult;
import ch.epfl.scala.bsp4j.SourceItem;
import ch.epfl.scala.bsp4j.SourceItemKind;
import ch.epfl.scala.bsp4j.SourcesItem;
import ch.epfl.scala.bsp4j.SourcesParams;
import ch.epfl.scala.bsp4j.SourcesResult;
import ch.epfl.scala.bsp4j.StatusCode;
import ch.epfl.scala.bsp4j.TestParams;
import ch.epfl.scala.bsp4j.TestParamsDataKind;
import ch.epfl.scala.bsp4j.TestResult;
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsResult;
import org.apache.commons.lang3.StringUtils;
import org.gradle.tooling.CancellationToken;

/**
 * Service to handle build target related BSP requests.
 */
public class BuildTargetService {

  private static final String MAVEN_DATA_KIND = "maven";

  private final BuildTargetManager buildTargetManager;

  private final GradleApiConnector connector;

  private final PreferenceManager preferenceManager;

  private BuildClient client;

  private boolean firstTime;

  /**
   * Initialize the build target service.
   *
   * @param buildTargetManager the build target manager.
   * @param connector wrapper round connection to Gradle API
   * @param preferenceManager the preference manager.
   */
  public BuildTargetService(BuildTargetManager buildTargetManager,
      GradleApiConnector connector, PreferenceManager preferenceManager) {
    this.buildTargetManager = buildTargetManager;
    this.connector = connector;
    this.preferenceManager = preferenceManager;
    this.firstTime = true;
  }

  private List<BuildTargetChangeInfo> updateBuildTargets(CancellationToken cancelToken) {
    GradleSourceSets sourceSets = connector.getGradleSourceSets(preferenceManager.getRootUri(),
        client, cancelToken);
    Function<GradleSourceSet, String> displayNameMaker = Utils.getDisplayNameMaker(
        preferenceManager.getPreferences());
    return buildTargetManager.store(sourceSets, displayNameMaker);
  }

  private BuildTargetManager getBuildTargetManager(CancellationToken cancelToken) {
    if (firstTime) {
      updateBuildTargets(cancelToken);
      firstTime = false;
      int buildTargetCount = buildTargetManager.getAllGradleBuildTargets().size();
      Map<String, String> map = TelemetryUtils.getMetadataMap("buildTargetCount",
          String.valueOf(buildTargetCount));
      LOGGER.log(Level.INFO, "Found " + buildTargetCount + " build targets during initialization.",
          map);
    }
    return buildTargetManager;
  }

  /**
   * reload the sourcesets from scratch and notify the BSP client if they have changed.
   *
   * @param cancelToken token to cancel Gradle command
   */
  public void reloadWorkspace(CancellationToken cancelToken) {
    // if the workspace hasn't been retrieved yet then do nothing here.
    if (!firstTime) {
      List<BuildTargetChangeInfo> changedTargets = updateBuildTargets(cancelToken);
      if (!changedTargets.isEmpty()) {
        notifyBuildTargetsChanged(changedTargets);
      }
    }
  }

  private void notifyBuildTargetsChanged(List<BuildTargetChangeInfo> changedTargets) {
    List<BuildTargetEvent> events = changedTargets.stream()
        .map(changeInfo -> {
          BuildTargetEvent event = new BuildTargetEvent(changeInfo.getBtId());
          if (changeInfo.hasChanged()) {
            event.setKind(BuildTargetEventKind.CHANGED);
            event.setDataKind("SourceSetChange");
            event.setData(changeInfo.getDifference());
          } else if (changeInfo.isAdded()) {
            event.setKind(BuildTargetEventKind.CREATED);
          } else if (changeInfo.isRemoved()) {
            event.setKind(BuildTargetEventKind.DELETED);
          }
          return event;
        })
        .collect(Collectors.toList());
    DidChangeBuildTarget param = new DidChangeBuildTarget(events);
    client.onBuildTargetDidChange(param);
  }

  private GradleBuildTarget getGradleBuildTarget(BuildTargetIdentifier btId,
      CancellationToken cancelToken) {
    return getBuildTargetManager(cancelToken).getGradleBuildTarget(btId);
  }

  /**
   * set the build client.
   *
   * @param client the BSP client
   */
  public void setClient(BuildClient client) {
    this.client = client;
  }

  /**
   * Get the build targets of the workspace.
   *
   * @param cancelToken token to cancel Gradle command
   * @return the workspace targets
   */
  public WorkspaceBuildTargetsResult getWorkspaceBuildTargets(CancellationToken cancelToken) {
    List<GradleBuildTarget> allTargets = getBuildTargetManager(cancelToken)
        .getAllGradleBuildTargets();
    Function<GradleBuildTarget, BuildTarget> mapper;
    if (preferenceManager.getPreferences().getIncludeTargetBaseDirectory()) {
      mapper = GradleBuildTarget::getBuildTarget;
    } else {
      mapper = gbt -> {
        // intellij can't handle duplicate base dirs so copy without them.
        BuildTarget bt = gbt.getBuildTarget();
        BuildTarget newBt = new BuildTarget(bt.getId(), bt.getTags(), bt.getLanguageIds(), bt.getDependencies(),
            bt.getCapabilities());
        newBt.setDisplayName(bt.getDisplayName());
        newBt.setBaseDirectory(null);
        newBt.setDataKind(bt.getDataKind());
        newBt.setData(bt.getData());
        return newBt;
      };
    }

    List<BuildTarget> targets = allTargets.stream()
        .map(mapper)
        .collect(Collectors.toList());
    return new WorkspaceBuildTargetsResult(targets);
  }

  private boolean isCancelled(CancellationToken cancelToken) {
    return cancelToken != null && cancelToken.isCancellationRequested();
  }

  /**
   * Get the sources.
   *
   * @param params targets to retrieve sources for
   * @param cancelToken token to cancel Gradle command
   * @return the target sources
   */
  public SourcesResult getBuildTargetSources(SourcesParams params, CancellationToken cancelToken) {
    List<SourcesItem> sourceItems = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip sources collection for the build target: " + btId.getUri()
              + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        List<SourceItem> sources = new ArrayList<>();
        for (File sourceDir : sourceSet.getSourceDirs()) {
          sources.add(new SourceItem(sourceDir.toPath().toUri().toString(),
              SourceItemKind.DIRECTORY, false /* generated */));
        }
        for (File sourceDir : sourceSet.getGeneratedSourceDirs()) {
          sources.add(new SourceItem(sourceDir.toPath().toUri().toString(),
              SourceItemKind.DIRECTORY, true /* generated */));
        }
        SourcesItem item = new SourcesItem(btId, sources);
        sourceItems.add(item);
      }
    }
    return new SourcesResult(sourceItems);
  }

  /**
   * Get the resources.
   *
   * @param params targets to retrieve resources for
   * @param cancelToken token to cancel Gradle command
   * @return the target resources
   */
  public ResourcesResult getBuildTargetResources(ResourcesParams params,
      CancellationToken cancelToken) {
    List<ResourcesItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip resources collection for the build target: " + btId.getUri()
              + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        List<String> resources = new ArrayList<>();
        for (File resourceDir : sourceSet.getResourceDirs()) {
          resources.add(resourceDir.toPath().toUri().toString());
        }
        ResourcesItem item = new ResourcesItem(btId, resources);
        items.add(item);
      }
    }
    return new ResourcesResult(items);
  }

  /**
   * Get the output paths.
   *
   * @param params targets to retrieve output paths for
   * @param cancelToken token to cancel Gradle command
   * @return the target output paths
   */
  public OutputPathsResult getBuildTargetOutputPaths(OutputPathsParams params,
      CancellationToken cancelToken) {
    List<OutputPathsItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip output collection for the build target: " + btId.getUri()
              + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        List<OutputPathItem> outputPaths = new ArrayList<>();

        Set<File> sourceOutputDirs = sourceSet.getSourceOutputDirs();
        if (sourceOutputDirs != null) {
          String suffix = preferenceManager.getPreferences().getUseQualifiedOutputPaths()
              ? "?kind=source" : "";
          for (File sourceOutputDir : sourceOutputDirs) {
            outputPaths.add(new OutputPathItem(
                sourceOutputDir.toPath().toUri() + suffix,
                OutputPathItemKind.DIRECTORY
            ));
          }
        }

        Set<File> resourceOutputDirs = sourceSet.getResourceOutputDirs();
        if (resourceOutputDirs != null) {
          String suffix = preferenceManager.getPreferences().getUseQualifiedOutputPaths() 
              ? "?kind=resource" : "";
          for (File resourceOutputDir : resourceOutputDirs) {
            outputPaths.add(new OutputPathItem(
                resourceOutputDir.toPath().toUri() + suffix,
                OutputPathItemKind.DIRECTORY
            ));
          }
        }

        OutputPathsItem item = new OutputPathsItem(btId, outputPaths);
        items.add(item);
      }
    }
    return new OutputPathsResult(items);
  }

  /**
   * Get inverse sources.
   *
   * @param params documents to retrieve inverse sources for
   * @param cancelToken token to cancel Gradle command
   * @return the inverse sources
   */
  public InverseSourcesResult getBuildTargetInverseSources(InverseSourcesParams params,
      CancellationToken cancelToken) {
    String source = params.getTextDocument().getUri();
    URI uri = UriUtils.getUriFromString(source);
    Path path = Path.of(uri);
    Map<Path, BuildTargetIdentifier> sources = buildTargetManager.getSourceDirsMap();
    List<BuildTargetIdentifier> btIds = new ArrayList<>();
    for (Map.Entry<Path, BuildTargetIdentifier> entry : sources.entrySet()) {
      if (path.startsWith(entry.getKey())) {
        btIds.add(entry.getValue());
      }
    }
    return new InverseSourcesResult(btIds);
  }

  /**
   * Get artifacts dependencies - old way.
   *
   * @param params targets to retrieve artifacts dependencies for
   * @param cancelToken token to cancel Gradle command
   * @return the artifacts dependencies
   */
  public DependencySourcesResult getBuildTargetDependencySources(DependencySourcesParams params,
      CancellationToken cancelToken) {
    List<DependencySourcesItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip output collection for the build target: " + btId.getUri()
                  + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        List<String> sources = new ArrayList<>();
        for (GradleModuleDependency dep : sourceSet.getModuleDependencies()) {
          List<String> artifacts = dep.getArtifacts().stream()
                  .filter(a -> "sources".equals(a.getClassifier()))
                  .map(a -> a.getUri().toString())
                  .toList();
          sources.addAll(artifacts);
        }

        items.add(new DependencySourcesItem(btId, sources));
      }
    }
    return new DependencySourcesResult(items);
  }

  /**
   * Get artifacts dependencies.
   *
   * @param params targets to retrieve artifacts dependencies for
   * @param cancelToken token to cancel Gradle command
   * @return the artifacts dependencies
   */
  public DependencyModulesResult getBuildTargetDependencyModules(DependencyModulesParams params,
      CancellationToken cancelToken) {
    List<DependencyModulesItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip output collection for the build target: " + btId.getUri()
              + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        List<DependencyModule> modules = new ArrayList<>();
        for (GradleModuleDependency dep : sourceSet.getModuleDependencies()) {
          DependencyModule module = new DependencyModule(dep.getModule(), dep.getVersion());
          module.setDataKind(MAVEN_DATA_KIND);
          List<MavenDependencyModuleArtifact> artifacts = dep.getArtifacts().stream().map(a -> {
            MavenDependencyModuleArtifact artifact = new MavenDependencyModuleArtifact(
                a.getUri().toString());
            artifact.setClassifier(a.getClassifier());
            return artifact;
          }).collect(Collectors.toList());
          MavenDependencyModule mavenModule = new MavenDependencyModule(
              dep.getGroup(),
              dep.getModule(),
              dep.getVersion(),
              artifacts
          );
          module.setData(mavenModule);
          modules.add(module);
        }

        DependencyModulesItem item = new DependencyModulesItem(btId, modules);
        items.add(item);
      }
    }
    return new DependencyModulesResult(items);
  }

  /**
   * Compile the build targets.
   *
   * @param params targets to build
   * @param cancelToken token to cancel Gradle command
   * @return the compile result
   */
  public CompileResult compile(CompileParams params, CancellationToken cancelToken) {
    if (params.getTargets().isEmpty()) {
      return new CompileResult(StatusCode.OK);
    } else {
      ProgressReporter reporter = new CompileProgressReporter(client,
          params.getOriginId(), getFullTaskPathMap());
      StatusCode code = runTasks(params.getTargets(), btId -> getBuildTaskName(btId, cancelToken),
          reporter, cancelToken);
      CompileResult result = new CompileResult(code);
      result.setOriginId(params.getOriginId());

      // Schedule a task to refetch the build targets after compilation, this is to
      // auto detect the source roots changes for those code generation framework,
      // such as Protocol Buffer.
      // This doesn't take into account compilation triggered from running main class or tests.
      // This cannot be cancelled as it's not triggered from a BSP Client so the CompletableFuture
      // is left in the ether.
      // It could be shifted into the `GradleBuildServer#buildTargetCompile` and chained onto that
      // result but that would delay the CompileResult
      if (!Boolean.getBoolean("bsp.plugin.reloadworkspace.disabled")) {
        CompletableFuture.runAsync(() -> reloadWorkspace(null));
      }
      return result;
    }
  }

  /**
   * clean the build targets.
   *
   * @param params targets to clean sources for
   * @param cancelToken token to cancel Gradle command
   * @return the clean result
   */
  public CleanCacheResult cleanCache(CleanCacheParams params, CancellationToken cancelToken) {
    ProgressReporter reporter = new DefaultProgressReporter(client);
    StatusCode code = runTasks(params.getTargets(), btId -> getCleanTaskName(btId, cancelToken),
        reporter, cancelToken);
    return new CleanCacheResult(code == StatusCode.OK);
  }

  /**
   * create a map of all known taskpaths to the build targets they affect.
   * used to associate progress events to the correct target.
   */
  private Map<String, Set<BuildTargetIdentifier>> getFullTaskPathMap() {
    Map<String, Set<BuildTargetIdentifier>> fullTaskPathMap = new HashMap<>();
    for (GradleBuildTarget buildTarget : buildTargetManager.getAllGradleBuildTargets()) {
      Set<String> tasks = buildTarget.getSourceSet().getTaskNames();
      BuildTargetIdentifier btId = buildTarget.getBuildTarget().getId();
      for (String taskName : tasks) {
        fullTaskPathMap.computeIfAbsent(taskName, k -> new HashSet<>()).add(btId);
      }
    }
    return fullTaskPathMap;
  }

  /**
   * group targets by project root and execute the supplied tasks.
   */
  private StatusCode runTasks(List<BuildTargetIdentifier> targets,
      Function<BuildTargetIdentifier, String> taskNameCreator,
      ProgressReporter reporter, CancellationToken cancelToken) {
    Map<URI, Set<BuildTargetIdentifier>> groupedTargets =
        groupBuildTargetsByRootDir(targets, cancelToken);
    StatusCode code = StatusCode.OK;
    for (Map.Entry<URI, Set<BuildTargetIdentifier>> entry : groupedTargets.entrySet()) {
      if (!isCancelled(cancelToken)) {
        // remove duplicates as some tasks will have the same name for each sourceset e.g. clean.
        String[] tasks = entry.getValue().stream().map(taskNameCreator).distinct()
            .toArray(String[]::new);
        code = connector.runTasks(entry.getKey(), reporter, tasks, cancelToken);
        if (code == StatusCode.ERROR) {
          break;
        }
      }
    }
    return code;
  }

  /**
   * Get the Java compiler paths.
   *
   * @param params targets to get the Java compiler paths for
   * @param cancelToken token to cancel Gradle command
   * @return the targets compiler paths
   */
  public JvmCompileClasspathResult getBuildTargetJvmCompileClasspath(
      JvmCompileClasspathParams params, CancellationToken cancelToken) {
    List<JvmCompileClasspathItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip jvm compile classpath collection for the build target: "
              + btId.getUri() + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        List<String> classpath = sourceSet.getCompileClasspath().stream()
            .map(file -> file.toPath().toUri().toString())
            .collect(Collectors.toList());
        items.add(new JvmCompileClasspathItem(btId, classpath));
      }
    }
    return new JvmCompileClasspathResult(items);
  }


  /**
   * Get the Java compiler options.
   *
   * @param params targets to get the Java compiler options for
   * @param cancelToken token to cancel Gradle command
   * @return the targets javac options
   */
  public JavacOptionsResult getBuildTargetJavacOptions(JavacOptionsParams params,
      CancellationToken cancelToken) {
    List<JavacOptionsItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip javac options collection for the build target: " + btId.getUri()
              + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        JavaExtension javaExtension = SupportedLanguages.JAVA.getExtension(sourceSet);
        if (javaExtension == null) {
          LOGGER.fine("Skip javac options collection for the build target: " + btId.getUri()
              + ". Because the java extension cannot be found from source set.");
          continue;
        }
        List<String> classpath = sourceSet.getCompileClasspath().stream()
            .map(file -> file.toPath().toUri().toString())
            .collect(Collectors.toList());
        String classesDir;
        if (javaExtension.getClassesDir() != null) {
          classesDir = javaExtension.getClassesDir().toPath().toUri().toString();
        } else {
          classesDir = "";
        }
        items.add(new JavacOptionsItem(
            btId,
            javaExtension.getCompilerArgs(),
            classpath,
            classesDir
        ));
      }
    }
    return new JavacOptionsResult(items);
  }
  
  /**
   * Get the Scala compiler options.
   *
   * @param params targets to get the Scala compiler options for
   * @param cancelToken token to cancel Gradle command
   * @return the targets scalac options
   */
  public ScalacOptionsResult getBuildTargetScalacOptions(ScalacOptionsParams params,
      CancellationToken cancelToken) {
    List<ScalacOptionsItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      if (!isCancelled(cancelToken)) {
        GradleBuildTarget target = getGradleBuildTarget(btId, cancelToken);
        if (target == null) {
          LOGGER.warning("Skip scalac options collection for the build target: " + btId.getUri()
              + ". Because it cannot be found in the cache.");
          continue;
        }

        GradleSourceSet sourceSet = target.getSourceSet();
        ScalaExtension scalaExtension = SupportedLanguages.SCALA.getExtension(sourceSet);
        if (scalaExtension == null) {
          LOGGER.fine("Skip scalac options collection for the build target: " + btId.getUri()
                  + ". Because the scalac extension cannot be found from source set.");
          continue;
        }
        List<String> classpath = sourceSet.getCompileClasspath().stream()
            .map(file -> file.toPath().toUri().toString())
            .collect(Collectors.toList());
        String classesDir;
        if (scalaExtension.getClassesDir() != null) {
          classesDir = scalaExtension.getClassesDir().toPath().toUri().toString();
        } else {
          classesDir = "";
        }
        items.add(new ScalacOptionsItem(
            btId,
            scalaExtension.getScalaCompilerArgs(),
            classpath,
            classesDir
        ));
      }
    }
    return new ScalacOptionsResult(items);
  }

  /**
   * Run the test classes.
   *
   * @param params targets and other test information required to run the tests
   * @param cancelToken token to cancel Gradle command
   * @return the targets test results
   */
  public TestResult buildTargetTest(TestParams params, CancellationToken cancelToken) {
    TestResult testResult = new TestResult(StatusCode.OK);
    testResult.setOriginId(params.getOriginId());
    // running tests can trigger compilation that must be reported on
    CompileProgressReporter compileProgressReporter =
        new CompileProgressReporter(client, params.getOriginId(), getFullTaskPathMap());
    Map<URI, Set<BuildTargetIdentifier>> groupedTargets =
        groupBuildTargetsByRootDir(params.getTargets(), cancelToken);
    for (Map.Entry<URI, Set<BuildTargetIdentifier>> entry : groupedTargets.entrySet()) {
      StatusCode statusCode;
      if (!isCancelled(cancelToken)) {
        if (!entry.getValue().isEmpty()) {
          GradleBuildTarget target = buildTargetManager.getGradleBuildTarget(
              entry.getValue().iterator().next());
          String gradleVersion = target.getSourceSet().getGradleVersion();
          if (TestParamsDataKind.SCALA_TEST.equals(params.getDataKind())) {
            // existing logic for scala test (class level)
            statusCode = runScalaTests(entry.getKey(), params, compileProgressReporter,
                cancelToken, gradleVersion);
          } else if ("scala-test-suites-selection".equals(params.getDataKind())) {
            statusCode = runScalaTestSuitesSelection(entry.getKey(), params,
                compileProgressReporter, cancelToken, gradleVersion);
          } else {
            LOGGER.warning("Test Data Kind " + params.getDataKind() + " not supported");
            statusCode = StatusCode.ERROR;
          }
        } else {
          LOGGER.warning("No build targets for " + entry.getKey());
          statusCode = StatusCode.ERROR;
        }
      } else {
        statusCode = StatusCode.CANCELLED;
      }

      if (statusCode != StatusCode.OK) {
        testResult.setStatusCode(statusCode);
      }
    }

    return testResult;
  }

  private StatusCode runScalaTests(
      URI projectUri,
      TestParams params,
      CompileProgressReporter compileProgressReporter,
      CancellationToken cancelToken,
      String gradleVersion
  ) {
    // ScalaTestParams is for a list of classes only
    ScalaTestParams testParams = JsonUtils.toModel(params.getData(), ScalaTestParams.class);
    Map<BuildTargetIdentifier, Map<String, Set<String>>> testClasses = new HashMap<>();
    for (ScalaTestClassesItem testClassesItem : testParams.getTestClasses()) {
      Map<String, Set<String>> classesMethods = new HashMap<>();
      for (String classNames : testClassesItem.getClasses()) {
        classesMethods.put(classNames, null);
      }
      testClasses.put(testClassesItem.getTarget(), classesMethods);
    }
    return connector.runTests(projectUri, testClasses, testParams.getJvmOptions(),
        params.getArguments(), null, client, params.getOriginId(),
        compileProgressReporter, cancelToken, gradleVersion);
  }

  private StatusCode runScalaTestSuitesSelection(
      URI projectUri,
      TestParams params,
      CompileProgressReporter compileProgressReporter,
      CancellationToken cancelToken,
      String gradleVersion
  ) {
    // ScalaTestSuites is for a list of classes + methods
    // Since it doesn't supply the specific BuildTarget we require a single
    // build target in the params and reject any request that doesn't match this
    if (params.getTargets().size() != 1) {
      LOGGER.warning("Test params with Test Data Kind " + params.getDataKind()
          + " must contain only 1 build target");
      return StatusCode.ERROR;
    } else {
      ScalaTestSuites testSuites = JsonUtils.toModel(params.getData(), ScalaTestSuites.class);
      Map<String, String> envVars = null;
      boolean argsValid = true;
      if (testSuites.getEnvironmentVariables() != null) {
        // arg is of the form KEY=VALUE
        List<String[]> splitArgs = testSuites.getEnvironmentVariables()
            .stream()
            .map(arg -> arg.split("="))
            .toList();
        argsValid = splitArgs.stream().allMatch(arg -> arg.length == 2);
        if (argsValid) {
          envVars = splitArgs.stream().collect(Collectors.toMap(arg -> arg[0], arg -> arg[1]));
        }
      }
      if (!argsValid) {
        LOGGER.warning("Test params arguments must each be in the form KEY=VALUE. "
            + testSuites.getEnvironmentVariables());
        return StatusCode.ERROR;
      } else {
        Map<String, Set<String>> classesMethods = new HashMap<>();
        for (ScalaTestSuiteSelection testSuiteSelection : testSuites.getSuites()) {
          Set<String> methods = classesMethods
              .computeIfAbsent(testSuiteSelection.getClassName(), k -> new HashSet<>());
          methods.addAll(testSuiteSelection.getTests());
        }
        Map<BuildTargetIdentifier, Map<String, Set<String>>> testClasses = new HashMap<>();
        testClasses.put(params.getTargets().get(0), classesMethods);
        return connector.runTests(projectUri, testClasses, testSuites.getJvmOptions(),
            params.getArguments(), envVars, client, params.getOriginId(),
            compileProgressReporter, cancelToken, gradleVersion);
      }
    }
  }

  /**
   * get the test classes.
   *
   * @param params targets to get the test classes for
   * @param cancelToken token to cancel Gradle command
   * @return the targets test environments
   */
  public JvmTestEnvironmentResult getBuildTargetJvmTestEnvironment(
      JvmTestEnvironmentParams params, CancellationToken cancelToken) {
    Map<BuildTargetIdentifier, List<GradleTestEntity>> mainClassesMap = new HashMap<>();
    Map<URI, Set<BuildTargetIdentifier>> groupedTargets =
        groupBuildTargetsByRootDir(params.getTargets(), cancelToken);
    // retrieving tests can trigger compilation that must be reported on
    CompileProgressReporter compileProgressReporter = new CompileProgressReporter(client,
            params.getOriginId(), getFullTaskPathMap());
    for (Map.Entry<URI, Set<BuildTargetIdentifier>> entry : groupedTargets.entrySet()) {
      Map<BuildTargetIdentifier, Set<GradleTestTask>> testTaskMap = new HashMap<>();
      String gradleVersion = null;
      for (BuildTargetIdentifier btId : entry.getValue()) {
        GradleBuildTarget target = buildTargetManager.getGradleBuildTarget(btId);
        if (target == null) {
          LOGGER.warning("Skip test collection for the build target: " + btId.getUri()
              + ". Because it cannot be found in the cache.");
          continue;
        }
        gradleVersion = target.getSourceSet().getGradleVersion();
        testTaskMap.put(btId, target.getSourceSet().getTestTasks());
      }
      if (gradleVersion != null) {
        URI projectUri = entry.getKey();
        Map<BuildTargetIdentifier, List<GradleTestEntity>> partialMainClassesMap =
            connector.getTestClasses(projectUri, testTaskMap, client,
                compileProgressReporter, cancelToken, gradleVersion);
        mainClassesMap.putAll(partialMainClassesMap);
      }
    }
    List<JvmEnvironmentItem> items = new ArrayList<>();
    for (Map.Entry<BuildTargetIdentifier, List<GradleTestEntity>> entry :
        mainClassesMap.entrySet()) {
      for (GradleTestEntity gradleTestEntity : entry.getValue()) {
        GradleTestTask gradleTestTask = gradleTestEntity.getGradleTestTask();
        List<String> classpath = gradleTestTask.getClasspath()
            .stream()
            .map(file -> file.toURI().toString())
            .collect(Collectors.toList());
        List<String> jvmOptions = gradleTestTask.getJvmOptions();
        String workingDirectory = gradleTestTask.getWorkingDirectory().toURI().toString();
        Map<String, String> environmentVariables = gradleTestTask.getEnvironmentVariables();
        List<JvmMainClass> mainClasses = gradleTestEntity.getTestClasses()
            .stream()
            .map(mainClass -> new JvmMainClass(mainClass, Collections.emptyList()))
            .collect(Collectors.toList());
        JvmEnvironmentItem item = new JvmEnvironmentItem(entry.getKey(),
            classpath, jvmOptions, workingDirectory, environmentVariables);
        item.setMainClasses(mainClasses);
        items.add(item);
      }
    }
    return new JvmTestEnvironmentResult(items);
  }

  /**
   * Run the main class.
   *
   * @param params targets and other run information required to run the main classes
   * @param cancelToken token to cancel Gradle command
   * @return the run main class result
   */
  public RunResult buildTargetRun(RunParams params, CancellationToken cancelToken) {
    RunResult runResult = new RunResult(StatusCode.OK);
    runResult.setOriginId(params.getOriginId());
    if (!RunParamsDataKind.SCALA_MAIN_CLASS.equals(params.getDataKind())) {
      LOGGER.warning("Run Data Kind " + params.getDataKind() + " not supported");
      runResult.setStatusCode(StatusCode.ERROR);
    } else {
      // running tests can trigger compilation that must be reported on
      CompileProgressReporter compileProgressReporter = new CompileProgressReporter(client,
              params.getOriginId(), getFullTaskPathMap());
      GradleBuildTarget buildTarget = getGradleBuildTarget(params.getTarget(),
          cancelToken);
      if (buildTarget == null) {
        // TODO: https://github.com/microsoft/build-server-for-gradle/issues/50
        throw new IllegalArgumentException("The build target does not exist: "
          + params.getTarget().getUri());
      }
      URI projectUri = getRootProjectUri(params.getTarget(), cancelToken);
      // ideally BSP would have a jvmRunEnv style runkind for executing tests, not scala.
      ScalaMainClass mainClass = JsonUtils.toModel(params.getData(), ScalaMainClass.class);
      // TODO BSP is not clear on which argument set takes precedence
      List<String> arguments1 = params.getArguments();
      List<String> arguments2 = mainClass.getArguments();
      List<String> argumentsToUse;
      if (arguments1 == null || arguments1.isEmpty()) {
        argumentsToUse = arguments2;
      } else {
        argumentsToUse = arguments1;
      }
      StatusCode statusCode = connector.runMainClass(projectUri,
              buildTarget.getSourceSet().getProjectPath(),
              buildTarget.getSourceSet().getSourceSetName(),
              mainClass.getClassName(),
              params.getEnvironmentVariables(),
              mainClass.getJvmOptions(),
              argumentsToUse,
              client,
              params.getOriginId(),
              compileProgressReporter,
              cancelToken);

      if (statusCode != StatusCode.OK) {
        runResult.setStatusCode(statusCode);
      }
    }
    return runResult;
  }

  /**
   * get the main classes runtime environment.
   *
   * @param params targets to get the main classes for
   * @param cancelToken token to cancel Gradle command
   * @return the targets run environments
   */
  public JvmRunEnvironmentResult getBuildTargetJvmRunEnvironment(
      JvmRunEnvironmentParams params, CancellationToken cancelToken) {

    List<JvmEnvironmentItem> items = new ArrayList<>();
    for (BuildTargetIdentifier btId : params.getTargets()) {
      GradleBuildTarget target = buildTargetManager.getGradleBuildTarget(btId);
      if (target == null) {
        LOGGER.warning("Skip retrieving run environment for the build target: " + btId.getUri()
            + ". Because it cannot be found in the cache.");
        continue;
      }
      for (GradleRunTask runTask : target.getSourceSet().getRunTasks()) {
        List<String> classpath = runTask.getClasspath()
            .stream()
            .map(file -> file.toURI().toString())
            .collect(Collectors.toList());
        List<String> jvmOptions = runTask.getJvmOptions();
        String workingDirectory = runTask.getWorkingDirectory().toURI().toString();
        Map<String, String> environmentVariables = runTask.getEnvironmentVariables();
        String mainClass = runTask.getMainClass();
        List<JvmMainClass> mainClasses = new ArrayList<>();
        if (mainClass != null) {
          List<String> arguments = runTask.getArguments();
          JvmMainClass jvmMainClass = new JvmMainClass(mainClass, arguments);
          mainClasses.add(jvmMainClass);
        }
        JvmEnvironmentItem item = new JvmEnvironmentItem(btId,
            classpath, jvmOptions, workingDirectory, environmentVariables);
        item.setMainClasses(mainClasses);
        items.add(item);
      }
    }
    return new JvmRunEnvironmentResult(items);
  }

  /**
   * Group the build targets by the project root directory,
   * projects with the same root directory can run their tasks
   * in one single call.
   */
  private Map<URI, Set<BuildTargetIdentifier>> groupBuildTargetsByRootDir(
      List<BuildTargetIdentifier> targets, CancellationToken cancelToken) {
    Map<URI, Set<BuildTargetIdentifier>> groupedTargets = new HashMap<>();
    for (BuildTargetIdentifier btId : targets) {
      if (!isCancelled(cancelToken)) {
        URI projectUri = getRootProjectUri(btId, cancelToken);
        if (projectUri == null) {
          continue;
        }
        groupedTargets.computeIfAbsent(projectUri, k -> new HashSet<>()).add(btId);
      }
    }
    return groupedTargets;
  }

  /**
   * Try to get the project root directory uri. If root directory is not available,
   * return the uri of the build target.
   */
  private URI getRootProjectUri(BuildTargetIdentifier btId, CancellationToken cancelToken) {
    GradleBuildTarget gradleBuildTarget = getGradleBuildTarget(btId, cancelToken);
    if (gradleBuildTarget == null) {
      // TODO: https://github.com/microsoft/build-server-for-gradle/issues/50
      throw new IllegalArgumentException("The build target does not exist: " + btId.getUri());
    }
    BuildTarget buildTarget = gradleBuildTarget.getBuildTarget();
    if (buildTarget.getBaseDirectory() != null) {
      return UriUtils.getUriFromString(buildTarget.getBaseDirectory());
    }

    return UriUtils.getUriWithoutQuery(btId.getUri());
  }

  /**
   * Return a source set task name.
   */
  private String getProjectTaskName(BuildTargetIdentifier btId, String title,
      Function<GradleSourceSet, String> creator, CancellationToken cancelToken) {
    GradleBuildTarget gradleBuildTarget = getGradleBuildTarget(btId, cancelToken);
    if (gradleBuildTarget == null) {
      // TODO: https://github.com/microsoft/build-server-for-gradle/issues/50
      throw new IllegalArgumentException("The build target does not exist: " + btId.getUri());
    }
    String taskName = creator.apply(gradleBuildTarget.getSourceSet());
    if (StringUtils.isBlank(taskName)) {
      throw new IllegalArgumentException("The build target does not have a " + title + " task: "
          + btId.getUri());
    }
    return taskName;
  }

  /**
   * Return the build task name - [project path]:[task].
   */
  private String getBuildTaskName(BuildTargetIdentifier btId, CancellationToken cancelToken) {
    return getProjectTaskName(btId, "classes", GradleSourceSet::getClassesTaskName, cancelToken);
  }

  /**
   * Return the clean task name - [project path]:[task].
   */
  private String getCleanTaskName(BuildTargetIdentifier btId, CancellationToken cancelToken) {
    return getProjectTaskName(btId, "clean", GradleSourceSet::getCleanTaskName, cancelToken);
  }
}
