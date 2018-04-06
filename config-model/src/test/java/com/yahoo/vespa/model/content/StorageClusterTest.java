// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.core.StorIntegritycheckerConfig;
import com.yahoo.vespa.config.content.core.StorVisitorConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.PersistenceConfig;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.documentmodel.NewDocumentType;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class StorageClusterTest {

    StorageCluster parse(String xml) throws Exception {
        MockRoot root = new MockRoot();
        root.getDeployState().getDocumentModel().getDocumentManager().add(
                new NewDocumentType(new NewDocumentType.Name("music"))
        );
        root.getDeployState().getDocumentModel().getDocumentManager().add(
                new NewDocumentType(new NewDocumentType.Name("movies"))
        );
        ContentCluster cluster = ContentClusterUtils.createCluster(xml, root);

        root.freezeModelTopology();
        return cluster.getStorageNodes();
    }

    @Test
    public void testBasics() throws Exception {
        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        parse("<content id=\"foofighters\"><documents/>\n" +
              "  <group>" +
              "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
              "  </group>" +
              "</content>\n").
              getConfig(builder);

        StorServerConfig config = new StorServerConfig(builder);
        assertEquals(false, config.is_distributor());
        assertEquals("foofighters", config.cluster_name());
    }

    @Test
    public void testMerges() throws Exception {
        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        parse("" +
                "<content id=\"foofighters\">\n" +
                "  <documents/>" +
                "  <tuning>" +
                "    <merges max-per-node=\"1K\" max-queue-size=\"10K\"/>\n" +
                "  </tuning>" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</content>"
        ).getConfig(builder);

        StorServerConfig config = new StorServerConfig(builder);
        assertEquals(1024, config.max_merges_per_node());
        assertEquals(1024*10, config.max_merge_queue_size());
    }

    @Test
    public void testVisitors() throws Exception {
        StorVisitorConfig.Builder builder = new StorVisitorConfig.Builder();
        parse(
                "<cluster id=\"bees\">\n" +
                "  <documents/>" +
                "  <tuning>\n" +
                "    <visitors thread-count=\"7\" max-queue-size=\"1000\">\n" +
                "      <max-concurrent fixed=\"42\" variable=\"100\"/>\n" +
                "    </visitors>\n" +
                "  </tuning>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>"
        ).getConfig(builder);

        StorVisitorConfig config = new StorVisitorConfig(builder);
        assertEquals(42, config.maxconcurrentvisitors_fixed());
        assertEquals(100, config.maxconcurrentvisitors_variable());
        assertEquals(7, config.visitorthreads());
        assertEquals(1000, config.maxvisitorqueuesize());
    }

    @Test
    public void testPersistenceThreads() throws Exception {
        StorFilestorConfig.Builder builder = new StorFilestorConfig.Builder();
        parse(
                "<cluster id=\"bees\">\n" +
                "    <documents/>" +
                "    <tuning>\n" +
                "        <persistence-threads>\n" +
                "            <thread lowest-priority=\"VERY_LOW\" count=\"2\"/>\n" +
                "            <thread lowest-priority=\"VERY_HIGH\" count=\"1\"/>\n" +
                "            <thread count=\"1\"/>\n" +
                "        </persistence-threads>\n" +
                "    </tuning>\n" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>"
        ).getConfig(builder);

        StorFilestorConfig config = new StorFilestorConfig(builder);

        assertEquals(4, config.num_threads());
        assertEquals(false, config.enable_multibit_split_optimalization());
    }

    @Test
    public void testNoPersistenceThreads() throws Exception {
        StorFilestorConfig.Builder builder = new StorFilestorConfig.Builder();
        parse(
                "<cluster id=\"bees\">\n" +
                        "    <documents/>" +
                        "    <tuning>\n" +
                        "    </tuning>\n" +
                        "  <group>" +
                        "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                        "  </group>" +
                        "</cluster>"
        ).getConfig(builder);

        StorFilestorConfig config = new StorFilestorConfig(builder);

        assertEquals(6, config.num_threads());
    }

    @Test
    public void integrity_checker_explicitly_disabled_when_not_running_with_vds_provider() throws Exception {
        StorIntegritycheckerConfig.Builder builder = new StorIntegritycheckerConfig.Builder();
        parse(
                "<cluster id=\"bees\">\n" +
                "  <documents/>" +
                "  <group>" +
                "     <node distribution-key=\"0\" hostalias=\"mockhost\"/>" +
                "  </group>" +
                "</cluster>"
        ).getConfig(builder);
        StorIntegritycheckerConfig config = new StorIntegritycheckerConfig(builder);
        // '-' --> don't run on the given week day
        assertEquals("-------", config.weeklycycle());
    }

    @Test
    public void testCapacity() throws Exception {
        String xml =
                "<cluster id=\"storage\">\n" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "    <node distribution-key=\"1\" hostalias=\"mockhost\" capacity=\"1.5\"/>\n" +
                        "    <node distribution-key=\"2\" hostalias=\"mockhost\" capacity=\"2.0\"/>\n" +
                        "  </group>\n" +
                        "</cluster>";

        ContentCluster cluster = ContentClusterUtils.createCluster(xml, new MockRoot());

        for (int i = 0; i < 3; ++i) {
            StorageNode node = cluster.getStorageNodes().getChildren().get("" + i);
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageNodes().getConfig(builder);
            node.getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
            assertEquals(1.0 + (double)i * 0.5, config.node_capacity(), 0.001);
        }
    }

    @Test
    public void testRootFolder() throws Exception {
        String xml =
                "<cluster id=\"storage\">\n" +
                        "  <documents/>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "</cluster>";

        ContentCluster cluster = ContentClusterUtils.createCluster(xml, new MockRoot());

        StorageNode node = cluster.getStorageNodes().getChildren().get("0");

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageNodes().getConfig(builder);
            node.getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
            assertEquals(getDefaults().underVespaHome("var/db/vespa/search/storage/storage/0"), config.root_folder());
        }

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getDistributorNodes().getConfig(builder);
            cluster.getDistributorNodes().getChildren().get("0").getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
            assertEquals(getDefaults().underVespaHome("var/db/vespa/search/storage/distributor/0"), config.root_folder());
        }
    }

    @Test
    public void testGenericPersistenceTuning() throws Exception {
        String xml =
                "<cluster id=\"storage\">\n" +
                        "<documents/>" +
                        "<engine>\n" +
                        "    <fail-partition-on-error>true</fail-partition-on-error>\n" +
                        "    <revert-time>34m</revert-time>\n" +
                        "    <recovery-time>5d</recovery-time>\n" +
                        "</engine>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "</cluster>";

        ContentCluster cluster = ContentClusterUtils.createCluster(xml, new MockRoot());

        PersistenceConfig.Builder builder = new PersistenceConfig.Builder();
        cluster.getStorageNodes().getConfig(builder);

        PersistenceConfig config = new PersistenceConfig(builder);
        assertEquals(true, config.fail_partition_on_error());
        assertEquals(34 * 60, config.revert_time_period());
        assertEquals(5 * 24 * 60 * 60, config.keep_remove_time_period());
    }

    @Test
    public void requireThatUserDoesNotSpecifyBothGroupAndNodes() throws Exception {
        String xml =
                "<cluster id=\"storage\">\n" +
                        "<documents/>\n" +
                        "<engine>\n" +
                        "    <fail-partition-on-error>true</fail-partition-on-error>\n" +
                        "    <revert-time>34m</revert-time>\n" +
                        "    <recovery-time>5d</recovery-time>\n" +
                        "</engine>" +
                        "  <group>\n" +
                        "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "  </group>\n" +
                        "  <nodes>\n" +
                        "    <node distribution-key=\"1\" hostalias=\"mockhost\"/>\n" +
                        "  </nodes>\n" +
                        "</cluster>";

        try {
            final MockRoot root = new MockRoot();
            root.getDeployState().getDocumentModel().getDocumentManager().add(
                    new NewDocumentType(new NewDocumentType.Name("music"))
            );
            ContentClusterUtils.createCluster(xml, root);
            fail("Did not fail when having both group and nodes");
        } catch (RuntimeException e) {
            e.printStackTrace();
            assertEquals("Both group and nodes exists, only one of these tags is legal", e.getMessage());
        }
    }

    @Test
    public void requireThatGroupNamesMustBeUniqueAmongstSiblings() throws Exception {
        String xml =
                "<cluster id=\"storage\">\n" +
                "<documents/>\n" +
                "  <group>\n" +
                "    <distribution partitions=\"*\"/>\n" +
                "    <group distribution-key=\"0\" name=\"bar\">\n" +
                "      <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                "    </group>\n" +
                "    <group distribution-key=\"0\" name=\"bar\">\n" +
                "      <node distribution-key=\"1\" hostalias=\"mockhost\"/>\n" +
                "    </group>\n" +
                "  </group>\n" +
                "</cluster>";

        try {
            ContentClusterUtils.createCluster(xml, new MockRoot());
            fail("Did not get exception with duplicate group names");
        } catch (RuntimeException e) {
            assertEquals("Cluster 'storage' has multiple groups with name 'bar' in the same subgroup. " +
                         "Group sibling names must be unique.", e.getMessage());
        }
    }

    @Test
    public void requireThatGroupNamesCanBeDuplicatedAcrossLevels() throws Exception {
        String xml =
                "<cluster id=\"storage\">\n" +
                "<documents/>\n" +
                "  <group>\n" +
                "    <distribution partitions=\"*\"/>\n" +
                "    <group distribution-key=\"0\" name=\"bar\">\n" +
                "      <group distribution-key=\"0\" name=\"foo\">\n" +
                "        <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                "      </group>\n" +
                "    </group>\n" +
                "    <group distribution-key=\"0\" name=\"foo\">\n" +
                "      <group distribution-key=\"0\" name=\"bar\">\n" +
                "        <node distribution-key=\"1\" hostalias=\"mockhost\"/>\n" +
                "      </group>\n" +
                "    </group>\n" +
                "  </group>\n" +
                "</cluster>";

        // Should not throw.
        ContentClusterUtils.createCluster(xml, new MockRoot());
    }

    @Test
    public void requireThatNestedGroupsRequireDistribution() throws Exception {
        String xml =
                "<cluster id=\"storage\">\n" +
                        "<documents/>\n" +
                        "  <group>\n" +
                        "    <group distribution-key=\"0\" name=\"bar\">\n" +
                        "      <node distribution-key=\"0\" hostalias=\"mockhost\"/>\n" +
                        "    </group>\n" +
                        "    <group distribution-key=\"0\" name=\"baz\">\n" +
                        "      <node distribution-key=\"1\" hostalias=\"mockhost\"/>\n" +
                        "    </group>\n" +
                        "  </group>\n" +
                        "</cluster>";

        try {
            ContentClusterUtils.createCluster(xml, new MockRoot());
            fail("Did not get exception with missing distribution element");
        } catch (RuntimeException e) {
            assertEquals("'distribution' attribute is required with multiple subgroups", e.getMessage());
        }
    }
}
