// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.server.Slobrok;
import java.util.logging.Level;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MasterElectionTest extends FleetControllerTest {

    private static final Logger log = Logger.getLogger(MasterElectionTest.class.getName());

    private Supervisor supervisor;
    private final List<FleetController> fleetControllers = new ArrayList<>();

    @Rule
    public TestRule cleanupZookeeperLogsOnSuccess = new CleanupZookeeperLogsOnSuccess();

    @Rule
    public Timeout globalTimeout= Timeout.seconds(120);

    private static int defaultZkSessionTimeoutInMillis() { return 30_000; }

    protected void setUpFleetController(int count, boolean useFakeTimer, FleetControllerOptions options) throws Exception {
        if (zooKeeperServer == null) {
            zooKeeperServer = new ZooKeeperTestServer();
        }
        slobrok = new Slobrok();
        usingFakeTimer = useFakeTimer;
        this.options = options;
        this.options.zooKeeperSessionTimeout = defaultZkSessionTimeoutInMillis();
        this.options.zooKeeperServerAddress = zooKeeperServer.getAddress();
        this.options.slobrokConnectionSpecs = new String[1];
        this.options.slobrokConnectionSpecs[0] = "tcp/localhost:" + slobrok.port();
        this.options.fleetControllerCount = count;
        for (int i=0; i<count; ++i) {
            FleetControllerOptions nodeOptions = options.clone();
            nodeOptions.fleetControllerIndex = i;
            fleetControllers.add(createFleetController(usingFakeTimer, nodeOptions, true, new StatusHandler.ContainerStatusPageServer()));
        }
    }

    private FleetControllerOptions adjustConfig(FleetControllerOptions o,
                                                int fleetControllerIndex, int fleetControllerCount) {
        FleetControllerOptions options = o.clone();
        options.zooKeeperSessionTimeout = defaultZkSessionTimeoutInMillis();
        options.zooKeeperServerAddress = zooKeeperServer.getAddress();
        options.slobrokConnectionSpecs = new String[1];
        options.slobrokConnectionSpecs[0] = "tcp/localhost:" + slobrok.port(); // Spec.fromLocalHostName(slobrok.port()).toString();
        options.fleetControllerIndex = fleetControllerIndex;
        options.fleetControllerCount = fleetControllerCount;
        return options;
    }

    private void waitForZookeeperDisconnected() throws TimeoutException {
        long maxTime = System.currentTimeMillis() + timeoutMS;
        for (FleetController f : fleetControllers) {
            while (f.hasZookeeperConnection()) {
                timer.advanceTime(1000);
                try { Thread.sleep(1); } catch (InterruptedException e) { /* ignore */ }
                if (System.currentTimeMillis() > maxTime)
                    throw new TimeoutException("Failed to notice zookeeper down within timeout of " + timeoutMS + " ms");
            }
        }
        waitForCompleteCycles();
    }

    private void waitForCompleteCycle(int findex) {
        fleetControllers.get(findex).waitForCompleteCycle(timeoutMS);
    }

    private void waitForCompleteCycles() {
        for (int i = 0; i < fleetControllers.size(); ++i) {
            waitForCompleteCycle(i);
        }
    }

    protected void tearDownSystem() throws Exception {
        for (FleetController fleetController : fleetControllers) {
            if (fleetController != null) {
                fleetController.shutdown();
            }
        }
        if (slobrok != null) {
            slobrok.stop();
        }
        super.tearDownSystem();
    }

    public void tearDown() throws Exception {
        if (supervisor != null) {
            supervisor.transport().shutdown().join();
        }
        super.tearDown();
    }

    /** Ignored for unknown reasons */
    @Test
    @Ignore
    public void testMasterElection() throws Exception {
        startingTest("MasterElectionTest::testMasterElection");
        log.log(Level.INFO, "STARTING TEST: MasterElectionTest::testMasterElection()");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.masterZooKeeperCooldownPeriod = 1;
        setUpFleetController(5, false, options);
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 0");
        fleetControllers.get(0).shutdown();
        waitForMaster(1);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 1");
        fleetControllers.get(1).shutdown();
        waitForMaster(2);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 2");
        fleetControllers.get(2).shutdown();

        // Too few for there to be a master at this point
        for (int i=0; i<fleetControllers.size(); ++i) {
            if (fleetControllers.get(i).isRunning()) waitForCompleteCycle(i);
            assertFalse("Fleet controller " + i, fleetControllers.get(i).isMaster());
        }

        StatusHandler.ContainerStatusPageServer statusPageServer = new StatusHandler.ContainerStatusPageServer();
        log.log(Level.INFO, "STARTING FLEET CONTROLLER 2");
        fleetControllers.set(2, createFleetController(usingFakeTimer, fleetControllers.get(2).getOptions(), true, statusPageServer));
        waitForMaster(2);
        log.log(Level.INFO, "STARTING FLEET CONTROLLER 0");
        fleetControllers.set(0, createFleetController(usingFakeTimer, fleetControllers.get(0).getOptions(), true, statusPageServer));
        waitForMaster(0);
        log.log(Level.INFO, "STARTING FLEET CONTROLLER 1");
        fleetControllers.set(1, createFleetController(usingFakeTimer, fleetControllers.get(1).getOptions(), true, statusPageServer));
        waitForMaster(0);

        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 4");
        fleetControllers.get(4).shutdown();
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 3");
        fleetControllers.get(3).shutdown();
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 2");
        fleetControllers.get(2).shutdown();

        // Too few for there to be a master at this point
        for (int i=0; i<fleetControllers.size(); ++i) {
            if (fleetControllers.get(i).isRunning()) waitForCompleteCycle(i);
            assertFalse(fleetControllers.get(i).isMaster());
        }
    }

    private void waitForMaster(int master) {
        log.log(Level.INFO, "Entering waitForMaster");
        boolean isOnlyMaster = false;
        for (int i=0; i < FleetControllerTest.timeoutMS; i+=100) {
            if (!fleetControllers.get(master).isMaster()) {
                log.log(Level.INFO, "Node " + master + " is not master yet, sleeping more");
                timer.advanceTime(100);
                waitForCompleteCycle(master);
            } else {
                log.log(Level.INFO, "Node " + master + " is master. Checking that noone else is master");
                isOnlyMaster = true;
                for (int j=0; j<fleetControllers.size(); ++j) {
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
            try{ Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
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
    public void testClusterStateVersionIncreasesAcrossMasterElections() throws Exception {
        startingTest("MasterElectionTest::testClusterStateVersionIncreasesAcrossMasterElections");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.masterZooKeeperCooldownPeriod = 1;
        setUpFleetController(3, false, options);
        // Currently need to have content nodes present for the cluster controller to even bother
        // attempting to persisting its cluster state version to ZK.
        setUpVdsNodes(false, new DummyVdsNodeOptions());
        fleetController = fleetControllers.get(0); // Required to prevent waitForStableSystem from NPE'ing
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
    public void testVotingCorrectnessInFaceOfZKDisconnect() throws Exception {
        startingTest("MasterElectionTest::testVotingCorrectnessInFaceOfZKDisconnect");
        FleetControllerOptions options = defaultOptions("mycluster");
        // "Magic" port value is in range allocated to module for testing.
        zooKeeperServer = ZooKeeperTestServer.createWithFixedPort(18342);
        options.masterZooKeeperCooldownPeriod = 100;
        setUpFleetController(2, false, options);
        waitForMaster(0);

        zooKeeperServer.shutdown(true);
        waitForCompleteCycles();
        timer.advanceTime(options.zooKeeperSessionTimeout);
        waitForZookeeperDisconnected();

        zooKeeperServer = ZooKeeperTestServer.createWithFixedPort(18342);
        timer.advanceTime(10 * 1000); // Wait long enough for fleetcontroller wanting to retry zookeeper connection

        log.log(Level.INFO, "WAITING FOR 0 TO BE MASTER");
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN");
    }

    @Test
    public void testZooKeeperUnavailable() throws Exception {
        startingTest("MasterElectionTest::testZooKeeperUnavailable");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.masterZooKeeperCooldownPeriod = 100;
        options.zooKeeperServerAddress = "localhost";
        setUpFleetController(3, false, options);
        waitForMaster(0);

        log.log(Level.INFO, "STOPPING ZOOKEEPER SERVER AT " + zooKeeperServer.getAddress());
        zooKeeperServer.shutdown(true);
        waitForCompleteCycles();
        timer.advanceTime(options.zooKeeperSessionTimeout);
        waitForZookeeperDisconnected();
        // Noone can be master if server is unavailable
        log.log(Level.INFO, "Checking master status");
        for (int i=0; i<fleetControllers.size(); ++i) {
            assertFalse("Index " + i, fleetControllers.get(i).isMaster());
        }

        zooKeeperServer = new ZooKeeperTestServer();
        log.log(Level.INFO, "STARTED ZOOKEEPER SERVER AT " + zooKeeperServer.getAddress());
        for (FleetController fc : fleetControllers) {
            FleetControllerOptions myoptions = fc.getOptions();
            myoptions.zooKeeperServerAddress = zooKeeperServer.getAddress();
            fc.updateOptions(myoptions, 0);
            log.log(Level.INFO, "Should now have sent out new zookeeper server address " + myoptions.zooKeeperServerAddress + " to fleetcontroller " + myoptions.fleetControllerIndex);
        }
        timer.advanceTime(10 * 1000); // Wait long enough for fleetcontroller wanting to retry zookeeper connection
        waitForMaster(0);
        log.log(Level.INFO, "SHUTTING DOWN");
    }

    /** Ignored for unknown reasons */
    @Test
    @Ignore
    public void testMasterZooKeeperCooldown() throws Exception {
        startingTest("MasterElectionTest::testMasterZooKeeperCooldown");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.masterZooKeeperCooldownPeriod = 3600 * 1000; // An hour
        setUpFleetController(3, false, options);
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

    private void waitForMasterReason(String reason, Integer master, List<Target> connections, int nodes[]) {
        long endTime = System.currentTimeMillis() + timeoutMS;
        while (System.currentTimeMillis() < endTime) {
            boolean allOk = true;
            for (int node : nodes) {
                Request req = new Request("getMaster");
                connections.get(node).invokeSync(req, FleetControllerTest.timeoutS);
                if (req.isError()) {
                    allOk = false;
                    break;
                }
                if (master != null && master != req.returnValues().get(0).asInt32()) {
                    allOk = false;
                    break;
                }
                if (reason != null && ! reason.equals(req.returnValues().get(1).asString())) {
                    allOk = false;
                    break;
                }
            }
            if (allOk) return;
            try{ Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        }
        throw new IllegalStateException("Did not get master reason '" + reason
                + "' within timeout of " + timeoutMS + " ms");
    }

    /** Ignored for unknown reasons */
    @Test
    @Ignore
    public void testGetMaster() throws Exception {
        startingTest("MasterElectionTest::testGetMaster");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.masterZooKeeperCooldownPeriod = 3600 * 1000; // An hour
        setUpFleetController(3, false, options);
        waitForMaster(0);

        supervisor = new Supervisor(new Transport());
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

        for (int nodeIndex = 0; nodeIndex<3; ++nodeIndex) {
            for (int retry = 0; retry < FleetControllerTest.timeoutS * 10; ++retry) {
                req = new Request("getMaster");
                connections.get(nodeIndex).invokeSync(req, FleetControllerTest.timeoutS);
                assertFalse(req.errorMessage(), req.isError());
                if (req.returnValues().get(0).asInt32() == 0 &&
                    req.returnValues().get(1).asString().equals("All 3 nodes agree that 0 is current master.")) {
                    break;
                }
            }
            assertEquals(req.toString(), 0, req.returnValues().get(0).asInt32());
            assertEquals(req.toString(), "All 3 nodes agree that 0 is current master.", req.returnValues().get(1).asString());
        }

        log.log(Level.INFO, "SHUTTING DOWN FLEET CONTROLLER 0");
        fleetControllers.get(0).shutdown();
            // Wait until fc 1 & 2 votes for node 1
        waitForCompleteCycle(1);
        waitForCompleteCycle(2);
            // 5 minutes is not long enough period to wait before letting this node be master.
        timer.advanceTime(300 * 1000); // 5 minutes

        int[] remainingNodes = { 1, 2 };
        waitForMasterReason(
                "2 of 3 nodes agree 1 should be master, but old master cooldown period of 3600000 ms has not passed yet. To ensure it has got time to realize it is no longer master before we elect a new one, currently there is no master.",
                -1, connections, remainingNodes);
            // Verify that fc 1 is not master, and the correct reasons for why not
        assertFalse(fleetControllers.get(1).isMaster());

        // But after an hour it should become one.
        timer.advanceTime(3600 * 1000); // 60 minutes
        waitForMaster(1);

        req = new Request("getMaster");
        connections.get(0).invokeSync(req, FleetControllerTest.timeoutS);
        assertEquals(req.toString(), 104, req.errorCode());
        assertEquals(req.toString(), "Connection error", req.errorMessage());

        for (int i=0; i<FleetControllerTest.timeoutS * 10; ++i) {
            req = new Request("getMaster");
            connections.get(1).invokeSync(req, FleetControllerTest.timeoutS);
            assertFalse(req.errorMessage(), req.isError());
            if (req.returnValues().get(0).asInt32() != -1) break;
                // We may have bad timing causing node not to have realized it is master yet
        }
        assertEquals(req.toString(), 1, req.returnValues().get(0).asInt32());
        assertEquals(req.toString(), "2 of 3 nodes agree 1 is master.", req.returnValues().get(1).asString());

        for (int i=0; i<FleetControllerTest.timeoutS * 10; ++i) {
            req = new Request("getMaster");
            connections.get(2).invokeSync(req, FleetControllerTest.timeoutS);
            assertFalse(req.errorMessage(), req.isError());
            if (req.returnValues().get(0).asInt32() != -1) break;
        }
        assertEquals(req.toString(), 1, req.returnValues().get(0).asInt32());
        assertEquals(req.toString(), "2 of 3 nodes agree 1 is master.", req.returnValues().get(1).asString());
    }

    @Test
    public void testReconfigure() throws Exception {
        startingTest("MasterElectionTest::testReconfigure");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.masterZooKeeperCooldownPeriod = 1;
        setUpFleetController(3, false, options);
        waitForMaster(0);

        FleetControllerOptions newOptions = options.clone();
        for (int i=0; i<fleetControllers.size(); ++i) {
            FleetControllerOptions nodeOptions = adjustConfig(newOptions, i, fleetControllers.size());
            fleetControllers.get(i).updateOptions(nodeOptions, 2);
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
    public void cluster_state_version_written_to_zookeeper_even_with_empty_send_set() throws Exception {
        startingTest("MasterElectionTest::cluster_state_version_written_to_zookeeper_even_with_empty_send_set");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.masterZooKeeperCooldownPeriod = 1;
        options.minRatioOfDistributorNodesUp = 0;
        options.minRatioOfStorageNodesUp = 0;
        options.minDistributorNodesUp = 0;
        options.minStorageNodesUp = 1;
        setUpFleetController(3, false, options);
        setUpVdsNodes(false, new DummyVdsNodeOptions());
        fleetController = fleetControllers.get(0); // Required to prevent waitForStableSystem from NPE'ing
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
        setWantedState(this.nodes.get(2*10 - 1), State.MAINTENANCE, "bar");
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

        assertThat(postElectionVersionNumber, greaterThan(preElectionVersionNumber));
    }

    @Test
    public void previously_published_state_is_taken_into_account_for_default_space_when_controller_bootstraps() throws Exception {
        startingTest("MasterElectionTest::previously_published_state_is_taken_into_account_for_default_space_when_controller_bootstraps");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.clusterHasGlobalDocumentTypes = true;
        options.masterZooKeeperCooldownPeriod = 1;
        options.minTimeBeforeFirstSystemStateBroadcast = 100000;
        setUpFleetController(3, false, options);
        setUpVdsNodes(false, new DummyVdsNodeOptions());
        fleetController = fleetControllers.get(0); // Required to prevent waitForStableSystem from NPE'ing
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

        fleetControllers.set(0, createFleetController(usingFakeTimer, fleetControllers.get(0).getOptions(), true, new StatusHandler.ContainerStatusPageServer()));
        waitForMaster(0);
        waitForCompleteCycle(0);

        // We should NOT publish a state where all storage nodes are in Maintenance, since they were
        // marked as Up in the last published cluster state.
        log.info("Bundle after restart cycle: " + fleetControllers.get(0).getClusterStateBundle());
        waitForStateInAllSpaces("version:\\d+ distributor:10 storage:10");
    }

    @Test
    public void default_space_nodes_not_marked_as_maintenance_when_cluster_has_no_global_document_types() throws Exception {
        startingTest("MasterElectionTest::default_space_nodes_not_marked_as_maintenance_when_cluster_has_no_global_document_types");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.clusterHasGlobalDocumentTypes = false;
        options.masterZooKeeperCooldownPeriod = 1;
        options.minTimeBeforeFirstSystemStateBroadcast = 100000;
        setUpFleetController(3, false, options);
        setUpVdsNodes(false, new DummyVdsNodeOptions());
        fleetController = fleetControllers.get(0); // Required to prevent waitForStableSystem from NPE'ing
        waitForMaster(0);
        waitForStableSystem();
        waitForStateInAllSpaces("version:\\d+ distributor:10 storage:10");
    }

}
