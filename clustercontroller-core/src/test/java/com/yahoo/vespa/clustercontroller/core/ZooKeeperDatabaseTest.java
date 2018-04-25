// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.database.Database;
import com.yahoo.vespa.clustercontroller.core.database.ZooKeeperDatabase;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class ZooKeeperDatabaseTest {

    private static class Fixture implements AutoCloseable {
        final ZooKeeperTestServer zkServer;
        ClusterFixture clusterFixture;
        ZooKeeperDatabase zkDatabase;
        int nodeIndex = 1;
        Duration sessionTimeout = Duration.ofSeconds(60);
        Database.DatabaseListener mockListener = mock(Database.DatabaseListener.class);

        Fixture() throws IOException {
            zkServer = new ZooKeeperTestServer();
            clusterFixture = ClusterFixture.forFlatCluster(10);
        }

        void createDatabase() throws Exception {
            closeDatabaseIfOpen();
            zkDatabase = new ZooKeeperDatabase(clusterFixture.cluster(), nodeIndex, zkServer.getAddress(),
                    (int)sessionTimeout.toMillis(), mockListener);
        }

        ZooKeeperDatabase db() { return zkDatabase; }

        void closeDatabaseIfOpen() {
            if (zkDatabase != null) {
                zkDatabase.close();
                zkDatabase = null;
            }
        }

        @Override
        public void close() {
            closeDatabaseIfOpen();
            zkServer.shutdown(true);
        }
    }

    @Test
    public void can_store_and_load_cluster_state_bundle_from_database() throws Exception {
        try (Fixture f = new Fixture()) {
            f.createDatabase();
            ClusterStateBundle bundleToStore = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2",
                    StateMapping.of("default", "distributor:2 storage:2 .0.s:d"),
                    StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2"));
            f.db().storeLastPublishedStateBundle(bundleToStore);

            ClusterStateBundle bundleReceived = f.db().retrieveLastPublishedStateBundle();
            assertThat(bundleReceived, equalTo(bundleToStore));
        }
    }

    @Test
    public void empty_state_bundle_is_returned_if_no_bundle_already_stored_in_database() throws Exception {
        try (Fixture f = new Fixture()) {
            f.createDatabase();
            ClusterStateBundle bundleReceived = f.db().retrieveLastPublishedStateBundle();

            assertThat(bundleReceived, equalTo(ClusterStateBundle.empty()));
        }
    }

}
