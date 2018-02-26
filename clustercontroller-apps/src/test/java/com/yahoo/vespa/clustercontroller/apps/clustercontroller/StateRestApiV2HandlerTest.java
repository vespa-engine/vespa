// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.vespa.clustercontroller.core.restapiv2.ClusterControllerStateRestAPI;
import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public class StateRestApiV2HandlerTest {

    @Test
    public void testNoMatchingSockets() {
        ClusterController controller = new ClusterController();
        ClusterInfoConfig config = new ClusterInfoConfig(
                new ClusterInfoConfig.Builder().clusterId("cluster-id").nodeCount(1));
        ClusterInfoConfig.Builder clusterConfig = new ClusterInfoConfig.Builder();
        new StateRestApiV2Handler(controller, config, StateRestApiV2Handler.testOnlyContext());
    }

    @Test
    public void testMappingOfIndexToClusterControllers() {
        ClusterInfoConfig.Builder builder = new ClusterInfoConfig.Builder()
                .clusterId("cluster-id")
                .nodeCount(1)
                .services(new ClusterInfoConfig.Services.Builder()
                        .index(1)
                        .hostname("host-1")
                        .ports(new ClusterInfoConfig.Services.Ports.Builder().number(80).tags("state http"))
                        .ports(new ClusterInfoConfig.Services.Ports.Builder().number(81).tags("ignored port http")))
                .services(new ClusterInfoConfig.Services.Builder()
                        .index(3)
                        .hostname("host-3")
                        .ports(new ClusterInfoConfig.Services.Ports.Builder().number(85).tags("state http"))
                        .ports(new ClusterInfoConfig.Services.Ports.Builder().number(86).tags("foo http bar state")));

        ClusterInfoConfig config = new ClusterInfoConfig(builder);
        Map<Integer, ClusterControllerStateRestAPI.Socket> mapping = StateRestApiV2Handler.getClusterControllerSockets(config);
        Map<Integer, ClusterControllerStateRestAPI.Socket> expected = new TreeMap<>();

        expected.put(1, new ClusterControllerStateRestAPI.Socket("host-1", 80));
        expected.put(3, new ClusterControllerStateRestAPI.Socket("host-3", 85));

        assertEquals(expected, mapping);
    }

}
