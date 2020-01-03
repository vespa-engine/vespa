// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.google.common.collect.Sets;
import com.yahoo.container.plugin.bundle.AnalyzeBundle;
import com.yahoo.container.plugin.classanalysis.Analyze;
import com.yahoo.container.plugin.classanalysis.ClassFileMetaData;
import com.yahoo.container.plugin.classanalysis.ExportPackageAnnotation;
import com.yahoo.container.plugin.classanalysis.PackageTally;
import com.yahoo.container.plugin.osgi.ExportPackageParser;
import com.yahoo.container.plugin.osgi.ExportPackages;
import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.osgi.ImportPackages.Import;
import com.yahoo.container.plugin.util.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.container.plugin.bundle.AnalyzeBundle.publicPackagesAggregated;
import static com.yahoo.container.plugin.osgi.ExportPackages.exportsByPackageName;
import static com.yahoo.container.plugin.osgi.ImportPackages.calculateImports;
import static com.yahoo.container.plugin.util.Files.allDescendantFiles;
import static com.yahoo.container.plugin.util.IO.withFileOutputStream;


/**
 * @author Tony Vaagenes
 * @author ollivir
 */
@Mojo(name = "generate-osgi-manifest", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class GenerateOsgiManifestMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    private MavenProject project = null;

    /**
     * If set to true, the artifact's version is used as default package version for ExportPackages.
     * Packages from included (compile scoped) artifacts will use the version for their own artifact.
     * If the package is exported with an explicit version in package-info.java, that version will be
     * used regardless of this parameter.
     */
    @Parameter(alias = "UseArtifactVersionForExportPackages", defaultValue = "false")
    private boolean useArtifactVersionForExportPackages;

    @Parameter
    private String discApplicationClass = null;

    @Parameter
    private String discPreInstallBundle = null;

    @Parameter(alias = "Bundle-Version", defaultValue = "${project.version}")
    private String bundleVersion = null;

    // TODO: default should include groupId, but that will require a lot of changes both by us and users.
    @Parameter(alias = "Bundle-SymbolicName", defaultValue = "${project.artifactId}")
    private String bundleSymbolicName = null;

    @Parameter(alias = "Bundle-Activator")
    private String bundleActivator = null;

    @Parameter(alias = "X-JDisc-Privileged-Activator")
    private String jdiscPrivilegedActivator = null;

    @Parameter(alias = "Import-Package")
    private String importPackage = null;

    @Parameter(alias = "WebInfUrl")
    private String webInfUrl = null;

    @Parameter(alias = "Main-Class")
    private String mainClass = null;

    public void execute() throws MojoExecutionException {
        try {
            Artifacts.ArtifactSet artifactSet = Artifacts.getArtifacts(project);
            warnOnUnsupportedArtifacts(artifactSet.getNonJarArtifacts());

            // Packages from Export-Package and Global-Package headers in provided scoped jars
            AnalyzeBundle.PublicPackages publicPackagesFromProvidedJars = publicPackagesAggregated(
                    artifactSet.getJarArtifactsProvided().stream().map(Artifact::getFile).collect(Collectors.toList()));

            // Packages from Export-Package headers in provided scoped jars
            Set<String> exportedPackagesFromProvidedDeps = publicPackagesFromProvidedJars.exportedPackageNames();

            // Packaged defined in this project's code
            PackageTally projectPackages = getProjectClassesTally();

            // Packages defined in compile scoped jars
            PackageTally compileJarsPackages = definedPackages(artifactSet.getJarArtifactsToInclude());

            // The union of packages in the project and compile scoped jars
            PackageTally includedPackages = projectPackages.combine(compileJarsPackages);

            warnIfPackagesDefinedOverlapsGlobalPackages(includedPackages.definedPackages(), publicPackagesFromProvidedJars.globals);
            logDebugPackageSets(publicPackagesFromProvidedJars, includedPackages);

            if (hasJdiscCoreProvided(artifactSet.getJarArtifactsProvided())) {
                // jdisc_core being provided guarantees that log output does not contain its exported packages
                logMissingPackages(exportedPackagesFromProvidedDeps, projectPackages, compileJarsPackages, includedPackages);
            } else {
                getLog().warn("This project does not have jdisc_core as provided dependency, so the " +
                                      "generated 'Import-Package' OSGi header may be missing important packages.");
            }
            logOverlappingPackages(projectPackages, exportedPackagesFromProvidedDeps);
            logUnnecessaryPackages(compileJarsPackages, exportedPackagesFromProvidedDeps);

            Map<String, Import> calculatedImports = calculateImports(includedPackages.referencedPackages(),
                                                                     includedPackages.definedPackages(),
                                                                     exportsByPackageName(publicPackagesFromProvidedJars.exports));

            Map<String, Optional<String>> manualImports = emptyToNone(importPackage).map(GenerateOsgiManifestMojo::getManualImports)
                    .orElseGet(HashMap::new);
            for (String packageName : manualImports.keySet()) {
                calculatedImports.remove(packageName);
            }
            createManifestFile(new File(project.getBuild().getOutputDirectory()), manifestContent(project,
                    artifactSet.getJarArtifactsToInclude(), manualImports, calculatedImports.values(), includedPackages));

        } catch (Exception e) {
            throw new MojoExecutionException("Failed generating osgi manifest", e);
        }
    }

    private void logDebugPackageSets(AnalyzeBundle.PublicPackages publicPackagesFromProvidedJars, PackageTally includedPackages) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Referenced packages = " + includedPackages.referencedPackages());
            getLog().debug("Defined packages = " + includedPackages.definedPackages());
            getLog().debug("Exported packages of dependencies = " + publicPackagesFromProvidedJars.exports.stream()
                    .map(e -> "(" + e.getPackageNames().toString() + ", " + e.version().orElse("")).collect(Collectors.joining(", ")));
        }
    }

    private boolean hasJdiscCoreProvided(List<Artifact> providedArtifacts) {
        return providedArtifacts.stream().anyMatch(artifact -> artifact.getArtifactId().equals("jdisc_core"));
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
        Set<String> overlappingProjectPackages = Sets.intersection(projectPackages.definedPackages(), exportedPackagesFromProvidedDeps);
        if (! overlappingProjectPackages.isEmpty()) {
            getLog().warn("This project defines packages that are also defined in provided scoped dependencies " +
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

    private static void warnIfPackagesDefinedOverlapsGlobalPackages(Set<String> internalPackages, List<String> globalPackages)
            throws MojoExecutionException {
        Set<String> overlap = Sets.intersection(internalPackages, new HashSet<>(globalPackages));
        if (! overlap.isEmpty()) {
            throw new MojoExecutionException(
                    "The following packages are both global and included in the bundle:\n   " + String.join("\n   ", overlap));
        }
    }

    private Collection<String> osgiExportPackages(Map<String, ExportPackageAnnotation> exportedPackages) {
        return exportedPackages.entrySet().stream().map(entry -> entry.getKey() + ";version=" + entry.getValue().osgiVersion())
                .collect(Collectors.toList());
    }

    private static String trimWhitespace(Optional<String> lines) {
        return Stream.of(lines.orElse("").split(",")).map(String::trim).collect(Collectors.joining(","));
    }

    private Map<String, String> manifestContent(MavenProject project, Collection<Artifact> jarArtifactsToInclude,
            Map<String, Optional<String>> manualImports, Collection<Import> imports, PackageTally pluginPackageTally) {
        Map<String, String> ret = new HashMap<>();
        String importPackage = Stream.concat(manualImports.entrySet().stream().map(e -> asOsgiImport(e.getKey(), e.getValue())),
                                             imports.stream().map(Import::asOsgiImport)).sorted()
                .collect(Collectors.joining(","));

        String exportPackage = osgiExportPackages(pluginPackageTally.exportedPackages()).stream().sorted()
                .collect(Collectors.joining(","));

        ret.put("Created-By", "vespa container maven plugin");
        ret.put("Bundle-ManifestVersion", "2");
        addIfNotEmpty(ret, "Bundle-Name", project.getName());
        addIfNotEmpty(ret, "Bundle-SymbolicName", bundleSymbolicName);
        addIfNotEmpty(ret, "Bundle-Version", asBundleVersion(bundleVersion));
        ret.put("Bundle-Vendor", "Yahoo!");
        addIfNotEmpty(ret, "Bundle-ClassPath", bundleClassPath(jarArtifactsToInclude));
        addIfNotEmpty(ret, "Bundle-Activator", bundleActivator);
        addIfNotEmpty(ret, "X-JDisc-Privileged-Activator", jdiscPrivilegedActivator);
        addIfNotEmpty(ret, "Main-Class", mainClass);
        addIfNotEmpty(ret, "X-JDisc-Application", discApplicationClass);
        addIfNotEmpty(ret, "X-JDisc-Preinstall-Bundle", trimWhitespace(Optional.ofNullable(discPreInstallBundle)));
        addIfNotEmpty(ret, "WebInfUrl", webInfUrl);
        addIfNotEmpty(ret, "Import-Package", importPackage);
        addIfNotEmpty(ret, "Export-Package", exportPackage);

        return ret;
    }

    private static void addIfNotEmpty(Map<String, String> map, String key, String value) {
        if (value != null && ! value.isEmpty()) {
            map.put(key, value);
        }
    }

    private static String asOsgiImport(String packageName, Optional<String> version) {
        return version.map(s -> packageName + ";version=" + quote(s)).orElse(packageName);
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    private static void createManifestFile(File outputDirectory, Map<String, String> manifestContent) {
        Manifest manifest = toManifest(manifestContent);

        withFileOutputStream(new File(outputDirectory, JarFile.MANIFEST_NAME), outputStream -> {
            manifest.write(outputStream);
            return null;
        });
    }

    private static Manifest toManifest(Map<String, String> manifestContent) {
        Manifest manifest = new Manifest();
        Attributes mainAttributes = manifest.getMainAttributes();

        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifestContent.forEach(mainAttributes::putValue);

        return manifest;
    }

    private static String bundleClassPath(Collection<Artifact> artifactsToInclude) {
        return Stream.concat(Stream.of("."), artifactsToInclude.stream().map(GenerateOsgiManifestMojo::dependencyPath))
                .collect(Collectors.joining(","));
    }

    private static String dependencyPath(Artifact artifact) {
        return "dependencies/" + artifact.getFile().getName();
    }

    private static String asBundleVersion(String projectVersion) {
        if (projectVersion == null) {
            throw new IllegalArgumentException("Missing project version.");
        }

        String[] parts = projectVersion.split("-", 2);
        List<String> numericPart = Stream.of(parts[0].split("\\.")).map(s -> Strings.replaceEmptyString(s, "0")).limit(3)
                .collect(Collectors.toList());
        while (numericPart.size() < 3) {
            numericPart.add("0");
        }

        return String.join(".", numericPart);
    }

    private void warnOnUnsupportedArtifacts(Collection<Artifact> nonJarArtifacts) {
        List<Artifact> unsupportedArtifacts = nonJarArtifacts.stream().filter(a -> ! a.getType().equals("pom"))
                .collect(Collectors.toList());

        unsupportedArtifacts.forEach(artifact -> getLog()
                .warn(String.format("Unsupported artifact '%s': Type '%s' is not supported. Please file a feature request.",
                        artifact.getId(), artifact.getType())));
    }

    private ArtifactVersion artifactVersionOrNull(String version) {
        return useArtifactVersionForExportPackages ? new DefaultArtifactVersion(version) : null;
    }

    private PackageTally getProjectClassesTally() {
        File outputDirectory = new File(project.getBuild().getOutputDirectory());

        List<ClassFileMetaData> analyzedClasses = allDescendantFiles(outputDirectory)
                .filter(file -> file.getName().endsWith(".class"))
                .map(classFile -> Analyze.analyzeClass(classFile, artifactVersionOrNull(bundleVersion)))
                .collect(Collectors.toList());

        return PackageTally.fromAnalyzedClassFiles(analyzedClasses);
    }

    private PackageTally definedPackages(Collection<Artifact> jarArtifacts) {
        List<PackageTally> tallies = new ArrayList<>();
        for (var artifact : jarArtifacts) {
            try {
                tallies.add(definedPackages(new JarFile(artifact.getFile()), artifactVersionOrNull(artifact.getVersion())));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return PackageTally.combine(tallies);
    }

    private static PackageTally definedPackages(JarFile jarFile, ArtifactVersion version) throws MojoExecutionException {
        List<ClassFileMetaData> analyzedClasses = new ArrayList<>();
        for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
            JarEntry entry = entries.nextElement();
            if (! entry.isDirectory() && entry.getName().endsWith(".class")) {
                analyzedClasses.add(analyzeClass(jarFile, entry, version));
            }
        }
        return PackageTally.fromAnalyzedClassFiles(analyzedClasses);
    }

    private static ClassFileMetaData analyzeClass(JarFile jarFile, JarEntry entry, ArtifactVersion version) throws MojoExecutionException {
        try {
            return Analyze.analyzeClass(jarFile.getInputStream(entry), version);
        } catch (Exception e) {
            throw new MojoExecutionException(
                    String.format("While analyzing the class '%s' in jar file '%s'", entry.getName(), jarFile.getName()), e);
        }
    }

    private static Map<String, Optional<String>> getManualImports(String importPackage) {
        try {
            Map<String, Optional<String>> ret = new HashMap<>();
            List<Export> imports = parseImportPackages(importPackage);
            for (Export imp : imports) {
                Optional<String> version = getVersionThrowOthers(imp.getParameters());
                imp.getPackageNames().forEach(pn -> ret.put(pn, version));
            }

            return ret;
        } catch (Exception e) {
            throw new RuntimeException("Error in Import-Package:" + importPackage, e);
        }
    }

    private static Optional<String> getVersionThrowOthers(List<ExportPackages.Parameter> parameters) {
        if (parameters.size() == 1 && "version".equals(parameters.get(0).getName())) {
            return Optional.of(parameters.get(0).getValue());
        } else if (parameters.size() == 0) {
            return Optional.empty();
        } else {
            List<String> paramNames = parameters.stream().map(ExportPackages.Parameter::getName).collect(Collectors.toList());
            throw new RuntimeException("A single, optional version parameter expected, but got " + paramNames);
        }
    }

    private static List<Export> parseImportPackages(String importPackages) {
        return ExportPackageParser.parseExports(importPackages);
    }

    private static Optional<String> emptyToNone(String str) {
        return Optional.ofNullable(str).map(String::trim).filter(s -> ! s.isEmpty());
    }

}
