// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InfrastructureDeploymentValidatorTest {

    @Test
    public void allows_infrastructure_deployments() {
        assertDoesNotThrow(() -> runValidator(ApplicationId.global().tenant()));
    }

    @Test
    public void prevents_non_infrastructure_deployments() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> runValidator(TenantName.defaultName()));
        assertEquals("Tenant is not allowed to override application type", exception.getMessage());
    }

    private void runValidator(TenantName tenantName) throws IOException, SAXException {
        String services = """
                <services version='1.0' application-type="hosted-infrastructure">
                    <container id='default' version='1.0' />
                </services>
                """;
        var app = new MockApplicationPackage.Builder()
                .withTenantname(tenantName)
                .withServices(services)
                .build();
        var deployState = new DeployState.Builder().applicationPackage(app).build();
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);

        var validator = new InfrastructureDeploymentValidator();
        validator.validate(model, deployState);
    }
}
