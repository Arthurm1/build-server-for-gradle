# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.10.0
## What's Changed
* enhancement - allow BSP client config of display name and base dir usage by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/12ecf16363567905cf65b45ce0dc0d463483ce1f

## 0.9.0
## What's Changed
* fix - ensure BSP server works with Intellij by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/fe07d24d41deaf4f82dbba6c2107eba81d13bf0f and https://github.com/Arthurm1/build-server-for-gradle/commit/07c6576fcb9ba863bf1a38ce039a4d76fab77f1a and https://github.com/Arthurm1/build-server-for-gradle/commit/10ed7b92b38c6a11fe8d5a2946f8b0421a8d4c70
* fix - use correct java executable depending on OS by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/6d2bfacd534b9417a8dbc6dd42e98df7299cb64a

## 0.8.0
## What's Changed
* enhancement - add BSP build discovery file creator by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/be08b8803a014649d0f9097ad44d2b16c100a5b9
* fix - add missing MavenCentral repository by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/f8e79e97fbb8c972e63d808fbaf63fe971a0329f

## 0.5.0
## What's Changed
* enhancement - Faster source set retrieval by @Arthurm1 in https://github.com/microsoft/build-server-for-gradle/pull/168
* enhancement - Named pipe support by @Jiaming in https://github.com/microsoft/build-server-for-gradle/pull/162
* enhancement - Android support by @Tanish-Ranjan in https://github.com/microsoft/build-server-for-gradle/pull/173 and in https://github.com/microsoft/build-server-for-gradle/pull/194
* enhancement - Kotlin support by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/0ee64d90f4ea5bea886f493d06af5f1226059b9f
* enhancement - Groovy support by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/b7684e7415723b3d5924ad379e4cfc92c5b46d45
* enhancement - Retrieve JVM runtime classpath by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/741a3965a47ad6ad7300337cbd96ddac7a0098f0
* enhancement - Include changed details in buildTarget/didChange message by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/acf5ef19b0c5bca78b63dd5c4537f421a3340964
* enhancement - Support buildTarget/run by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/72a6d4f44aac1ebd2b38f4264b755b544173d2d4
* enhancement - Support buildTarget/inverseSources by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/4a010f8020016028eb6e5fbb1f208a0d8781786e
* enhancement - Support buildTarget/jvmTestEnvironment by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/05770798fbf8ffb96de57b49d86dc91b8a17fb7d
* enhancement - Support buildTarget/jvmRunEnvironment by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/b54d5ab6093f4a39bc67cbcd0fa307d6fe4c0f85
* enhancement - Support buildTarget/jvmCompileClasspath by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/98b89c88bb12f81c4aa6c36bc010e145886af482
* enhancement - Support BSP cancellation by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/34675a1447df18389cb8aec5496519ee14490a75
* enhancement - Support semanticDB plugin by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/94549a6845392cdaada41231df595ddbfab8b329
* enhancement - Add export to Bloop by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/1811d5b4aec532ed0a06cfb7c53a70278f77fa62
* fix - Get java version compatibility only from java compile task by @Arthurm1 in https://github.com/microsoft/build-server-for-gradle/pull/188
* fix - Recognise generated sources by language by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/1790e3fe2e05220e4959f77f3c8c0c81c9d36b08 and https://github.com/Arthurm1/build-server-for-gradle/commit/7ac0402fdae4887d0f3fd4630b5def2ca77d59e9
* fix - Remove duplicate Scala compiler options by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/c74475acad604f7ef868669a290327689c0aa33b
* fix - Stop spurious buildTarget/didChange messages by @Arthurm1 in https://github.com/Arthurm1/build-server-for-gradle/commit/33dc7fedbbbca35733447f185a494af845ed6937 and https://github.com/Arthurm1/build-server-for-gradle/commit/ed49b92bd5e304a754080feb25867cd09f2a5dd8

## 0.3.0
## What's Changed
* enhancement - Add support for running tests by @Arthurm1 in https://github.com/microsoft/build-server-for-gradle/pull/144
* fix - Only send build target count during initialization by @jdneo in https://github.com/microsoft/build-server-for-gradle/pull/153
* fix - Handle older versions of Gradle by @Arthurm1 in https://github.com/microsoft/build-server-for-gradle/pull/149
* fix - Add JDK 22 compatibility support by @jdneo in https://github.com/microsoft/build-server-for-gradle/pull/152
* fix- Return the root cause of the message by @jdneo in https://github.com/microsoft/build-server-for-gradle/pull/156
* fix - LanguageExtension downcast resolution by @Tanish-Ranjan in https://github.com/microsoft/build-server-for-gradle/pull/160
* fix - Set env vars for Windows testing by @Arthurm1 in https://github.com/microsoft/build-server-for-gradle/pull/164
* fix - Composite Builds using Build Actions by @Tanish-Ranjan in https://github.com/microsoft/build-server-for-gradle/pull/154
* fix - Cannot get test display name by @jdneo in https://github.com/microsoft/build-server-for-gradle/pull/166
* fix - Unsupported class file for the build action class by @jdneo in https://github.com/microsoft/build-server-for-gradle/pull/170
* fix - Use project dir instead of project path by @jdneo in https://github.com/microsoft/build-server-for-gradle/pull/171
* fix - --no-daemon not working on Gradle 8.9 by @jdneo in https://github.com/microsoft/build-server-for-gradle/pull/172

## New Contributors
* @Tanish-Ranjan made their first contribution in https://github.com/microsoft/build-server-for-gradle/pull/160

## 0.2.0
## What's Changed
* enhancement - Populate more JavacOptionsResult info by @Arthurm1 in [#105](https://github.com/microsoft/build-server-for-gradle/pull/105)
* enhancement - Implement buildTargetCleanCache by @Arthurm1 in [#110](https://github.com/microsoft/build-server-for-gradle/pull/110)
* enhancement - Populate buildTarget/displayName by @Arthurm1 in [#106](https://github.com/microsoft/build-server-for-gradle/pull/106)
* enhancement - Populate java compiler args by @Arthurm1 in [#109](https://github.com/microsoft/build-server-for-gradle/pull/109)
* enhancement - Support buildTarget/dependencySources by @Arthurm1 in [#130](https://github.com/microsoft/build-server-for-gradle/pull/130)
* enhancement - Add Scala support by @Arthurm1 in [#113](https://github.com/microsoft/build-server-for-gradle/pull/113)
* fix - Put NOTICE.txt into META-INF folder of the output jar by @jdneo in [#103](https://github.com/microsoft/build-server-for-gradle/pull/103)
* fix - Check Gradle version for Java 21 by @jdneo in [#111](https://github.com/microsoft/build-server-for-gradle/pull/111)
* fix - Populate test tag correctly for all sourcesets by @Arthurm1 in [#108](https://github.com/microsoft/build-server-for-gradle/pull/108)
* fix - Handle different project dependency configs by @Arthurm1 in [#107](https://github.com/microsoft/build-server-for-gradle/pull/107)
* fix - Change display name format by @Arthurm1 in [#123](https://github.com/microsoft/build-server-for-gradle/pull/123)
* fix - Remove the fallback Gradle version by @jdneo in [#137](https://github.com/microsoft/build-server-for-gradle/pull/137)
* fix - Ignore compilation on empty build targets by @Arthurm1 in [#133](https://github.com/microsoft/build-server-for-gradle/pull/133)
* fix - Remove Comment generated by the 'init' task by @donat in [#138](https://github.com/microsoft/build-server-for-gradle/pull/138)
* refactor - Support multiple languages. by @jdneo in [#125](https://github.com/microsoft/build-server-for-gradle/pull/125)
* refactor - Shift sourceset population out of buildInitialize by @Arthurm1 in [#135](https://github.com/microsoft/build-server-for-gradle/pull/135)
* refactor - Add more error reporting for getGradleSourceSets failure by @Arthurm1 in [#139](https://github.com/microsoft/build-server-for-gradle/pull/139)
* refactor - Handle progress reports better by @Arthurm1 in [#116](https://github.com/microsoft/build-server-for-gradle/pull/116)
* dependencies - Update project to use Gradle 8.7 by @Arthurm1 in [#134](https://github.com/microsoft/build-server-for-gradle/pull/134)
* dependencies - Update Gradle tooling API to 8.7 by @Arthurm1 in [#146](https://github.com/microsoft/build-server-for-gradle/pull/146)
* test - Add message checking to integration tests by @Arthurm1 in [#136](https://github.com/microsoft/build-server-for-gradle/pull/136)
* test - Auto download toolchain in test project by @Arthurm1 in [#143](https://github.com/microsoft/build-server-for-gradle/pull/143)

## New Contributors
* @Arthurm1 made their first contribution in https://github.com/microsoft/build-server-for-gradle/pull/105
* @donat made their first contribution in https://github.com/microsoft/build-server-for-gradle/pull/138

**Full Changelog**: https://github.com/microsoft/build-server-for-gradle/compare/0.1.2...0.2.0

## 0.1.2
### Fixed
- Plugin with id not found. [PR#98](https://github.com/microsoft/build-server-for-gradle/pull/98)
- No builders are available to build a model of type. [PR#99](https://github.com/microsoft/build-server-for-gradle/pull/99)
- No such method error: CompileOptions.getAnnotationProcessorGeneratedSourcesDirectory(). [PR#100](https://github.com/microsoft/build-server-for-gradle/pull/100)

## 0.1.1
### Fixed
- Reuse Gradle connector for the same project root. [PR#94](https://github.com/microsoft/build-server-for-gradle/pull/94)
- Improve the Gradle home path search logic. [PR#95](https://github.com/microsoft/build-server-for-gradle/pull/95)
- Use Gradle 7.4.2 as a fallback version when no suitable build approach is found. [PR#96](https://github.com/microsoft/build-server-for-gradle/pull/96)

## 0.1.0
- Initial implementation.
