// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.storage.test;

import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Tests storage model
 *
 *
 * @author gjoranv
 */
public class StorageModelTestCase {

    @Test(expected=RuntimeException.class)
    public void testTwoClustersSameName() {
        createModel("src/test/cfg/storage/twoclusterssamename");
    }

    private VespaModel createModel(String filename) {
        return new VespaModelCreatorWithFilePkg(filename).create();
    }

    @Test
    public void testIndexGreaterThanNumNodes() {
        VespaModel vespaModel = createModel("src/test/cfg/storage/app_index_higher_than_num_nodes");

        // Test fleet controller config
        FleetcontrollerConfig fleetController1Config = new FleetcontrollerConfig((FleetcontrollerConfig.Builder)
                vespaModel.getConfig(new FleetcontrollerConfig.Builder(), "content/fleetcontroller"));

        assertEquals(60000, fleetController1Config.storage_transition_time());
        assertEquals(8, fleetController1Config.ideal_distribution_bits());
    }

    @Test
    public void testMetricsSnapshotIntervalYAMAS() {
        VespaModel vespaModel = createModel("src/test/cfg/storage/clustercontroller_advanced");
        ContentCluster contentCluster = vespaModel.getContentClusters().values().iterator().next();
        assertNotNull(contentCluster);
        MetricsmanagerConfig.Builder builder = new MetricsmanagerConfig.Builder();
        contentCluster.getConfig(builder);
        MetricsmanagerConfig config = new MetricsmanagerConfig(builder);
        assertThat(config.snapshot().periods(0), is(60));
    }

}
