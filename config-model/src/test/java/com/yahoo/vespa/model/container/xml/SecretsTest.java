// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.secret.config.SecretsConfig;
import com.yahoo.component.ComponentId;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author lesters
 */
public class SecretsTest extends ContainerModelBuilderTestBase {

    private static String IMPL_ID = "ai.vespa.secret.aws.SecretsImpl";

    @Test
    void testCloudSecretsNeedHosted() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secrets>",
                "    <openAiApiKey vault='prod' name='openai-apikey' />",
                "  </secrets>",
                "</container>");
        createModel(root, clusterElem);
        ApplicationContainerCluster container = getContainerCluster("container");
        Component<?, ?> component = container.getComponentsMap().get(ComponentId.fromString(IMPL_ID));
        assertNull(component);
    }

    @Test
    void testSecretsCanBeSetUp() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secrets>",
                "    <openAiApiKey vault='prod' name='openai-apikey' />",
                "  </secrets>",
                "</container>");
        DeployState state = new DeployState.Builder()
                .properties(new TestProperties().setHostedVespa(true))
                .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                .build();
        createModel(root, state, null, clusterElem);
        ApplicationContainerCluster container = getContainerCluster("container");
        assertComponentConfigured(container, IMPL_ID);
        CloudSecrets secrets = (CloudSecrets) container.getComponentsMap().get(ComponentId.fromString(IMPL_ID));

        SecretsConfig.Builder configBuilder = new SecretsConfig.Builder();
        secrets.getConfig(configBuilder);
        SecretsConfig secretsConfig = configBuilder.build();

        assertEquals(1, secretsConfig.secret().size());
        assertEquals("openai-apikey", secretsConfig.secret("openAiApiKey").name());
    }

}
