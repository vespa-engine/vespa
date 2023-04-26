// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.model.test.TestRoot;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.engines.ProtonEngine;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.content.utils.SchemaBuilder;
import com.yahoo.vespa.model.routing.DocumentProtocol;
import com.yahoo.vespa.model.routing.Routing;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

public class ContentClusterTest extends ContentBaseTest {

    private final static String HOSTS = "<admin version='2.0'><adminserver hostalias='mockhost' /></admin>";

    ContentCluster parse(String xml) {
        xml = HOSTS + xml;
        TestRoot root = new TestDriver().buildModel(xml);
        return root.getConfigModels(Content.class).get(0).getCluster();
    }

    @Test
    void testHierarchicRedundancy() {
        ContentCluster cc = parse("" +
                "<content version=\"1.0\" id=\"storage\">\n" +
                "  <documents/>" +
                "  <engine>" +
                "    <proton>" +
                "      <searchable-copies>3</searchable-copies>" +
                "    </proton>" +
                "  </engine>" +
                "  <redundancy>15</redundancy>\n" +
                "  <group name='root'>" +
                "    <distribution partitions='1|1|*'/>" +
                "    <group name='g-1' distribution-key='0'>" +
                "      <node hostalias='mockhost' distribution-key='0'/>" +
                "      <node hostalias='mockhost' distribution-key='1'/>" +
                "      <node hostalias='mockhost' distribution-key='2'/>" +
                "      <node hostalias='mockhost' distribution-key='3'/>" +
                "      <node hostalias='mockhost' distribution-key='4'/>" +
                "    </group>" +
                "    <group name='g-2' distribution-key='1'>" +
                "      <node hostalias='mockhost' distribution-key='5'/>" +
                "      <node hostalias='mockhost' distribution-key='6'/>" +
                "      <node hostalias='mockhost' distribution-key='7'/>" +
                "      <node hostalias='mockhost' distribution-key='8'/>" +
                "      <node hostalias='mockhost' distribution-key='9'/>" +
                "    </group>" +
                "    <group name='g-3' distribution-key='1'>" +
                "      <node hostalias='mockhost' distribution-key='10'/>" +
                "      <node hostalias='mockhost' distribution-key='11'/>" +
                "      <node hostalias='mockhost' distribution-key='12'/>" +
                "      <node hostalias='mockhost' distribution-key='13'/>" +
                "      <node hostalias='mockhost' distribution-key='14'/>" +
                "    </group>" +
                "  </group>" +
                "</content>"
        );
        DistributionConfig.Builder distributionBuilder = new DistributionConfig.Builder();
        cc.getConfig(distributionBuilder);
        DistributionConfig distributionConfig = distributionBuilder.build();
        assertEquals(3, distributionConfig.cluster("storage").ready_copies());
        assertEquals(15, distributionConfig.cluster("storage").initial_redundancy());
        assertEquals(15, distributionConfig.cluster("storage").redundancy());
        assertEquals(4, distributionConfig.cluster("storage").group().size());
        assertEquals(1, distributionConfig.cluster().size());

        StorDistributionConfig.Builder storBuilder = new StorDistributionConfig.Builder();
        cc.getConfig(storBuilder);
        StorDistributionConfig storConfig = new StorDistributionConfig(storBuilder);
        assertEquals(15, storConfig.initial_redundancy());
        assertEquals(15, storConfig.redundancy());
        assertEquals(3, storConfig.ready_copies());

        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        cc.getSearch().getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        assertEquals(1, protonConfig.distribution().searchablecopies());
        assertEquals(5, protonConfig.distribution().redundancy());
    }

    @Test
    void testRedundancy() {
        ContentCluster cc = parse("" +
                "<content version=\"1.0\" id=\"storage\">\n" +
                "  <documents/>" +
                "  <engine>" +
                "    <proton>" +
                "      <searchable-copies>3</searchable-copies>" +
                "    </proton>" +
                "  </engine>" +
                "  <redundancy reply-after='4'>5</redundancy>\n" +
                "  <group>" +
                "    <node hostalias='mockhost' distribution-key='0'/>" +
                "    <node hostalias='mockhost' distribution-key='1'/>" +
                "    <node hostalias='mockhost' distribution-key='2'/>" +
                "    <node hostalias='mockhost' distribution-key='3'/>" +
                "    <node hostalias='mockhost' distribution-key='4'/>" +
                "  </group>" +
                "</content>"
        );
        DistributionConfig.Builder distributionBuilder = new DistributionConfig.Builder();
        cc.getConfig(distributionBuilder);
        DistributionConfig distributionConfig = distributionBuilder.build();
        assertEquals(3, distributionConfig.cluster("storage").ready_copies());
        assertEquals(4, distributionConfig.cluster("storage").initial_redundancy());
        assertEquals(5, distributionConfig.cluster("storage").redundancy());

        StorDistributionConfig.Builder storBuilder = new StorDistributionConfig.Builder();
        cc.getConfig(storBuilder);
        StorDistributionConfig storConfig = new StorDistributionConfig(storBuilder);
        assertEquals(4, storConfig.initial_redundancy());
        assertEquals(5, storConfig.redundancy());
        assertEquals(3, storConfig.ready_copies());

        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        cc.getSearch().getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        assertEquals(3, protonConfig.distribution().searchablecopies());
        assertEquals(5, protonConfig.distribution().redundancy());
    }

    @Test
    void testMinRedundancy() {
        {   // Groups ensures redundancy
            ContentCluster cc = parse("""
                                              <content version='1.0' id='storage'>
                                                <documents/>
                                                <min-redundancy>2</min-redundancy>
                                                <group name='root'>"
                                                  <distribution partitions='1|*'/>
                                                  <group name='g0' distribution-key='0'>
                                                    <node hostalias='mockhost' distribution-key='0'/>
                                                    <node hostalias='mockhost' distribution-key='1'/>
                                                  </group>
                                                  <group name='g1' distribution-key='1'>
                                                    <node hostalias='mockhost' distribution-key='2'/>
                                                    <node hostalias='mockhost' distribution-key='3'/>
                                                  </group>
                                                </group>
                                              </content>
                                              """
            );
            ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
            cc.getSearch().getConfig(protonBuilder);
            ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
            assertEquals(1, protonConfig.distribution().redundancy());
            assertEquals(1, protonConfig.distribution().searchablecopies());
        }

        {  // Redundancy must be within group
            ContentCluster cc = parse("""
                                              <content version='1.0' id='storage'>
                                                <documents/>
                                                <min-redundancy>2</min-redundancy>
                                                <nodes>
                                                  <node hostalias='mockhost' distribution-key='0'/>
                                                  <node hostalias='mockhost' distribution-key='1'/>
                                                  <node hostalias='mockhost' distribution-key='2'/>
                                                  <node hostalias='mockhost' distribution-key='3'/>
                                                </nodes>
                                              </content>
                                              """
            );
            ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
            cc.getSearch().getConfig(protonBuilder);
            ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
            assertEquals(2, protonConfig.distribution().redundancy());
            assertEquals(2, protonConfig.distribution().searchablecopies());
        }

        {   // Multiple gropups but they do not ensure redundancy
            ContentCluster cc = parse("""
                                              <content version='1.0' id='storage'>
                                                <documents/>
                                                <min-redundancy>4</min-redundancy>
                                                <group name='root'>"
                                                  <distribution partitions='1|*'/>
                                                  <group name='g0' distribution-key='0'>
                                                    <node hostalias='mockhost' distribution-key='0'/>
                                                    <node hostalias='mockhost' distribution-key='1'/>
                                                  </group>
                                                  <group name='g1' distribution-key='1'>
                                                    <node hostalias='mockhost' distribution-key='2'/>
                                                    <node hostalias='mockhost' distribution-key='3'/>
                                                  </group>
                                                </group>
                                              </content>
                                              """
            );
            ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
            cc.getSearch().getConfig(protonBuilder);
            ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
            assertEquals(2, protonConfig.distribution().redundancy());
            assertEquals(1, protonConfig.distribution().searchablecopies());
        }

    }

    @Test
    void testNoId() {
        ContentCluster c = parse(
                "<content version=\"1.0\">\n" +
                        "  <redundancy>1</redundancy>\n" +
                        "  <documents/>" +
                        "  <redundancy reply-after=\"4\">5</redundancy>\n" +
                        "  <group>" +
                        "    <node hostalias=\"mockhost\" distribution-key=\"0\"/>\"" +
                        "  </group>" +
                        "</content>"
        );

        assertEquals("content", c.getName());
    }

    @Test
    void testEndToEnd() {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<services>\n" +
                        "\n" +
                        "  <admin version=\"2.0\">\n" +
                        "    <adminserver hostalias=\"configserver\" />\n" +
                        "    <logserver hostalias=\"logserver\" />\n" +
                        "    <slobroks>\n" +
                        "      <slobrok hostalias=\"configserver\" />\n" +
                        "      <slobrok hostalias=\"logserver\" />\n" +
                        "    </slobroks>\n" +
                        "    <cluster-controllers>\n" +
                        "      <cluster-controller hostalias=\"configserver\"/>" +
                        "      <cluster-controller hostalias=\"configserver2\"/>" +
                        "      <cluster-controller hostalias=\"configserver3\"/>" +
                        "    </cluster-controllers>\n" +
                        "  </admin>\n" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>1</redundancy>\n" +
                        "     <documents>" +
                        "       <document type=\"type1\" mode=\"index\"/>\n" +
                        "       <document type=\"type2\" mode=\"index\"/>\n" +
                        "     </documents>\n" +
                        "     <group>" +
                        "       <node hostalias='node0' distribution-key='0' />" +
                        "     </group>" +
                        "    <tuning>" +
                        "      <cluster-controller>\n" +
                        "        <init-progress-time>34567</init-progress-time>" +
                        "      </cluster-controller>" +
                        "    </tuning>" +
                        "   </content>" +
                        "\n" +
                        "</services>";

        List<String> sds = ApplicationPackageUtils.generateSchemas("type1", "type2");
        VespaModel model = new VespaModelCreatorWithMockPkg(null, xml, sds).create();
        assertEquals(2, model.getContentClusters().get("bar").getDocumentDefinitions().size());
        ContainerCluster<?> cluster = model.getAdmin().getClusterControllers();
        assertEquals(3, cluster.getContainers().size());
    }

    VespaModel createEnd2EndOneNode(ModelContext.Properties properties) {
        String services =
                "<?xml version='1.0' encoding='UTF-8' ?>" +
                "<services version='1.0'>" +
                "  <admin version='2.0'>" +
                "    <adminserver hostalias='node1'/>" +
                "  </admin>" +
                "   <container id='default' version='1.0'>" +
                "     <search/>" +
                "     <nodes>" +
                "       <node hostalias='node1'/>" +
                "     </nodes>" +
                "   </container>" +
                "   <content id='storage' version='1.0'>" +
                "     <redundancy>2</redundancy>" +
                "     <group>" +
                "       <node distribution-key='0' hostalias='node1'/>" +
                "       <node distribution-key='1' hostalias='node1'/>" +
                "     </group>" +
                "     <tuning>" +
                "       <cluster-controller>" +
                "         <transition-time>0</transition-time>" +
                "       </cluster-controller>" +
                "     </tuning>" +
                "     <documents>" +
                "       <document mode='index' type='type1'/>" +
                "     </documents>" +
                "     <engine>" +
                "       <proton/>" +
                "     </engine>" +
                "   </content>" +
                " </services>";
        return createEnd2EndOneNode(properties, services);
    }

    VespaModel createEnd2EndOneNode(ModelContext.Properties properties, String services) {
        DeployState.Builder deployStateBuilder = new DeployState.Builder().properties(properties);
        List<String> sds = ApplicationPackageUtils.generateSchemas("type1");
        return (new VespaModelCreatorWithMockPkg(null, services, sds)).create(deployStateBuilder);
    }

    @Test
    void testEndToEndOneNode() {
        VespaModel model = createEnd2EndOneNode(new TestProperties());

        assertEquals(1, model.getContentClusters().get("storage").getDocumentDefinitions().size());
        ContainerCluster<?> cluster = model.getAdmin().getClusterControllers();
        assertEquals(1, cluster.getContainers().size());
    }

    @Test
    void testSearchTuning() {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<services>\n" +
                        "\n" +
                        "  <admin version=\"2.0\">\n" +
                        "    <adminserver hostalias=\"node0\" />\n" +
                        "    <cluster-controllers>\n" +
                        "      <cluster-controller hostalias=\"node0\"/>" +
                        "    </cluster-controllers>\n" +
                        "  </admin>\n" +
                        "  <content version='1.0' id='bar'>" +
                        "     <redundancy>1</redundancy>\n" +
                        "     <documents>" +
                        "       <document type=\"type1\" mode='index'/>\n" +
                        "       <document type=\"type2\" mode='index'/>\n" +
                        "     </documents>\n" +
                        "     <group>" +
                        "       <node hostalias='node0' distribution-key='0'/>" +
                        "     </group>" +
                        "    <tuning>\n" +
                        "      <cluster-controller>" +
                        "        <init-progress-time>34567</init-progress-time>" +
                        "      </cluster-controller>" +
                        "    </tuning>" +
                        "   </content>" +
                        "\n" +
                        "</services>";

        List<String> sds = ApplicationPackageUtils.generateSchemas("type1", "type2");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();

        assertTrue(model.getContentClusters().get("bar").getPersistence() instanceof ProtonEngine.Factory);

        {
            StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
            model.getConfig(builder, "bar/distributor/0");
            StorDistributormanagerConfig config = new StorDistributormanagerConfig(builder);
            assertFalse(config.inlinebucketsplitting());
            assertTrue(config.enable_two_phase_garbage_collection());
        }

        {
            StorFilestorConfig.Builder builder = new StorFilestorConfig.Builder();
            model.getConfig(builder, "bar/storage/0");
            StorFilestorConfig config = new StorFilestorConfig(builder);
            assertFalse(config.enable_multibit_split_optimalization());
        }
    }

    @Test
    void testRedundancyRequired() {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<services>\n" +
                        "\n" +
                        "  <admin version=\"2.0\">\n" +
                        "    <adminserver hostalias=\"node0\" />\n" +
                        "  </admin>\n" +
                        "  <content version='1.0' id='bar'>" +
                        "     <documents>" +
                        "       <document type=\"type1\" mode='index'/>\n" +
                        "     </documents>\n" +
                        "     <group>\n" +
                        "       <node hostalias='node0' distribution-key='0'/>\n" +
                        "     </group>\n" +
                        "   </content>\n" +
                        "</services>\n";

        List<String> sds = ApplicationPackageUtils.generateSchemas("type1", "type2");
        try {
            new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();
            fail("Deploying without redundancy should fail");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Either <redundancy> or <min-redundancy> must be set"), e.getMessage());
        }
    }

    @Test
    void testRedundancyFinalLessThanInitial() {
        try {
            parse(
                    "<content version=\"1.0\" id=\"storage\">\n" +
                            "  <redundancy reply-after=\"4\">2</redundancy>\n" +
                            "     <group>" +
                            "       <node hostalias='node0' distribution-key='0' />" +
                            "     </group>" +
                            "</content>"
            );
            fail("no exception thrown");
        } catch (Exception e) { /* ignore */
        }
    }

    @Test
    void testReadyTooHigh() {
        try {
            parse(
                    "<content version=\"1.0\" id=\"storage\">\n" +
                            "  <engine>" +
                            "     <proton>" +
                            "       <searchable-copies>3</searchable-copies>" +
                            "     </proton>" +
                            "  </engine>" +
                            "  <redundancy>2</redundancy>\n" +
                            "     <group>" +
                            "       <node hostalias='node0' distribution-key='0' />" +
                            "     </group>" +
                            "</content>"
            );
            fail("no exception thrown");
        } catch (Exception e) { /* ignore */
        }
    }

    FleetcontrollerConfig getFleetControllerConfig(String xml) {
        ContentCluster cluster = parse(xml);

        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        cluster.getConfig(builder);
        cluster.getClusterControllerConfig().getConfig(builder);
        return new FleetcontrollerConfig(builder);
    }

    @Test
    void testFleetControllerOverride()
    {
        {
            FleetcontrollerConfig config = getFleetControllerConfig(
                    "<content version=\"1.0\" id=\"storage\">\n" +
                           "   <redundancy>3</redundancy>" +
                            "  <documents/>" +
                            "  <group>\n" +
                            "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                            "  </group>\n" +
                            "</content>"
            );

            assertEquals(0, config.min_storage_up_ratio(), 0.01);
            assertEquals(0, config.min_distributor_up_ratio(), 0.01);
            assertEquals(1, config.min_storage_up_count());
            assertEquals(1, config.min_distributors_up_count());
        }

        {
            FleetcontrollerConfig config = getFleetControllerConfig(
                    "<content version=\"1.0\" id=\"storage\">\n" +
                            "  <redundancy>3</redundancy>" +
                            "  <documents/>" +
                            "  <group>\n" +
                            "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                            "    <node distribution-key=\"1\" hostalias=\"mockhost\"/>\n" +
                            "    <node distribution-key=\"2\" hostalias=\"mockhost\"/>\n" +
                            "    <node distribution-key=\"3\" hostalias=\"mockhost\"/>\n" +
                            "    <node distribution-key=\"4\" hostalias=\"mockhost\"/>\n" +
                            "    <node distribution-key=\"5\" hostalias=\"mockhost\"/>\n" +
                            "  </group>\n" +
                            "</content>"
            );

            assertNotSame(0, config.min_storage_up_ratio());
        }
    }

    @Test
    void testImplicitDistributionBits()
    {
        ContentCluster cluster = parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <redundancy>3</redundancy>" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "</content>"
        );

        assertDistributionBitsInConfig(cluster, 8);

        cluster = parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <redundancy>3</redundancy>" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "</content>"
        );

        assertDistributionBitsInConfig(cluster, 8);
    }

    @Test
    void testExplicitDistributionBits()
    {
        ContentCluster cluster = parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <redundancy>3</redundancy>" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "  <tuning>\n" +
                        "    <distribution type=\"strict\"/>\n" +
                        "  </tuning>\n" +
                        "</content>"
        );

        assertDistributionBitsInConfig(cluster, 8);

        cluster = parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <redundancy>2</redundancy>" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "  <tuning>\n" +
                        "    <distribution type=\"loose\"/>\n" +
                        "  </tuning>\n" +
                        "</content>"
        );

        assertDistributionBitsInConfig(cluster, 8);
    }

    @Test
    void testZoneDependentDistributionBits() throws Exception {
        String xml = new ContentClusterBuilder().docTypes("test").getXml();

        ContentCluster prodWith16Bits = createWithZone(xml, new Zone(Environment.prod, RegionName.from("us-east-3")));
        assertDistributionBitsInConfig(prodWith16Bits, 16);

        ContentCluster stagingNot16Bits = createWithZone(xml, new Zone(Environment.staging, RegionName.from("us-east-3")));
        assertDistributionBitsInConfig(stagingNot16Bits, 8);
    }

    @Test
    void testGenerateSearchNodes()
    {
        ContentCluster cluster = parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <redundancy>3</redundancy>" +
                        "  <documents/>" +
                        "  <engine>" +
                        "    <proton/>" +
                        "  </engine>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "    <node distribution-key=\"1\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "</content>"
        );

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageCluster().getConfig(builder);
            cluster.getStorageCluster().getChildren().get("0").getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
        }

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageCluster().getConfig(builder);
            cluster.getStorageCluster().getChildren().get("1").getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
        }
    }

    @Test
    void testAlternativeNodeSyntax()
    {
        ContentCluster cluster = parse(
                "<content version=\"1.0\" id=\"test\">\n" +
                        "  <redundancy>3</redundancy>" +
                        "  <documents/>" +
                        "  <engine>" +
                        "    <proton/>" +
                        "  </engine>" +
                        "  <nodes>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "    <node distribution-key=\"1\" hostalias=\"mockhost\"/>\n" +
                        "  </nodes>\n" +
                        "</content>"
        );

        DistributionConfig.Builder bob = new DistributionConfig.Builder();
        cluster.getConfig(bob);
        DistributionConfig.Cluster.Group group = bob.build().cluster("test").group(0);
        assertEquals("invalid", group.name());
        assertEquals("invalid", group.index());
        assertEquals(2, group.nodes().size());

        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();

        cluster.getConfig(builder);

        StorDistributionConfig config = new StorDistributionConfig(builder);

        assertEquals("invalid", config.group(0).name());
        assertEquals("invalid", config.group(0).index());
        assertEquals(2, config.group(0).nodes().size());
    }

    @Test
    void testReadyWhenInitialOne() {
        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();
        parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <documents/>" +
                        "  <redundancy>1</redundancy>\n" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                        "  </group>" +
                        "</content>"
        ).getConfig(builder);

        StorDistributionConfig config = new StorDistributionConfig(builder);
        assertEquals(1, config.initial_redundancy());
        assertEquals(1, config.redundancy());
        assertEquals(1, config.ready_copies());
    }

    public void testProvider(String tagName, StorServerConfig.Persistence_provider.Type.Enum type) {
        ContentCluster cluster = parse(
            "<content version=\"1.0\" id=\"storage\">\n" +
            "  <documents/>" +
            "  <redundancy>3</redundancy>" +
            "  <engine>\n" +
            "    <" + tagName + "/>\n" +
            "  </engine>\n" +
            "  <group>\n" +
            "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
            "  </group>" +
            "</content>"
        );

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageCluster().getConfig(builder);
            cluster.getStorageCluster().getChildren().get("0").getConfig(builder);

            StorServerConfig config = new StorServerConfig(builder);

            assertEquals(type, config.persistence_provider().type());
        }

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getDistributorNodes().getConfig(builder);
            cluster.getDistributorNodes().getChildren().get("0").getConfig(builder);

            StorServerConfig config = new StorServerConfig(builder);

            assertEquals(type, config.persistence_provider().type());
        }
    }

    @Test
    void testProviders() {
        testProvider("proton", StorServerConfig.Persistence_provider.Type.RPC);
        testProvider("dummy", StorServerConfig.Persistence_provider.Type.DUMMY);
    }

    @Test
    void testMetrics() {
        MetricsmanagerConfig.Builder builder = new MetricsmanagerConfig.Builder();

        ContentCluster cluster = parse("<content version=\"1.0\" id=\"storage\">\n" +
                "  <redundancy>3</redundancy>" +
                "  <documents/>" +
                "  <group>\n" +
                "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                "  </group>\n" +
                "</content>"
        );
        cluster.getConfig(builder);

        MetricsmanagerConfig config = new MetricsmanagerConfig(builder);

        assertEquals(6, config.consumer().size());
        assertEquals("status", config.consumer(0).name());
        assertEquals("*", config.consumer(0).addedmetrics(0));
        assertEquals("partofsum", config.consumer(0).removedtags(0));

        assertEquals("log", config.consumer(1).name());
        assertEquals("logdefault", config.consumer(1).tags().get(0));
        assertEquals("loadtype", config.consumer(1).removedtags(0));

        assertEquals("yamas", config.consumer(2).name());
        assertEquals("yamasdefault", config.consumer(2).tags().get(0));
        assertEquals("loadtype", config.consumer(2).removedtags(0));

        assertEquals("health", config.consumer(3).name());

        assertEquals("statereporter", config.consumer(5).name());
        assertEquals("*", config.consumer(5).addedmetrics(0));
        assertEquals("thread", config.consumer(5).removedtags(0));
        assertEquals("partofsum", config.consumer(5).removedtags(1));
        assertEquals(0, config.consumer(5).tags().size());

        cluster.getStorageCluster().getConfig(builder);
        config = new MetricsmanagerConfig(builder);
        assertEquals(6, config.consumer().size());

        assertEquals("fleetcontroller", config.consumer(4).name());
        assertEquals(4, config.consumer(4).addedmetrics().size());
        assertEquals("vds.datastored.alldisks.docs", config.consumer(4).addedmetrics(0));
        assertEquals("vds.datastored.alldisks.bytes", config.consumer(4).addedmetrics(1));
        assertEquals("vds.datastored.alldisks.buckets", config.consumer(4).addedmetrics(2));
        assertEquals("vds.datastored.bucket_space.buckets_total", config.consumer(4).addedmetrics(3));
    }

    public MetricsmanagerConfig.Consumer getConsumer(String consumer, MetricsmanagerConfig config) {
        for (MetricsmanagerConfig.Consumer c : config.consumer()) {
            if (c.name().equals(consumer)) {
                return c;
            }
        }

        return null;
    }

    @Test
    void testConfiguredMetrics() {
        String xml = "" +
                "<services>" +
                "<content version=\"1.0\" id=\"storage\">\n" +
                "  <redundancy>1</redundancy>\n" +
                "  <documents>" +
                "   <document type=\"type1\" mode='index'/>\n" +
                "   <document type=\"type2\" mode='index'/>\n" +
                "  </documents>" +
                "  <group>\n" +
                "    <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "  </group>\n" +
                "</content>" +
                "<admin version=\"2.0\">" +
                "  <logserver hostalias=\"node0\"/>" +
                "  <adminserver hostalias=\"node0\"/>" +
                "</admin>" +
                "</services>";


        List<String> sds = ApplicationPackageUtils.generateSchemas("type1", "type2");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();

        {
            MetricsmanagerConfig.Builder builder = new MetricsmanagerConfig.Builder();
            model.getConfig(builder, "storage/storage/0");
            MetricsmanagerConfig config = new MetricsmanagerConfig(builder);

            String expected =
                    "[vds.filestor.allthreads.put\n" +
                            "vds.filestor.allthreads.get\n" +
                            "vds.filestor.allthreads.remove\n" +
                            "vds.filestor.allthreads.update\n" +
                            "vds.datastored.alldisks.docs\n" +
                            "vds.datastored.alldisks.bytes\n" +
                            "vds.filestor.queuesize\n" +
                            "vds.filestor.averagequeuewait\n" +
                            "vds.visitor.cv_queuewaittime\n" +
                            "vds.visitor.allthreads.averagequeuewait\n" +
                            "vds.visitor.allthreads.averagevisitorlifetime\n" +
                            "vds.visitor.allthreads.created]";
            String actual = getConsumer("log", config).addedmetrics().toString().replaceAll(", ", "\n");
            assertEquals(expected, actual);
            assertEquals("[logdefault]", getConsumer("log", config).tags().toString());
            expected =
                    "[vds.datastored.alldisks.docs\n" +
                            "vds.datastored.alldisks.bytes\n" +
                            "vds.datastored.alldisks.buckets\n" +
                            "vds.datastored.bucket_space.buckets_total]";
            actual = getConsumer("fleetcontroller", config).addedmetrics().toString().replaceAll(", ", "\n");
            assertEquals(expected, actual);
        }

        {
            MetricsmanagerConfig.Builder builder = new MetricsmanagerConfig.Builder();
            model.getConfig(builder, "storage/distributor/0");
            MetricsmanagerConfig config = new MetricsmanagerConfig(builder);

            assertEquals("[logdefault]", getConsumer("log", config).tags().toString());
        }
    }

    @Test
    void flush_on_shutdown_is_default_on_for_non_hosted() throws Exception {
        assertPrepareRestartCommand(createOneNodeCluster(false));
    }

    @Test
    void flush_on_shutdown_can_be_turned_off_for_non_hosted() throws Exception {
        assertNoPreShutdownCommand(createClusterWithFlushOnShutdownOverride(false, false));
    }

    @Test
    void flush_on_shutdown_is_default_on_for_hosted() throws Exception {
        assertPrepareRestartCommand(createOneNodeCluster(true));
    }

    @Test
    void flush_on_shutdown_can_be_turned_on_for_hosted() throws Exception {
        assertPrepareRestartCommand(createClusterWithFlushOnShutdownOverride(true, true));
    }

    private static String oneNodeClusterXml() {
        return "<content version=\"1.0\" id=\"mockcluster\">" +
                "  <redundancy>3</redundancy>" +
                "  <documents/>" +
                "  <group>" +
                "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</content>";
    }

    private static ContentCluster createOneNodeCluster(boolean isHostedVespa) throws Exception {
        return createOneNodeCluster(oneNodeClusterXml(), new TestProperties().setHostedVespa(isHostedVespa));
    }

    private static ContentCluster createOneNodeCluster(TestProperties props) throws Exception {
        return createOneNodeCluster(oneNodeClusterXml(), props);
    }

    private static ContentCluster createOneNodeCluster(TestProperties props, Optional<Flavor> flavor) throws Exception {
        return createOneNodeCluster(oneNodeClusterXml(), props, flavor);
    }

    private static ContentCluster createClusterWithFlushOnShutdownOverride(boolean flushOnShutdown, boolean isHostedVespa) throws Exception {
        return createOneNodeCluster("<content version=\"1.0\" id=\"mockcluster\">" +
                "  <redundancy>1</redundancy>" +
                "  <documents/>" +
                "  <engine>" +
                "    <proton>" +
                "      <flush-on-shutdown>" + flushOnShutdown + "</flush-on-shutdown>" +
                "    </proton>" +
                "  </engine>" +
                "  <group>" +
                "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</content>", new TestProperties().setHostedVespa(isHostedVespa));
    }

    private static ContentCluster createOneNodeCluster(String clusterXml, TestProperties props) throws Exception {
        return createOneNodeCluster(clusterXml, props, Optional.empty());
    }

    private static ContentCluster createOneNodeCluster(String clusterXml, TestProperties props, Optional<Flavor> flavor) throws Exception {
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .properties(props);
        MockRoot root = flavor.isPresent() ?
                ContentClusterUtils.createMockRoot(new SingleNodeProvisioner(flavor.get()),
                        Collections.emptyList(), deployStateBuilder) :
                ContentClusterUtils.createMockRoot(Collections.emptyList(), deployStateBuilder);
        ContentCluster cluster = ContentClusterUtils.createCluster(clusterXml, root);
        root.freezeModelTopology();
        cluster.validate();
        return cluster;
    }

    private static void assertPrepareRestartCommand(ContentCluster cluster) {
        Optional<String> command = cluster.getSearch().getSearchNodes().get(0).getPreShutdownCommand();
        assertTrue(command.isPresent());
        assertTrue(command.get().matches(".*vespa-proton-cmd [0-9]+ prepareRestart"));
    }

    private static void assertNoPreShutdownCommand(ContentCluster cluster) {
        Optional<String> command = cluster.getSearch().getSearchNodes().get(0).getPreShutdownCommand();
        assertFalse(command.isPresent());
    }

    @Test
    void reserved_document_name_throws_exception() {
        String xml = "<content version=\"1.0\" id=\"storage\">" +
                "  <redundancy>1</redundancy>" +
                "  <documents>" +
                "    <document type=\"true\" mode=\"index\"/>" +
                "  </documents>" +
                "  <group>" +
                "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</content>";

        List<String> sds = ApplicationPackageUtils.generateSchemas("true");
        try {
            new VespaModelCreatorWithMockPkg(null, xml, sds).create();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("The following document types conflict with reserved keyword names: 'true'."));
        }
    }

    private void assertClusterHasBucketSpaceMappings(AllClustersBucketSpacesConfig config, String clusterId,
                                                     List<String> defaultSpaceTypes, List<String> globalSpaceTypes) {
        AllClustersBucketSpacesConfig.Cluster cluster = config.cluster(clusterId);
        assertNotNull(cluster);
        assertEquals(defaultSpaceTypes.size() + globalSpaceTypes.size(), cluster.documentType().size());
        assertClusterHasTypesInBucketSpace(cluster, "default", defaultSpaceTypes);
        assertClusterHasTypesInBucketSpace(cluster, "global", globalSpaceTypes);
    }

    private void assertClusterHasTypesInBucketSpace(AllClustersBucketSpacesConfig.Cluster cluster,
                                                    String bucketSpace, List<String> expectedTypes) {
        for (String type : expectedTypes) {
            assertNotNull(cluster.documentType(type));
            assertEquals(bucketSpace, cluster.documentType(type).bucketSpace());
        }
    }

    private VespaModel createDualContentCluster() {
        String xml =
                "<services>" +
                        "<admin version=\"2.0\">" +
                        "  <adminserver hostalias=\"node0\"/>" +
                        "</admin>" +
                        "<content version=\"1.0\" id=\"foo_c\">" +
                        "  <redundancy>1</redundancy>" +
                        "  <documents>" +
                        "    <document type=\"bunnies\" mode=\"index\"/>" +
                        "    <document type=\"hares\" mode=\"index\"/>" +
                        "  </documents>" +
                        "  <group>" +
                        "    <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "  </group>" +
                        "</content>" +
                        "<content version=\"1.0\" id=\"bar_c\">" +
                        "  <redundancy>1</redundancy>" +
                        "  <documents>" +
                        "    <document type=\"rabbits\" mode=\"index\" global=\"true\"/>" +
                        "  </documents>" +
                        "  <group>" +
                        "    <node distribution-key=\"0\" hostalias=\"node0\"/>" +
                        "  </group>" +
                        "</content>" +
                        "</services>";
        List<String> sds = ApplicationPackageUtils.generateSchemas("bunnies", "hares", "rabbits");
        return new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();
    }

    @Test
    void all_clusters_bucket_spaces_config_contains_mappings_across_all_clusters() {
        VespaModel model = createDualContentCluster();
        AllClustersBucketSpacesConfig.Builder builder = new AllClustersBucketSpacesConfig.Builder();
        model.getConfig(builder, "client");
        AllClustersBucketSpacesConfig config = builder.build();

        assertEquals(2, config.cluster().size());

        assertClusterHasBucketSpaceMappings(config, "foo_c", Arrays.asList("bunnies", "hares"), Collections.emptyList());
        assertClusterHasBucketSpaceMappings(config, "bar_c", Collections.emptyList(), Collections.singletonList("rabbits"));
    }

    @Test
    void test_routing_with_multiple_clusters() {
        VespaModel model = createDualContentCluster();
        Routing routing = model.getRouting();
        assertNotNull(routing);
        assertEquals("[]", routing.getErrors().toString());
        assertEquals(1, routing.getProtocols().size());
        DocumentProtocol protocol = (DocumentProtocol) routing.getProtocols().get(0);
        RoutingTableSpec spec = protocol.getRoutingTableSpec();
        assertEquals(3, spec.getNumHops());
        assertEquals("docproc/cluster.bar_c.indexing/chain.indexing", spec.getHop(0).getName());
        assertEquals("docproc/cluster.foo_c.indexing/chain.indexing", spec.getHop(1).getName());
        assertEquals("indexing", spec.getHop(2).getName());

        assertEquals(10, spec.getNumRoutes());
        assertRoute(spec.getRoute(0), "bar_c", "[MessageType:bar_c]");
        assertRoute(spec.getRoute(1), "bar_c-direct", "[Content:cluster=bar_c]");
        assertRoute(spec.getRoute(2), "bar_c-index", "docproc/cluster.bar_c.indexing/chain.indexing", "[Content:cluster=bar_c]");
        assertRoute(spec.getRoute(3), "default", "indexing");
        assertRoute(spec.getRoute(4), "default-get", "indexing");
        assertRoute(spec.getRoute(5), "foo_c", "[MessageType:foo_c]");
        assertRoute(spec.getRoute(6), "foo_c-direct", "[Content:cluster=foo_c]");
        assertRoute(spec.getRoute(7), "foo_c-index", "docproc/cluster.foo_c.indexing/chain.indexing", "[Content:cluster=foo_c]");
        assertRoute(spec.getRoute(8), "storage/cluster.bar_c", "route:bar_c");
        assertRoute(spec.getRoute(9), "storage/cluster.foo_c", "route:foo_c");
    }

    private ContentCluster createWithZone(String clusterXml, Zone zone) throws Exception {
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .zone(zone)
                .properties(new TestProperties().setHostedVespa(true));
        List<String> schemas = SchemaBuilder.createSchemas("test");
        MockRoot root = ContentClusterUtils.createMockRoot(schemas, deployStateBuilder);
        ContentCluster cluster = ContentClusterUtils.createCluster(clusterXml, root);
        root.freezeModelTopology();
        cluster.validate();
        return cluster;
    }

    private void assertDistributionBitsInConfig(ContentCluster cluster, int distributionBits) {
        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        cluster.getConfig(builder);
        cluster.getClusterControllerConfig().getConfig(builder);
        FleetcontrollerConfig config = new FleetcontrollerConfig(builder);
        assertEquals(distributionBits, config.ideal_distribution_bits());

        StorDistributormanagerConfig.Builder sdBuilder = new StorDistributormanagerConfig.Builder();
        cluster.getConfig(sdBuilder);
        StorDistributormanagerConfig storDistributormanagerConfig = new StorDistributormanagerConfig(sdBuilder);
        assertEquals(distributionBits, storDistributormanagerConfig.minsplitcount());
    }

    private void verifyTopKProbabilityPropertiesControl() {
        VespaModel model = createEnd2EndOneNode(new TestProperties());

        ContentCluster cc = model.getContentClusters().get("storage");
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        cc.getSearch().getConfig(builder);

        DispatchConfig cfg = new DispatchConfig(builder);
        assertEquals(0.9999, cfg.topKProbability(), 0.0);
    }

    @Test
    void default_topKprobability_controlled_by_properties() {
        verifyTopKProbabilityPropertiesControl();
    }

    private void verifyQueryDispatchPolicy(String policy, DispatchConfig.DistributionPolicy.Enum expected) {
        TestProperties properties = new TestProperties();
        if (policy != null) {
            properties.setQueryDispatchPolicy(policy);
        }
        VespaModel model = createEnd2EndOneNode(properties);

        ContentCluster cc = model.getContentClusters().get("storage");
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        cc.getSearch().getConfig(builder);

        DispatchConfig cfg = new DispatchConfig(builder);
        assertEquals(expected, cfg.distributionPolicy());
    }

    @Test
    public void default_dispatch_controlled_by_properties() {
        verifyQueryDispatchPolicy(null, DispatchConfig.DistributionPolicy.ADAPTIVE);
        verifyQueryDispatchPolicy("adaptive", DispatchConfig.DistributionPolicy.ADAPTIVE);
        verifyQueryDispatchPolicy("round-robin", DispatchConfig.DistributionPolicy.ROUNDROBIN);
        verifyQueryDispatchPolicy("best-of-random-2", DispatchConfig.DistributionPolicy.BEST_OF_RANDOM_2);
        verifyQueryDispatchPolicy("latency-amortized-over-requests", DispatchConfig.DistributionPolicy.LATENCY_AMORTIZED_OVER_REQUESTS);
        verifyQueryDispatchPolicy("latency-amortized-over-time", DispatchConfig.DistributionPolicy.LATENCY_AMORTIZED_OVER_TIME);
        try {
            verifyQueryDispatchPolicy("unknown", DispatchConfig.DistributionPolicy.ADAPTIVE);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Unknown dispatch policy 'unknown'", e.getMessage());
        }
    }

    private void verifySummaryDecodeType(String policy, DispatchConfig.SummaryDecodePolicy.Enum expected) {
        TestProperties properties = new TestProperties();
        if (policy != null) {
            properties.setSummaryDecodePolicy(policy);
        }
        VespaModel model = createEnd2EndOneNode(properties);

        ContentCluster cc = model.getContentClusters().get("storage");
        DispatchConfig.Builder builder = new DispatchConfig.Builder();
        cc.getSearch().getConfig(builder);

        DispatchConfig cfg = new DispatchConfig(builder);
        assertEquals(expected, cfg.summaryDecodePolicy());
    }

    @Test
    public void verify_summary_decoding_controlled_by_properties() {
        verifySummaryDecodeType(null, DispatchConfig.SummaryDecodePolicy.EAGER);
        verifySummaryDecodeType("illegal-config", DispatchConfig.SummaryDecodePolicy.EAGER);
        verifySummaryDecodeType("eager", DispatchConfig.SummaryDecodePolicy.EAGER);
        verifySummaryDecodeType("ondemand", DispatchConfig.SummaryDecodePolicy.ONDEMAND);
        verifySummaryDecodeType("on-demand", DispatchConfig.SummaryDecodePolicy.ONDEMAND);
    }

    private int resolveMaxCompactBuffers(OptionalInt maxCompactBuffers) {
        TestProperties testProperties = new TestProperties();
        if (maxCompactBuffers.isPresent()) {
            testProperties.maxCompactBuffers(maxCompactBuffers.getAsInt());
        }
        VespaModel model = createEnd2EndOneNode(testProperties);
        ContentCluster cc = model.getContentClusters().get("storage");
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        cc.getSearch().getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        assertEquals(1, protonConfig.documentdb().size());
        return protonConfig.documentdb(0).allocation().max_compact_buffers();
    }

    @Test
    void default_max_compact_buffers_config_controlled_by_properties() {
        assertEquals(1, resolveMaxCompactBuffers(OptionalInt.empty()));
        assertEquals(2, resolveMaxCompactBuffers(OptionalInt.of(2)));
        assertEquals(7, resolveMaxCompactBuffers(OptionalInt.of(7)));
    }

    private long resolveMaxTLSSize(Optional<Flavor> flavor) throws Exception {
        TestProperties testProperties = new TestProperties();

        ContentCluster cc = createOneNodeCluster(testProperties, flavor);
        ProtonConfig.Builder protonBuilder = new ProtonConfig.Builder();
        cc.getSearch().getSearchNodes().get(0).getConfig(protonBuilder);
        ProtonConfig protonConfig = new ProtonConfig(protonBuilder);
        return protonConfig.flush().memory().maxtlssize();
    }

    @Test
    void verifyt_max_tls_size() throws Exception {
        var flavor = new Flavor(new FlavorsConfig.Flavor(new FlavorsConfig.Flavor.Builder().name("test").minDiskAvailableGb(100)));
        assertEquals(21474836480L, resolveMaxTLSSize(Optional.empty()));
        assertEquals(2147483648L, resolveMaxTLSSize(Optional.of(flavor)));
    }

    void assertZookeeperServerImplementation(String expectedClassName,
                                             ClusterControllerContainerCluster clusterControllerCluster) {
        for (ClusterControllerContainer c : clusterControllerCluster.getContainers()) {
            var builder = new ComponentsConfig.Builder();
            c.getConfig(builder);
            assertEquals(1, new ComponentsConfig(builder).components().stream()
                                                         .filter(component -> component.classId().equals(expectedClassName))
                                                         .count());
        }
    }

    private StorDistributormanagerConfig resolveStorDistributormanagerConfig(TestProperties props) throws Exception {
        var cc = createOneNodeCluster(props);

        var builder = new StorDistributormanagerConfig.Builder();
        cc.getDistributorNodes().getConfig(builder);

        return (new StorDistributormanagerConfig(builder));
    }

    private int resolveMaxInhibitedGroupsConfigWithFeatureFlag(int maxGroups) throws Exception {
        var cfg = resolveStorDistributormanagerConfig(new TestProperties().maxActivationInhibitedOutOfSyncGroups(maxGroups));
        return cfg.max_activation_inhibited_out_of_sync_groups();
    }

    @Test
    void default_distributor_max_inhibited_group_activation_config_controlled_by_properties() throws Exception {
        assertEquals(0, resolveMaxInhibitedGroupsConfigWithFeatureFlag(0));
        assertEquals(2, resolveMaxInhibitedGroupsConfigWithFeatureFlag(2));
    }

    private int resolveNumDistributorStripesConfig(Optional<Flavor> flavor) throws Exception {
        var cc = createOneNodeCluster(new TestProperties(), flavor);
        var builder = new StorDistributormanagerConfig.Builder();
        cc.getDistributorNodes().getChildren().get("0").getConfig(builder);
        return (new StorDistributormanagerConfig(builder)).num_distributor_stripes();
    }

    private int resolveTunedNumDistributorStripesConfig(int numCpuCores) throws Exception {
        var flavor = new Flavor(new FlavorsConfig.Flavor(new FlavorsConfig.Flavor.Builder().name("test").minCpuCores(numCpuCores)));
        return resolveNumDistributorStripesConfig(Optional.of(flavor));
    }

    @Test
    void num_distributor_stripes_config_defaults_to_zero() throws Exception {
        // This triggers tuning when starting the distributor process, based on CPU core sampling on the node.
        assertEquals(0, resolveNumDistributorStripesConfig(Optional.empty()));
    }

    @Test
    void num_distributor_stripes_config_tuned_by_flavor() throws Exception {
        assertEquals(1, resolveTunedNumDistributorStripesConfig(1));
        assertEquals(1, resolveTunedNumDistributorStripesConfig(16));
        assertEquals(2, resolveTunedNumDistributorStripesConfig(17));
        assertEquals(2, resolveTunedNumDistributorStripesConfig(64));
        assertEquals(4, resolveTunedNumDistributorStripesConfig(65));
    }

    @Test
    void testDedicatedClusterControllers() {
        VespaModel noContentModel = createEnd2EndOneNode(new TestProperties().setHostedVespa(true)
                        .setMultitenant(true),
                "<?xml version='1.0' encoding='UTF-8' ?>" +
                        "<services version='1.0'>" +
                        "  <container id='default' version='1.0' />" +
                        " </services>");
        assertEquals(Map.of(), noContentModel.getContentClusters());
        assertNull(noContentModel.getAdmin().getClusterControllers(), "No cluster controller without content");

        VespaModel oneContentModel = createEnd2EndOneNode(new TestProperties().setHostedVespa(true)
                        .setMultitenant(true),
                "<?xml version='1.0' encoding='UTF-8' ?>" +
                        "<services version='1.0'>" +
                        "  <container id='default' version='1.0' />" +
                        "  <content id='storage' version='1.0'>" +
                        "    <redundancy>1</redundancy>" +
                        "    <documents>" +
                        "      <document mode='index' type='type1' />" +
                        "    </documents>" +
                        "  </content>" +
                        " </services>");
        assertNotNull(oneContentModel.getAdmin().getClusterControllers(), "Shared cluster controller with content");

        String twoContentServices = "<?xml version='1.0' encoding='UTF-8' ?>" +
                "<services version='1.0'>" +
                "  <container id='default' version='1.0' />" +
                "  <content id='storage' version='1.0'>" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document mode='index' type='type1' />" +
                "    </documents>" +
                "    <tuning>" +
                "      <cluster-controller>" +
                "        <min-distributor-up-ratio>0.618</min-distributor-up-ratio>" +
                "      </cluster-controller>" +
                "    </tuning>" +
                "  </content>" +
                "  <content id='dev-null' version='1.0'>" +
                "    <redundancy>1</redundancy>" +
                "    <documents>" +
                "      <document mode='index' type='type1' />" +
                "    </documents>" +
                "    <tuning>" +
                "      <cluster-controller>" +
                "        <min-distributor-up-ratio>0.418</min-distributor-up-ratio>" +
                "      </cluster-controller>" +
                "    </tuning>" +
                "  </content>" +
                " </services>";
        VespaModel twoContentModel = createEnd2EndOneNode(new TestProperties().setHostedVespa(true)
                        .setMultitenant(true),
                twoContentServices);
        assertNotNull(twoContentModel.getAdmin().getClusterControllers(), "Shared cluster controller with content");

        ClusterControllerContainerCluster clusterControllers = twoContentModel.getAdmin().getClusterControllers();

        assertEquals(1, clusterControllers.reindexingContext().documentTypesForCluster("storage").size());
        assertEquals(1, clusterControllers.reindexingContext().documentTypesForCluster("dev-null").size());
        var storageBuilder = new FleetcontrollerConfig.Builder();
        var devNullBuilder = new FleetcontrollerConfig.Builder();
        twoContentModel.getConfig(storageBuilder, "admin/standalone/cluster-controllers/0/components/clustercontroller-storage-configurer");
        twoContentModel.getConfig(devNullBuilder, "admin/standalone/cluster-controllers/0/components/clustercontroller-dev-null-configurer");
        assertEquals(0.618, storageBuilder.build().min_distributor_up_ratio(), 1e-9);
        assertEquals(0.418, devNullBuilder.build().min_distributor_up_ratio(), 1e-9);

        assertZookeeperServerImplementation("com.yahoo.vespa.zookeeper.ReconfigurableVespaZooKeeperServer",
                clusterControllers);
        assertZookeeperServerImplementation("com.yahoo.vespa.zookeeper.Reconfigurer",
                clusterControllers);
        assertZookeeperServerImplementation("com.yahoo.vespa.zookeeper.VespaZooKeeperAdminImpl",
                clusterControllers);
    }

    @Test
    void testAllow2GroupsDown() {
        String services = "<?xml version='1.0' encoding='UTF-8' ?>" +
                "<services version='1.0'>" +
                "  <container id='default' version='1.0' />" +
                "  <content id='storage' version='1.0'>" +
                "    <redundancy>4</redundancy>" +
                "    <documents>" +
                "      <document mode='index' type='type1' />" +
                "    </documents>" +
                "  <group name='root'>" +
                "    <distribution partitions='1|1|1|*'/>" +
                "    <group name='g-1' distribution-key='0'>" +
                "      <node hostalias='mockhost' distribution-key='0'/>" +
                "    </group>" +
                "    <group name='g-2' distribution-key='1'>" +
                "      <node hostalias='mockhost' distribution-key='1'/>" +
                "    </group>" +
                "    <group name='g-3' distribution-key='2'>" +
                "      <node hostalias='mockhost' distribution-key='2'/>" +
                "    </group>" +
                "    <group name='g-4' distribution-key='3'>" +
                "      <node hostalias='mockhost' distribution-key='3'/>" +
                "    </group>" +
                "  </group>" +
                "    <tuning>" +
                "      <cluster-controller>" +
                "        <max-groups-allowed-down>2</max-groups-allowed-down>" +
                "      </cluster-controller>" +
                "    </tuning>" +
                "    <engine>" +
                "      <proton>" +
                "        <searchable-copies>4</searchable-copies>" +
                "      </proton>" +
                "    </engine>" +
                "  </content>" +
                " </services>";
        VespaModel model = createEnd2EndOneNode(new TestProperties().setAllowMoreThanOneContentGroupDown(true), services);

        var fleetControllerConfigBuilder = new FleetcontrollerConfig.Builder();
        model.getConfig(fleetControllerConfigBuilder, "admin/cluster-controllers/0/components/clustercontroller-storage-configurer");
        assertEquals(2, fleetControllerConfigBuilder.build().max_number_of_groups_allowed_to_be_down());
    }

}
