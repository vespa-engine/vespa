// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.secret.config.SecretsConfig;
import ai.vespa.secret.config.aws.AsmTenantSecretConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.config.model.api.TenantVault;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author lesters
 */
public class SecretsTest extends ContainerModelBuilderTestBase {

    private static final String SECRETS_IMPL_ID = CloudSecrets.CLASS;


    @Test
    void testCloudSecretsNeedHosted() {
        createModel(root, containerXml());
        ApplicationContainerCluster container = getContainerCluster("container");
        Component<?, ?> component = container.getComponentsMap().get(ComponentId.fromString(SECRETS_IMPL_ID));
        assertNull(component);
    }

    @Test
    void testSecretsCanBeSetUp() {
        DeployState state = new DeployState.Builder()
                .properties(new TestProperties().setHostedVespa(true))
                .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                .build();
        createModel(root, state, null, containerXml());
        ApplicationContainerCluster container = getContainerCluster("container");
        assertComponentConfigured(container, SECRETS_IMPL_ID);
        var secretsConfig = getSecretsConfig(container);

        assertEquals(1, secretsConfig.secret().size());
        assertEquals("openai-apikey", secretsConfig.secret("openAiApiKey").name());
    }

    @Test
    void tenant_vaults_are_propagated_in_config() {
        var tenantVaults = List.of(
                new TenantVault("id1", "name1", "externalId1", List.of()),
                new TenantVault("id2", "name2", "externalId2",
                                List.of(new TenantVault.Secret("sId1", "sName1"))));

        var deployState = new DeployState.Builder()
                .properties(new TestProperties()
                                    .setHostedVespa(true)
                                    .setTenantVaults(tenantVaults))
                .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                .build();

        createModel(root, deployState, null, containerXml());
        ApplicationContainerCluster container = getContainerCluster("container");

        var config = getAsmTenantSecretConfig(container);
        assertEquals(SystemName.Public.value(), config.system());
        assertEquals("default", config.tenant());

        var vaults = config.vaults();
        assertEquals(2, vaults.size());

        var vault1 = vaults.get(0);
        assertEquals("id1", vault1.id());
        assertEquals("name1", vault1.name());
        assertEquals("externalId1", vault1.externalId());
        assertEquals(0, vault1.secrets().size());

        var vault2 = vaults.get(1);
        assertEquals("id2", vault2.id());
        assertEquals("name2", vault2.name());
        assertEquals("externalId2", vault2.externalId());
        assertEquals(1, vault2.secrets().size());

        var secret = vault2.secrets().get(0);
        assertEquals("sId1", secret.id());
        assertEquals("sName1", secret.name());
    }

    private static AsmTenantSecretConfig getAsmTenantSecretConfig(ApplicationContainerCluster container) {
        var secrets = (CloudAsmSecrets) container.getComponentsMap().get(ComponentId.fromString(CloudAsmSecrets.CLASS));

        AsmTenantSecretConfig.Builder configBuilder = new AsmTenantSecretConfig.Builder();
        secrets.getConfig(configBuilder);
        return configBuilder.build();
    }

    private static SecretsConfig getSecretsConfig(ApplicationContainerCluster container) {
        var secrets = (CloudSecrets) container.getComponentsMap().get(ComponentId.fromString(SECRETS_IMPL_ID));

        SecretsConfig.Builder configBuilder = new SecretsConfig.Builder();
        secrets.getConfig(configBuilder);
        return configBuilder.build();
    }

    private static Element containerXml() {
        return DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secrets>",
                "    <openAiApiKey vault='prod' name='openai-apikey' />",
                "  </secrets>",
                "</container>");
    }

}
