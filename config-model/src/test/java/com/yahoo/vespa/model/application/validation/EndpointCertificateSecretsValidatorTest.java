// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.CertificateNotReadyException;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Optional;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertTrue;

/**
 * @author andreer
 */
public class EndpointCertificateSecretsValidatorTest {
    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    private static String servicesXml() {
        return joinLines("<services version='1.0'>",
                "  <container id='default' version='1.0'>",
                "  </container>",
                "</services>");
    }

    private static String deploymentXml() {
        return joinLines("<deployment version='1.0' >",
                "  <prod />",
                "</deployment>");
    }

    @Test
    public void missing_certificate_fails_validation() throws Exception {
        DeployState deployState = deployState(servicesXml(), deploymentXml(), Optional.of(EndpointCertificateSecrets.MISSING));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        exceptionRule.expect(CertificateNotReadyException.class);
        exceptionRule.expectMessage("TLS enabled, but could not retrieve certificate yet");

        new EndpointCertificateSecretsValidator().validate(model, deployState);
    }

    @Test
    public void validation_succeeds_with_certificate() throws Exception {
        DeployState deployState = deployState(servicesXml(), deploymentXml(), Optional.of(new EndpointCertificateSecrets("cert", "key")));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new EndpointCertificateSecretsValidator().validate(model, deployState);
    }

    @Test
    public void validation_succeeds_without_certificate() throws Exception {
        DeployState deployState = deployState(servicesXml(), deploymentXml(), Optional.empty());
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new EndpointCertificateSecretsValidator().validate(model, deployState);
    }

    private static DeployState deployState(String servicesXml, String deploymentXml, Optional<EndpointCertificateSecrets> endpointCertificateSecretsSecrets) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withDeploymentSpec(deploymentXml)
                .build();
        DeployState.Builder builder = new DeployState.Builder()
                .applicationPackage(app)
                .zone(new Zone(Environment.prod, RegionName.from("foo")))
                .properties(
                        new TestProperties()
                                .setHostedVespa(true)
                                .setEndpointCertificateSecrets(endpointCertificateSecretsSecrets));
        final DeployState deployState = builder.build();

        assertTrue("Test must emulate a hosted deployment.", deployState.isHosted());
        return deployState;
    }
}
