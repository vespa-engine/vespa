// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.text.XML;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FleetControllerClusterTest {

    private ClusterControllerConfig parse(String xml, TestProperties props) {
        Document doc = XML.getDocument(xml);
        var deployState = new DeployState.Builder().properties(props).build();
        MockRoot root = new MockRoot("", deployState);
        var clusterElement = new ModelElement(doc.getDocumentElement());
        return new ClusterControllerConfig.Builder("storage",
                                                   clusterElement,
                                                   new ClusterResourceLimits.Builder(false,
                                                                                     props.resourceLimitDisk(),
                                                                                     props.resourceLimitMemory())
                                                           .build(clusterElement).getClusterControllerLimits(),
                                                   props.allowMoreThanOneContentGroupDown(new ClusterSpec.Id("default")))
                .build(root.getDeployState(), root, clusterElement.getXml());
    }

    private ClusterControllerConfig parse(String xml) {
        return parse(xml, new TestProperties());
    }

    @Test
    void testParameters() {
        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        parse("""
                      <cluster id="storage">
                        <documents/>  <tuning>
                          <bucket-splitting minimum-bits="7" />
                          <cluster-controller>
                            <init-progress-time>13</init-progress-time>
                            <transition-time>27</transition-time>
                            <max-premature-crashes>4</max-premature-crashes>
                            <stable-state-period>72</stable-state-period>
                            <min-distributor-up-ratio>0.7</min-distributor-up-ratio>
                            <min-storage-up-ratio>0.3</min-storage-up-ratio>
                          </cluster-controller>
                        </tuning>
                      </cluster>""",
              new TestProperties().setAllowMoreThanOneContentGroupDown(true)).
                getConfig(builder);

        FleetcontrollerConfig config = new FleetcontrollerConfig(builder);
        assertEquals(13 * 1000, config.init_progress_time());
        assertEquals(27 * 1000, config.storage_transition_time());
        assertEquals(4, config.max_premature_crashes());
        assertEquals(72 * 1000, config.stable_state_time_period());
        assertEquals(0.7, config.min_distributor_up_ratio(), 0.01);
        assertEquals(0.3, config.min_storage_up_ratio(), 0.01);
        assertEquals(7, config.ideal_distribution_bits());
    }

    @Test
    void testDurationParameters() {
        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents/>" +
                "  <tuning>\n" +
                "    <cluster-controller>\n" +
                "      <init-progress-time>13ms</init-progress-time>\n" +
                "    </cluster-controller>\n" +
                "  </tuning>\n" +
                "</cluster>").
                getConfig(builder);

        FleetcontrollerConfig config = new FleetcontrollerConfig(builder);
        assertEquals(13, config.init_progress_time());
    }

    @Test
    void min_node_ratio_per_group_tuning_config_is_propagated() {
        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents/>\n" +
                "  <tuning>\n" +
                "    <min-node-ratio-per-group>0.75</min-node-ratio-per-group>\n" +
                "  </tuning>\n" +
                "</cluster>").
                getConfig(builder);

        FleetcontrollerConfig config = new FleetcontrollerConfig(builder);
        assertEquals(0.75, config.min_node_ratio_per_group(), 0.01);
    }

    @Test
    void min_node_ratio_per_group_is_implicitly_zero_when_omitted() {
        var config = getConfigForBasicCluster();
        assertEquals(0.0, config.min_node_ratio_per_group(), 0.01);
    }

    @Test
    void default_cluster_feed_block_limits_are_set() {
        assertLimits(0.75, 0.8, getConfigForBasicCluster());
    }

    @Test
    void resource_limits_can_be_set_in_tuning() {
        assertLimits(0.6, 0.7, getConfigForResourceLimitsTuning(0.6, 0.7));
        assertLimits(0.6, 0.8, getConfigForResourceLimitsTuning(0.6, null));
        assertLimits(0.75, 0.7, getConfigForResourceLimitsTuning(null, 0.7));
    }

    private static final double DELTA = 0.00001;

    private void assertLimits(double expDisk, double expMemory, FleetcontrollerConfig config) {
        var limits = config.cluster_feed_block_limit();
        assertEquals(3, limits.size());
        assertEquals(expDisk, limits.get("disk"), DELTA);
        assertEquals(expMemory, limits.get("memory"), DELTA);
        assertEquals(0.9, limits.get("attribute-address-space"), DELTA);
    }

    private FleetcontrollerConfig getConfigForResourceLimitsTuning(Double diskLimit, Double memoryLimit) {
        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        parse(joinLines("<cluster id=\"test\">",
                "<documents/>",
                "<tuning>",
                "  <resource-limits>",
                (diskLimit != null ? ("    <disk>" + diskLimit + "</disk>") : ""),
                (memoryLimit != null ? ("    <memory>" + memoryLimit + "</memory>") : ""),
                "  </resource-limits>",
                "</tuning>" +
                "</cluster>")).
                getConfig(builder);
        return new FleetcontrollerConfig(builder);
    }

    @Test
    void feature_flag_controls_min_node_ratio_per_group() {
        verifyFeatureFlagControlsMinNodeRatioPerGroup(0.0, new TestProperties());
        verifyFeatureFlagControlsMinNodeRatioPerGroup(0.3,
                new TestProperties().setMinNodeRatioPerGroup(0.3));
    }

    private void verifyFeatureFlagControlsMinNodeRatioPerGroup(double expRatio, TestProperties props) {
        var config = getConfigForBasicCluster(props);
        assertEquals(expRatio, config.min_node_ratio_per_group(), DELTA);
    }

    private FleetcontrollerConfig getConfigForBasicCluster(TestProperties props) {
        var builder = new FleetcontrollerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents/>\n" +
                "</cluster>", props).
                getConfig(builder);
        return new FleetcontrollerConfig(builder);
    }

    private FleetcontrollerConfig getConfigForBasicCluster() {
        return getConfigForBasicCluster(new TestProperties());
    }
}
