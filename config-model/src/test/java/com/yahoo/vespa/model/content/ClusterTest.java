// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.model.test.TestRoot;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.engines.ProtonEngine;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.content.utils.SearchDefinitionBuilder;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

// TODO Rename to ContentClusterTest
public class ClusterTest extends ContentBaseTest {

    private final static String HOSTS = "<admin version='2.0'><adminserver hostalias='mockhost' /></admin>";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    ContentCluster parse(String xml) {
        xml = HOSTS + xml;
        TestRoot root = new TestDriver().buildModel(xml);
        return root.getConfigModels(Content.class).get(0).getCluster();
    }

    @Test
    public void testHierarchicRedundancy() {
        ContentCluster cc = parse("" +
            "<content version=\"1.0\" id=\"storage\">\n" +
            "  <documents/>" +
            "  <engine>" +
            "    <proton>" +
            "      <searchable-copies>3</searchable-copies>" +
            "    </proton>" +
            "  </engine>" +
            "  <redundancy>15</redundancy>\n" +
            "  <group name='root' distribution-key='0'>" +
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
    public void testRedundancy() {
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
    public void testNoId() {
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
    public void testRedundancyDefaults() {
        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();
        parse(
            "<content version=\"1.0\" id=\"storage\">\n" +
            "  <documents/>" +
            "  <group>" +
            "    <node hostalias=\"mockhost\" distribution-key=\"0\"/>\"" +
            "    <node hostalias=\"mockhost\" distribution-key=\"1\"/>\"" +
            "    <node hostalias=\"mockhost\" distribution-key=\"2\"/>\"" +
            "  </group>" +
            "</content>"
        ).getConfig(builder);

        StorDistributionConfig config = new StorDistributionConfig(builder);
        assertEquals(2, config.initial_redundancy());
        assertEquals(3, config.redundancy());
        assertEquals(2, config.ready_copies());
    }

    @Test
    public void testEndToEnd() throws Exception {
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

        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("type1", "type2");
        VespaModel model = (new VespaModelCreatorWithMockPkg(null, xml, sds)).create();
        assertEquals(2, model.getContentClusters().get("bar").getDocumentDefinitions().size());
        ContainerCluster cluster = model.getAdmin().getClusterControllers();
        assertEquals(3, cluster.getContainers().size());
    }

    @Test
    public void testEndToEndOneNode() throws Exception {
        String services =
        "<?xml version='1.0' encoding='UTF-8' ?>" +
        "<services version='1.0'>" +
        "  <admin version='2.0'>" +
        "    <adminserver hostalias='node1'/>" +
        "  </admin>"  +
        "   <jdisc id='default' version='1.0'>" +
        "     <search/>" +
        "     <nodes>" +
        "       <node hostalias='node1'/>" +
        "     </nodes>" +
        "   </jdisc>" +
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
        "       <document mode='store-only' type='type1'/>" +
        "     </documents>" +
        "     <engine>" +
        "       <proton/>" +
        "     </engine>" +
        "   </content>" +
        " </services>";

        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("type1");
        VespaModel model = (new VespaModelCreatorWithMockPkg(null, services, sds)).create();
        assertEquals(1, model.getContentClusters().get("storage").getDocumentDefinitions().size());
        ContainerCluster cluster = model.getAdmin().getClusterControllers();
        assertEquals(1, cluster.getContainers().size());
    }

    @Test
    public void testSearchTuning() throws Exception {
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

        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("type1", "type2");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();

        assertTrue(model.getContentClusters().get("bar").getPersistence() instanceof ProtonEngine.Factory);

        {
            StorDistributormanagerConfig.Builder builder = new StorDistributormanagerConfig.Builder();
            model.getConfig(builder, "bar/distributor/0");
            StorDistributormanagerConfig config = new StorDistributormanagerConfig(builder);
            assertEquals(false, config.inlinebucketsplitting());
        }

        {
            StorFilestorConfig.Builder builder = new StorFilestorConfig.Builder();
            model.getConfig(builder, "bar/storage/0");
            StorFilestorConfig config = new StorFilestorConfig(builder);
            assertEquals(false, config.enable_multibit_split_optimalization());
        }
    }

    @Test
    public void testRedundancyRequired() throws Exception {
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

        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("type1", "type2");
        try{
            new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();
            assertTrue("Deploying without redundancy should fail", false);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("missing required element \"redundancy\""));
        }
    }

    @Test
    public void testRedundancyFinalLessThanInitial() {
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
        } catch (Exception e) {
        }
    }

    @Test
    public void testReadyTooHigh() {
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
        } catch (Exception e) {
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
    public void testFleetControllerOverride()
    {
        {
            FleetcontrollerConfig config = getFleetControllerConfig(
                "<content version=\"1.0\" id=\"storage\">\n" +
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
    public void testImplicitDistributionBits()
    {
        ContentCluster cluster = parse(
            "<content version=\"1.0\" id=\"storage\">\n" +
            "  <documents/>" +
            "  <group>\n" +
            "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
            "  </group>\n" +
            "</content>"
        );

        assertDistributionBitsInConfig(cluster, 8);

        cluster = parse(
            "<content version=\"1.0\" id=\"storage\">\n" +
            "  <documents/>" +
            "  <engine>" +
            "    <vds/>" +
            "  </engine>" +
            "  <group>\n" +
            "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
            "  </group>\n" +
            "</content>"
        );

        assertDistributionBitsInConfig(cluster, 8);
    }

    @Test
    public void testExplicitDistributionBits()
    {
        ContentCluster cluster = parse(
            "<content version=\"1.0\" id=\"storage\">\n" +
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
            "  <documents/>" +
            "  <engine>" +
            "    <vds/>" +
            "  </engine>" +
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
    public void testZoneDependentDistributionBits() throws Exception {
        String xml = new ContentClusterBuilder().docTypes("test").getXml();

        ContentCluster prodWith16Bits = createWithZone(xml, new Zone(Environment.prod, RegionName.from("us-east-3")));
        assertDistributionBitsInConfig(prodWith16Bits, 16);

        ContentCluster stagingNot16Bits = createWithZone(xml, new Zone(Environment.staging, RegionName.from("us-east-3")));
        assertDistributionBitsInConfig(stagingNot16Bits, 8);
    }
    @Test
    public void testGenerateSearchNodes()
    {
        ContentCluster cluster = parse(
            "<content version=\"1.0\" id=\"storage\">\n" +
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
            cluster.getStorageNodes().getConfig(builder);
            cluster.getStorageNodes().getChildren().get("0").getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
        }

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageNodes().getConfig(builder);
            cluster.getStorageNodes().getChildren().get("1").getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
        }
    }

    @Test
    public void testAlternativeNodeSyntax()
    {
        ContentCluster cluster = parse(
            "<content version=\"1.0\" id=\"test\">\n" +
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

        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();

        cluster.getConfig(builder);

        StorDistributionConfig config = new StorDistributionConfig(builder);

        assertEquals("invalid", config.group(0).name());
        assertEquals("invalid", config.group(0).index());
        assertEquals(2, config.group(0).nodes().size());
    }

    @Test
    public void testReadyWhenInitialOne() {
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
            cluster.getStorageNodes().getConfig(builder);
            cluster.getStorageNodes().getChildren().get("0").getConfig(builder);

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
    public void testProviders() {
        testProvider("proton", StorServerConfig.Persistence_provider.Type.RPC);
        testProvider("dummy", StorServerConfig.Persistence_provider.Type.DUMMY);
    }

    @Test
    public void testMetrics() {
        MetricsmanagerConfig.Builder builder = new MetricsmanagerConfig.Builder();

        ContentCluster cluster = parse("<content version=\"1.0\" id=\"storage\">\n" +
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
        assertEquals("disk", config.consumer(5).tags(0));

        cluster.getStorageNodes().getConfig(builder);
        config = new MetricsmanagerConfig(builder);
        assertEquals(6, config.consumer().size());

        assertEquals("fleetcontroller", config.consumer(4).name());
        assertEquals(3, config.consumer(4).addedmetrics().size());
        assertEquals("vds.datastored.alldisks.docs", config.consumer(4).addedmetrics(0));
        assertEquals("vds.datastored.alldisks.bytes", config.consumer(4).addedmetrics(1));
        assertEquals("vds.datastored.alldisks.buckets", config.consumer(4).addedmetrics(2));
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
    public void testConfiguredMetrics() throws Exception {
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
            "  <metric-consumers>" +
            "    <consumer name=\"foobar\">" +
            "      <metric name=\"storage.foo.bar\"/>" +
            "    </consumer>" +
            "    <consumer name=\"log\">" +
            "      <metric name=\"extralogmetric\"/>" +
            "      <metric name=\"extralogmetric3\"/>" +
            "    </consumer>" +
            "    <consumer name=\"fleetcontroller\">" +
            "      <metric name=\"extraextra\"/>" +
            "    </consumer>" +
            "  </metric-consumers>" +
            "</admin>" +
            "</services>";


        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("type1", "type2");
        VespaModel model = new VespaModelCreatorWithMockPkg(getHosts(), xml, sds).create();

        {
            MetricsmanagerConfig.Builder builder = new MetricsmanagerConfig.Builder();
            model.getConfig(builder, "storage/storage/0");
            MetricsmanagerConfig config = new MetricsmanagerConfig(builder);

            assertEquals("[storage.foo.bar]", getConsumer("foobar", config).addedmetrics().toString());
            String expected =
                    "[extralogmetric\n" +
                    "extralogmetric3\n" +
                    "vds.filestor.alldisks.allthreads.put.sum\n" +
                    "vds.filestor.alldisks.allthreads.get.sum\n" +
                    "vds.filestor.alldisks.allthreads.remove.sum\n" +
                    "vds.filestor.alldisks.allthreads.update.sum\n" +
                    "vds.datastored.alldisks.docs\n" +
                    "vds.datastored.alldisks.bytes\n" +
                    "vds.filestor.alldisks.queuesize\n" +
                    "vds.filestor.alldisks.averagequeuewait.sum\n" +
                    "vds.visitor.cv_queuewaittime\n" +
                    "vds.visitor.allthreads.averagequeuewait\n" +
                    "vds.visitor.allthreads.averagevisitorlifetime\n" +
                    "vds.visitor.allthreads.created.sum]";
            String actual = getConsumer("log", config).addedmetrics().toString().replaceAll(", ", "\n");
            assertEquals(expected, actual);
            assertEquals("[logdefault]", getConsumer("log", config).tags().toString());
            expected =
                    "[extraextra\n" +
                    "vds.datastored.alldisks.docs\n" +
                    "vds.datastored.alldisks.bytes\n" +
                    "vds.datastored.alldisks.buckets]";
            actual = getConsumer("fleetcontroller", config).addedmetrics().toString().replaceAll(", ", "\n");
            assertEquals(expected, actual);
        }

        {
            MetricsmanagerConfig.Builder builder = new MetricsmanagerConfig.Builder();
            model.getConfig(builder, "storage/distributor/0");
            MetricsmanagerConfig config = new MetricsmanagerConfig(builder);

            assertEquals("[storage.foo.bar]", getConsumer("foobar", config).addedmetrics().toString());
            assertEquals("[extralogmetric, extralogmetric3, vds.distributor.docsstored, vds.distributor.bytesstored, vds.idealstate.delete_bucket.done_ok, vds.idealstate.merge_bucket.done_ok, vds.idealstate.split_bucket.done_ok, vds.idealstate.join_bucket.done_ok, vds.idealstate.buckets_rechecking]", getConsumer("log", config).addedmetrics().toString());
            assertEquals("[logdefault]", getConsumer("log", config).tags().toString());
            assertEquals("[extraextra]", getConsumer("fleetcontroller", config).addedmetrics().toString());
        }
    }

    @Test
    public void requireThatPreShutdownCommandIsSet() {
        ContentCluster cluster = parse(
            "<content version=\"1.0\" id=\"storage\">" +
            "  <documents/>" +
            "  <engine>" +
            "    <proton>" +
            "      <flush-on-shutdown>true</flush-on-shutdown>" +
            "    </proton>" +
            "  </engine>" +
            "  <group>" +
            "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
            "  </group>" +
            "</content>");
        assertThat(cluster.getSearch().getSearchNodes().size(), is(1));
        assertTrue(cluster.getSearch().getSearchNodes().get(0).getPreShutdownCommand().isPresent());

        cluster = parse(
            "<content version=\"1.0\" id=\"storage\">" +
            "  <documents/>" +
            "  <engine>" +
            "    <proton>" +
            "      <flush-on-shutdown>  \n " +
            "  true  </flush-on-shutdown>" +
            "    </proton>" +
            "  </engine>" +
            "  <group>" +
            "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
            "  </group>" +
            "</content>");
        assertThat(cluster.getSearch().getSearchNodes().size(), is(1));
        assertTrue(cluster.getSearch().getSearchNodes().get(0).getPreShutdownCommand().isPresent());
    }

    @Test
    public void reserved_document_name_throws_exception() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("The following document types conflict with reserved keyword names: 'true'.");

        String xml = "<content version=\"1.0\" id=\"storage\">" +
              "  <redundancy>1</redundancy>" +
              "  <documents>" +
              "    <document type=\"true\" mode=\"index\"/>" +
              "  </documents>" +
              "  <group>" +
              "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
              "  </group>" +
              "</content>";

        List<String> sds = ApplicationPackageUtils.generateSearchDefinitions("true");
        new VespaModelCreatorWithMockPkg(null, xml, sds).create();
    }

    private ContentCluster createWithZone(String clusterXml, Zone zone) throws Exception {
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                .zone(zone)
                .properties(new DeployProperties.Builder()
                                    .hostedVespa(true)
                                    .build());
        List<String> searchDefinitions = SearchDefinitionBuilder.createSearchDefinitions("test");
        MockRoot root = ContentClusterUtils.createMockRoot(searchDefinitions, deployStateBuilder);
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

}
