// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;

public abstract class CheckStyleConventionPlugin implements Plugin<Project>
{
    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Override
    public void apply(Project project)
    {
        project.getPluginManager().apply("checkstyle");

        // setup tool version from versions.toml
        VersionCatalog versionCatalog = project.getExtensions()
            .getByType(VersionCatalogsExtension.class).named("libs");
        VersionConstraint versionConstraint = versionCatalog
            .findVersion("checkstyleTool").orElseThrow(() ->
            new IllegalStateException("`checkstyleTool` not specified in `versions.toml` `[libs]`"));
        CheckstyleExtension checkStyle = project.getExtensions().findByType(CheckstyleExtension.class);
        checkStyle.setToolVersion(versionConstraint.getRequiredVersion());

        checkStyle.setMaxWarnings(0);

        project.getTasks().withType(Checkstyle.class).configureEach(task -> {
            task.exclude("**/BuildInfo.java");
            Provider<JavaLauncher> launcher = getJavaToolchainService()
                .launcherFor(javaToolchainSpec -> javaToolchainSpec.getLanguageVersion()
                    .set(JavaLanguageVersion.of(17)));
            task.getJavaLauncher().set(launcher);
        });
    }
}