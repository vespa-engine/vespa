// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import com.yahoo.container.plugin.classanalysis.Analyze;
import com.yahoo.container.plugin.classanalysis.ClassFileMetaData;
import com.yahoo.container.plugin.classanalysis.ExportPackageAnnotation;
import com.yahoo.container.plugin.classanalysis.PackageTally;
import com.yahoo.container.plugin.osgi.ExportPackageParser;
import com.yahoo.container.plugin.osgi.ExportPackages;
import com.yahoo.container.plugin.osgi.ImportPackages;
import com.yahoo.container.plugin.util.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.container.plugin.util.IO.withFileOutputStream;

/**
 * @author bjorncs
 */
abstract class AbstractGenerateOsgiManifestMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    MavenProject project;

    /**
     * If set to true, the artifact's version is used as default package version for ExportPackages.
     * Packages from included (compile scoped) artifacts will use the version for their own artifact.
     * If the package is exported with an explicit version in package-info.java, that version will be
     * used regardless of this parameter.
     */
    @Parameter(alias = "UseArtifactVersionForExportPackages", defaultValue = "false")
    boolean useArtifactVersionForExportPackages;

    @Parameter(alias = "Bundle-Version", defaultValue = "${project.version}")
    String bundleVersion;

    // TODO: default should include groupId, but that will require a lot of changes both by us and users.
    @Parameter(alias = "Bundle-SymbolicName", defaultValue = "${project.artifactId}")
    String bundleSymbolicName;

    @Parameter(alias = "Import-Package")
    String importPackage;

    // Spifly specific headers for java.util.ServiceLoader support in OSGi context. For internal use only.
    @Parameter(alias = "SPI-Provider")
    String spiProvider;
    @Parameter(alias = "SPI-Consumer")
    String spiConsumer;

    Map<String, String> generateManifestContent(
            Collection<Artifact> jarArtifactsToInclude,
            Map<String, ImportPackages.Import> calculatedImports,
            PackageTally pluginPackageTally) {

        Map<String, Optional<String>> manualImports = getManualImports();
        for (String packageName : manualImports.keySet()) {
            calculatedImports.remove(packageName);
        }
        Collection<ImportPackages.Import> imports = calculatedImports.values();

        Map<String, String> ret = new HashMap<>();
        String importPackage = Stream.concat(manualImports.entrySet().stream().map(e -> asOsgiImport(e.getKey(), e.getValue())),
                imports.stream().map(ImportPackages.Import::asOsgiImport)).sorted()
                .collect(Collectors.joining(","));

        String exportPackage = osgiExportPackages(pluginPackageTally.exportedPackages()).stream().sorted()
                .collect(Collectors.joining(","));

        ret.put("Created-By", "vespa container maven plugin");
        ret.put("Bundle-ManifestVersion", "2");
        ret.put("Bundle-Name", project.getName());
        ret.put("Bundle-SymbolicName", bundleSymbolicName);
        ret.put("Bundle-Version", asBundleVersion(bundleVersion));
        ret.put("Bundle-Vendor", "Yahoo!");
        addIfNotEmpty(ret, "Bundle-ClassPath", bundleClassPath(jarArtifactsToInclude));
        addIfNotEmpty(ret, "Import-Package", importPackage);
        addIfNotEmpty(ret, "Export-Package", exportPackage);
        addIfNotEmpty(ret, "SPI-Provider", spiProvider);
        addIfNotEmpty(ret, "SPI-Consumer", spiConsumer);
        return ret;
    }

    PackageTally definedPackages(Collection<Artifact> jarArtifacts) {
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

    ArtifactVersion artifactVersionOrNull(String version) {
        return useArtifactVersionForExportPackages ? new DefaultArtifactVersion(version) : null;
    }

    static void createManifestFile(Path outputDirectory, Map<String, String> manifestContent) {
        Manifest manifest = new Manifest();
        Attributes mainAttributes = manifest.getMainAttributes();

        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifestContent.forEach(mainAttributes::putValue);

        withFileOutputStream(outputDirectory.resolve(JarFile.MANIFEST_NAME).toFile(), out -> {
            manifest.write(out);
            return null;
        });
    }

    static void addIfNotEmpty(Map<String, String> map, String key, String value) {
        if (value != null && ! value.isEmpty()) {
            map.put(key, value);
        }
    }

    private Collection<String> osgiExportPackages(Map<String, ExportPackageAnnotation> exportedPackages) {
        return exportedPackages.entrySet().stream().map(entry -> entry.getKey() + ";version=" + entry.getValue().osgiVersion())
                .toList();
    }

    private static String asOsgiImport(String packageName, Optional<String> version) {
        return version
                .map(s -> packageName + ";version=" + ("\"" + s + "\""))
                .orElse(packageName);
    }

    private static String bundleClassPath(Collection<Artifact> artifactsToInclude) {
        return Stream.concat(Stream.of("."), artifactsToInclude.stream()
                .map(artifact -> "dependencies/" + artifact.getFile().getName()))
                .collect(Collectors.joining(","));
    }

    private Map<String, Optional<String>> getManualImports() {
        try {
            if (importPackage == null || importPackage.isBlank()) return Map.of();
            Map<String, Optional<String>> ret = new HashMap<>();
            List<ExportPackages.Export> imports = ExportPackageParser.parseExports(importPackage);
            for (ExportPackages.Export imp : imports) {
                Optional<String> version = getVersionThrowOthers(imp.getParameters());
                imp.getPackageNames().forEach(pn -> ret.put(pn, version));
            }
            return ret;
        } catch (Exception e) {
            throw new RuntimeException("Error in Import-Package:" + importPackage, e);
        }
    }

    private static String asBundleVersion(String projectVersion) {
        if (projectVersion == null) {
            throw new IllegalArgumentException("Missing project version.");
        }

        String[] parts = projectVersion.split("-", 2);
        List<String> numericPart = Stream.of(parts[0].split("\\.")).map(s -> Strings.replaceEmptyString(s, "0")).limit(3)
                .collect(Collectors.toCollection(ArrayList::new));
        while (numericPart.size() < 3) {
            numericPart.add("0");
        }

        return String.join(".", numericPart);
    }

    private static Optional<String> getVersionThrowOthers(List<ExportPackages.Parameter> parameters) {
        if (parameters.size() == 1 && "version".equals(parameters.get(0).getName())) {
            return Optional.of(parameters.get(0).getValue());
        } else if (parameters.size() == 0) {
            return Optional.empty();
        } else {
            List<String> paramNames = parameters.stream().map(ExportPackages.Parameter::getName).toList();
            throw new RuntimeException("A single, optional version parameter expected, but got " + paramNames);
        }
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

}
