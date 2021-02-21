// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServiceTest extends StateRestApiTest {

    @Test
    public void testService() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor", 0));
        String expected =
                "{\"node\": {\n" +
                "  \"1\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/1\"},\n" +
                "  \"2\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/2\"},\n" +
                "  \"3\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/3\"},\n" +
                "  \"5\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/5\"},\n" +
                "  \"7\": {\"link\": \"\\/cluster\\/v2\\/music\\/distributor\\/7\"}\n" +
                "}}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testRecursiveCluster() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/distributor", 1));
        String expected =
                "{\"node\": {\n" +
                "  \"1\": {\n" +
                "    \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "    \"state\": {\n" +
                "      \"generated\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"unit\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"user\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"2\": {\n" +
                "    \"attributes\": {\"hierarchical-group\": \"east.g1\"},\n" +
                "    \"state\": {\n" +
                "      \"generated\": {\n" +
                "        \"state\": \"down\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"unit\": {\n" +
                "        \"state\": \"down\",\n" +
                "        \"reason\": \"Node not seen in slobrok.\"\n" +
                "      },\n" +
                "      \"user\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"3\": {\n" +
                "    \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "    \"state\": {\n" +
                "      \"generated\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"unit\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"user\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"5\": {\n" +
                "    \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "    \"state\": {\n" +
                "      \"generated\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"unit\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"user\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"7\": {\n" +
                "    \"attributes\": {\"hierarchical-group\": \"east.g2\"},\n" +
                "    \"state\": {\n" +
                "      \"generated\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"unit\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      },\n" +
                "      \"user\": {\n" +
                "        \"state\": \"up\",\n" +
                "        \"reason\": \"\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }
}
