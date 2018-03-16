// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.*;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.StateRestAPI;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.UnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.server.JsonWriter;

import java.util.*;

// TODO: Author
public abstract class StateRestApiTest {

    protected ClusterControllerMock books;
    protected ClusterControllerMock music;
    protected StateRestAPI restAPI;
    protected JsonWriter jsonWriter = new JsonWriter();
    protected Map<Integer, ClusterControllerStateRestAPI.Socket> ccSockets;

    public static class StateRequest implements UnitStateRequest {
        private String[] path;
        private int recursive;

        public StateRequest(String req, int recursive) {
            path = req.isEmpty() ? new String[0] : req.split("/");
            this.recursive = recursive;
        }
        @Override
        public int getRecursiveLevels() { return recursive;
        }
        @Override
        public String[] getUnitPath() { return path; }
    }

    protected void setUp(boolean dontInitializeNode2) throws Exception {
        Distribution distribution = new Distribution(Distribution.getSimpleGroupConfig(2, 10));
        jsonWriter.setDefaultPathPrefix("/cluster/v2");
        {
            Set<ConfiguredNode> nodes = FleetControllerTest.toNodes(0, 1, 2, 3);
            ContentCluster cluster = new ContentCluster(
                    "books", nodes, distribution, 6 /* minStorageNodesUp*/, 0.9 /* minRatioOfStorageNodesUp */);
            initializeCluster(cluster, nodes);
            AnnotatedClusterState baselineState = AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString("distributor:4 storage:4"));
            Map<String, AnnotatedClusterState> bucketSpaceStates = new HashMap<>();
            bucketSpaceStates.put("default", AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString("distributor:4 storage:4 .3.s:m")));
            bucketSpaceStates.put("global", baselineState);
            books = new ClusterControllerMock(cluster, baselineState.getClusterState(),
                    ClusterStateBundle.of(baselineState, bucketSpaceStates), 0, 0);
        }
        {
            Set<ConfiguredNode> nodes = FleetControllerTest.toNodes(1, 2, 3, 5, 7);
            Set<ConfiguredNode> nodesInSlobrok = FleetControllerTest.toNodes(1, 3, 5, 7);

            ContentCluster cluster = new ContentCluster(
                    "music", nodes, distribution, 4 /* minStorageNodesUp*/, 0.0 /* minRatioOfStorageNodesUp */);
            if (dontInitializeNode2) {
                // TODO: this skips initialization of node 2 to fake that it is not answering
                // which really leaves us in an illegal state
                initializeCluster(cluster, nodesInSlobrok);
            }
            else {
                initializeCluster(cluster, nodes);
            }
            AnnotatedClusterState baselineState = AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString("distributor:8 .0.s:d .2.s:d .4.s:d .6.s:d "
                                                + "storage:8 .0.s:d .2.s:d .4.s:d .6.s:d"));
            music = new ClusterControllerMock(cluster, baselineState.getClusterState(),
                    ClusterStateBundle.ofBaselineOnly(baselineState), 0, 0);
        }
        ccSockets = new TreeMap<>();
        ccSockets.put(0, new ClusterControllerStateRestAPI.Socket("localhost", 80));
        restAPI = new ClusterControllerStateRestAPI(new ClusterControllerStateRestAPI.FleetControllerResolver() {
            @Override
            public Map<String, RemoteClusterControllerTaskScheduler> getFleetControllers() {
                Map<String, RemoteClusterControllerTaskScheduler> fleetControllers = new LinkedHashMap<>();
                fleetControllers.put(books.context.cluster.getName(), books);
                fleetControllers.put(music.context.cluster.getName(), music);
                return fleetControllers;
            }
        }, ccSockets);
    }

    protected void initializeCluster(ContentCluster cluster, Collection<ConfiguredNode> nodes) {
        for (ConfiguredNode configuredNode : nodes) {
            for (NodeType type : NodeType.getTypes()) {
                NodeState reported = new NodeState(type, State.UP);
                if (type.equals(NodeType.STORAGE)) {
                    reported.setDiskCount(2);
                }

                NodeInfo nodeInfo = cluster.clusterInfo().setRpcAddress(new Node(type, configuredNode.index()), "rpc:" + type + "/" + configuredNode);
                nodeInfo.setReportedState(reported, 10);
                nodeInfo.setHostInfo(HostInfo.createHostInfo(getHostInfo()));
            }
        }
    }

    private String getHostInfo() {
        return "{\n" +
                "    \"cluster-state-version\": 0,\n" +
                "    \"metrics\": {\n" +
                "        \"values\": [\n" +
                "            {\n" +
                "                \"name\": \"vds.datastored.alldisks.buckets\",\n" +
                "                \"values\": {\n" +
                "                    \"last\": 1\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"name\": \"vds.datastored.alldisks.docs\",\n" +
                "                \"values\": {\n" +
                "                    \"last\": 2\n" +
                "                }\n" +
                "            },\n" +
                "            {\n" +
                "                \"name\": \"vds.datastored.alldisks.bytes\",\n" +
                "                \"values\": {\n" +
                "                    \"last\": 3\n" +
                "                }\n" +
                "            }\n" +
                "        ]\n" +
                "    },\n" +
                "    \"distributor\": {\n" +
                "        \"storage-nodes\": [\n" +
                "            {\n" +
                "                \"node-index\": 1,\n" +
                "                \"min-current-replication-factor\": 2\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 3,\n" +
                "                \"min-current-replication-factor\": 2\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 5,\n" +
                "                \"min-current-replication-factor\": 2\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 7,\n" +
                "                \"min-current-replication-factor\": 2\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}";
    }
}
