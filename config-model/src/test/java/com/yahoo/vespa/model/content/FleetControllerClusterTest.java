// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import org.junit.Test;
import org.w3c.dom.Document;

import static org.junit.Assert.assertEquals;

public class FleetControllerClusterTest {
    ClusterControllerConfig parse(String xml) {
        Document doc = XML.getDocument(xml);
        MockRoot root = new MockRoot();
        return new ClusterControllerConfig.Builder("storage", new ModelElement(doc.getDocumentElement())).build(root.getDeployState(), root,
                new ModelElement(doc.getDocumentElement()).getXml());
    }

    @Test
    public void testParameters() {
        FleetcontrollerConfig.Builder builder = new FleetcontrollerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents/>" +
                "  <tuning>\n" +
                "    <bucket-splitting minimum-bits=\"7\" />" +
                "    <cluster-controller>\n" +
                "      <init-progress-time>13</init-progress-time>\n" +
                "      <transition-time>27</transition-time>\n" +
                "      <max-premature-crashes>4</max-premature-crashes>\n" +
                "      <stable-state-period>72</stable-state-period>\n" +
                "      <min-distributor-up-ratio>0.7</min-distributor-up-ratio>\n" +
                "      <min-storage-up-ratio>0.3</min-storage-up-ratio>\n" +
                "    </cluster-controller>\n" +
                "  </tuning>\n" +
                "</cluster>").
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
    public void testDurationParameters() {
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
    public void min_node_ratio_per_group_tuning_config_is_propagated() {
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
    public void min_node_ratio_per_group_is_implicitly_zero_when_omitted() {
        var config = getConfigForBasicCluster();
        assertEquals(0.0, config.min_node_ratio_per_group(), 0.01);
    }

    @Test
    public void default_cluster_feed_block_limits_are_set() {
        var config = getConfigForBasicCluster();
        var limits = config.cluster_feed_block_limit();
        assertEquals(4, limits.size());
        assertEquals(0.79, limits.get("memory"), 0.0001);
        assertEquals(0.79, limits.get("disk"), 0.0001);
        assertEquals(0.89, limits.get("attribute-enum-store"), 0.0001);
        assertEquals(0.89, limits.get("attribute-multi-value"), 0.0001);
    }

    FleetcontrollerConfig getConfigForBasicCluster() {
        var builder = new FleetcontrollerConfig.Builder();
        parse("<cluster id=\"storage\">\n" +
                "  <documents/>\n" +
                "</cluster>").
                getConfig(builder);
        return new FleetcontrollerConfig(builder);
    }
}
