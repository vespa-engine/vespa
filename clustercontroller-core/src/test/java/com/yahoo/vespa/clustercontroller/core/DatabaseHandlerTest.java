// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.database.Database;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseFactory;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeAddedOrRemovedListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DatabaseHandlerTest {

    static class Fixture {
        final ClusterFixture clusterFixture = ClusterFixture.forFlatCluster(10);
        final FleetController mockController = mock(FleetController.class);
        final Database mockDatabase = mock(Database.class);
        final Timer mockTimer = mock(Timer.class);
        final DatabaseFactory mockDbFactory = (params) -> mockDatabase;
        final String databaseAddress = "localhost:0";
        final Object monitor = new Object();
        final ClusterStateBundle dummyBundle;

        Fixture() throws Exception {
            dummyBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2",
                    StateMapping.of("default", "distributor:2 storage:2 .0.s:d"),
                    StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2"));

            when(mockDatabase.isClosed()).thenReturn(false);
            when(mockDatabase.storeMasterVote(anyInt())).thenReturn(true);
            when(mockDatabase.storeLastPublishedStateBundle(any())).thenReturn(true);
            when(mockTimer.getCurrentTimeInMillis()).thenReturn(1000000L);
        }

        DatabaseHandler.Context createMockContext() {
            return new DatabaseHandler.Context() {
                @Override
                public ContentCluster getCluster() {
                    return clusterFixture.cluster();
                }

                @Override
                public FleetController getFleetController() {
                    return mockController;
                }

                @Override
                public NodeAddedOrRemovedListener getNodeAddedOrRemovedListener() {
                    return null;
                }

                @Override
                public NodeStateOrHostInfoChangeHandler getNodeStateUpdateListener() {
                    return null;
                }
            };
        }

        DatabaseHandler createHandler() throws Exception {
            return new DatabaseHandler(mockDbFactory, mockTimer, databaseAddress, 0, monitor);
        }
    }

    @Test
    public void can_store_latest_cluster_state_bundle() throws Exception {
        Fixture f = new Fixture();
        DatabaseHandler handler = f.createHandler();
        handler.doNextZooKeeperTask(f.createMockContext()); // Database setup step
        handler.saveLatestClusterStateBundle(f.createMockContext(), f.dummyBundle);

        verify(f.mockDatabase).storeLastPublishedStateBundle(eq(f.dummyBundle));
    }

    @Test
    public void can_load_latest_cluster_state_bundle() throws Exception {
        Fixture f = new Fixture();
        DatabaseHandler handler = f.createHandler();
        handler.doNextZooKeeperTask(f.createMockContext()); // Database setup step

        when(f.mockDatabase.retrieveLastPublishedStateBundle()).thenReturn(f.dummyBundle);

        ClusterStateBundle retrievedBundle = handler.getLatestClusterStateBundle();
        assertThat(retrievedBundle, equalTo(f.dummyBundle));
    }

    // FIXME I don't like the semantics of this, but it mirrors the legacy behavior for the
    // rest of the DB load operations exposed by the DatabaseHandler.
    @Test
    public void empty_bundle_is_returned_if_no_db_connection() throws Exception {
        Fixture f = new Fixture();
        DatabaseHandler handler = f.createHandler();
        // Note: no DB setup step

        ClusterStateBundle retrievedBundle = handler.getLatestClusterStateBundle();
        assertThat(retrievedBundle, equalTo(ClusterStateBundle.empty()));
    }

}
