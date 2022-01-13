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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        getPomXmlContent(deployLogger, jarFile)
                .ifPresent(pomXml -> validatePomXml(deployLogger, filename, pomXml));
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
        Map<DeprecatedProvidedBundle, List<String>> deprecatedPackagesInUse = new HashMap<>();

        importPackage.forEach((packageName, attrs) -> {
            VersionRange versionRange = attrs.getVersion() != null
                    ? VersionRange.parseOSGiVersionRange(attrs.getVersion())
                    : null;

            for (DeprecatedProvidedBundle deprecatedBundle : DeprecatedProvidedBundle.values()) {
                for (Predicate<String> matcher : deprecatedBundle.javaPackageMatchers) {
                    if (matcher.test(packageName)
                            && (versionRange == null || deprecatedBundle.versionDiscriminator.test(versionRange))) {
                        deprecatedPackagesInUse.computeIfAbsent(deprecatedBundle, __ -> new ArrayList<>())
                                .add(packageName);
                    }
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

    private static final Pattern POM_FILE_LOCATION = Pattern.compile("META-INF/maven/.+?/.+?/pom.xml");

    private Optional<String> getPomXmlContent(DeployLogger deployLogger, JarFile jarFile) {
        return jarFile.stream()
                .filter(f -> POM_FILE_LOCATION.matcher(f.getName()).matches())
                .findFirst()
                .map(f -> {
                    try {
                        return new String(jarFile.getInputStream(f).readAllBytes());
                    } catch (IOException e) {
                        deployLogger.log(Level.INFO,
                                String.format("Unable to read '%s' from '%s'", f.getName(), jarFile.getName()));
                        return null;
                    }
                });
    }

    private void validatePomXml(DeployLogger deployLogger, String jarFilename, String pomXmlContent) {
        try {
            Document pom = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(pomXmlContent)));
            NodeList dependencies = (NodeList) XPathFactory.newDefaultInstance().newXPath()
                    .compile("/project/dependencies/dependency")
                    .evaluate(pom, XPathConstants.NODESET);
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                String groupId = dependency.getElementsByTagName("groupId").item(0).getTextContent();
                String artifactId = dependency.getElementsByTagName("artifactId").item(0).getTextContent();
                for (DeprecatedMavenArtifact deprecatedArtifact : DeprecatedMavenArtifact.values()) {
                    if (groupId.equals(deprecatedArtifact.groupId) && artifactId.equals(deprecatedArtifact.artifactId)) {
                        deployLogger.logApplicationPackage(Level.WARNING,
                                String.format(
                                        "The pom.xml of bundle '%s' includes a dependency to the artifact '%s:%s'. \n%s",
                                        jarFilename, groupId, artifactId, deprecatedArtifact.description));
                    }
                }
            }
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            deployLogger.log(Level.INFO, String.format("Unable to parse pom.xml from %s", jarFilename));
        }
    }

    private enum DeprecatedMavenArtifact {
        VESPA_HTTP_CLIENT_EXTENSION("com.yahoo.vespa", "vespa-http-client-extensions",
                "This artifact will be removed in Vespa 8. " +
                        "Programmatic use can be safely removed from system/staging tests. " +
                        "See internal Vespa 8 release notes for details.");

        final String groupId;
        final String artifactId;
        final String description;

        DeprecatedMavenArtifact(String groupId, String artifactId, String description) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.description = description;
        }
    }

    private enum DeprecatedProvidedBundle {
        ORG_JSON("org.json:json",
                "The org.json library will no longer provided by jdisc runtime on Vespa 8. " +
                        "See https://docs.vespa.ai/en/vespa8-release-notes.html#container-runtime.",
                Set.of("org\\.json"));

        final String name;
        final Collection<Predicate<String>> javaPackageMatchers;
        final Predicate<VersionRange> versionDiscriminator;
        final String description;

        DeprecatedProvidedBundle(String name, String description, Collection<String> javaPackagePatterns) {
            this(name, description, __ -> true, javaPackagePatterns);
        }

        DeprecatedProvidedBundle(String name,
                                 String description,
                                 Predicate<VersionRange> versionDiscriminator,
                                 Collection<String> javaPackagePatterns) {
            this.name = name;
            this.javaPackageMatchers = javaPackagePatterns.stream()
                .map(s -> Pattern.compile(s).asMatchPredicate())
                .collect(Collectors.toList());
            this.versionDiscriminator = versionDiscriminator;
            this.description = description;
        }
    }
}
