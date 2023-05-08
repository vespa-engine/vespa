// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.database.CasWriteFailed;
import com.yahoo.vespa.clustercontroller.core.database.Database;
import com.yahoo.vespa.clustercontroller.core.database.ZooKeeperDatabase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
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
            var id = new FleetControllerId(clusterFixture.cluster.getName(), nodeIndex);
            var context = new TestFleetControllerContext(id);
            zkDatabase = new ZooKeeperDatabase(context, zkServer.getAddress(),
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
    void can_store_and_load_cluster_state_bundle_from_database() throws Exception {
        try (Fixture f = new Fixture()) {
            f.createDatabase();
            f.db().retrieveLastPublishedStateBundle(); // Must be called once prior to prime last known znode version
            ClusterStateBundle bundleToStore = dummyBundle();
            f.db().storeLastPublishedStateBundle(bundleToStore);

            ClusterStateBundle bundleReceived = f.db().retrieveLastPublishedStateBundle();
            assertEquals(bundleToStore, bundleReceived);
        }
    }

    private static ClusterStateBundle dummyBundle() {
        return ClusterStateBundleUtil.makeBundle("distributor:2 storage:2",
                StateMapping.of("default", "distributor:2 storage:2 .0.s:d"),
                StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2"));
    }

    @Test
    void storing_cluster_state_bundle_with_mismatching_expected_znode_version_throws_exception() {
        Throwable exception = assertThrows(CasWriteFailed.class, () -> {
            try (Fixture f = new Fixture()) {
                f.createDatabase();
                f.db().storeLastPublishedStateBundle(dummyBundle());
            }
        });
        assertTrue(exception.getMessage().contains("version mismatch in cluster state bundle znode (expected -2)"));
    }

    @Test
    void storing_cluster_state_version_with_mismatching_expected_znode_version_throws_exception() {
        Throwable exception = assertThrows(CasWriteFailed.class, () -> {
            try (Fixture f = new Fixture()) {
                f.createDatabase();
                f.db().storeLatestSystemStateVersion(12345);
            }
        });
        assertTrue(exception.getMessage().contains("version mismatch in cluster state version znode (expected -2)"));
    }

    @Test
    void empty_state_bundle_is_returned_if_no_bundle_already_stored_in_database() throws Exception {
        try (Fixture f = new Fixture()) {
            f.createDatabase();
            ClusterStateBundle bundleReceived = f.db().retrieveLastPublishedStateBundle();

            assertEquals(ClusterStateBundle.empty(), bundleReceived);
        }
    }

}
