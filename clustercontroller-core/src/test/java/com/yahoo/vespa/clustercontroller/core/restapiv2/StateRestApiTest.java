// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.AnnotatedClusterState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTaskScheduler;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.StateRestAPI;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.UnitStateRequest;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.server.JsonWriter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public abstract class StateRestApiTest {

    private ClusterControllerMock books;
    ClusterControllerMock music;
    StateRestAPI restAPI;
    JsonWriter jsonWriter = new JsonWriter();
    Map<Integer, ClusterControllerStateRestAPI.Socket> ccSockets;

    public static class StateRequest implements UnitStateRequest {
        private final String[] path;
        private final int recursive;

        StateRequest(String req, int recursive) {
            path = req.isEmpty() ? new String[0] : req.split("/");
            this.recursive = recursive;
        }
        @Override
        public int getRecursiveLevels() { return recursive;
        }
        @Override
        public String[] getUnitPath() { return path; }
    }

    protected void setUp(boolean dontInitializeNode2) {
        Distribution distribution = new Distribution(getSimpleGroupConfig(2, 10));
        jsonWriter.setDefaultPathPrefix("/cluster/v2");
        {
            Set<ConfiguredNode> nodes = toNodes(0, 1, 2, 3);
            ContentCluster cluster = new ContentCluster("books", nodes, distribution);
            initializeCluster(cluster, nodes);
            AnnotatedClusterState baselineState = AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString("distributor:4 storage:4"));
            Map<String, AnnotatedClusterState> bucketSpaceStates = new HashMap<>();
            bucketSpaceStates.put("default", AnnotatedClusterState.withoutAnnotations(ClusterState.stateFromString("distributor:4 storage:4 .3.s:m")));
            bucketSpaceStates.put("global", baselineState);
            books = new ClusterControllerMock(cluster, baselineState.getClusterState(),
                    ClusterStateBundle.of(baselineState, bucketSpaceStates), 0, 0);
        }
        {
            Set<ConfiguredNode> nodes = toNodes(1, 2, 3, 5, 7);
            Set<ConfiguredNode> nodesInSlobrok = toNodes(1, 3, 5, 7);

            ContentCluster cluster = new ContentCluster("music", nodes, distribution);
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
        restAPI = new ClusterControllerStateRestAPI(() -> {
            Map<String, RemoteClusterControllerTaskScheduler> fleetControllers = new LinkedHashMap<>();
            fleetControllers.put(books.context.cluster.getName(), books);
            fleetControllers.put(music.context.cluster.getName(), music);
            return fleetControllers;
        }, ccSockets);
    }

    protected void setUpMusicGroup(int nodeCount, String node1StateString) {
        books = null;
        Distribution distribution = new Distribution(getSimpleGroupConfig(2, nodeCount));
        jsonWriter.setDefaultPathPrefix("/cluster/v2");
        ContentCluster cluster = new ContentCluster("music", distribution.getNodes(), distribution);
        initializeCluster(cluster, distribution.getNodes());
        AnnotatedClusterState baselineState = AnnotatedClusterState
                .withoutAnnotations(ClusterState.stateFromString("distributor:" + nodeCount + " storage:" + nodeCount + node1StateString));
        Map<String, AnnotatedClusterState> bucketSpaceStates = new HashMap<>();
        bucketSpaceStates.put("default", AnnotatedClusterState
                .withoutAnnotations(ClusterState.stateFromString("distributor:" + nodeCount + " storage:" + nodeCount)));
        bucketSpaceStates.put("global", baselineState);
        music = new ClusterControllerMock(cluster, baselineState.getClusterState(),
                ClusterStateBundle.of(baselineState, bucketSpaceStates), 0, 0);
        ccSockets = new TreeMap<>();
        ccSockets.put(0, new ClusterControllerStateRestAPI.Socket("localhost", 80));
        restAPI = new ClusterControllerStateRestAPI(() -> {
            Map<String, RemoteClusterControllerTaskScheduler> fleetControllers = new LinkedHashMap<>();
            fleetControllers.put(music.context.cluster.getName(), music);
            return fleetControllers;
        }, ccSockets);
    }

    private void initializeCluster(ContentCluster cluster, Collection<ConfiguredNode> nodes) {
        for (ConfiguredNode configuredNode : nodes) {
            for (NodeType type : NodeType.getTypes()) {
                NodeState reported = new NodeState(type, State.UP);

                NodeInfo nodeInfo = cluster.clusterInfo().setRpcAddress(new Node(type, configuredNode.index()), "rpc:" + type + "/" + configuredNode);
                nodeInfo.setReportedState(reported, 10);
                nodeInfo.setHostInfo(HostInfo.createHostInfo(getHostInfo(nodes)));
            }
        }
    }

    private String getHostInfo(Collection<ConfiguredNode> nodes) {
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

                nodes.stream()
                        .map(configuredNode ->
                "            {\n" +
                "                \"node-index\": " + configuredNode.index() + ",\n" +
                "                \"min-current-replication-factor\": 2\n" +
                "            }")
                        .collect(Collectors.joining(",\n")) + "\n" +

                "        ]\n" +
                "    }\n" +
                "}";
    }

    // TODO: Use config builder instead of creating raw: config
    private static String getSimpleGroupConfig(int redundancy, int nodeCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("raw:redundancy ").append(redundancy).append("\n").append("group[4]\n");

        int group = 0;
        sb.append("group[" + group + "].index \"invalid\"\n")
          .append("group[" + group + "].name \"invalid\"\n")
          .append("group[" + group + "].partitions \"1|*\"\n");

        ++group;
        sb.append("group[" + group + "].index \"0\"\n")
          .append("group[" + group + "].name \"east\"\n")
          .append("group[" + group + "].partitions \"*\"\n");

        ++group;
        sb.append("group[" + group + "].index \"0.0\"\n")
          .append("group[" + group + "].name \"g1\"\n")
          .append("group[" + group + "].partitions \"*\"\n")
          .append("group[" + group + "].nodes[").append((nodeCount + 1) / 2).append("]\n");
        for (int i=0; i<nodeCount; i += 2) {
            sb.append("group[" + group + "].nodes[").append(i / 2).append("].index ").append(i).append("\n");
        }

        ++group;
        sb.append("group[" + group + "].index \"0.1\"\n")
          .append("group[" + group + "].name \"g2\"\n")
          .append("group[" + group + "].partitions \"*\"\n")
          .append("group[" + group + "].nodes[").append(nodeCount / 2).append("]\n");
        for (int i=1; i<nodeCount; i += 2) {
            sb.append("group[" + group + "].nodes[").append(i / 2).append("].index ").append(i).append("\n");
        }
        return sb.toString();
    }

    private static Set<ConfiguredNode> toNodes(Integer ... indexes) {
        return Arrays.stream(indexes)
                .map(i -> new ConfiguredNode(i, false))
                .collect(Collectors.toSet());
    }

}
