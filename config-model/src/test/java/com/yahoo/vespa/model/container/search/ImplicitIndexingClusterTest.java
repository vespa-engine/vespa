// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ImplicitIndexingClusterTest {

    @Test
    void existing_jdisc_is_used_as_indexing_cluster_when_multitenant() {
        final String servicesXml = "<services version=\"1.0\">\n" + //
                "  <container version=\"1.0\" id=\"jdisc\">\n" + //
                "    <search />\n" + //
                "    <nodes count=\"1\" />\n" + //
                "    <http>\n" + //
                "      <server id=\"bar\" port=\"" + Defaults.getDefaults().vespaWebServicePort() + "\" />\n" + //
                "    </http>\n" + //
                "  </container>\n" + //
                "  <content id=\"music\" version=\"1.0\">\n" + //
                "    <redundancy>1</redundancy>\n" + //
                "    <documents>\n" + //
                "      <document type=\"music\" mode=\"index\" />\n" + //
                "    </documents>\n" + //
                "    <nodes count=\"1\" />\n" + //
                "  </content>\n" + //
                "</services>\n";


        VespaModel vespaModel = buildMultiTenantVespaModel(servicesXml);
        ContainerCluster jdisc = vespaModel.getContainerClusters().get("jdisc");
        assertNotNull(jdisc.getDocproc(), "Docproc not added to jdisc");
        assertNotNull(jdisc.getDocprocChains().allChains().getComponent("indexing"), "Indexing chain not added to jdisc");
    }

    private static VespaModel buildMultiTenantVespaModel(String servicesXml) {
        ModelContext.Properties properties = new TestProperties().setMultitenant(true).setHostedVespa(true);
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .properties(properties)
                .modelHostProvisioner(new InMemoryProvisioner(6, false));

        return new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                .withServices("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + servicesXml)
                .withSearchDefinition(MockApplicationPackage.MUSIC_SCHEMA)
                .build())
                .create(deployStateBuilder);
    }
}
