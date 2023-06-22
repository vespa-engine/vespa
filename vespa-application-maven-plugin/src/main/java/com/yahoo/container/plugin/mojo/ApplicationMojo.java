// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.OrFileFilter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
@Mojo(name = "packageApplication", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class ApplicationMojo extends AbstractMojo {

    private static final List<String> IGNORED_FILES = List.of(".DS_Store");

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
        if ( ! applicationDestination.exists()) return;

        if (vespaversion == null)
            vespaversion = project.getPlugin("com.yahoo.vespa:vespa-application-maven-plugin").getVersion();

        Version compileVersion = Version.from(vespaversion);
        if (compileVersion.isSnapshot()) return;

        MavenProject current = project;
        while (current.getParent() != null && current.getParent().getParentArtifact() != null)
            current = current.getParent();

        Version parentVersion = null;
        Artifact parentArtifact = current.getParentArtifact();
        if (parentArtifact != null && isVespaParent(parentArtifact.getGroupId())) {
            try {
                parentVersion = Version.from(parentArtifact.getSelectedVersion().toString());
            } catch (ArtifactResolutionException e) {
                parentVersion = Version.from(parentArtifact.getVersion());
            }
            if (parentVersion.compareTo(compileVersion) < 0)
                throw new IllegalArgumentException("compile version (" + compileVersion + ") cannot be higher than parent version (" + parentVersion + ")");
        }

        String metaData = String.format("""
                                        {
                                          "compileVersion": "%s",
                                          "buildTime": %d,
                                          "parentVersion": %s
                                        }
                                        """,
                                        compileVersion,
                                        System.currentTimeMillis(),
                                        parentVersion == null ? null : "\"" + parentVersion + "\"");
        try {
            Files.writeString(applicationDestination.toPath().resolve("build-meta.json"), metaData);
        }
        catch (IOException e) {
            throw new MojoExecutionException("Failed writing compile version and build time.", e);
        }
    }

    static boolean isVespaParent(String groupId) {
        return groupId.matches("(com\\.yahoo\\.vespa|ai\\.vespa)(\\..+)?");
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
                FileUtils.copyDirectory(applicationPackage, applicationDestination, ignoredFilesFilter());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed copying applicationPackage", e);
            }
        }
    }

    static FileFilter ignoredFilesFilter() {
        var ioFileFilters = IGNORED_FILES.stream()
                .map(NameFileFilter::new)
                .map(IOFileFilter.class::cast)
                .toList();
        return new NotFileFilter(new OrFileFilter(ioFileFilters));
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
