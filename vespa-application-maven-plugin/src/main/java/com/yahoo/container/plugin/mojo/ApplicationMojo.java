// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Tony Vaagenes
 */
@Mojo(name = "packageApplication", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ApplicationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(property = "vespaversion")
    private String vespaversion;

    @Parameter(defaultValue = "src/main/application")
    private String sourceDir;

    @Parameter(defaultValue = "target/application")
    private String destinationDir;

    @Override
    public void execute() throws MojoExecutionException {
        File applicationPackage = new File(project.getBasedir(), sourceDir);
        File applicationDestination = new File(project.getBasedir(), destinationDir);
        copyApplicationPackage(applicationPackage, applicationDestination);
        addBuildMetaData(applicationDestination);

        File componentsDir = createComponentsDir(applicationDestination);
        copyModuleBundles(project.getBasedir(), componentsDir);
        copyBundlesForSubModules(componentsDir);

        try {
            Compression.zipDirectory(applicationDestination, "");
        } catch (Exception e) {
            throw new MojoExecutionException("Failed zipping application.", e);
        }
    }

    /** Writes meta data about this package if the destination directory exists. */
    private void addBuildMetaData(File applicationDestination) throws MojoExecutionException {
        if ( ! applicationDestination.exists())
            return;

        if (vespaversion == null) // Get the build version of the parent project unless specifically set.
            vespaversion = project.getProperties().getProperty("vespaversion");

        String metaData = String.format("{\"compileVersion\": \"%s\",\n \"buildTime\": %d}",
                                        vespaversion,
                                        System.currentTimeMillis());
        try {
            Files.write(applicationDestination.toPath().resolve("build-meta.json"),
                        metaData.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed writing compile version and build time.", e);
        }
    }

    private void copyBundlesForSubModules(File componentsDir) throws MojoExecutionException {
        List<String> modules = emptyListIfNull(project.getModules());
        for (String module : modules) {
            File moduleDir = new File(project.getBasedir(), module);
            if (moduleDir.exists()) {
                copyModuleBundles(moduleDir, componentsDir);
            }
        }
    }

    private File createComponentsDir(File applicationDestination) throws MojoExecutionException {
        File componentsDir = new File(applicationDestination, "components");
        componentsDir.mkdirs();
        if (!componentsDir.exists() || !componentsDir.isDirectory()) {
            throw new MojoExecutionException("Failed creating components directory (" + componentsDir + ")");
        }
        return componentsDir;
    }

    private void copyApplicationPackage(File applicationPackage, File applicationDestination) throws MojoExecutionException {
        if (applicationPackage.exists()) {
            try {
                copyDirectory(applicationPackage.toPath(), applicationDestination.toPath());
            } catch (Exception e) {
                throw new MojoExecutionException("Failed copying applicationPackage", e);
            }
        }
    }

    private static void copyDirectory(Path source, Path destination) {
        try (Stream<Path> fileStreams = Files.walk(source)) {
            fileStreams.forEachOrdered(sourcePath -> {
                try {
                    Files.copy(sourcePath, source.resolve(destination.relativize(sourcePath)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void copyModuleBundles(File moduleDir, File componentsDir) throws MojoExecutionException {
        File moduleTargetDir = new File(moduleDir, "target");
        if (moduleTargetDir.exists()) {
            File[] bundles = moduleTargetDir.listFiles((dir, name) -> name.endsWith("-deploy.jar") ||
                                                                      name.endsWith("-bundle.jar") ||
                                                                      name.endsWith("-jar-with-dependencies.jar"));
            if (bundles == null) return;
            for (File bundle : bundles) {
                try {
                    copyFile(bundle, new File(componentsDir, bundle.getName()));
                    getLog().info("Copying bundle to application: " + bundle.getName());
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed copying bundle " + bundle, e);
                }
            }
        }
    }

    private void copyFile(File source, File destination) throws IOException {
        try (FileInputStream sourceStream = new FileInputStream(source);
             FileOutputStream destinationStream = new FileOutputStream(destination)) {
            sourceStream.transferTo(destinationStream);
        }
    }

    private static <T> List<T> emptyListIfNull(List<T> modules) {
        return modules == null ? Collections.emptyList(): modules;
    }

}
