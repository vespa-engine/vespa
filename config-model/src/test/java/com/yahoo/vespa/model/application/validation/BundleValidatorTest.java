// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BundleValidatorTest {
    @TempDir
    public File tempDir;

    @Test
    void basicBundleValidation() throws Exception {
        // Valid jar file
        JarFile ok = createTemporaryJarFile("ok");
        BundleValidator bundleValidator = new BundleValidator();
        bundleValidator.validateJarFile(DeployState.createTestState(), ok);

        // No manifest
        validateWithException("nomanifest", "Non-existing or invalid manifest in nomanifest.jar");
    }

    private void validateWithException(String jarName, String exceptionMessage) throws IOException {
        try {
            JarFile jarFile = createTemporaryJarFile(jarName);
            BundleValidator bundleValidator = new BundleValidator();
            bundleValidator.validateJarFile(DeployState.createTestState(), jarFile);
            assert (false);
        } catch (IllegalArgumentException e) {
            assertEquals(exceptionMessage, e.getMessage());
        }
    }

    @Test
    void require_that_deploying_snapshot_bundle_gives_warning() throws IOException {
        final StringBuffer buffer = new StringBuffer();

        DeployState state = createDeployState(buffer);
        JarFile jarFile = createTemporaryJarFile("snapshot_bundle");
        new BundleValidator().validateJarFile(state, jarFile);
        assertTrue(buffer.toString().contains("Deploying snapshot bundle"));
    }

    @Test
    void outputs_deploy_warning_on_import_of_packages_from_deprecated_artifact() throws IOException {
        final StringBuffer buffer = new StringBuffer();
        DeployState state = createDeployState(buffer);
        BundleValidator validator = new BundleValidator();
        JarFile jarFile = createTemporaryJarFile("import-warnings");
        validator.validateJarFile(state, jarFile);
        String output = buffer.toString();
        assertThat(output)
                .contains("JAR file 'import-warnings.jar' imports the packages [org.json] from 'org.json:json'. \n" +
                        "This bundle is no longer provided on Vespa 8 - see https://docs.vespa.ai/en/vespa8-release-notes.html#container-runtime.");
        assertThat(output)
                .contains("JAR file 'import-warnings.jar' imports the packages [org.eclipse.jetty.client.api] from 'jetty'. \n" +
                        "The Jetty bundles are no longer provided on Vespa 8 - see https://docs.vespa.ai/en/vespa8-release-notes.html#container-runtime.");
    }

    private DeployState createDeployState(StringBuffer buffer) {
        DeployLogger logger = (__, message) -> buffer.append(message).append('\n');
        return DeployState.createTestState(logger);
    }

    private JarFile createTemporaryJarFile(String testArtifact) throws IOException {
        Path jarFile = Paths.get(tempDir.toString(), testArtifact + ".jar");
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

    private void verifyParsing(String text, List<String> expected) {
        TokenizeAndDeQuote tokenizer = new TokenizeAndDeQuote(";,=", "\"'");
        var words = tokenizer.tokenize(text);
        assertEquals(expected, words);
    }

    @Test
    void testImportPackagesParsing() {
        assertEquals(new ImportPackageInfo("com.yahoo.config;version=\"[1.0.0,2)\",com.yahoo.filedistribution.fileacquirer;version=\"[1.0.0,2)\"," +
                        "com.yahoo.jdisc,com.yahoo.jdisc.handler,java.io;version=\"[0.0.0,1)\",java.lang;version=\"[0.0.0,1)\"," +
                        "java.nio;version=\"[0.0.0,1)\",java.util.concurrent;version=\"[0.0.0,1)\",java.util;version=\"[0.0.0,1)\"").packages(),
                List.of("com.yahoo.config", "com.yahoo.filedistribution.fileacquirer", "com.yahoo.jdisc",
                        "com.yahoo.jdisc.handler", "java.io", "java.lang", "java.nio", "java.util.concurrent", "java.util"));
        assertEquals(new ImportPackageInfo("org.json;version=\"[0.0.0,1)\",org.eclipse.jetty.client.api;version=\"[9.4.46,10)").packages(),
                List.of("org.json", "org.eclipse.jetty.client.api"));
    }

    @Test
    void testQuotedTokenizer() {
        verifyParsing("org.json;version=\"[0.0.0,1)\",org.eclipse.jetty.client.api;version=\"[9.4.46,10)\"",
                List.of("org.json", "version", "[0.0.0,1)", "org.eclipse.jetty.client.api", "version", "[9.4.46,10)"));
        verifyParsing("org.json;version='[0.0.0,1)',org.eclipse.jetty.client.api;version='[9.4.46,10)'",
                List.of("org.json", "version", "[0.0.0,1)", "org.eclipse.jetty.client.api", "version", "[9.4.46,10)"));
    }


}
