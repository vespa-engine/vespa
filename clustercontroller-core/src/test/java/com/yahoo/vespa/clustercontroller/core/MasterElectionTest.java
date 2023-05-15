// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
@Timeout(120)
public class MasterElectionTest extends FleetControllerTest {

    private static final Logger log = Logger.getLogger(MasterElectionTest.class.getName());
    private static int defaultZkSessionTimeoutInMillis() { return 30_000; }

    private Supervisor supervisor;

    @BeforeEach
    public void setup() {
        supervisor = new Supervisor(new Transport());
    }

    @AfterEach
    public void teardown() {
        supervisor.transport().shutdown().join();
    }

    protected void setUpFleetControllers(int count, Timer timer, FleetControllerOptions.Builder builder) throws Exception {
        if (zooKeeperServer == null) {
            zooKeeperServer = new ZooKeeperTestServer();
        }
        slobrok = new Slobrok();
        builder.setZooKeeperSessionTimeout(defaultZkSessionTimeoutInMillis())
               .setZooKeeperServerAddress(zooKeeperServer.getAddress())
               .setSlobrokConnectionSpecs(getSlobrokConnectionSpecs(slobrok))
               .setCount(count);
        options = builder.build();
        for (int i = 0; i < count; ++i) {
            FleetControllerOptions.Builder b = FleetControllerOptions.Builder.copy(options);
            b.setIndex(i);
            fleetControllers.add(createFleetController(timer, b.build()));
        }
    }

    private FleetControllerOptions adjustConfig(FleetControllerOptions options, int fleetControllerIndex, int fleetControllerCount) {
        return FleetControllerOptions.Builder.copy(options)
                                             .setZooKeeperSessionTimeout(defaultZkSessionTimeoutInMillis())
                                             .setZooKeeperServerAddress(zooKeeperServer.getAddress())
                                             .setSlobrokConnectionSpecs(getSlobrokConnectionSpecs(slobrok))
                                             .setIndex(fleetControllerIndex)
                                             .setCount(fleetControllerCount)
                                             .build();
    }

    private void waitForZookeeperDisconnected() throws TimeoutException {
        Instant maxTime = Instant.now().plus(timeout());
        for (FleetController f : fleetControllers) {
            while (f.hasZookeeperConnection()) {
                try { Thread.sleep(1); } catch (InterruptedException e) { /* ignore */ }
                if (Instant.now().isAfter(maxTime))
                    throw new TimeoutException("Failed to notice zookeeper down within timeout of " + timeout());
            }
        }
        waitForCompleteCycles();
    }

    private void waitForCompleteCycle(int findex) {
        fleetControllers.get(findex).waitForCompleteCycle(timeout());
    }

    private void waitForCompleteCycles() {
        for (int i = 0; i < fleetControllers.size(); ++i) {
            waitForCompleteCycle(i);
        }
    }

    @Test
    void testMasterElection() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions();
        builder.setMasterZooKeeperCooldownPeriod(100);
        Timer timer = new RealTimer();
        setUpFleetControllers(3, timer, builder);
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 0");
        fleetControllers.get(0).shutdown();
        waitForMaster(1);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 1");
        fleetControllers.get(1).shutdown();

        // Too few for there to be a master at this point
        for (int i = 0; i < fleetControllers.size(); ++i) {
            if (fleetControllers.get(i).isRunning()) waitForCompleteCycle(i);
            assertFalse(fleetControllers.get(i).isMaster(), "Fleet controller " + i);
        }

        log.log(Level.INFO, "STARTING FLEET CONTROLLER 1");
        fleetControllers.set(1, createFleetController(timer, fleetControllers.get(1).getOptions()));
        waitForMaster(1);
        log.log(Level.INFO, "STARTING FLEET CONTROLLER 0");
        fleetControllers.set(0, createFleetController(timer, fleetControllers.get(0).getOptions()));
        waitForMaster(0);
    }

    @Test
    void testMasterElectionWith5FleetControllers() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions();
        RealTimer timer = new RealTimer();
        setUpFleetControllers(5, timer, builder);
        waitForMaster(0);
    }

    private void waitForMaster(int master) {
        log.log(Level.INFO, "Entering waitForMaster");
        boolean isOnlyMaster = false;
        for (int i = 0; i < timeout().toMillis(); i += 100) {
            if (!fleetControllers.get(master).isMaster()) {
                log.log(Level.INFO, "Node " + master + " is not master yet, sleeping more");
                waitForCompleteCycle(master);
            } else {
                log.log(Level.INFO, "Node " + master + " is master. Checking that no one else is master");
                isOnlyMaster = true;
                for (int j = 0; j < fleetControllers.size(); ++j) {
                    if (j != master && fleetControllers.get(j).isMaster()) {
                        isOnlyMaster = false;
                        log.log(Level.INFO, "Node " + j + " also says it is master.");
                    }
                }

                if (isOnlyMaster) {
                    break;
                }
            }
            // Have to wait to get zookeeper communication chance to happen.
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        if (!isOnlyMaster) {
            log.log(Level.INFO, "Node " + master + " is not the only master");
            throw new IllegalStateException("Node " + master + " never got to be the only master.");
        }

        log.log(Level.INFO, "Leaving waitForMaster");
    }

    private static class StrictlyIncreasingVersionChecker {
        private ClusterState lastState;

        private StrictlyIncreasingVersionChecker(ClusterState initialState) {
            this.lastState = initialState;
        }

        static StrictlyIncreasingVersionChecker bootstrappedWith(ClusterState initialState) {
            return new StrictlyIncreasingVersionChecker(initialState);
        }

        void updateAndVerify(ClusterState currentState) {
            final ClusterState last = lastState;
            lastState = currentState;
            if (currentState.getVersion() <= last.getVersion()) {
                throw new IllegalStateException(
                        String.format("Cluster state version strict increase invariant broken! " +
                                      "Old state was '%s', new state is '%s'", last, currentState));
            }
        }
    }

    @Test
    void testClusterStateVersionIncreasesAcrossMasterElections() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMasterZooKeeperCooldownPeriod(1);
        Timer timer = new RealTimer();
        setUpFleetControllers(3, timer, options);
        // Currently need to have content nodes present for the cluster controller to even bother
        // attempting to persisting its cluster state version to ZK.
        setUpVdsNodes(timer);
        waitForStableSystem();
        waitForMaster(0);
        Stream.of(0, 1, 2).forEach(this::waitForCompleteCycle);
        StrictlyIncreasingVersionChecker checker = StrictlyIncreasingVersionChecker.bootstrappedWith(
                fleetControllers.get(0).getClusterState());
        fleetControllers.get(0).shutdown();
        waitForMaster(1);
        Stream.of(1, 2).forEach(this::waitForCompleteCycle);
        checker.updateAndVerify(fleetControllers.get(1).getClusterState());
    }

    @Test
    void testVotingCorrectnessInFaceOfZKDisconnect() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        // "Magic" port value is in range allocated to module for testing.
        zooKeeperServer = ZooKeeperTestServer.createWithFixedPort(18342);
        options.setMasterZooKeeperCooldownPeriod(100);
        Timer timer = new RealTimer();
        setUpFleetControllers(2, timer, options);
        waitForMaster(0);

        zooKeeperServer.shutdown(true);
        waitForCompleteCycles();
        waitForZookeeperDisconnected();

        zooKeeperServer = ZooKeeperTestServer.createWithFixedPort(18342);

        log.log(Level.INFO, "WAITING FOR 0 TO BE MASTER");
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN");
    }

    @Test
    void testZooKeeperUnavailable() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMasterZooKeeperCooldownPeriod(100)
                .setZooKeeperServerAddress("localhost");
        Timer timer = new RealTimer();
        setUpFleetControllers(3, timer, builder);
        waitForMaster(0);

        log.log(Level.INFO, "STOPPING ZOOKEEPER SERVER AT " + zooKeeperServer.getAddress());
        zooKeeperServer.shutdown(true);
        waitForCompleteCycles();
        waitForZookeeperDisconnected();
        // No one can be master if server is unavailable
        log.log(Level.INFO, "Checking master status");
        for (int i = 0; i < fleetControllers.size(); ++i) {
            assertFalse(fleetControllers.get(i).isMaster(), "Index " + i);
        }

        zooKeeperServer = new ZooKeeperTestServer();
        log.log(Level.INFO, "STARTED ZOOKEEPER SERVER AT " + zooKeeperServer.getAddress());
        for (FleetController fc : fleetControllers) {
            FleetControllerOptions.Builder myoptions = FleetControllerOptions.Builder.copy(fc.getOptions());
            myoptions.setZooKeeperServerAddress(zooKeeperServer.getAddress());
            fc.updateOptions(myoptions.build());
            log.log(Level.INFO, "Should now have sent out new zookeeper server address " + myoptions.zooKeeperServerAddress() +
                    " to fleetcontroller " + myoptions.fleetControllerIndex());
        }
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN");
    }

    @Test
    @Disabled("Unstable, disable test, as functionality is not deemed critical")
    void testMasterZooKeeperCooldown() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMasterZooKeeperCooldownPeriod(3600 * 1000); // An hour
        FakeTimer timer = new FakeTimer();
        setUpFleetControllers(3, timer, options);
        waitForMaster(0);
        timer.advanceTime(24 * 3600 * 1000); // A day
        waitForCompleteCycle(1);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 0");
        fleetControllers.get(0).shutdown();
        waitForCompleteCycle(1);
        // 5 minutes is not long enough period to wait before letting this node be master.
        timer.advanceTime(300 * 1000); // 5 minutes
        waitForCompleteCycle(1);
        assertFalse(fleetControllers.get(1).isMaster());
        // But after an hour it should become one.
        timer.advanceTime(4000 * 1000); // more than 60 minutes
        waitForMaster(1);
    }

    private void waitForNoMasterWithExpectedReason(String reason, List<Target> connections, int[] nodes) {
        Objects.requireNonNull(reason, "reason cannot be null");
        Instant endTime = Instant.now().plus(timeout());
        while (Instant.now().isBefore(endTime)) {
            boolean allOk = true;
            for (int node : nodes) {
                Request req = new Request("getMaster");
                connections.get(node).invokeSync(req, timeout());
                if (req.isError()) {
                    allOk = false;
                    break;
                }
                if (req.returnValues().get(0).asInt32() != -1) {  // -1 means no master, which we are waiting for
                    allOk = false;
                    break;
                }
                if ( ! reason.equals(req.returnValues().get(1).asString())) {
                    allOk = false;
                    break;
                }
            }
            if (allOk) return;
            try { Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        }
        throw new IllegalStateException("Did not get master reason '" + reason + "' within timeout of " + timeout());
    }

    @Test
    void testGetMaster() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMasterZooKeeperCooldownPeriod(3600 * 1000); // An hour
        FakeTimer timer = new FakeTimer();
        setUpFleetControllers(3, timer, options);
        waitForMaster(0);

        List<Target> connections = new ArrayList<>();
        for (FleetController fleetController : fleetControllers) {
            int rpcPort = fleetController.getRpcPort();
            Target connection = supervisor.connect(new Spec("localhost", rpcPort));
            assertTrue(connection.isValid());
            connections.add(connection);
        }

        timer.advanceTime(24 * 3600 * 1000); // A day
        waitForCompleteCycles();

        Request req = new Request("getMaster");

        long maxRetries = timeout().toMillis() / 100;
        for (int nodeIndex = 0; nodeIndex < 3; ++nodeIndex) {
            for (int retry = 0; retry < maxRetries; ++retry) {
                req = new Request("getMaster");
                connections.get(nodeIndex).invokeSync(req, timeout());
                assertFalse(req.isError(), req.errorMessage());
                if (req.returnValues().get(0).asInt32() == 0 &&
                        req.returnValues().get(1).asString().equals("All 3 nodes agree that 0 is current master.")) {
                    break;
                }
            }
            assertEquals(0, req.returnValues().get(0).asInt32(), req.toString());
            assertEquals("All 3 nodes agree that 0 is current master.", req.returnValues().get(1).asString(), req.toString());
        }

        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 0");
        fleetControllers.get(0).shutdown();
        // Wait until fc 1 & 2 votes for node 1
        waitForCompleteCycle(1);
        waitForCompleteCycle(2);
        // 5 minutes is not long enough period to wait before letting this node be master.
        timer.advanceTime(300 * 1000); // 5 minutes

        int[] remainingNodes = {1, 2};
        waitForNoMasterWithExpectedReason(
                "2 of 3 nodes agree 1 should be master, but old master cooldown period of 3600000 ms has not passed yet. To ensure it has got time to realize it is no longer master before we elect a new one, currently there is no master.",
                connections,
                remainingNodes);
        // Verify that fc 1 is not master, and the correct reasons for why not
        assertFalse(fleetControllers.get(1).isMaster());

        // But after an hour it should become one.
        timer.advanceTime(3600 * 1000); // 60 minutes
        waitForMaster(1);

        req = new Request("getMaster");
        connections.get(0).invokeSync(req, timeout());
        assertEquals(104, req.errorCode(), req.toString());
        assertEquals("Connection error", req.errorMessage(), req.toString());

        for (int i = 0; i < maxRetries; ++i) {
            req = new Request("getMaster");
            connections.get(1).invokeSync(req, timeout());
            assertFalse(req.isError(), req.errorMessage());
            if (req.returnValues().get(0).asInt32() != -1) break;
            // We may have bad timing causing node not to have realized it is master yet
        }
        assertEquals(1, req.returnValues().get(0).asInt32(), req.toString());
        assertEquals("2 of 3 nodes agree 1 is master.", req.returnValues().get(1).asString(), req.toString());

        for (int i = 0; i < maxRetries; ++i) {
            req = new Request("getMaster");
            connections.get(2).invokeSync(req, timeout());
            assertFalse(req.isError(), req.errorMessage());
            if (req.returnValues().get(0).asInt32() != -1) break;
        }
        assertEquals(1, req.returnValues().get(0).asInt32(), req.toString());
        assertEquals("2 of 3 nodes agree 1 is master.", req.returnValues().get(1).asString(), req.toString());
    }

    @Test
    void testReconfigure() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMasterZooKeeperCooldownPeriod(1);
        Timer timer = new RealTimer();
        setUpFleetControllers(3, timer, options);
        waitForMaster(0);

        FleetControllerOptions newOptions = FleetControllerOptions.Builder.copy(options.build()).build();
        for (int i = 0; i < fleetControllers.size(); ++i) {
            FleetControllerOptions nodeOptions = adjustConfig(newOptions, i, fleetControllers.size());
            fleetControllers.get(i).updateOptions(nodeOptions);
        }
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 0");
        fleetControllers.get(0).shutdown();
        waitForMaster(1);
    }

    /**
     * Should always write new version to ZooKeeper, even if the version will not
     * be published to any nodes. External services may still observe the version
     * number via the cluster REST API, and we should therefore ensure that we never
     * risk rolling back the version number in the face of a reelection.
     */
    @Test
    void cluster_state_version_written_to_zookeeper_even_with_empty_send_set() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMasterZooKeeperCooldownPeriod(1)
                .setMinRatioOfDistributorNodesUp(0)
                .setMinRatioOfStorageNodesUp(0)
                .setMinDistributorNodesUp(0)
                .setMinStorageNodesUp(1);
        Timer timer = new RealTimer();
        setUpFleetControllers(3, timer, builder);
        setUpVdsNodes(timer);
        waitForStableSystem();
        waitForMaster(0);

        // Explanation for this convoluted sequence of actions: we want to trigger a scenario where
        // we have a cluster state version bump _without_ there being any nodes to send the new state
        // to. If there's an "optimization" to skip writing the version to ZooKeeper if there are no
        // nodes in the version send set, a newly elected, different master will end up reusing the
        // very same version number.
        // We mark all nodes' Reported states as down (which means an empty send set, as no nodes are
        // online), then mark one storage node as Wanted state as Maintenance. This forces a cluster
        // state change.
        this.nodes.forEach(n -> {
            n.disconnectImmediately();
            waitForCompleteCycle(0);
        });
        setWantedState(this.nodes.get(2 * 10 - 1), State.MAINTENANCE, "bar", supervisor);
        waitForCompleteCycle(0);

        // This receives the version number of the highest _working_ cluster state, with
        // no guarantees that it has been published to any nodes yet.
        final long preElectionVersionNumber = fleetControllers.get(0).getSystemState().getVersion();

        // Nuke controller 0, leaving controller 1 in charge.
        // It should have observed the most recently written version number and increase this
        // number before publishing its own new state.
        fleetControllers.get(0).shutdown();
        waitForMaster(1);
        waitForCompleteCycle(1);

        final long postElectionVersionNumber = fleetControllers.get(1).getSystemState().getVersion();

        assertTrue(postElectionVersionNumber > preElectionVersionNumber);
    }

    @Test
    void previously_published_state_is_taken_into_account_for_default_space_when_controller_bootstraps() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setClusterHasGlobalDocumentTypes(true)
                .setMasterZooKeeperCooldownPeriod(1)
                .setMinTimeBeforeFirstSystemStateBroadcast(100000);
        Timer timer = new RealTimer();
        setUpFleetControllers(3, timer, builder);
        setUpVdsNodes(timer);
        waitForMaster(0);
        waitForStableSystem();
        log.info("Waiting for full maintenance mode in default space");
        waitForStateInSpace("default", "version:\\d+ distributor:10 storage:10 .0.s:m .1.s:m .2.s:m .3.s:m .4.s:m .5.s:m .6.s:m .7.s:m .8.s:m .9.s:m");

        log.info("Responding with zero global merges pending from all distributors");
        final int ackVersion = fleetControllers.get(0).getClusterStateBundle().getVersion();
        // ACKing with no merge state in host info (implied: no pending merges) should not cause
        // a new state to be published before the last node has ACKed. Consequently there should
        // not be any race potential where a new version is published concurrently with our attempts
        // at ACKing a previous one.
        this.nodes.stream().filter(DummyVdsNode::isDistributor).forEach(node -> {
            node.setNodeState(new NodeState(NodeType.DISTRIBUTOR, State.UP),
                    String.format("{\"cluster-state-version\":%d}", ackVersion));
        });
        waitForStateInAllSpaces("version:\\d+ distributor:10 storage:10");

        log.info("Bundle before restart cycle: " + fleetControllers.get(0).getClusterStateBundle());
        log.info("Doing restart cycle of controller 0");
        fleetControllers.get(0).shutdown();
        waitForMaster(1);
        waitForCompleteCycle(1);

        fleetControllers.set(0, createFleetController(timer, fleetControllers.get(0).getOptions()));
        waitForMaster(0);
        waitForCompleteCycle(0);

        // We should NOT publish a state where all storage nodes are in Maintenance, since they were
        // marked as Up in the last published cluster state.
        log.info("Bundle after restart cycle: " + fleetControllers.get(0).getClusterStateBundle());
        waitForStateInAllSpaces("version:\\d+ distributor:10 storage:10");
    }

    @Test
    void default_space_nodes_not_marked_as_maintenance_when_cluster_has_no_global_document_types() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setClusterHasGlobalDocumentTypes(false)
                .setMasterZooKeeperCooldownPeriod(1)
                .setMinTimeBeforeFirstSystemStateBroadcast(100000);
        Timer timer = new RealTimer();
        setUpFleetControllers(3, timer, builder);
        setUpVdsNodes(timer);
        waitForMaster(0);
        waitForStableSystem();
        waitForStateInAllSpaces("version:\\d+ distributor:10 storage:10");
    }

}
