// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import org.junit.Test;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.BaseDeployLogger;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ComponentValidatorTest {
    private static final String JARS_DIR = "src/test/cfg/application/validation/testjars/";

    @Test
    public void basicComponentValidation() throws Exception {
        // Valid jar file
        JarFile ok = new JarFile(new File(JARS_DIR + "ok.jar"));
        ComponentValidator componentValidator = new ComponentValidator(ok);
        componentValidator.validateAll(new BaseDeployLogger());

        // No manifest
        validateWithException("nomanifest.jar", "Non-existing or invalid manifest in " + JARS_DIR + "nomanifest.jar");
    }

    private void validateWithException(String jarName, String exceptionMessage) throws IOException {
        try {
            JarFile jarFile = new JarFile(JARS_DIR + jarName);
            ComponentValidator componentValidator = new ComponentValidator(jarFile);
            componentValidator.validateAll(new BaseDeployLogger());
            assert (false);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(exceptionMessage));
        }
    }

    @Test
    public void require_that_deploying_snapshot_bundle_gives_warning() throws IOException {
        final StringBuffer buffer = new StringBuffer();
        
        DeployLogger logger = new DeployLogger() {
            @Override
            public void log(Level level, String message) {
                buffer.append(message).append('\n');
            }
        };
        
        new ComponentValidator(new JarFile(JARS_DIR + "snapshot_bundle.jar")).validateAll(logger);
        assertThat(buffer.toString(), containsString("Deploying snapshot bundle"));
    }
}
