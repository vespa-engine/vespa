// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BundleValidatorTest {
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void basicBundleValidation() throws Exception {
        // Valid jar file
        JarFile ok = createTemporaryJarFile("ok");
        BundleValidator bundleValidator = new BundleValidator();
        bundleValidator.validateJarFile(new BaseDeployLogger(), ok);

        // No manifest
        validateWithException("nomanifest", "Non-existing or invalid manifest in nomanifest.jar");
    }

    private void validateWithException(String jarName, String exceptionMessage) throws IOException {
        try {
            JarFile jarFile = createTemporaryJarFile(jarName);
            BundleValidator bundleValidator = new BundleValidator();
            bundleValidator.validateJarFile(new BaseDeployLogger(), jarFile);
            assert (false);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), exceptionMessage);
        }
    }

    @Test
    public void require_that_deploying_snapshot_bundle_gives_warning() throws IOException {
        final StringBuffer buffer = new StringBuffer();

        DeployLogger logger = createDeployLogger(buffer);
        JarFile jarFile = createTemporaryJarFile("snapshot_bundle");
        new BundleValidator().validateJarFile(logger, jarFile);
        assertTrue(buffer.toString().contains("Deploying snapshot bundle"));
    }

    @Test
    public void outputs_deploy_warning_on_import_of_packages_from_deprecated_artifact() throws IOException {
        final StringBuffer buffer = new StringBuffer();
        DeployLogger logger = createDeployLogger(buffer);
        BundleValidator validator = new BundleValidator();
        JarFile jarFile = createTemporaryJarFile("import-warnings");
        validator.validateJarFile(logger, jarFile);
        assertThat(buffer.toString())
                .contains("For JAR file 'import-warnings.jar': \n" +
                        "Manifest imports the following Java packages from 'org.json:json': [org.json]. \n" +
                        "The org.json library will no longer provided by jdisc runtime on Vespa 8. See https://docs.vespa.ai/en/vespa8-release-notes.html#container-runtime.");
    }

    @Test
    public void outputs_deploy_warning_on_deprecated_dependency() throws IOException {
        StringBuffer buffer = new StringBuffer();
        DeployLogger logger = createDeployLogger(buffer);
        BundleValidator validator = new BundleValidator();
        JarFile jarFile = createTemporaryJarFile("pom-xml-warnings");
        validator.validateJarFile(logger, jarFile);
        assertThat(buffer.toString())
                .contains("The pom.xml of bundle 'pom-xml-warnings.jar' includes a dependency to the artifact " +
                        "'com.yahoo.vespa:vespa-http-client-extensions'. \n" +
                        "This artifact will be removed in Vespa 8. " +
                        "Programmatic use can be safely removed from system/staging tests. " +
                        "See internal Vespa 8 release notes for details.\n");
    }

    private JarFile createTemporaryJarFile(String testArtifact) throws IOException {
        Path jarFile = tempDir.newFile(testArtifact + ".jar").toPath();
        Path artifactDirectory = Paths.get("src/test/cfg/application/validation/testjars/" + testArtifact);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarFile))) {
            Files.walk(artifactDirectory).forEach(path -> {
                Path relativePath = artifactDirectory.relativize(path);
                String zipName = relativePath.toString();
                uncheck(() -> {
                    if (Files.isDirectory(path)) {
                        out.putNextEntry(new JarEntry(zipName + "/"));
                    } else {
                        out.putNextEntry(new JarEntry(zipName));
                        out.write(Files.readAllBytes(path));
                    }
                    out.closeEntry();
                });
            });
        }
        return new JarFile(jarFile.toFile());
    }

    private DeployLogger createDeployLogger(StringBuffer buffer) {
        return (__, message) -> buffer.append(message).append('\n');
    }
}
