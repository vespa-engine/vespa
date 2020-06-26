// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;

/**
 * @author bjorncs
 */
class TestBundleUtils {
    private TestBundleUtils() {}

    static Path outputDirectory(MavenProject project) { return targetDirectory(project).resolve("test-bundle/"); }

    static Path manifestFile(MavenProject project) { return outputDirectory(project).resolve(JarFile.MANIFEST_NAME); }

    static Path archiveFile(MavenProject project) {
        return targetDirectory(project).resolve(project.getBuild().getFinalName() + "-tests.jar");
    }

    private static Path targetDirectory(MavenProject project) { return Paths.get(project.getBuild().getDirectory()); }
}
