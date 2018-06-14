// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.yahoo.container.plugin.util.Files;
import com.yahoo.container.plugin.util.JarFiles;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.JarArchiver;

import java.io.File;
import java.nio.channels.Channels;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
@Mojo(name = "assemble-container-plugin", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class AssembleContainerPluginMojo extends AbstractMojo {
    private static enum Dependencies {
        WITH, WITHOUT
    }

    @Parameter(defaultValue = "${project}")
    private MavenProject project = null;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session = null;

    @Parameter
    private MavenArchiveConfiguration archiveConfiguration = new MavenArchiveConfiguration();

    @Parameter(alias = "UseCommonAssemblyIds", defaultValue = "false")
    private boolean useCommonAssemblyIds = false;

    @Override
    public void execute() throws MojoExecutionException {
        Map<Dependencies, String> jarSuffixes = new EnumMap<Dependencies, String>(Dependencies.class);

        if (useCommonAssemblyIds) {
            jarSuffixes.put(Dependencies.WITHOUT, ".jar");
            jarSuffixes.put(Dependencies.WITH, "-jar-with-dependencies.jar");
        } else {
            jarSuffixes.put(Dependencies.WITHOUT, "-without-dependencies.jar");
            jarSuffixes.put(Dependencies.WITH, "-deploy.jar");
        }

        Map<Dependencies, File> jarFiles = new EnumMap<Dependencies, File>(Dependencies.class);
        jarSuffixes.forEach((dep, suffix) -> {
            jarFiles.put(dep, jarFileInBuildDirectory(build().getFinalName(), suffix));
        });

        // force recreating the archive
        archiveConfiguration.setForced(true);
        archiveConfiguration.setManifestFile(new File(new File(build().getOutputDirectory()), JarFile.MANIFEST_NAME));

        JarArchiver jarWithoutDependencies = new JarArchiver();
        addClassesDirectory(jarWithoutDependencies);
        createArchive(jarFiles.get(Dependencies.WITHOUT), jarWithoutDependencies);
        project.getArtifact().setFile(jarFiles.get(Dependencies.WITHOUT));

        JarArchiver jarWithDependencies = new JarArchiver();
        addClassesDirectory(jarWithDependencies);
        addDependencies(jarWithDependencies);
        createArchive(jarFiles.get(Dependencies.WITH), jarWithDependencies);
    }

    private File jarFileInBuildDirectory(String name, String suffix) {
        return new File(build().getDirectory(), name + suffix);
    }

    private void addClassesDirectory(JarArchiver jarArchiver) {
        File classesDirectory = new File(build().getOutputDirectory());
        if (classesDirectory.isDirectory()) {
            jarArchiver.addDirectory(classesDirectory);
        }
    }

    private void createArchive(File jarFile, JarArchiver jarArchiver) throws MojoExecutionException {
        MavenArchiver mavenArchiver = new MavenArchiver();
        mavenArchiver.setArchiver(jarArchiver);
        mavenArchiver.setOutputFile(jarFile);
        try {
            mavenArchiver.createArchive(session, project, archiveConfiguration);
        } catch (Exception e) {
            throw new MojoExecutionException("Error creating archive " + jarFile.getName(), e);
        }
    }

    private void addDependencies(JarArchiver jarArchiver) {
        Artifacts.getArtifactsToInclude(project).forEach(artifact -> {
            if ("jar".equals(artifact.getType())) {
                jarArchiver.addFile(artifact.getFile(), "dependencies/" + artifact.getFile().getName());
                copyConfigDefinitions(artifact.getFile(), jarArchiver);
            } else {
                getLog().warn("Unkown artifact type " + artifact.getType());
            }
        });
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
            File destinationFile = new File(build().getOutputDirectory(), defPath);
            destinationFile.getParentFile().mkdirs();

            Files.withFileOutputStream(destinationFile, output -> {
                output.getChannel().transferFrom(Channels.newChannel(input), 0, Long.MAX_VALUE);
                return null;
            });
            jarArchiver.addFile(destinationFile, entry.getName());
            return null;
        });
    }

    private Build build() {
        return project.getBuild();
    }
}
