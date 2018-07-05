// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

/**
 * @author Tony Vaagenes
 */
class CloverChecker {
    private final Log log;

    CloverChecker(Log log) {
        this.log = log;
    }

    @SuppressWarnings("unchecked") //getCompileSourceRoots() returns List instead of List<String>
    public boolean isForkedCloverLifecycle(MavenProject project, Path outputDirectory) {
        return "clover".equals(project.getArtifact().getClassifier()) &&
                project.getCompileSourceRoots().stream().anyMatch(
                        equalsPathAbsolutely(regularOutputDirectory(project, outputDirectory)));
    }

    /*
     * Regular output directory for generated classes,
     * i.e. not the clover output directory.
     *
     * Example:
     * If outputDirectory is target/clover/generated-sources/vespa-configgen-plugin,
     * return target/generated-sources/vespa-configgen-plugin.
     */
    private Path regularOutputDirectory(MavenProject project, Path outputDirectory) {
        Path cloverTargetPath = Paths.get(project.getBuild().getDirectory());
        Path targetPath = cloverTargetPath.getParent();

        if (!targetPath.endsWith("target")) {
            log.warn("Guessing that target directory is " + targetPath + ", this might not be correct.");
        }

        Path outputDirectoryRelativeToCloverDirectory = cloverTargetPath.relativize(outputDirectory);
        return targetPath.resolve(outputDirectoryRelativeToCloverDirectory);
    }

    private Predicate<String> equalsPathAbsolutely(Path path) {
        Path absolutePath = path.toAbsolutePath();

        return candidateStringPath -> Paths.get(candidateStringPath).toAbsolutePath().equals(absolutePath);
    }
}
