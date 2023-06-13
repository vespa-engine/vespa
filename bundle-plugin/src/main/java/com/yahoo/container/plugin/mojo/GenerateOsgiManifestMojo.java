// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.google.common.collect.Sets;
import com.yahoo.container.plugin.classanalysis.Analyze;
import com.yahoo.container.plugin.classanalysis.ClassFileMetaData;
import com.yahoo.container.plugin.classanalysis.PackageTally;
import com.yahoo.container.plugin.osgi.ExportPackages;
import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.osgi.ImportPackages.Import;
import com.yahoo.container.plugin.util.Artifacts;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.container.plugin.bundle.AnalyzeBundle.exportedPackagesAggregated;
import static com.yahoo.container.plugin.bundle.AnalyzeBundle.nonPublicApiPackagesAggregated;
import static com.yahoo.container.plugin.classanalysis.Packages.disallowedImports;
import static com.yahoo.container.plugin.osgi.ExportPackages.exportsByPackageName;
import static com.yahoo.container.plugin.osgi.ImportPackages.calculateImports;
import static com.yahoo.container.plugin.util.Files.allDescendantFiles;


/**
 * @author Tony Vaagenes
 * @author ollivir
 */
@Mojo(name = "generate-osgi-manifest", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class GenerateOsgiManifestMojo extends AbstractGenerateOsgiManifestMojo {

    private enum BundleType {
        CORE,      // up to container-dev
        INTERNAL,  // other vespa bundles (need not be set for groupId 'com.yahoo.vespa')
        USER
    }

    private static final String VESPA_GROUP_ID = "com.yahoo.vespa";

    @Parameter
    private String discApplicationClass = null;

    @Parameter
    private String discPreInstallBundle = null;

    @Parameter(alias = "Bundle-Activator")
    private String bundleActivator = null;

    @Parameter(alias = "X-JDisc-Privileged-Activator")
    private String jdiscPrivilegedActivator = null;

    @Parameter(alias = "WebInfUrl")
    private String webInfUrl = null;

    @Parameter(alias = "Main-Class")
    private String mainClass = null;

    @Parameter(alias = "Bundle-Type")
    private BundleType bundleType = BundleType.USER;

    @Parameter(defaultValue = "false")
    private boolean suppressWarningMissingImportPackages;
    @Parameter(defaultValue = "false")
    private boolean suppressWarningPublicApi;
    @Parameter(defaultValue = "false")
    private boolean suppressWarningOverlappingPackages;

    @Parameter(defaultValue = "false")
    private boolean failOnWarnings;

    @Parameter(defaultValue = "false")
    private boolean buildLegacyVespaPlatformBundle;

    public void execute() throws MojoExecutionException {
        try {
            if (discPreInstallBundle != null && ! buildLegacyVespaPlatformBundle)
                throw new MojoExecutionException("The 'discPreInstallBundle' parameter can only be used by legacy Vespa platform bundles.");

            Artifacts.ArtifactSet artifactSet = Artifacts.getArtifacts(project);
            warnOnUnsupportedArtifacts(artifactSet.getNonJarArtifacts());
            if (! isContainerDiscArtifact(project.getArtifact()))
                throwIfInternalContainerArtifactsAreIncluded(artifactSet.getJarArtifactsToInclude());

            List<Artifact> providedJarArtifacts = artifactSet.getJarArtifactsProvided();
            List<File> providedJarFiles = providedJarArtifacts.stream().map(Artifact::getFile).toList();
            List<Export> exportedPackagesFromProvidedJars = exportedPackagesAggregated(providedJarFiles);
            List<String> nonPublicApiPackagesFromProvidedJars = nonPublicApiPackagesAggregated(providedJarFiles);

            // Packages from Export-Package/PublicApi headers in provided scoped jars
            Set<String> exportedPackagesFromProvidedDeps = ExportPackages.packageNames(exportedPackagesFromProvidedJars);

            // Packaged defined in this project's code
            PackageTally projectPackages = getProjectClassesTally();

            // Packages defined in compile scoped jars
            PackageTally compileJarsPackages = definedPackages(artifactSet.getJarArtifactsToInclude());

            // The union of packages in the project and compile scoped jars
            PackageTally includedPackages = projectPackages.combine(compileJarsPackages);

            logDebugPackageSets(exportedPackagesFromProvidedJars, includedPackages);

            Optional<Artifact> jdiscCore = providedJarArtifacts.stream()
                    .filter(artifact -> artifact.getArtifactId().equals("jdisc_core"))
                    .findAny();

            if (jdiscCore.isPresent()) {
                // jdisc_core being provided guarantees that log output does not contain its exported packages
                logMissingPackages(exportedPackagesFromProvidedDeps, projectPackages, compileJarsPackages, includedPackages);
            } else if (! suppressWarningMissingImportPackages) {
                warnOrThrow(("This project does not have '%s' as provided dependency, so the generated 'Import-Package' " +
                        "OSGi header may be missing important packages.").formatted(wantedProvidedDependency()));
            }
            logOverlappingPackages(projectPackages, exportedPackagesFromProvidedDeps);
            logUnnecessaryPackages(compileJarsPackages, exportedPackagesFromProvidedDeps);

            Map<String, Import> calculatedImports = calculateImports(includedPackages.referencedPackages(),
                                                                     includedPackages.definedPackages(),
                                                                     exportsByPackageName(exportedPackagesFromProvidedJars));

            List<String> nonPublicApiUsed = disallowedImports(calculatedImports, nonPublicApiPackagesFromProvidedJars);
            logNonPublicApiUsage(nonPublicApiUsed);

            Map<String, String> manifestContent = generateManifestContent(artifactSet.getJarArtifactsToInclude(), calculatedImports, includedPackages);
            addAdditionalManifestProperties(manifestContent);
            addManifestPropertiesForInternalBundles(manifestContent, includedPackages);
            addManifestPropertiesForUserBundles(manifestContent, jdiscCore, nonPublicApiUsed);

            createManifestFile(Paths.get(project.getBuild().getOutputDirectory()), manifestContent);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed generating osgi manifest", e);
        }
    }

    private String wantedProvidedDependency() {
        return switch (effectiveBundleType()) {
            case CORE -> "jdisc_core";
            case INTERNAL -> "container-dev";
            case USER -> "container";
        };
    }

    private BundleType effectiveBundleType() {
        if (bundleType != BundleType.USER) return bundleType;
        return isVespaInternalGroupId(project.getGroupId()) ? BundleType.INTERNAL : BundleType.USER;
    }

    private void addAdditionalManifestProperties(Map<String, String> manifestContent) {
        addIfNotEmpty(manifestContent, "Bundle-Activator", bundleActivator);
        addIfNotEmpty(manifestContent, "X-JDisc-Privileged-Activator", jdiscPrivilegedActivator);
        addIfNotEmpty(manifestContent, "Main-Class", mainClass);
        addIfNotEmpty(manifestContent, "X-JDisc-Application", discApplicationClass);
        addIfNotEmpty(manifestContent, "X-JDisc-Preinstall-Bundle", trimWhitespace(Optional.ofNullable(discPreInstallBundle)));
        addIfNotEmpty(manifestContent, "WebInfUrl", webInfUrl);
    }

    private void addManifestPropertiesForInternalBundles(Map<String, String> manifestContent, PackageTally includedPackages) {
        if (effectiveBundleType() == BundleType.USER) return;

        // TODO: this attribute is not necessary, remove?
        addIfNotEmpty(manifestContent, "X-JDisc-PublicApi-Package", publicApi(includedPackages));

        addIfNotEmpty(manifestContent, "X-JDisc-Non-PublicApi-Export-Package", nonPublicApi(includedPackages));
    }

    private void addManifestPropertiesForUserBundles(Map<String, String> manifestContent,
                                                     Optional<Artifact> jdiscCore,
                                                     List<String> nonPublicApiUsed) {
        if (effectiveBundleType() != BundleType.USER) return;

        jdiscCore.ifPresent(
                artifact -> addIfNotEmpty(manifestContent, "X-JDisc-Vespa-Build-Version", artifact.getVersion()));
        addIfNotEmpty(manifestContent, "X-JDisc-Non-PublicApi-Import-Package", String.join(",", nonPublicApiUsed));
    }

    private void logNonPublicApiUsage(List<String> nonPublicApiUsed) {
        if (suppressWarningPublicApi || effectiveBundleType() != BundleType.USER || nonPublicApiUsed.isEmpty()) return;
        warnOrThrow("This project uses packages that are not part of Vespa's public api: %s".formatted(nonPublicApiUsed));
    }

    private static String publicApi(PackageTally tally) {
        return tally.publicApiPackages().stream().sorted().collect(Collectors.joining(","));
    }

    private static String nonPublicApi(PackageTally tally) {
        return tally.nonPublicApiExportedPackages().stream().sorted().collect(Collectors.joining(","));
    }

    private void logDebugPackageSets(List<Export> exportedPackagesFromProvidedJars, PackageTally includedPackages) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Referenced packages = " + includedPackages.referencedPackages());
            getLog().debug("Defined packages = " + includedPackages.definedPackages());
            getLog().debug("Exported packages of dependencies = " + exportedPackagesFromProvidedJars.stream()
                    .map(e -> "(" + e.getPackageNames().toString() + ", " + e.version().orElse("")).collect(Collectors.joining(", ")));
        }
    }

    private void logMissingPackages(Set<String> exportedPackagesFromProvidedJars,
                                    PackageTally projectPackages,
                                    PackageTally compileJarPackages,
                                    PackageTally includedPackages) {

        Set<String> definedAndExportedPackages = Sets.union(includedPackages.definedPackages(), exportedPackagesFromProvidedJars);

        Set<String> missingProjectPackages = projectPackages.referencedPackagesMissingFrom(definedAndExportedPackages);
        if (! missingProjectPackages.isEmpty()) {
            getLog().warn("Packages unavailable runtime are referenced from project classes " +
                                  "(annotations can usually be ignored): " + missingProjectPackages);
        }

        Set<String> missingCompilePackages = compileJarPackages.referencedPackagesMissingFrom(definedAndExportedPackages);
        if (! missingCompilePackages.isEmpty()) {
            getLog().info("Packages unavailable runtime are referenced from compile scoped jars " +
                                  "(annotations can usually be ignored): " + missingCompilePackages);
        }
    }

    private void logOverlappingPackages(PackageTally projectPackages,
                                        Set<String> exportedPackagesFromProvidedDeps) {
        if (suppressWarningOverlappingPackages) return;

        Set<String> overlappingProjectPackages = Sets.intersection(projectPackages.definedPackages(), exportedPackagesFromProvidedDeps);
        if (! overlappingProjectPackages.isEmpty()) {
            warnOrThrow("This project defines packages that are also defined in provided scoped dependencies " +
                         "(overlapping packages are strongly discouraged): " + overlappingProjectPackages);
        }
    }

    /*
     * This mostly detects packages re-exported via composite bundles like jdisc_core and container-disc.
     * An artifact can only be represented once, either in compile or provided scope. So if the project
     * adds an artifact in compile scope that we deploy as a pre-installed bundle, we won't see the same
     * artifact as provided via container-dev and hence can't detect the duplicate packages.
     */
    private void logUnnecessaryPackages(PackageTally compileJarsPackages,
                                        Set<String> exportedPackagesFromProvidedDeps) {
        Set<String> unnecessaryPackages = Sets.intersection(compileJarsPackages.definedPackages(), exportedPackagesFromProvidedDeps);
        if (! unnecessaryPackages.isEmpty()) {
            getLog().info("Compile scoped jars contain the following packages that are most likely " +
                                  "available from jdisc runtime: " + unnecessaryPackages);
        }
    }

    private static String trimWhitespace(Optional<String> lines) {
        return Stream.of(lines.orElse("").split(",")).map(String::trim).collect(Collectors.joining(","));
    }

    private void warnOnUnsupportedArtifacts(Collection<Artifact> nonJarArtifacts) {
        List<Artifact> unsupportedArtifacts = nonJarArtifacts.stream().filter(a -> ! a.getType().equals("pom"))
                .toList();

        unsupportedArtifacts.forEach(artifact -> warnOrThrow(String.format("Unsupported artifact '%s': Type '%s' is not supported. Please file a feature request.",
                                                                           artifact.getId(), artifact.getType())));
    }

    private void throwIfInternalContainerArtifactsAreIncluded(Collection<Artifact> includedArtifacts) throws MojoExecutionException {
        /* In most cases it's sufficient to test for 'component', as it's the lowest level container artifact,
         * Embedding container artifacts will cause class loading issues at runtime, because the classes will
         * not be equal to those seen by the framework (e.g. AbstractComponent). */
        if (includedArtifacts.stream().anyMatch(this::isJdiscComponentArtifact)) {
            throw new MojoExecutionException(
                    "This project includes the 'com.yahoo.vespa:component' artifact in compile scope." +
                            " It must have scope 'provided' to avoid resource leaks in your application at runtime." +
                            " Please use 'mvn dependency:tree' to find the root cause.");
        }
    }

    private boolean isVespaInternalGroupId(String groupId) {
        return groupId.equals(VESPA_GROUP_ID)
                || groupId.equals(VESPA_GROUP_ID + ".hosted")
                || groupId.equals(VESPA_GROUP_ID + ".hosted.controller");
    }

    private boolean isJdiscComponentArtifact(Artifact a) {
        return a.getArtifactId().equals("component") && a.getGroupId().equals(VESPA_GROUP_ID);
    }

    private boolean isContainerDiscArtifact(Artifact a) {
        return a.getArtifactId().equals("container-disc") && a.getGroupId().equals(VESPA_GROUP_ID);
    }

    private PackageTally getProjectClassesTally() {
        File outputDirectory = new File(project.getBuild().getOutputDirectory());

        List<ClassFileMetaData> analyzedClasses = allDescendantFiles(outputDirectory)
                .filter(file -> file.getName().endsWith(".class"))
                .map(classFile -> Analyze.analyzeClass(classFile, artifactVersionOrNull(bundleVersion)))
                .toList();

        return PackageTally.fromAnalyzedClassFiles(analyzedClasses);
    }

    private void warnOrThrow(String... messages){
        String message = String.join("\n", messages);
        if (failOnWarnings) {
            throw new RuntimeException(message);
        }
        getLog().warn(message);
    }

}
