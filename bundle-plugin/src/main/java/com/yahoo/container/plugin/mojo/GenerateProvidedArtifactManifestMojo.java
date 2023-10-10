// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.shade.DefaultShader;
import org.apache.maven.plugins.shade.ShadeRequest;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Replaces the Class-Path of a jar file manifest with a list of provided artifacts in a new manifest entry.
 * The Class-Path is used as input because it is trivial to generate with the maven-jar-plugin.
 *
 * @author gjoranv
 */
@Mojo(name = "generate-provided-artifact-manifest", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class GenerateProvidedArtifactManifestMojo extends AbstractMojo {

    public static final String PROVIDED_ARTIFACTS_MANIFEST_ENTRY = "X-JDisc-Provided-Artifact";

    @Parameter(defaultValue = "${project}")
    public MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    public File outputDirectory;


    @Override
    public void execute() throws MojoExecutionException {
        var originalJar = project.getArtifact().getFile();
        var shadedJar = shadedJarFile();

        var req = new ShadeRequest();
        req.setJars(Set.of(originalJar));
        req.setUberJar(shadedJar);
        req.setResourceTransformers(List.of(new ProvidedArtifactsManifestUpdater()));
        req.setRelocators(List.of());
        req.setFilters(List.of());

        try {
            new DefaultShader().shade(req);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
        try {
            getLog().info("Replacing original jar with transformed jar");
            FileUtils.copyFile(shadedJar, originalJar);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private File shadedJarFile() {
        var a = project.getArtifact();
        var name = project.getArtifactId() + "-shaded." + a.getArtifactHandler().getExtension();
        return new File(outputDirectory, name);
    }

    private static class ProvidedArtifactsManifestUpdater implements ResourceTransformer {

        private Manifest manifest;

        @Override
        public boolean canTransformResource(String resource) {
            return JarFile.MANIFEST_NAME.equalsIgnoreCase(resource);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void processResource(String resource, InputStream is, List<Relocator> relocators) throws IOException {
            manifest = new Manifest(is);
            Attributes attributes = manifest.getMainAttributes();
            var providedArtifacts = attributes.getValue("Class-Path");
            if (providedArtifacts == null) return;

            attributes.remove(new Attributes.Name("Class-Path"));
            attributes.putValue(PROVIDED_ARTIFACTS_MANIFEST_ENTRY, providedArtifacts.replace(" ", ","));
            attributes.putValue("Created-By", "vespa container maven plugin");
        }

        @Override
        public boolean hasTransformedResource() {
            return true;
        }

        @Override
        public void modifyOutputStream(JarOutputStream os) throws IOException {
            if (manifest == null) return;

            JarEntry jarEntry = new JarEntry(JarFile.MANIFEST_NAME);
            os.putNextEntry(jarEntry);
            manifest.write(os);
        }
    }

}
