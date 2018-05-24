// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ImplicitIndexingClusterTest {
    @Test
    public void existing_jdisc_is_used_as_indexing_cluster_when_multitenant() {
        final String servicesXml = "<services version=\"1.0\">\n" + //
                "  <jdisc version=\"1.0\" id=\"jdisc\">\n" + //
                "    <search />\n" + //
                "    <nodes count=\"1\" />\n" + //
                ACCESS_CONTROL_XML + //
                "  </jdisc>\n" + //
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
        assertNotNull("Docproc not added to jdisc", jdisc.getDocproc());
        assertNotNull("Indexing chain not added to jdisc", jdisc.getDocprocChains().allChains().getComponent("indexing"));
    }

    private final String ACCESS_CONTROL_XML = "<http>\n" +//
            "  <filtering>\n" +//
            "    <access-control domain=\"foo\" />\n" +//
            "  </filtering>\n" +//
            "  <server id=\"bar\" port=\"4080\" />\n" +//
            "</http>\n";

    private static VespaModel buildMultiTenantVespaModel(String servicesXml) {
        DeployProperties properties = new DeployProperties.Builder().multitenant(true).hostedVespa(true).build();
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .properties(properties)
                .modelHostProvisioner(new InMemoryProvisioner(true, "host1.yahoo.com", "host2.yahoo.com", "host3.yahoo.com"));

        return new VespaModelCreatorWithMockPkg(new MockApplicationPackage.Builder()
                .withServices("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + servicesXml)
                .withSearchDefinition(MockApplicationPackage.MUSIC_SEARCHDEFINITION)
                .build())
                .create(deployStateBuilder);
    }
}
