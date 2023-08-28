// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FleetControllerClusterTest {

    private FleetcontrollerConfig parse(String xml, TestProperties props) {
        var deployStateBuilder = new DeployState.Builder().properties(props);
        var mockPkg = new VespaModelCreatorWithMockPkg(null, xml, ApplicationPackageUtils.generateSchemas("type1"));
        var model = mockPkg.create(deployStateBuilder);
        var builder = new FleetcontrollerConfig.Builder();
        model.getConfig(builder, "admin/cluster-controllers/0/components/clustercontroller-storage-configurer");
        return builder.build();
    }

    private FleetcontrollerConfig parse(String xml) {
        return parse(xml, new TestProperties());
    }

    @Test
    void testParameters() {
        var config = parse("""
                      <content id="storage" version="1.0">
                        <documents>
                          <document type="type1" mode="index"/>
                        </documents>
                        <redundancy>2</redundancy>
                        <tuning>
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
                      </content>""",
              new TestProperties());

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
        var config = parse(
                "<content id='storage' version='1.0'>\n" +
                        "<documents>\n" +
                        "  <document type='type1' mode='index'/>" +
                        "</documents>\n" +
                        "<redundancy>2</redundancy>\n" +
                        "  <tuning>\n" +
                        "    <cluster-controller>\n" +
                        "      <init-progress-time>13ms</init-progress-time>\n" +
                        "    </cluster-controller>\n" +
                        "  </tuning>\n" +
                        "</content>");

        assertEquals(13, config.init_progress_time());
    }

    @Test
    void min_node_ratio_per_group_tuning_config_is_propagated() {
        var config = parse("<content id='storage' version='1.0'>" +
                                   "<documents>" +
                                   "<document type='type1' mode='index'/>" +
                                   "</documents>" +
                                   "<redundancy>2</redundancy>" +
                                   "  <tuning>\n" +
                                   "    <min-node-ratio-per-group>0.75</min-node-ratio-per-group>\n" +
                                   "  </tuning>\n" +
                                   "</content>");

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
        return parse(joinLines("<content id='storage' version='1.0'>" +
                                       "<documents>" +
                                       "<document type='type1' mode='index'/>" +
                                       "</documents>" +
                                       "<redundancy>2</redundancy>" +
                                       "<tuning>",
                               "  <resource-limits>",
                               (diskLimit != null ? ("    <disk>" + diskLimit + "</disk>") : ""),
                               (memoryLimit != null ? ("    <memory>" + memoryLimit + "</memory>") : ""),
                               "  </resource-limits>",
                               "</tuning>" +
                                       "</content>"));
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
        return parse("<content id='storage' version='1.0'>" +
                "<documents>" +
                "<document type='type1' mode='index'/>" +
                "</documents>" +
                "<redundancy>2</redundancy>" +
                "</content>", props);
    }

    private FleetcontrollerConfig getConfigForBasicCluster() {
        return getConfigForBasicCluster(new TestProperties());
    }
}
