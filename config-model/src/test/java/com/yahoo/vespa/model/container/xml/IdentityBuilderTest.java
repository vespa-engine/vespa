// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
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
                "        <region>default</region>\n" +
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

    @Test
    void tenant_identity_in_public_systems() {
        assertTenantIdentity("vespa.tenant.mytenant", new TestProperties());
        assertTenantIdentity("user.someone.tenants.mytenant",
                             new TestProperties().setTenantParentDomain(AthenzDomain.from("user.someone.tenants")));
    }

    private void assertTenantIdentity(String domain, TestProperties properties) {
        var root = new MockRoot("root");
        Element clusterElem = DomBuilderTest.parse("<container id='default' version='1.0'><search /></container>");
        createModel(root, new DeployState.Builder()
                        .properties(properties.setHostedVespa(true)
                                              .setApplicationId(ApplicationId.from("mytenant", "myapp", "default")))
                        .zone(new Zone(SystemName.Public, Environment.dev, RegionName.defaultName()))
                        .build(),
                null,
                clusterElem);

        IdentityConfig identityConfig = root.getConfig(IdentityConfig.class, "default/component/" + IdentityProvider.CLASS);
        assertEquals(domain, identityConfig.domain());
        assertEquals("myapp-sandbox", identityConfig.service());
    }
}
