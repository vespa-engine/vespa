// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.utils.staterestapi.response.UnitResponse;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PartitionTest extends StateRestApiTest {

    @Test
    public void testPartition() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1/0", 0));
        String expected =
                "{\n" +
                "  \"state\": {\"generated\": {\n" +
                "    \"state\": \"up\",\n" +
                "    \"reason\": \"\"\n" +
                "  }},\n" +
                "  \"metrics\": {\n" +
                "    \"bucket-count\": 1,\n" +
                "    \"unique-document-count\": 2,\n" +
                "    \"unique-document-total-size\": 3\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }

    @Test
    public void testRecursiveCluster() throws Exception {
        setUp(true);
        UnitResponse response = restAPI.getState(new StateRequest("music/storage/1/0", 1));
        String expected =
                "{\n" +
                "  \"state\": {\"generated\": {\n" +
                "    \"state\": \"up\",\n" +
                "    \"reason\": \"\"\n" +
                "  }},\n" +
                "  \"metrics\": {\n" +
                "    \"bucket-count\": 1,\n" +
                "    \"unique-document-count\": 2,\n" +
                "    \"unique-document-total-size\": 3\n" +
                "  }\n" +
                "}";
        assertEquals(expected, jsonWriter.createJson(response).toString(2));
    }
}
