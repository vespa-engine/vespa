package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStoreConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.SecretStore;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author tokle
 */
public class SecretStoreTest  extends ContainerModelBuilderTestBase {

    @Test
    void secret_store_can_be_set_up() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secret-store type='oath-ckms'>",
                "    <group name='group1' environment='env1'/>",
                "  </secret-store>",
                "</container>");
        createModel(root, clusterElem);
        SecretStore secretStore = getContainerCluster("container").getSecretStore().get();
        assertEquals("group1", secretStore.getGroups().get(0).name);
        assertEquals("env1", secretStore.getGroups().get(0).environment);
    }

    @Test
    void cloud_secret_store_requires_configured_secret_store() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secret-store type='cloud'>",
                "    <store id='store'>",
                "      <aws-parameter-store account='store1' region='eu-north-1'/>",
                "    </store>",
                "  </secret-store>",
                "</container>");
        try {
            DeployState state = new DeployState.Builder()
                    .properties(new TestProperties().setHostedVespa(true))
                    .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                    .build();
            createModel(root, state, null, clusterElem);
            fail("secret store not defined");
        } catch (RuntimeException e) {
            assertEquals("No configured secret store named store1", e.getMessage());
        }
    }


    @Test
    void cloud_secret_store_can_be_set_up() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <secret-store type='cloud'>",
                "    <store id='store'>",
                "      <aws-parameter-store account='store1' region='eu-north-1'/>",
                "    </store>",
                "  </secret-store>",
                "</container>");

        DeployState state = new DeployState.Builder()
                .properties(
                        new TestProperties()
                                .setHostedVespa(true)
                                .setTenantSecretStores(List.of(new TenantSecretStore("store1", "1234", "role", Optional.of("externalid")))))
                .zone(new Zone(SystemName.Public, Environment.prod, RegionName.defaultName()))
                .build();
        createModel(root, state, null, clusterElem);

        ApplicationContainerCluster container = getContainerCluster("container");
        assertComponentConfigured(container, "com.yahoo.jdisc.cloud.aws.AwsParameterStore");
        CloudSecretStore secretStore = (CloudSecretStore) container.getComponentsMap().get(ComponentId.fromString("com.yahoo.jdisc.cloud.aws.AwsParameterStore"));


        SecretStoreConfig.Builder configBuilder = new SecretStoreConfig.Builder();
        secretStore.getConfig(configBuilder);
        SecretStoreConfig secretStoreConfig = configBuilder.build();

        assertEquals(1, secretStoreConfig.awsParameterStores().size());
        assertEquals("store1", secretStoreConfig.awsParameterStores().get(0).name());
    }

    @Test
    void cloud_secret_store_fails_to_set_up_in_non_public_zone() {
        try {
            Element clusterElem = DomBuilderTest.parse(
                    "<container version='1.0'>",
                    "  <secret-store type='cloud'>",
                    "    <store id='store'>",
                    "      <aws-parameter-store account='store1' region='eu-north-1'/>",
                    "    </store>",
                    "  </secret-store>",
                    "</container>");

            DeployState state = new DeployState.Builder()
                    .properties(
                            new TestProperties()
                                    .setHostedVespa(true)
                                    .setTenantSecretStores(List.of(new TenantSecretStore("store1", "1234", "role", Optional.of("externalid")))))
                    .zone(new Zone(SystemName.main, Environment.prod, RegionName.defaultName()))
                    .build();
            createModel(root, state, null, clusterElem);
        } catch (RuntimeException e) {
            assertEquals("Cloud secret store is not supported in non-public system, see the documentation",
                    e.getMessage());
            return;
        }
        fail();
    }

}
