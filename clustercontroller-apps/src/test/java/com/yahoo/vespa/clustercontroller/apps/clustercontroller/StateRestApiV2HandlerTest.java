// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.clustercontroller.core.restapiv2.ClusterControllerStateRestAPI;
import junit.framework.TestCase;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StateRestApiV2HandlerTest extends TestCase {

    public void testNoMatchingSockets() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 100, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000));
        ClusterController controller = new ClusterController();
        ClusterInfoConfig config = new ClusterInfoConfig(
                new ClusterInfoConfig.Builder().clusterId("cluster-id").nodeCount(1));
        ClusterInfoConfig.Builder clusterConfig = new ClusterInfoConfig.Builder();
        new StateRestApiV2Handler(executor, controller, config, AccessLog.voidAccessLog());
        executor.shutdown();
    }

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
