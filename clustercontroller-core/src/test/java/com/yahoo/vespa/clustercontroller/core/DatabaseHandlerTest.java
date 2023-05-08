// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.Database;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseFactory;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DatabaseHandlerTest {

    private AutoCloseable openMock = null;

    @Captor
    ArgumentCaptor<TreeMap<Node, NodeState>> wantedStatesArgument;

    static class Fixture {
        final ClusterFixture clusterFixture = ClusterFixture.forFlatCluster(10);
        final FleetController mockController = mock(FleetController.class);
        final Database mockDatabase = mock(Database.class);
        final Timer mockTimer = mock(Timer.class);
        final DatabaseFactory mockDbFactory = (params) -> mockDatabase;
        final String databaseAddress = "localhost:0";
        final Object monitor = new Object();
        final ClusterStateBundle dummyBundle;

        Fixture() {
            dummyBundle = ClusterStateBundleUtil.makeBundle("distributor:2 storage:2",
                    StateMapping.of("default", "distributor:2 storage:2 .0.s:d"),
                    StateMapping.of("upsidedown", "distributor:2 .0.s:d storage:2"));

            when(mockDatabase.isClosed()).thenReturn(false);
            when(mockDatabase.storeMasterVote(anyInt())).thenReturn(true);
            when(mockDatabase.storeLastPublishedStateBundle(any())).thenReturn(true);
            when(mockTimer.getCurrentTimeInMillis()).thenReturn(1000000L);
        }

        DatabaseHandler.DatabaseContext createMockContext() {
            return new DatabaseHandler.DatabaseContext() {
                @Override
                public ContentCluster getCluster() {
                    return clusterFixture.cluster();
                }

                @Override
                public FleetController getFleetController() {
                    return mockController;
                }

                @Override
                public NodeListener getNodeStateUpdateListener() {
                    return null;
                }
            };
        }

        DatabaseHandler createHandler() throws Exception {
            FleetControllerContext fleetControllerContext = mock(FleetControllerContext.class);
            when(fleetControllerContext.id()).thenReturn(new FleetControllerId("clusterName", 0));
            return new DatabaseHandler(fleetControllerContext, mockDbFactory, mockTimer, databaseAddress, monitor);
        }
    }

    @BeforeEach
    public void setUp() {
        openMock = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        openMock.close();
    }

    @Test
    void can_store_latest_cluster_state_bundle() throws Exception {
        Fixture f = new Fixture();
        DatabaseHandler handler = f.createHandler();
        handler.doNextZooKeeperTask(f.createMockContext()); // Database setup step
        handler.saveLatestClusterStateBundle(f.createMockContext(), f.dummyBundle);

        verify(f.mockDatabase).storeLastPublishedStateBundle(eq(f.dummyBundle));
    }

    @Test
    void can_load_latest_cluster_state_bundle() throws Exception {
        Fixture f = new Fixture();
        DatabaseHandler handler = f.createHandler();
        handler.doNextZooKeeperTask(f.createMockContext()); // Database setup step

        when(f.mockDatabase.retrieveLastPublishedStateBundle()).thenReturn(f.dummyBundle);

        ClusterStateBundle retrievedBundle = handler.getLatestClusterStateBundle();
        assertEquals(f.dummyBundle, retrievedBundle);
    }

    // FIXME I don't like the semantics of this, but it mirrors the legacy behavior for the
    // rest of the DB load operations exposed by the DatabaseHandler.
    @Test
    void empty_bundle_is_returned_if_no_db_connection() throws Exception {
        Fixture f = new Fixture();
        DatabaseHandler handler = f.createHandler();
        // Note: no DB setup step

        ClusterStateBundle retrievedBundle = handler.getLatestClusterStateBundle();
        assertEquals(ClusterStateBundle.empty(), retrievedBundle);
    }

    @Test
    void save_wanted_state_of_configured_nodes() throws Exception {
        var fixture = new Fixture();
        DatabaseHandler handler = fixture.createHandler();
        DatabaseHandler.DatabaseContext databaseContext = fixture.createMockContext();

        // The test fixture contains 10 nodes with indices 1-10.  A wanted state for
        // an existing node (5) should be preserved.  Note that it is not possible to set a
        // wanted state outside the existing nodes.
        Node storageNode5 = Node.ofStorage(5);
        NodeState maintenance = new NodeState(NodeType.STORAGE, State.MAINTENANCE);
        databaseContext.getCluster().getNodeInfo(storageNode5).setWantedState(maintenance);
        var expectedWantedStates = new TreeMap<>(Map.of(storageNode5, maintenance));

        // Ensure database is connected to ZooKeeper
        assertTrue(handler.doNextZooKeeperTask(databaseContext));

        // Verify ZooKeeperDatabase::storeWantedStates is invoked once
        verify(fixture.mockDatabase, times(0)).storeWantedStates(any());
        assertTrue(handler.saveWantedStates(databaseContext));
        verify(fixture.mockDatabase, times(1)).storeWantedStates(wantedStatesArgument.capture());

        // Verify ZooKeeperDatabase::storeWantedStates only saves states for existing nodes
        assertEquals(expectedWantedStates, wantedStatesArgument.getValue());
    }
}
