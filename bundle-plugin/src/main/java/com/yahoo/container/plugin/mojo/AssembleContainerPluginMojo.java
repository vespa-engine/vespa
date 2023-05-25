// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.yahoo.container.plugin.util.Artifacts;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.jar.JarArchiver;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * @author Tony Vaagenes
 * @author Olli Virtanen
 * @author bjorncs
 */
@Mojo(name = "assemble-container-plugin", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class AssembleContainerPluginMojo extends AbstractAssembleBundleMojo {
    private enum Dependencies {
        WITH, WITHOUT
    }

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(alias = "UseCommonAssemblyIds", defaultValue = "false")
    private boolean useCommonAssemblyIds = false;

    @Parameter(alias = "AttachBundle", defaultValue = "false")
    private boolean attachBundleArtifact;

    @Parameter(alias = "BundleClassifier", defaultValue = "bundle")
    private String bundleClassifierName;

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

        Path manifestFile = Paths.get(build().getOutputDirectory(), JarFile.MANIFEST_NAME);

        JarArchiver jarWithoutDependencies = new JarArchiver();
        addDirectory(jarWithoutDependencies, Paths.get(build().getOutputDirectory()));
        createArchive(jarWithoutDependencies, jarFiles.get(Dependencies.WITHOUT).toPath(), manifestFile);
        project.getArtifact().setFile(jarFiles.get(Dependencies.WITHOUT));

        JarArchiver jarWithDependencies = new JarArchiver();
        addDirectory(jarWithDependencies, Paths.get(build().getOutputDirectory()));
        addArtifacts(jarWithDependencies, Artifacts.getArtifactsToInclude(project));
        createArchive(jarWithDependencies, jarFiles.get(Dependencies.WITH).toPath(), manifestFile);

        if (attachBundleArtifact) {
            projectHelper.attachArtifact(project,
                                         project.getArtifact().getType(),
                                         bundleClassifierName,
                                         jarFiles.get(Dependencies.WITH));
        }
    }

    private File jarFileInBuildDirectory(String name, String suffix) { return new File(build().getDirectory(), name + suffix); }
    private Build build() { return project.getBuild(); }
}
