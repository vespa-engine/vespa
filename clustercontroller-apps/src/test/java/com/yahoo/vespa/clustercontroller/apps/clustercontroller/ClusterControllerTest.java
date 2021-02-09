// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.jdisc.Metric;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Doesn't really test cluster controller, but runs some lines of code.
 * System tests verifies that container can load it..
 */
public class ClusterControllerTest {

    private FleetControllerOptions options = new FleetControllerOptions("storage", Set.of(new ConfiguredNode(0, false)));

    private final Metric metric = new Metric() {
        @Override
        public void set(String s, Number number, Context context) {}
        @Override
        public void add(String s, Number number, Context context) {}
        @Override
        public Context createContext(Map<String, ?> stringMap) { return null; }
    };

    @Before
    public void setUp() {
        options = new FleetControllerOptions("storage", Set.of(new ConfiguredNode(0, false)));
        options.zooKeeperServerAddress = null;
        options.slobrokConfigId = "raw:";
        options.slobrokConnectionSpecs = null;
    }

    @Test
    public void testSimple() throws Exception {
        // Cluster controller object keeps state and should never be remade, so should
        // inject nothing
        ClusterController cc = new ClusterController();
        cc.setOptions(options, metric);
        cc.setOptions(options, metric);
        cc.getFleetControllers();
        cc.getAll();

        assertNotNull(cc.get("storage"));
        assertNull(cc.get("music"));
        cc.deconstruct();
    }

    @Test
    public void testShutdownException() throws Exception {
        ClusterController cc = new ClusterController() {
            void shutdownController(FleetController controller) throws Exception {
                throw new Exception("Foo");
            }
        };
        cc.setOptions(options, metric);
        cc.deconstruct();
    }

}
