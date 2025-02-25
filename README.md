# Build Server for Gradle

An implementation of the Build Server Protocol for Gradle.

## Requirement

JDK 17 or higher is required to build and launch the Build Server for Gradle.

## Supported Requests

Following BSP requests are supported in the current implementation:

- `build/initialize`
- `build/initialized`
- `build/shutdown`
- `build/exit`
- `buildTarget/sources`
- `buildTarget/resources`
- `buildTarget/outputPaths`
- `buildTarget/dependencyModules`
- `buildTarget/dependencySources`
- `buildTarget/inverseSources`
- `buildTarget/compile`
- `buildTarget/cleanCache`
- `buildTarget/javacOptions`
- `buildTarget/scalacOptions`
- `buildTarget/jvmCompileClasspath`
- `buildTarget/test`
- `buildTarget/run`
- `buildTarget/jvmRunEnvironment`
- `buildTarget/jvmTestEnvironment`
- `workspace/buildTargets`
- `workspace/reload`

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md)

## Downloading

The latest version can be downloaded from [Maven](https://central.sonatype.com/artifact/io.github.arthurm1.gradle.bsp/server/overview) or local versions can be used by downloading the [source code](https://github.com/arthurm1/build-server-for-gradle), incrementing `version` in `gradle.properties` and then publishing to Maven Local using `./gradlew publishToMavenLocal`.

## Launch the Build Server for Gradle

### Run the Server

The main class of the build server is `com.microsoft.java.bs.core.Launcher`.

E.g. using [Coursier](https://get-coursier.io/) where XXXX is the latest version...

```
coursier launch io.github.arthurm1.gradle.bsp:server:XXXX -r https://repo.gradle.org/gradle/libs-releases -M com.microsoft.java.bs.core.Launcher
```


### Creating the BSP connection file (optional)

See [Build Server Discovery](https://build-server-protocol.github.io/docs/overview/server-discovery#the-bsp-connection-details)
The main class `com.microsoft.java.bs.core.BspFileCreator` will produce a BSP Connection file `.bsp/gradle-bsp.json`.
Run from the Gradle project directory.

E.g. using [Coursier](https://get-coursier.io/) where XXXX is the latest version...

```
coursier launch io.github.arthurm1.gradle.bsp:server:XXXX -r https://repo.gradle.org/gradle/libs-releases -M com.microsoft.java.bs.core.BspFileCreator
```

### BSP message logging

Set the `bsp.gradle.traceMessagesFile` system property to a file location (e.g. `~/bsp.trace.json`) to log all BSP messages to file.  The file will be truncated on restart of BSP server

### Preferences

A [Preferences](./server/src/main/java/com/microsoft/java/bs/core/internal/model/Preferences.java) object can be put into the data field of the `build/initialize` request for customization. Please check the comments in the code for the meaning of each preference.

### Transport Method

The Gradle Build Server supports two types of transport methods: standard input/output and named pipe (unix socket). By default, the server uses the standard input/output transport if no arguments are specified.

To use the named pipe (unix socket) transport, start the server with the argument `--pipe=<pipeName>`. If the `--pipe=` option is provided but `pipeName` left empty, the server defaults to using the standard input/output transport.

## Export to Bloop files (optional)

See [Bloop](https://scalacenter.github.io/bloop/)
The main class `com.microsoft.java.bs.core.BloopExporter` will export the project as Bloop files in `.bloop/*.json`.
Be aware that existing project files in the `.bloop` directory will be overwritten and old project files will not be deleted.
Run from the Gradle project directory.

E.g. using [Coursier](https://get-coursier.io/) where XXXX is the latest version...

```
coursier launch io.github.arthurm1.gradle.bsp:server:XXXX -r https://repo.gradle.org/gradle/libs-releases -M com.microsoft.java.bs.core.BloopExporter
```

## Contributing

This project welcomes contributions and suggestions. Most contributions require you to agree to a Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us the rights to use your contribution. For details, visit https://cla.opensource.microsoft.com.

When you submit a pull request, a CLA bot will automatically determine whether you need to provide a CLA and decorate the PR appropriately (e.g., status check, comment). Simply follow the instructions provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

Please check [CONTRIBUTING.md](./CONTRIBUTING.md) for more details about how to setup and develop the project.

## Developer Documentation

Assisting documents to explaining the working of Gradle Build Server can be found in [developer documentation](./docs/developer.md). This will be helpful for you if you're planning to integrate Gradle Build Server or contribute to the project.

## Trademarks

This project may contain trademarks or logos for projects, products, or services. Authorized use of Microsoft trademarks or logos is subject to and must follow [Microsoft's Trademark & Brand Guidelines](https://www.microsoft.com/en-us/legal/intellectualproperty/trademarks/usage/general). Use of Microsoft trademarks or logos in modified versions of this project must not cause confusion or imply Microsoft sponsorship. Any use of third-party trademarks or logos are subject to those third-party's policies.

## Telemetry

The Build Server for Gradle will not send telemetry by itself. But it will send telemetry event to its client. If you don't want this behavior, set the system property `disableServerTelemetry`.
