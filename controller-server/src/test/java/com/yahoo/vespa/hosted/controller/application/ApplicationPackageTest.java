package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author valerijf
 */
public class ApplicationPackageTest {
    @Test
    public void test_createEmptyForDeploymentRemoval() {
        ApplicationPackage app = ApplicationPackage.deploymentRemoval();
        assertEquals(DeploymentSpec.empty, app.deploymentSpec());
        assertEquals(List.of(), app.trustedCertificates());

        for (ValidationId validationId : ValidationId.values()) {
            assertTrue(app.validationOverrides().allows(validationId, Instant.now()));
        }
    }
}
