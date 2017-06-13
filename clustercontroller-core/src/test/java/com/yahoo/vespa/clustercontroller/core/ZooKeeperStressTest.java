// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.database.ZooKeeperDatabase;

import java.util.Map;

public class ZooKeeperStressTest extends junit.framework.TestCase {
    private Object lock = new Object();
    private int waitTime = 0;

    class LoadGiver extends Thread {
        ZooKeeperDatabase db;
        public int count = 0;
        public int errors = 0;
        public int index;
        public boolean stopNow = false;

        LoadGiver(ZooKeeperDatabase db, int index) {
            this.db = db;
            this.index = index;
        }

        public void doStop() {
            stopNow = true;
        }

        public void run() {
            try{
                while (!this.isInterrupted() && !stopNow) {
                    // Needs to take lock for each operation. Store new mastervote can not run at the same time as
                    // another store new master vote as they kill the ephemeral node
                    synchronized (lock) {
                        if (db.isClosed()) { System.err.println(this + " Session broke"); break; }
                        ++count;
                        if (db.retrieveLatestSystemStateVersion() == null) {
                            System.err.println("retrieveLatestSystemStateVersion() failed");
                            ++errors;
                        }
                    }
                    Map<Node, NodeState> wantedStates;
                    synchronized (lock) {
                        if (db.isClosed()) { System.err.println(this + " Session broke"); break; }
                        ++count;
                        wantedStates = db.retrieveWantedStates();
                        if (wantedStates == null) {
                            System.err.println("retrieveWantedStates() failed");
                            ++errors;
                        }
                    }
                    synchronized (lock) {
                        if (db.isClosed()) { System.err.println(this + " Session broke"); break; }
                        ++count;
                        if (!db.storeLatestSystemStateVersion(5)) {
                            System.err.println("storeLastestSystemStateVersion() failed");
                            ++errors;
                        }
                    }
                    synchronized (lock) {
                        if (db.isClosed()) { System.err.println(this + " Session broke"); break; }
                        ++count;
                        if (!db.storeMasterVote(0)) {
                            System.err.println("storeMasterVote() failed");
                            ++errors;
                        }
                    }
                    synchronized (lock) {
                        if (db.isClosed()) { System.err.println(this + " Session broke"); break; }
                        if (wantedStates != null) {
                            ++count;
                            if (!db.storeWantedStates(wantedStates)) {
                                System.err.println("storeWantedState() failed");
                                ++errors;
                            }
                        }
                    }
                    try{ Thread.sleep(waitTime); } catch (Exception e) {}
                }
            } catch (InterruptedException e) {}
        }

        public String toString() {
            return "LoadGiver(" + index + ": count " + count + ", errors " + errors + ")";
        }
    }

    public void testNothing() throws Exception {
        // Stupid junit fails if there's testclass without tests
    }

    public void testZooKeeperStressed() throws Exception {
    // Disabled for now.: Unstable
    /*
        ZooKeeperTestServer zooKeeperServer = new ZooKeeperTestServer();
        Database.DatabaseListener zksl = new Database.DatabaseListener() {
            public void handleZooKeeperSessionDown() {
                assertFalse("We lost session to ZooKeeper. Shouldn't happen", true);
            }

            public void handleMasterData(Map<Integer, Integer> data) {
            }
        };
        VdsCluster cluster = new VdsCluster("mycluster", 10, 10, true);
        int timeout = 30000;
        ZooKeeperDatabase db = new ZooKeeperDatabase(cluster, 0, zooKeeperServer.getAddress(), timeout, zksl);

        Collection<LoadGiver> loadGivers = new ArrayList();
        long time = System.currentTimeMillis();
        for (int i = 0; i<10; ++i) {
            loadGivers.add(new LoadGiver(db, i));
        }
        for (LoadGiver lg : loadGivers) {
            lg.start();
        }
        for (int i = 0; i<30000; i += 100) {
            Thread.sleep(100);
            boolean failed = false;
            for (LoadGiver lg : loadGivers) {
                if (lg.errors > 0) {
                    failed = true;
                }
            }
            if (failed) i += 5000;
        }
        int throughput = 0;
        int errors = 0;
        for (LoadGiver lg : loadGivers) {
            assertTrue("Error check prior to attempting to stop: " + lg.toString(), lg.errors == 0);
        }
        for (LoadGiver lg : loadGivers) {
            lg.doStop();
            throughput += lg.count;
            errors += lg.errors;
        }
        time = System.currentTimeMillis() - time;
        Double timesecs = new Double(time / 1000.0);
        if (timesecs > 0.001) {
            System.err.println("Throughput is " + (throughput / timesecs) + "msgs/sec, " + errors + " errors, total messages sent: " + throughput + ", waittime = " + waitTime);
        } else {
            System.err.println("too small time period " + time + " to calculate throughput");
        }
        //try{ Thread.sleep(5000); } catch (Exception e) {}
        for (LoadGiver lg : loadGivers) {
            lg.join();
        }
        for (LoadGiver lg : loadGivers) {
            System.err.println(lg);
        }
        // Disabling test. This fails occasionally for some reason.
        for (LoadGiver lg : loadGivers) {
            // assertTrue("Error check after having stopped: " + lg.toString(), lg.errors == 0);
        }
        */
    }
}
