// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ClusterListTest extends StateRestApiTest {

    @Test
    public void testClusterList() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("", 0));
        String expected =
                "{\"cluster\": {\n" +
                "  \"books\": {\"link\": \"\\/cluster\\/v2\\/books\"},\n" +
                "  \"music\": {\"link\": \"\\/cluster\\/v2\\/music\"}\n" +
                "}}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testRecursiveClusterList() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("", 1));
        String expected =
                "{\"cluster\": {\n" +
                "  \"books\": {\n" +
                "    \"state\": {\"generated\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    }},\n" +
                "    \"service\": {\n" +
                "      \"storage\": {\"link\": \"\\/cluster\\/v2\\/books\\/storage\"},\n" +
                "      \"distributor\": {\"link\": \"\\/cluster\\/v2\\/books\\/distributor\"}\n" +
                "    }\n" +
                "  },\n" +
                "  \"music\": {\n" +
                "    \"state\": {\"generated\": {\n" +
                "      \"state\": \"up\",\n" +
                "      \"reason\": \"\"\n" +
                "    }},\n" +
                "    \"service\": {\n" +
                "      \"storage\": {\"link\": \"\\/cluster\\/v2\\/music\\/storage\"},\n" +
                "      \"distributor\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\"}\n" +
                "    }\n" +
                "  }\n" +
                "}}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }
}
