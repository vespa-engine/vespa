// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorIntegritycheckerConfig;
import com.yahoo.vespa.config.content.core.StorVisitorConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.PersistenceConfig;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.documentmodel.NewDocumentType;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

public class StorageClusterTest {

    StorageCluster parse(String xml, Flavor flavor) {
        MockRoot root = new MockRoot("", new DeployState.Builder()
                .applicationPackage(new MockApplicationPackage.Builder().build())
                .modelHostProvisioner(new SingleNodeProvisioner(flavor)).build());
        return parse(xml, root);
    }
    StorageCluster parse(String xml, Flavor flavor, ModelContext.Properties properties) {
        MockRoot root = new MockRoot("", new DeployState.Builder()
                .applicationPackage(new MockApplicationPackage.Builder().build())
                .modelHostProvisioner(new SingleNodeProvisioner(flavor))
                .properties(properties).build());
        return parse(xml, root);
    }

    StorageCluster parse(String xml, ModelContext.Properties properties) {
        MockRoot root = new MockRoot("",
                new DeployState.Builder()
                        .properties(properties)
                        .applicationPackage(new MockApplicationPackage.Builder().build())
                        .build());
        return parse(xml, root);
    }
    StorageCluster parse(String xml) {
        return parse(xml, new TestProperties());
    }
    StorageCluster parse(String xml, MockRoot root) {
        root.getDeployState().getDocumentModel().getDocumentManager().add(
                new NewDocumentType(new NewDocumentType.Name("music"))
        );
        root.getDeployState().getDocumentModel().getDocumentManager().add(
                new NewDocumentType(new NewDocumentType.Name("movies"))
        );
        ContentCluster cluster = ContentClusterUtils.createCluster(xml, root);

        root.freezeModelTopology();
        return cluster.getStorageCluster();
    }

    private static String group() {
        return joinLines(
                "<group>",
                "   <node distribution-key=\"0\" hostalias=\"mockhost\"/>",
                "</group>");
    }
    private static String cluster(String clusterName, String insert) {
        return joinLines(
                "<content id=\"" + clusterName + "\">",
                "<documents/>",
                insert,
                group(),
                "</content>");
    }

    @Test
    void testBasics() {
        StorageCluster storage = parse(cluster("foofighters", ""));

        assertEquals(1, storage.getChildren().size());
        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        storage.getConfig(builder);
        StorServerConfig config = new StorServerConfig(builder);
        assertFalse(config.is_distributor());
        assertEquals("foofighters", config.cluster_name());
        assertEquals(4, config.content_node_bucket_db_stripe_bits());
    }

    @Test
    void testCommunicationManagerDefaults() {
        StorageCluster storage = parse(cluster("foofighters", ""));
        StorCommunicationmanagerConfig.Builder builder = new StorCommunicationmanagerConfig.Builder();
        storage.getChildren().get("0").getConfig(builder);
        StorCommunicationmanagerConfig config = new StorCommunicationmanagerConfig(builder);
        assertEquals(1, config.mbus().num_network_threads());
    }

    @Test
    void testMergeDefaults() {
        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        parse(cluster("foofighters", "")).getConfig(builder);

        StorServerConfig config = new StorServerConfig(builder);
        assertEquals(16, config.max_merges_per_node());
        assertEquals(100, config.max_merge_queue_size());
        assertTrue(config.disable_queue_limits_for_chained_merges());
    }

    @Test
    void testMerges() {
        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        parse(cluster("foofighters", joinLines(
                "<tuning>",
                "  <merges max-per-node=\"1K\" max-queue-size=\"10K\"/>",
                "</tuning>"))).getConfig(builder);

        StorServerConfig config = new StorServerConfig(builder);
        assertEquals(1024, config.max_merges_per_node());
        assertEquals(1024 * 10, config.max_merge_queue_size());
    }

    private StorServerConfig configFromProperties(TestProperties properties) {
        StorServerConfig.Builder builder = new StorServerConfig.Builder();
        parse(cluster("foofighters", ""), properties).getConfig(builder);
        return new StorServerConfig(builder);
    }

    private StorCommunicationmanagerConfig communicationmanagerConfigFromProperties(TestProperties properties) {
        StorCommunicationmanagerConfig.Builder builder = new StorCommunicationmanagerConfig.Builder();
        parse(cluster("foofighters", ""), properties).getChildren().values().iterator().next().getConfig(builder);
        return new StorCommunicationmanagerConfig(builder);
    }

    private StorFilestorConfig filestorConfigFromProducer(StorFilestorConfig.Producer producer) {
        var builder = new StorFilestorConfig.Builder();
        producer.getConfig(builder);
        return new StorFilestorConfig(builder);
    }

    @Test
    void verifyDefaultMbusConfig() {
        var confg = communicationmanagerConfigFromProperties(new TestProperties());
        assertEquals(1, confg.mbus().num_network_threads());
        assertEquals(1, confg.mbus().num_rpc_targets());
        assertEquals(1, confg.mbus().events_before_wakeup());
        assertEquals(1, confg.rpc().num_targets_per_node());
        assertEquals(1, confg.rpc().events_before_wakeup());
    }

    @Test
    void verifyDefaultMbusConfigControl() {
        var confg = communicationmanagerConfigFromProperties(new TestProperties()
                .setMbusNetworkThreads(7)
                .setRpcNumTargets(11)
                .setRpcEventsBeforeWakeup(12)
                .setMbusCppRpcNumTargets(8)
                .setMbusCppEventsBeforeWakeup(9));
        assertEquals(7, confg.mbus().num_network_threads());
        assertEquals(8, confg.mbus().num_rpc_targets());
        assertEquals(9, confg.mbus().events_before_wakeup());
        assertEquals(11, confg.rpc().num_targets_per_node());
        assertEquals(12, confg.rpc().events_before_wakeup());
    }

    @Test
    void testMergeFeatureFlags() {
        var config = configFromProperties(new TestProperties());
        assertEquals(16, config.max_merges_per_node());
        assertEquals(100, config.max_merge_queue_size());
    }

    @Test
    void merge_throttling_policy_config_defaults_to_static() {
        var config = configFromProperties(new TestProperties());
        assertEquals(StorServerConfig.Merge_throttling_policy.Type.STATIC, config.merge_throttling_policy().type());
    }

    @Test
    void testVisitors() {
        StorVisitorConfig.Builder builder = new StorVisitorConfig.Builder();
        parse(cluster("bees",
                joinLines(
                        "<tuning>",
                        "  <visitors thread-count=\"7\" max-queue-size=\"1000\">",
                        "    <max-concurrent fixed=\"42\" variable=\"100\"/>",
                        "  </visitors>",
                        "</tuning>"))
        ).getConfig(builder);

        StorVisitorConfig config = new StorVisitorConfig(builder);
        assertEquals(42, config.maxconcurrentvisitors_fixed());
        assertEquals(100, config.maxconcurrentvisitors_variable());
        assertEquals(7, config.visitorthreads());
        assertEquals(1000, config.maxvisitorqueuesize());
    }

    @Test
    void testPersistenceThreads() {

        StorageCluster stc = parse(cluster("bees", joinLines(
                "<tuning>",
                "  <persistence-threads count=\"7\"/>",
                "</tuning>")),
                new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(9).build())
        );

        {
            var config = filestorConfigFromProducer(stc);

            assertEquals(7, config.num_threads());
            assertFalse(config.enable_multibit_split_optimalization());
            assertEquals(2, config.num_response_threads());
        }
        {
            assertEquals(1, stc.getChildren().size());
            StorageNode sn = stc.getChildren().values().iterator().next();
            var config = filestorConfigFromProducer(sn);
            assertEquals(7, config.num_threads());
        }
    }

    @Test
    void testResponseThreads() {

        StorageCluster stc = parse(cluster("bees", joinLines(
                "<tuning>",
                "  <persistence-threads count=\"7\"/>",
                "</tuning>")),
                new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(9).build())
        );
        var config = filestorConfigFromProducer(stc);
        assertEquals(2, config.num_response_threads());
        assertEquals(StorFilestorConfig.Response_sequencer_type.ADAPTIVE, config.response_sequencer_type());
        assertEquals(7, config.num_threads());
    }

    @Test
    void testPersistenceThreadsOld() {

        StorageCluster stc = parse(cluster("bees", joinLines(
                "<tuning>",
                "  <persistence-threads>",
                "    <thread lowest-priority=\"VERY_LOW\" count=\"2\"/>",
                "    <thread lowest-priority=\"VERY_HIGH\" count=\"1\"/>",
                "    <thread count=\"1\"/>",
                "  </persistence-threads>",
                "</tuning>")),
                new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(9).build())
        );

        {
            var config = filestorConfigFromProducer(stc);

            assertEquals(4, config.num_threads());
            assertFalse(config.enable_multibit_split_optimalization());
        }
        {
            assertEquals(1, stc.getChildren().size());
            StorageNode sn = stc.getChildren().values().iterator().next();
            var config = filestorConfigFromProducer(sn);
            assertEquals(4, config.num_threads());
        }
    }

    @Test
    void testNoPersistenceThreads() {
        StorageCluster stc = parse(cluster("bees", ""),
                new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(9).build())
        );

        {
            var config = filestorConfigFromProducer(stc);
            assertEquals(8, config.num_threads());
        }
        {
            assertEquals(1, stc.getChildren().size());
            StorageNode sn = stc.getChildren().values().iterator().next();
            var config = filestorConfigFromProducer(sn);
            assertEquals(9, config.num_threads());
        }
    }

    private StorageCluster simpleCluster(ModelContext.Properties properties) {
        return parse(cluster("bees", ""),
                new Flavor(new FlavorsConfig.Flavor.Builder().name("test-flavor").minCpuCores(9).build()),
                properties);
    }

    @Test
    void testFeatureFlagControlOfResponseSequencer() {
        var config = filestorConfigFromProducer(simpleCluster(new TestProperties().setResponseNumThreads(13).setResponseSequencerType("THROUGHPUT")));
        assertEquals(13, config.num_response_threads());
        assertEquals(StorFilestorConfig.Response_sequencer_type.THROUGHPUT, config.response_sequencer_type());
    }

    private void verifyAsyncMessageHandlingOnSchedule(boolean expected, boolean value) {
        var config = filestorConfigFromProducer(simpleCluster(new TestProperties().setAsyncMessageHandlingOnSchedule(value)));
        assertEquals(expected, config.use_async_message_handling_on_schedule());
    }

    @Test
    void testFeatureFlagControlOfAsyncMessageHandlingOnSchedule() {
        verifyAsyncMessageHandlingOnSchedule(false, false);
        verifyAsyncMessageHandlingOnSchedule(true, true);
    }

    @Test
    void persistence_dynamic_throttling_parameters_have_sane_defaults() {
        var config = filestorConfigFromProducer(simpleCluster(new TestProperties()));
        assertEquals(StorFilestorConfig.Async_operation_throttler.Type.DYNAMIC, config.async_operation_throttler().type());
        assertEquals(1.2, config.async_operation_throttler().window_size_decrement_factor(), 0.0001);
        assertEquals(0.95, config.async_operation_throttler().window_size_backoff(), 0.0001);
        assertEquals(20, config.async_operation_throttler().min_window_size());
        assertEquals(-1, config.async_operation_throttler().max_window_size()); // <=0 implies +inf
        assertEquals(3.0, config.async_operation_throttler().resize_rate(), 0.0001);
        assertTrue(config.async_operation_throttler().throttle_individual_merge_feed_ops());
    }

    @Test
    void integrity_checker_explicitly_disabled_when_not_running_with_vds_provider() {
        StorIntegritycheckerConfig.Builder builder = new StorIntegritycheckerConfig.Builder();
        parse(cluster("bees", "")).getConfig(builder);
        StorIntegritycheckerConfig config = new StorIntegritycheckerConfig(builder);
        // '-' --> don't run on the given week day
        assertEquals("-------", config.weeklycycle());
    }

    @Test
    void testCapacity() {
        String xml = joinLines(
                "<cluster id=\"storage\">",
                "  <documents/>",
                "  <group>",
                "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>",
                "    <node distribution-key=\"1\" hostalias=\"mockhost\" capacity=\"1.5\"/>",
                "    <node distribution-key=\"2\" hostalias=\"mockhost\" capacity=\"2.0\"/>",
                "  </group>",
                "</cluster>");

        ContentCluster cluster = ContentClusterUtils.createCluster(xml, new MockRoot());

        for (int i = 0; i < 3; ++i) {
            StorageNode node = cluster.getStorageCluster().getChildren().get("" + i);
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageCluster().getConfig(builder);
            node.getConfig(builder);
            StorServerConfig config = new StorServerConfig(builder);
            assertEquals(1.0 + (double) i * 0.5, config.node_capacity(), 0.001);
        }
    }

    @Test
    void testRootFolder() {
        ContentCluster cluster = ContentClusterUtils.createCluster(cluster("storage", ""), new MockRoot());

        StorageNode node = cluster.getStorageCluster().getChildren().get("0");

        {
            StorServerConfig.Builder builder = new StorServerConfig.Builder();
            cluster.getStorageCluster().getConfig(builder);
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
    void testGenericPersistenceTuning() {
        String xml = joinLines(
                "<cluster id=\"storage\">",
                "  <documents/>",
                "  <engine>",
                "    <fail-partition-on-error>true</fail-partition-on-error>",
                "    <revert-time>34m</revert-time>",
                "    <recovery-time>5d</recovery-time>",
                "  </engine>",
                "  <group>",
                "    node distribution-key=\"0\" hostalias=\"mockhost\"/>",
                "  </group>",
                "</cluster>");

        ContentCluster cluster = ContentClusterUtils.createCluster(xml, new MockRoot());

        PersistenceConfig.Builder builder = new PersistenceConfig.Builder();
        cluster.getStorageCluster().getConfig(builder);

        PersistenceConfig config = new PersistenceConfig(builder);
        assertTrue(config.fail_partition_on_error());
        assertEquals(34 * 60, config.revert_time_period());
        assertEquals(5 * 24 * 60 * 60, config.keep_remove_time_period());
    }

    @Test
    void requireThatUserDoesNotSpecifyBothGroupAndNodes() {
        String xml = joinLines(
                "<cluster id=\"storage\">",
                "  <documents/>",
                "  <engine>",
                "    <fail-partition-on-error>true</fail-partition-on-error>",
                "    <revert-time>34m</revert-time>",
                "    <recovery-time>5d</recovery-time>",
                "  </engine>",
                "  <group>",
                "    <node distribution-key=\"0\" hostalias=\"mockhost\"/>",
                "  </group>",
                "  <nodes>",
                "    <node distribution-key=\"1\" hostalias=\"mockhost\"/>",
                "  </nodes>",
                "</cluster>");

        try {
            final MockRoot root = new MockRoot();
            root.getDeployState().getDocumentModel().getDocumentManager().add(
                    new NewDocumentType(new NewDocumentType.Name("music"))
            );
            ContentClusterUtils.createCluster(xml, root);
            fail("Did not fail when having both group and nodes");
        } catch (RuntimeException e) {
            assertEquals("Both <group> and <nodes> is specified: Only one of these tags can be used in the same configuration",
                    e.getMessage());
        }
    }

    @Test
    void requireThatGroupNamesMustBeUniqueAmongstSiblings() {
        String xml = joinLines(
                "<cluster id=\"storage\">",
                "  <redundancy>2</redundancy>",
                "  <documents/>",
                "  <group>",
                "    <distribution partitions=\"*\"/>",
                "    <group distribution-key=\"0\" name=\"bar\">",
                "      <node distribution-key=\"0\" hostalias=\"mockhost\"/>",
                "    </group>",
                "    <group distribution-key=\"0\" name=\"bar\">",
                "      <node distribution-key=\"1\" hostalias=\"mockhost\"/>",
                "    </group>",
                "  </group>",
                "</cluster>");

        try {
            ContentClusterUtils.createCluster(xml, new MockRoot());
            fail("Did not get exception with duplicate group names");
        } catch (RuntimeException e) {
            assertEquals("Cluster 'storage' has multiple groups with name 'bar' in the same subgroup. " +
                    "Group sibling names must be unique.", e.getMessage());
        }
    }

    @Test
    void requireThatGroupNamesCanBeDuplicatedAcrossLevels() {
        String xml = joinLines(
                "<cluster id=\"storage\">",
                "  <redundancy>2</redundancy>",
                "  <documents/>",
                "  <group>",
                "    <distribution partitions=\"*\"/>",
                "    <group distribution-key=\"0\" name=\"bar\">",
                "      <group distribution-key=\"0\" name=\"foo\">",
                "        <node distribution-key=\"0\" hostalias=\"mockhost\"/>",
                "      </group>",
                "    </group>",
                "    <group distribution-key=\"0\" name=\"foo\">",
                "      <group distribution-key=\"0\" name=\"bar\">",
                "        <node distribution-key=\"1\" hostalias=\"mockhost\"/>",
                "      </group>",
                "    </group>",
                "  </group>",
                "</cluster>");

        // Should not throw.
        ContentClusterUtils.createCluster(xml, new MockRoot());
    }

    @Test
    void requireThatNestedGroupsRequireDistribution() {
        String xml = joinLines(
                "<cluster id=\"storage\">",
                "  <documents/>",
                "  <group>",
                "    <group distribution-key=\"0\" name=\"bar\">",
                "      <node distribution-key=\"0\" hostalias=\"mockhost\"/>",
                "    </group>",
                "    <group distribution-key=\"0\" name=\"baz\">",
                "      <node distribution-key=\"1\" hostalias=\"mockhost\"/>",
                "    </group>",
                "  </group>",
                "</cluster>");

        try {
            ContentClusterUtils.createCluster(xml, new MockRoot());
            fail("Did not get exception with missing distribution element");
        } catch (RuntimeException e) {
            assertEquals("'distribution' attribute is required with multiple subgroups", e.getMessage());
        }
    }
}
