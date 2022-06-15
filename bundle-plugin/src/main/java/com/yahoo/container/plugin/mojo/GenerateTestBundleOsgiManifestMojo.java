// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.yahoo.container.plugin.classanalysis.Analyze;
import com.yahoo.container.plugin.classanalysis.ClassFileMetaData;
import com.yahoo.container.plugin.classanalysis.PackageTally;
import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.osgi.ImportPackages;
import com.yahoo.container.plugin.util.Artifacts;
import com.yahoo.container.plugin.util.TestBundleDependencyScopeTranslator;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.yahoo.container.plugin.bundle.AnalyzeBundle.exportedPackagesAggregated;
import static com.yahoo.container.plugin.util.TestBundleUtils.outputDirectory;
import static com.yahoo.container.plugin.osgi.ExportPackages.exportsByPackageName;
import static com.yahoo.container.plugin.osgi.ImportPackages.calculateImports;
import static com.yahoo.container.plugin.util.Files.allDescendantFiles;
import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
@Mojo(name = "generate-test-bundle-osgi-manifest", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class GenerateTestBundleOsgiManifestMojo extends AbstractGenerateOsgiManifestMojo {

    @Parameter
    private String testBundleScopeOverrides;

    public void execute() throws MojoExecutionException {
        try {
            Artifacts.ArtifactSet artifactSet = Artifacts.getArtifacts(
                    project, TestBundleDependencyScopeTranslator.from(project.getArtifacts(), testBundleScopeOverrides));

            List<File> providedJars = artifactSet.getJarArtifactsProvided().stream()
                    .map(Artifact::getFile)
                    .collect(toList());

            List<Export> exportedPackagesFromProvidedJars = exportedPackagesAggregated(providedJars);

            List<ClassFileMetaData> analyzedClasses = getProjectMainAndTestClasses();

            PackageTally projectPackages = PackageTally.fromAnalyzedClassFiles(analyzedClasses);

            PackageTally jarArtifactsToInclude = definedPackages(artifactSet.getJarArtifactsToInclude());

            PackageTally includedPackages = projectPackages.combine(jarArtifactsToInclude);

            Map<String, ImportPackages.Import> calculatedImports = calculateImports(includedPackages.referencedPackages(),
                    includedPackages.definedPackages(),
                    exportsByPackageName(exportedPackagesFromProvidedJars));

            Map<String, String> manifestContent = generateManifestContent(artifactSet.getJarArtifactsToInclude(), calculatedImports, includedPackages);
            addAdditionalManifestProperties(manifestContent, referencedTestCategories(analyzedClasses));
            createManifestFile(outputDirectory(project), manifestContent);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed generating osgi manifest", e);
        }
    }

    private void addAdditionalManifestProperties(Map<String, String> manifestContent, List<String> referencedTestCategories) {
        manifestContent.put("X-JDisc-Test-Bundle-Version", "1.0");
        manifestContent.put("X-JDisc-Test-Bundle-Categories", String.join(",", referencedTestCategories));
    }

    private List<ClassFileMetaData> getProjectMainAndTestClasses() {
        return Stream.concat(allDescendantFiles(new File(project.getBuild().getOutputDirectory())),
                             allDescendantFiles(new File(project.getBuild().getTestOutputDirectory())))
                     .filter(file -> file.getName().endsWith(".class"))
                     .map(classFile -> Analyze.analyzeClass(classFile, null))
                     .collect(toList());
    }

    private static List<String> referencedTestCategories(List<ClassFileMetaData> analyzedClasses) {
        Set<String> referencedClasses = new HashSet<>();
        for (ClassFileMetaData data : analyzedClasses)
            referencedClasses.addAll(data.getReferencedClasses());

        return Stream.of("ai.vespa.hosted.cd.SystemTest",
                         "ai.vespa.hosted.cd.StagingSetup",
                         "ai.vespa.hosted.cd.StagingTest",
                         "ai.vespa.hosted.cd.ProductionTest")
                     .filter(referencedClasses::contains)
                     .map(name -> name.substring(19))
                     .collect(toList());
    }

}
