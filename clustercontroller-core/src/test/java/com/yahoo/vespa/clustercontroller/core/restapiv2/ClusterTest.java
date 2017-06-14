// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClusterTest extends StateRestApiTest {

    @Test
    public void testCluster() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music", 0));
        String expected =
                "{\n" +
                "  \"state\": {\"generated\": {\n" +
                "    \"state\": \"up\",\n" +
                "    \"reason\": \"\"\n" +
                "  }},\n" +
                "  \"service\": {\n" +
                "    \"storage\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\"},\n" +
                "    \"distributor\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\"}\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testRecursiveCluster() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music", 1));
        String expected =
                "{\n" +
                "  \"state\": {\"generated\": {\n" +
                "    \"state\": \"up\",\n" +
                "    \"reason\": \"\"\n" +
                "  }},\n" +
                "  \"service\": {\n" +
                "    \"storage\": {\"node\": {\n" +
                "      \"1\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/1\"},\n" +
                "      \"2\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/2\"},\n" +
                "      \"3\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/3\"},\n" +
                "      \"5\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/5\"},\n" +
                "      \"7\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\\/7\"}\n" +
                "    }},\n" +
                "    \"distributor\": {\"node\": {\n" +
                "      \"1\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/1\"},\n" +
                "      \"2\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/2\"},\n" +
                "      \"3\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/3\"},\n" +
                "      \"5\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/5\"},\n" +
                "      \"7\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/7\"}\n" +
                "    }}\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }
}
