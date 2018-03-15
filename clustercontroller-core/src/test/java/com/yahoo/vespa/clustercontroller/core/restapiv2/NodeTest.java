// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NodeTest extends StateRestApiTest {

    @Test
    public void testDistributor() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor/1", 0));
        String expected =
                "{\n" +
                "  \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "  \"state\": {\n" +
                "    \"generated\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"unit\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"user\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testStorage() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1", 0));
        String expected =
                "{\n" +
                "  \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "  \"state\": {\n" +
                "    \"generated\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"unit\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"user\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"partition\": {\n" +
                "    \"0\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/1\\/0\"},\n" +
                "    \"1\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/1\\/1\"}\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testRecursiveNode() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1", 1));
        String expected =
                "{\n" +
                "  \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "  \"state\": {\n" +
                "    \"generated\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"unit\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"user\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"partition\": {\n" +
                "    \"0\": {\n" +
                "      \"state\": {\"generated\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      }},\n" +
                "      \"metrics\": {\n" +
                "        \"bucket-count\": 1,\n" +
                "        \"unique-document-count\": 2,\n" +
                "        \"unique-document-total-size\": 3\n" +
                "      }\n" +
                "    },\n" +
                "    \"1\": {\n" +
                "      \"state\": {\"generated\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      }},\n" +
                "      \"metrics\": {\n" +
                "        \"bucket-count\": 1,\n" +
                "        \"unique-document-count\": 2,\n" +
                "        \"unique-document-total-size\": 3\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testNodeNotSeenInSlobrok() throws Exception {
        setUp(true);
        ContentCluster old = music.context.cluster;
        music.context.cluster = new ContentCluster(old.getName(), old.getConfiguredNodes().values(), old.getDistribution(), 0, 0.0);
        NodeState currentState = new NodeState(NodeType.STORAGE, State.DOWN);
        currentState.setDescription("Not seen");
        music.context.currentConsolidatedState.setNodeState(new Node(NodeType.STORAGE, 1), currentState);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1", 0));
        String expected =
                "{\n" +
                "  \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "  \"state\": {\n" +
                "    \"generated\": {\n" +
                "      \"state\": \"down\",\n" +
                "      \"reason\": \"Not seen\"\n" +
                "    },\n" +
                "    \"unit\": {\n" +
                "      \"state\": \"down\",\n" +
                "      \"reason\": \"Node not seen in slobrok.\"\n" +
                "    },\n" +
                "    \"user\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testRecursiveStorageClusterDoesNotIncludePerNodeStatsOrMetrics() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage", 1));
        String expected =
                "{\n" +
                "  \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "  \"state\": {\n" +
                "    \"generated\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"unit\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    },\n" +
                "    \"user\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"partition\": {\n" +
                "    \"0\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/1\\/0\"},\n" +
                "    \"1\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/1\\/1\"}\n" +
                "  }\n" +
                "}";
        JSONObject json = jsonWriter.createJson(response);
        assertEquals(expected, json.getJSONObject("node").getJSONObject("1").toString(2));
    }

}
