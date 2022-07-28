// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class SecretStoreValidatorTest {

    private static String servicesXml() {
        return joinLines("<services version='1.0'>",
                         "  <container id='default' version='1.0'>",
                         "    <secret-store type='oath-ckms'>",
                         "      <group name='group1' environment='prod'/>",
                         "    </secret-store>",
                         "  </container>",
                         "</services>");
    }

    private static String deploymentXml(boolean addAthenz) {
        return joinLines("<deployment version='1.0' " + (addAthenz ?
                                 "athenz-domain='domain' athenz-service='service'" : "") + ">",
                         "  <prod />",
                         "</deployment>");
    }

    @Test
    void app_with_athenz_in_deployment_passes_validation() throws Exception {
        DeployState deployState = deployState(servicesXml(), deploymentXml(true));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new SecretStoreValidator().validate(model, deployState);
    }

    @Test
    void app_without_athenz_in_deployment_fails_validation() throws Exception {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

            DeployState deployState = deployState(servicesXml(), deploymentXml(false));
            VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

            new SecretStoreValidator().validate(model, deployState);

        });
        assertTrue(exception.getMessage().contains("Container cluster 'default' uses a secret store, so an Athenz domain and" +
                " an Athenz service must be declared in deployment.xml."));

    }

    @Test
    void app_without_secret_store_passes_validation_without_athenz_in_deployment() throws Exception {
        String servicesXml = joinLines("<services version='1.0'>",
                "  <container id='default' version='1.0' />",
                "</services>");
        DeployState deployState = deployState(servicesXml, deploymentXml(false));
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new SecretStoreValidator().validate(model, deployState);
    }

    private static DeployState deployState(String servicesXml, String deploymentXml) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .withDeploymentSpec(deploymentXml)
                .build();
        DeployState.Builder builder = new DeployState.Builder()
                .applicationPackage(app)
                .zone(new Zone(Environment.prod, RegionName.from("foo")))
                .properties(new TestProperties().setHostedVespa(true));
        final DeployState deployState = builder.build();

        assertTrue(deployState.isHosted(), "Test must emulate a hosted deployment.");
        return deployState;
    }
}
