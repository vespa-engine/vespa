// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.yahoo.container.plugin.util.Files;
import com.yahoo.container.plugin.util.JarFiles;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResourceCollection;

import java.io.File;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * @author bjorncs
 */
abstract class AbstractAssembleBundleMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    @Parameter
    MavenArchiveConfiguration archiveConfiguration = new MavenArchiveConfiguration();

    void addDirectory(JarArchiver jarArchiver, Path directory) {
        if (java.nio.file.Files.isDirectory(directory)) {
            PlexusIoFileResourceCollection resources = new PlexusIoFileResourceCollection();
            resources.setBaseDir(directory.toFile());
            jarArchiver.addResources(resources);
        }
    }

    void createArchive(JarArchiver jarArchiver, Path jarFile, Path manifestFile) throws MojoExecutionException {
        archiveConfiguration.setForced(true); // force recreating the archive
        archiveConfiguration.setManifestFile(manifestFile.toFile());
        MavenArchiver mavenArchiver = new MavenArchiver();
        mavenArchiver.setArchiver(jarArchiver);
        mavenArchiver.setOutputFile(jarFile.toFile());
        mavenArchiver.configureReproducibleBuild("1");
        try {
            mavenArchiver.createArchive(session, project, archiveConfiguration);
        } catch (Exception e) {
            throw new MojoExecutionException("Error creating archive " + jarFile.toFile().getName(), e);
        }
    }

    void addArtifacts(JarArchiver jarArchiver, Collection<Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            if ("jar".equals(artifact.getType())) {
                jarArchiver.addFile(artifact.getFile(), "dependencies/" + artifact.getFile().getName());
                copyConfigDefinitions(artifact.getFile(), jarArchiver);
            } else {
                getLog().warn("Unknown artifact type " + artifact.getType());
            }
        }
    }

    private void copyConfigDefinitions(File file, JarArchiver jarArchiver) {
        JarFiles.withJarFile(file, jarFile -> {
            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("configdefinitions/") && name.endsWith(".def")) {
                    copyConfigDefinition(jarFile, entry, jarArchiver);
                }
            }
            return null;
        });
    }

    private void copyConfigDefinition(JarFile jarFile, ZipEntry entry, JarArchiver jarArchiver) {
        JarFiles.withInputStream(jarFile, entry, input -> {
            String defPath = entry.getName().replace("/", File.separator);
            File destinationFile = new File(project.getBuild().getOutputDirectory(), defPath);
            destinationFile.getParentFile().mkdirs();

            Files.withFileOutputStream(destinationFile, output -> {
                output.getChannel().transferFrom(Channels.newChannel(input), 0, Long.MAX_VALUE);
                return null;
            });
            jarArchiver.addFile(destinationFile, entry.getName());
            return null;
        });
    }
}
