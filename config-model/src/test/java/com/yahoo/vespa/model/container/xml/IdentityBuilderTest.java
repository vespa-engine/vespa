// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.vespa.model.container.IdentityProvider;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mortent
 */
public class IdentityBuilderTest extends ContainerModelBuilderTestBase {
    @Test
    void identity_config_produced_from_deployment_spec() {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'><search /></container>");
        String deploymentXml = "<deployment version='1.0' athenz-domain='domain' athenz-service='service'>\n" +
                "    <test/>\n" +
                "    <prod>\n" +
                "        <region active='true'>default</region>\n" +
                "    </prod>\n" +
                "</deployment>\n";

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withDeploymentSpec(deploymentXml)
                .build();

        createModel(root, new DeployState.Builder()
                        .properties(new TestProperties().setHostedVespa(true))
                        .applicationPackage(applicationPackage)
                        .build(),
                null,
                clusterElem);

        IdentityConfig identityConfig = root.getConfig(IdentityConfig.class, "default/component/" + IdentityProvider.CLASS);
        assertEquals("domain", identityConfig.domain());
        assertEquals("service", identityConfig.service());
    }
}
