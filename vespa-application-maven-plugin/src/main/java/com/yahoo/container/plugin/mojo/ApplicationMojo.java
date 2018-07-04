// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
@Mojo(name = "packageApplication", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ApplicationMojo extends AbstractMojo {

    @Parameter( defaultValue = "${project}", readonly = true )
    protected MavenProject project;

    @Parameter(defaultValue = "src/main/application")
    private String sourceDir;

    @Parameter(defaultValue = "target/application")
    private String destinationDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File applicationPackage = new File(project.getBasedir(), sourceDir);
        File applicationDestination = new File(project.getBasedir(), destinationDir);
        copyApplicationPackage(applicationPackage, applicationDestination);

        File componentsDir = createComponentsDir(applicationDestination);
        copyModuleBundles(project.getBasedir(), componentsDir);
        copyBundlesForSubModules(componentsDir);

        try {
            Compression.zipDirectory(applicationDestination);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed zipping application.", e);
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
        componentsDir.mkdir();
        if (!componentsDir.exists() || !componentsDir.isDirectory()) {
            throw new MojoExecutionException("Failed creating components directory (" + componentsDir + ")");
        }
        return componentsDir;
    }

    private void copyApplicationPackage(File applicationPackage, File applicationDestination) throws MojoExecutionException {
        if (applicationPackage.exists()) {
            try {
                FileUtils.copyDirectory(applicationPackage, applicationDestination);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed copying applicationPackage", e);
            }
        }
    }

    private void copyModuleBundles(File moduleDir, File componentsDir) throws MojoExecutionException {
        File moduleTargetDir = new File(moduleDir, "target");
        if (moduleTargetDir.exists()) {
            File[] bundles = moduleTargetDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith("-deploy.jar") || name.endsWith("-jar-with-dependencies.jar");
                }
            });

            for (File bundle : bundles) {
                try {
                    copyFile(bundle, new File(componentsDir, bundle.getName()));
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed copying bundle " + bundle, e);
                }
            }
        }
    }

    private void copyFile(File source, File destination) throws IOException {
        try (FileInputStream sourceStream = new FileInputStream(source);
             FileOutputStream destinationStream = new FileOutputStream(destination)) {
            Compression.copyBytes(sourceStream, destinationStream);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> emptyListIfNull(List<T> modules) {
        return modules == null ?
                Collections.emptyList():
                modules;
    }
}
