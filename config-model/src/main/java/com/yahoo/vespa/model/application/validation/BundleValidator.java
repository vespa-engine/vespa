// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Domain;
import aQute.bnd.version.VersionRange;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;

/**
 * A validator for bundles.  Uses BND library for some of the validation.
 *
 * @author hmusum
 * @author bjorncs
 */
public class BundleValidator extends Validator {

    public BundleValidator() {}

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        ApplicationPackage app = deployState.getApplicationPackage();
        for (ComponentInfo info : app.getComponentsInfo(deployState.getVespaVersion())) {
            Path path = Path.fromString(info.getPathRelativeToAppDir());
            try {
                DeployLogger deployLogger = deployState.getDeployLogger();
                deployLogger.log(Level.FINE, String.format("Validating bundle at '%s'", path));
                JarFile jarFile = new JarFile(app.getFileReference(path));
                validateJarFile(deployLogger, jarFile);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to validate JAR file '" + path.last() + "'", e);
            }
        }
    }

    void validateJarFile(DeployLogger deployLogger, JarFile jarFile) throws IOException {
        Manifest manifest = jarFile.getManifest();
        String filename = Paths.get(jarFile.getName()).getFileName().toString();
        if (manifest == null) {
            throw new IllegalArgumentException("Non-existing or invalid manifest in " + filename);
        }
        validateManifest(deployLogger, filename, manifest);
    }

    private void validateManifest(DeployLogger deployLogger, String filename, Manifest mf) {
        // Check for required OSGI headers
        Attributes attributes = mf.getMainAttributes();
        HashSet<String> mfAttributes = new HashSet<>();
        for (Map.Entry<Object,Object> entry : attributes.entrySet()) {
            mfAttributes.add(entry.getKey().toString());
        }
        List<String> requiredOSGIHeaders = Arrays.asList(
                "Bundle-ManifestVersion", "Bundle-Name", "Bundle-SymbolicName", "Bundle-Version");
        for (String header : requiredOSGIHeaders) {
            if (!mfAttributes.contains(header)) {
                throw new IllegalArgumentException("Required OSGI header '" + header +
                        "' was not found in manifest in '" + filename + "'");
            }
        }

        if (attributes.getValue("Bundle-Version").endsWith(".SNAPSHOT")) {
            deployLogger.logApplicationPackage(Level.WARNING, "Deploying snapshot bundle " + filename +
                    ".\nTo use this bundle, you must include the qualifier 'SNAPSHOT' in  the version specification in services.xml.");
        }

        if (attributes.getValue("Import-Package") != null) {
            validateImportedPackages(deployLogger, filename, mf);
        }
    }

    private static void validateImportedPackages(DeployLogger deployLogger, String filename, Manifest manifest) {
        Domain osgiHeaders = Domain.domain(manifest);
        Parameters importPackage = osgiHeaders.getImportPackage();
        Map<DeprecatedArtifact, List<String>> deprecatedPackagesInUse = new HashMap<>();

        importPackage.forEach((packageName, attrs) -> {
            VersionRange versionRange = attrs.getVersion() != null
                    ? VersionRange.parseOSGiVersionRange(attrs.getVersion())
                    : null;

            for (DeprecatedArtifact deprecatedArtifact : DeprecatedArtifact.values()) {
                if (deprecatedArtifact.javaPackages.contains(packageName)
                        && (versionRange == null || deprecatedArtifact.versionDiscriminator.test(versionRange))) {
                    deprecatedPackagesInUse.computeIfAbsent(deprecatedArtifact, __ -> new ArrayList<>())
                            .add(packageName);
                }
            }
        });

        deprecatedPackagesInUse.forEach((artifact, packagesInUse) -> {
            deployLogger.logApplicationPackage(Level.WARNING,
                    String.format("For JAR file '%s': \n" +
                            "Manifest imports the following Java packages from '%s': %s. \n" +
                            "%s",
                            filename, artifact.name, packagesInUse, artifact.description));
        });
    }

    private enum DeprecatedArtifact {
        ORG_JSON("org.json:json",
                "The org.json library will no longer provided by jdisc runtime on Vespa 8. " +
                        "See https://docs.vespa.ai/en/vespa8-release-notes.html#container-runtime.",
                Set.of("org.json"));

        final String name;
        final Collection<String> javaPackages;
        final Predicate<VersionRange> versionDiscriminator;
        final String description;

        DeprecatedArtifact(String name, String description, Collection<String> javaPackages) {
            this(name, description, __ -> true, javaPackages);
        }

        DeprecatedArtifact(String name,
                           String description,
                           Predicate<VersionRange> versionDiscriminator,
                           Collection<String> javaPackages) {
            this.name = name;
            this.javaPackages = javaPackages;
            this.versionDiscriminator = versionDiscriminator;
            this.description = description;
        }
    }
}
