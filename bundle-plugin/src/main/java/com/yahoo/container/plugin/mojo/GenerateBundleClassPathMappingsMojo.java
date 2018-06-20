// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.google.common.base.Preconditions;
import com.yahoo.container.plugin.bundle.AnalyzeBundle;
import com.yahoo.container.plugin.osgi.ProjectBundleClassPaths;
import com.yahoo.container.plugin.osgi.ProjectBundleClassPaths.BundleClasspathMapping;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates mapping from Bundle-SymbolicName to classpath elements, e.g myBundle -&gt; [.m2/repository/com/mylib/Mylib.jar,
 * myBundleProject/target/classes] The mapping in stored in a json file.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
@Mojo(name = "generate-bundle-classpath-mappings", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateBundleClassPathMappingsMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}")
    private MavenProject project = null;

    //TODO: Combine with com.yahoo.container.plugin.mojo.GenerateOsgiManifestMojo.bundleSymbolicName
    @Parameter(alias = "Bundle-SymbolicName", defaultValue = "${project.artifactId}")
    private String bundleSymbolicName = null;

    /* Sample output -- target/test-classes/bundle-plugin.bundle-classpath-mappings.json
    {
      "mainBundle": {
          "bundleSymbolicName": "bundle-plugin-test",
          "classPathElements": [
              "/Users/tonyv/Repos/vespa/bundle-plugin-test/target/classes",
              "/Users/tonyv/.m2/repository/com/yahoo/vespa/jrt/6-SNAPSHOT/jrt-6-SNAPSHOT.jar",
              "/Users/tonyv/.m2/repository/com/yahoo/vespa/annotations/6-SNAPSHOT/annotations-6-SNAPSHOT.jar"
          ]
      },
      "providedDependencies": [
          {
              "bundleSymbolicName": "jrt",
              "classPathElements": [
                  "/Users/tonyv/.m2/repository/com/yahoo/vespa/jrt/6-SNAPSHOT/jrt-6-SNAPSHOT.jar"
              ]
          }
      ]
     }
    */
    @Override
    public void execute() throws MojoExecutionException {
        Preconditions.checkNotNull(bundleSymbolicName);

        Artifacts.ArtifactSet artifacts = Artifacts.getArtifacts(project);
        List<Artifact> embeddedArtifacts = artifacts.getJarArtifactsToInclude();
        List<Artifact> providedJarArtifacts = artifacts.getJarArtifactsProvided();

        List<File> embeddedArtifactsFiles = embeddedArtifacts.stream().map(Artifact::getFile).collect(Collectors.toList());

        List<String> classPathElements = Stream.concat(Stream.of(outputDirectory()), embeddedArtifactsFiles.stream())
                .map(File::getAbsolutePath).collect(Collectors.toList());

        ProjectBundleClassPaths classPathMappings = new ProjectBundleClassPaths(
                new BundleClasspathMapping(bundleSymbolicName, classPathElements),
                providedJarArtifacts.stream().map(f -> createDependencyClasspathMapping(f)).filter(Optional::isPresent).map(Optional::get)
                        .collect(Collectors.toList()));

        try {
            ProjectBundleClassPaths.save(testOutputPath().resolve(ProjectBundleClassPaths.CLASSPATH_MAPPINGS_FILENAME), classPathMappings);
        } catch (IOException e) {
            throw new MojoExecutionException("Error saving to file " + testOutputPath(), e);
        }
    }

    private File outputDirectory() {
        return new File(project.getBuild().getOutputDirectory());
    }

    private Path testOutputPath() {
        return Paths.get(project.getBuild().getTestOutputDirectory());
    }

    /* TODO:
     * 1) add the dependencies of the artifact in the future(i.e. dependencies of dependencies)
     * or
     * 2) obtain bundles with embedded dependencies from the maven repository,
     *     and support loading classes from the nested jar files in those bundles.
     */
    Optional<BundleClasspathMapping> createDependencyClasspathMapping(Artifact artifact) {
        return bundleSymbolicNameForArtifact(artifact)
                .map(name -> new BundleClasspathMapping(name, Arrays.asList(artifact.getFile().getAbsolutePath())));
    }

    private static Optional<String> bundleSymbolicNameForArtifact(Artifact artifact) {
        if (artifact.getFile().getName().endsWith(".jar")) {
            return AnalyzeBundle.bundleSymbolicName(artifact.getFile());
        } else {
            // Not the best heuristic. The other alternatives are parsing the pom file or
            // storing information in target/classes when building the provided bundles.
            return Optional.of(artifact.getArtifactId());
        }
    }
}
