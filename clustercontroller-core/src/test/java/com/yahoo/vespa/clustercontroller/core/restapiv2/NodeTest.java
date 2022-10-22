// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NodeTest extends StateRestApiTest {

    @Test
    void testDistributor() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor/1", 0));
        assertEquals("""
                     {
                       "attributes" : {
                         "hierarchical-group" : "east.g2"
                       },
                       "state" : {
                         "generated" : {
                           "state" : "up",
                           "reason" : ""
                         },
                         "unit" : {
                           "state" : "up",
                           "reason" : ""
                         },
                         "user" : {
                           "state" : "up",
                           "reason" : ""
                         }
                       }
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }

    @Test
    void testStorage() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1", 0));
        assertEquals("""
                     {
                       "attributes" : {
                         "hierarchical-group" : "east.g2"
                       },
                       "state" : {
                         "generated" : {
                           "state" : "up",
                           "reason" : ""
                         },
                         "unit" : {
                           "state" : "up",
                           "reason" : ""
                         },
                         "user" : {
                           "state" : "up",
                           "reason" : ""
                         }
                       },
                       "metrics" : {
                         "bucket-count" : 1,
                         "unique-document-count" : 2,
                         "unique-document-total-size" : 3
                       }
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }

    @Test
    void testRecursiveNode() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1", 1));
        assertEquals("""
                     {
                       "attributes" : {
                         "hierarchical-group" : "east.g2"
                       },
                       "state" : {
                         "generated" : {
                           "state" : "up",
                           "reason" : ""
                         },
                         "unit" : {
                           "state" : "up",
                           "reason" : ""
                         },
                         "user" : {
                           "state" : "up",
                           "reason" : ""
                         }
                       },
                       "metrics" : {
                         "bucket-count" : 1,
                         "unique-document-count" : 2,
                         "unique-document-total-size" : 3
                       }
                     }""",
                     jsonWriter.createJson(response).toPrettyString());
    }

    @Test
    void testNodeNotSeenInSlobrok() throws Exception {
        setUp(true);
        ContentCluster old = music.context.cluster;
        music.context.cluster = new ContentCluster(old.getName(), old.getConfiguredNodes().values(), old.getDistribution());
        NodeState currentState = new NodeState(NodeType.STORAGE, State.DOWN);
        currentState.setDescription("Not seen");
        music.context.currentConsolidatedState.setNodeState(new Node(NodeType.STORAGE, 1), currentState);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1", 0));
        assertEquals("""
                     {
                       "attributes" : {
                         "hierarchical-group" : "east.g2"
                       },
                       "state" : {
                         "generated" : {
                           "state" : "down",
                           "reason" : "Not seen"
                         },
                         "unit" : {
                           "state" : "down",
                           "reason" : "Node not seen in slobrok."
                         },
                         "user" : {
                           "state" : "up",
                           "reason" : ""
                         }
                       }
                     }""", jsonWriter.createJson(response).toPrettyString());
    }

}
