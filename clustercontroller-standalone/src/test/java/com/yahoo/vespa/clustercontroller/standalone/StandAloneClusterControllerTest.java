// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.standalone;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class StandAloneClusterControllerTest extends ClusterControllerTest {

    abstract class Runner implements Runnable {
        boolean completed = false;
        Exception exception = null;

        public void run() {
            try{
                attemptRun();
                completed = true;
            } catch (Exception e) {
                exception = e;
            }
        }

        public abstract void attemptRun() throws Exception;
    }

    @Test
    public void testSimpleStartStop() throws Exception {
        setupConfig();
        ClusterControllerConfigFetcher configFetcher = new ClusterControllerConfigFetcher();
        StandAloneClusterController controller = new StandAloneClusterController(configFetcher);
        assertEquals("Fleetcontroller 0 of cluster storage", controller.getName());
        controller.start();
        controller.stop();
    }

    @Test
    public void testShortRun() throws Exception {
        setupConfig();
        ClusterControllerConfigFetcher configFetcher = new ClusterControllerConfigFetcher() {
            int counter = 0;
            @Override
            public boolean updated(long timeoutMillis) {
                return (++counter % 2 == 0);
            }
            @Override
            public void close() {
                throw new RuntimeException("Failed to stop");
            }
        };
        final StandAloneClusterController controller = new StandAloneClusterController(configFetcher);
        assertEquals("Fleetcontroller 0 of cluster storage", controller.getName());
        controller.start();
        Runner r = new Runner() {
            @Override
            public void attemptRun() throws Exception {
                controller.run();
            }
        };
        Thread t = new Thread(r);
        t.start();
        for (int i=0; i<100; ++i) {
            try{ Thread.sleep(1); } catch (InterruptedException e) {}
            synchronized (controller) {
                controller.notifyAll();
            }
        }
        StandAloneClusterController.ShutdownHook hook = new StandAloneClusterController.ShutdownHook(controller);
        hook.start();
        hook.join();
        t.join();
        assertEquals(true, r.completed);
        assertNull(r.exception);
    }

    @Test
    public void testFailStart() throws Exception {
        setupConfig();
        ClusterControllerConfigFetcher configFetcher = new ClusterControllerConfigFetcher();
        StandAloneClusterController controller = new StandAloneClusterController(configFetcher) {
            @Override
            public void start() {
                throw new RuntimeException("Foo");
            }
        };
        StandAloneClusterController.runApplication(controller);
    }

    @Test
    public void testFailRun() throws Exception {
        setupConfig();
        ClusterControllerConfigFetcher configFetcher = new ClusterControllerConfigFetcher();
        StandAloneClusterController controller = new StandAloneClusterController(configFetcher) {
            @Override
            public void run() {
                throw new RuntimeException("Foo");
            }
        };
        StandAloneClusterController.runApplication(controller);
    }

    @Test
    public void testCallMainToGetCoverage() throws Exception {
        tearDown();
        setProperty("config.id", "file:non-existing");
        try{
            StandAloneClusterController.main(new String[0]);
            fail("Control should not get here");
        } catch (IllegalArgumentException e) {
        }
    }

}
