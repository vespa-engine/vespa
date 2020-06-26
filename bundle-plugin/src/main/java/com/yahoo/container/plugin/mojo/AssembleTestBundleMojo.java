// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.jar.JarArchiver;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.yahoo.container.plugin.mojo.TestBundleUtils.archiveFile;
import static com.yahoo.container.plugin.mojo.TestBundleUtils.manifestFile;

/**
 * @author bjorncs
 */
@Mojo(name = "assemble-test-bundle", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class AssembleTestBundleMojo extends AbstractAssembleBundleMojo {

    @Parameter
    private String testProvidedArtifacts;

    @Override
    public void execute() throws MojoExecutionException {
        Artifacts.ArtifactSet artifacts = Artifacts.getArtifacts(project, true, testProvidedArtifacts);
        JarArchiver archiver = new JarArchiver();
        addDirectory(archiver, Paths.get(project.getBuild().getOutputDirectory()));
        addDirectory(archiver, Paths.get(project.getBuild().getTestOutputDirectory()));
        addArtifacts(archiver, artifacts.getJarArtifactsToInclude());
        Path archiveFile = archiveFile(project);
        createArchive(archiver, archiveFile, manifestFile(project));
        project.getArtifact().setFile(archiveFile.toFile());
    }


}
