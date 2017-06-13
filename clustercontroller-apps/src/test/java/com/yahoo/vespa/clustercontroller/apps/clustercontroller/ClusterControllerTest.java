// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Doesn't really test cluster controller, but runs some lines of code.
 * System tests verifies that container can load it..
 */
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import junit.framework.TestCase;

import java.util.Map;

public class ClusterControllerTest extends TestCase {
    private FleetControllerOptions options = new FleetControllerOptions("storage");
    private Metric metric = new Metric() {
        @Override
        public void set(String s, Number number, Context context) {}
        @Override
        public void add(String s, Number number, Context context) {}
        @Override
        public Context createContext(Map<String, ?> stringMap) { return null; }
    };

    public void setUp() {
        options = new FleetControllerOptions("storage");
        options.zooKeeperServerAddress = null;
        options.slobrokConfigId = "raw:";
        options.slobrokConnectionSpecs = null;
    }

    public void testSimple() throws Exception {
            // Cluster controller object keeps state and should never be remade, so should
            // inject nothing
        ClusterController cc = new ClusterController();
        cc.setOptions("storage", options, metric);
        cc.setOptions("storage", options, metric);
        cc.getFleetControllers();
        cc.getAll();

        assertTrue(cc.get("storage") != null);
        assertFalse(cc.get("music") != null);
        cc.deconstruct();
    }

    public void testShutdownException() throws Exception {
        ClusterController cc = new ClusterController() {
            void shutdownController(FleetController controller) throws Exception {
                throw new Exception("Foo");
            }
        };
        cc.setOptions("storage", options, metric);
        cc.deconstruct();
    }

}
