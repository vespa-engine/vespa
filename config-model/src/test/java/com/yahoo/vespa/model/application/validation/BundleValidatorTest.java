// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BundleValidatorTest {
    private static final String JARS_DIR = "src/test/cfg/application/validation/testjars/";

    @Test
    public void basicBundleValidation() throws Exception {
        // Valid jar file
        JarFile ok = new JarFile(new File(JARS_DIR + "ok.jar"));
        BundleValidator bundleValidator = new BundleValidator();
        bundleValidator.validateJarFile(new BaseDeployLogger(), ok);

        // No manifest
        validateWithException("nomanifest.jar", "Non-existing or invalid manifest in " + JARS_DIR + "nomanifest.jar");
    }

    private void validateWithException(String jarName, String exceptionMessage) throws IOException {
        try {
            JarFile jarFile = new JarFile(JARS_DIR + jarName);
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

        new BundleValidator().validateJarFile(logger, new JarFile(JARS_DIR + "snapshot_bundle.jar"));
        assertTrue(buffer.toString().contains("Deploying snapshot bundle"));
    }

    @Test
    public void outputs_deploy_warning_on_import_of_packages_from_deprecated_artifact() throws IOException {
        final StringBuffer buffer = new StringBuffer();
        DeployLogger logger = createDeployLogger(buffer);
        BundleValidator validator = new BundleValidator();
        Manifest manifest = new Manifest(Files.newInputStream(Paths.get(JARS_DIR + "/manifest-producing-import-warnings.MF")));
        validator.validateManifest(logger, "my-app-bundle.jar", manifest);
        assertThat(buffer.toString())
                .contains("For JAR file 'my-app-bundle.jar': \n" +
                        "Manifest imports the following Java packages from 'org.json:json': [org.json]. \n" +
                        "The org.json library will no longer provided by jdisc runtime on Vespa 8. See https://docs.vespa.ai/en/vespa8-release-notes.html#container-runtime.");
    }

    private DeployLogger createDeployLogger(StringBuffer buffer) {
        return (__, message) -> buffer.append(message).append('\n');
    }
}
