// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for storage groups.
 */
public class StorageGroupTest {

    ContentCluster parse(String xml) {
        return ContentClusterUtils.createCluster(xml, new MockRoot());
    }

    @Test
    void testSingleGroup() throws Exception {
        ContentCluster cluster = parse(
                "<content id=\"storage\">\n" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <node hostalias=\"mockhost\" distribution-key=\"0\"/>\n" +
                        "    <node hostalias=\"mockhost\" distribution-key=\"1\"/>\n" +
                        "  </group>\n" +
                        "</content>"
        );

        assertEquals("content", cluster.getStorageCluster().getChildren().get("0").getServicePropertyString("clustertype"));
        assertEquals("storage", cluster.getStorageCluster().getChildren().get("0").getServicePropertyString("clustername"));
        assertEquals("0", cluster.getStorageCluster().getChildren().get("0").getServicePropertyString("index"));

        assertEquals("content", cluster.getDistributorNodes().getChildren().get("0").getServicePropertyString("clustertype"));
        assertEquals("storage", cluster.getDistributorNodes().getChildren().get("0").getServicePropertyString("clustername"));
        assertEquals("0", cluster.getDistributorNodes().getChildren().get("0").getServicePropertyString("index"));

        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();
        cluster.getConfig(builder);
        StorDistributionConfig config = new StorDistributionConfig(builder);

        assertEquals(1, config.group().size());
        assertEquals("invalid", config.group(0).index());
        assertEquals("invalid", config.group(0).name());
        assertEquals(2, config.group(0).nodes().size());
        assertEquals(0, config.group(0).nodes(0).index());
        assertEquals(1, config.group(0).nodes(1).index());
        //assertNotNull(cluster.getRootGroup().getNodes().get(0).getHost());

        DistributionConfig.Builder distributionBuilder = new DistributionConfig.Builder();
        cluster.getConfig(distributionBuilder);
        DistributionConfig.Cluster clusterConfig = distributionBuilder.build().cluster("storage");

        assertEquals(1, clusterConfig.group().size());
        assertEquals("invalid", clusterConfig.group(0).index());
        assertEquals("invalid", clusterConfig.group(0).name());
        assertEquals(2, clusterConfig.group(0).nodes().size());
        assertEquals(0, clusterConfig.group(0).nodes(0).index());
        assertEquals(1, clusterConfig.group(0).nodes(1).index());
    }

    @Test
    void testNestedGroupsNoDistribution() {
        try {
            parse(
                    "<content version=\"1.0\" id=\"storage\">\n" +
                            "  <group distribution-key=\"0\" name=\"base\">\n" +
                            "    <group distribution-key=\"0\" name=\"sub1\">\n" +
                            "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>\n" +
                            "      <node hostalias=\"mockhost\" distribution-key=\"1\"/>\n" +
                            "    </group>\n" +
                            "    <group distribution-key=\"1\" name=\"sub2\">\n" +
                            "      <node hostalias=\"mockhost\" distribution-key=\"2\"/>\n" +
                            "      <node hostalias=\"mockhost\" distribution-key=\"3\"/>\n" +
                            "    </group>\n" +
                            "  </group>\n" +
                            "</cluster>"
            );
            fail();
        } catch (Exception e) {
        }
    }

    @Test
    void testNestedGroups() throws Exception {
        ContentCluster cluster = parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <redundancy>4</redundancy>" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <distribution partitions=\"1|*\"/>\n" +
                        "    <group distribution-key=\"0\" name=\"sub1\">\n" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"0\"/>\n" +
                        "      <node hostalias=\"mockhost\" distribution-key=\"1\"/>\n" +
                        "    </group>\n" +
                        "    <group distribution-key=\"1\" name=\"sub2\">\n" +
                        "      <distribution partitions=\"1|*\"/>\n" +
                        "      <group distribution-key=\"0\" name=\"sub3\">\n" +
                        "        <node hostalias=\"mockhost\" distribution-key=\"2\"/>\n" +
                        "        <node hostalias=\"mockhost\" distribution-key=\"3\"/>\n" +
                        "      </group>\n" +
                        "      <group distribution-key=\"1\" name=\"sub4\">\n" +
                        "        <node hostalias=\"mockhost\" distribution-key=\"4\"/>\n" +
                        "        <node hostalias=\"mockhost\" distribution-key=\"5\"/>\n" +
                        "      </group>\n" +
                        "    </group>\n" +
                        "  </group>\n" +
                        "</content>"
        );

        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();
        cluster.getConfig(builder);
        StorDistributionConfig config = new StorDistributionConfig(builder);

        assertEquals(5, config.group().size());
        assertEquals("invalid", config.group(0).index());
        assertEquals("0", config.group(1).index());
        assertEquals("1", config.group(2).index());
        assertEquals("1.0", config.group(3).index());
        assertEquals("1.1", config.group(4).index());
        assertEquals("invalid", config.group(0).name());
        assertEquals("sub1", config.group(1).name());
        assertEquals("sub2", config.group(2).name());
        assertEquals("sub3", config.group(3).name());
        assertEquals("sub4", config.group(4).name());
        assertEquals(2, config.group(1).nodes().size());
        assertEquals(0, config.group(1).nodes(0).index());
        assertEquals(1, config.group(1).nodes(1).index());
        assertEquals(0, config.group(2).nodes().size());
        assertEquals(2, config.group(3).nodes().size());
        assertEquals(2, config.group(3).nodes(0).index());
        assertEquals(3, config.group(3).nodes(1).index());
        assertEquals(2, config.group(4).nodes().size());
        assertEquals(4, config.group(4).nodes(0).index());
        assertEquals(5, config.group(4).nodes(1).index());

        assertEquals("1|*", config.group(0).partitions());

        DistributionConfig.Builder distributionBuilder = new DistributionConfig.Builder();
        cluster.getConfig(distributionBuilder);
        DistributionConfig.Cluster clusterConfig = distributionBuilder.build().cluster("storage");

        assertEquals(5, clusterConfig.group().size());
        assertEquals("invalid", clusterConfig.group(0).index());
        assertEquals("0", clusterConfig.group(1).index());
        assertEquals("1", clusterConfig.group(2).index());
        assertEquals("1.0", clusterConfig.group(3).index());
        assertEquals("1.1", clusterConfig.group(4).index());
        assertEquals("invalid", clusterConfig.group(0).name());
        assertEquals("sub1", clusterConfig.group(1).name());
        assertEquals("sub2", clusterConfig.group(2).name());
        assertEquals("sub3", clusterConfig.group(3).name());
        assertEquals("sub4", clusterConfig.group(4).name());
        assertEquals(2, clusterConfig.group(1).nodes().size());
        assertEquals(0, clusterConfig.group(1).nodes(0).index());
        assertEquals(1, clusterConfig.group(1).nodes(1).index());
        assertEquals(0, clusterConfig.group(2).nodes().size());
        assertEquals(2, clusterConfig.group(3).nodes().size());
        assertEquals(2, clusterConfig.group(3).nodes(0).index());
        assertEquals(3, clusterConfig.group(3).nodes(1).index());
        assertEquals(2, clusterConfig.group(4).nodes().size());
        assertEquals(4, clusterConfig.group(4).nodes(0).index());
        assertEquals(5, clusterConfig.group(4).nodes(1).index());

        assertEquals("1|*", clusterConfig.group(0).partitions());
    }

    @Test
    void testGroupCapacity() throws Exception {
        ContentCluster cluster = parse(
                "<content version=\"1.0\" id=\"storage\">\n" +
                        "  <redundancy>2</redundancy>" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <distribution partitions=\"1|*\"/>\n" +
                        "    <group distribution-key=\"0\" name=\"sub1\">\n" +
                        "      <node hostalias=\"mockhost\" capacity=\"0.5\" distribution-key=\"0\"/>\n" +
                        "      <node hostalias=\"mockhost\" capacity=\"1.5\" distribution-key=\"1\"/>\n" +
                        "    </group>\n" +
                        "    <group distribution-key=\"1\" name=\"sub2\">\n" +
                        "      <node hostalias=\"mockhost\" capacity=\"2.0\" distribution-key=\"2\"/>\n" +
                        "      <node hostalias=\"mockhost\" capacity=\"1.5\" distribution-key=\"3\"/>\n" +
                        "    </group>\n" +
                        "  </group>\n" +
                        "</content>"
        );

        StorDistributionConfig.Builder builder = new StorDistributionConfig.Builder();
        cluster.getConfig(builder);
        StorDistributionConfig config = new StorDistributionConfig(builder);

        assertEquals(3, config.group().size());
        assertEquals(5.5, config.group(0).capacity(), 0.001);
        assertEquals(2, config.group(1).capacity(), 0.001);
        assertEquals(3.5, config.group(2).capacity(), 0.001);

        DistributionConfig.Builder distributionBuilder = new DistributionConfig.Builder();
        cluster.getConfig(distributionBuilder);
        DistributionConfig.Cluster clusterConfig = distributionBuilder.build().cluster("storage");

        assertEquals(3, clusterConfig.group().size());
        assertEquals(5.5, clusterConfig.group(0).capacity(), 0.001);
        assertEquals(2, clusterConfig.group(1).capacity(), 0.001);
        assertEquals(3.5, clusterConfig.group(2).capacity(), 0.001);
    }

}
