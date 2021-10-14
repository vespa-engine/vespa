// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.pig;

import com.sun.net.httpserver.HttpServer;
import com.yahoo.vespa.hadoop.util.MockQueryHandler;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.data.Tuple;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VespaQueryTest {

    @Test
    public void requireThatQueriesAreReturnedCorrectly() throws Exception {
        runQueryTest("src/test/pig/query.pig", createQueryHandler(""), 18901);
    }

    @Test
    public void requireThatQueriesAreReturnedCorrectlyWithAlternativeJsonRoot() throws Exception {
        runQueryTest("src/test/pig/query_alt_root.pig", createQueryHandler("children"), 18902);
    }

    private void runQueryTest(String script, MockQueryHandler queryHandler, int port) throws Exception {
        final String endpoint = "http://localhost:" + port;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", queryHandler);
        server.start();

        PigServer ps = setup(script, endpoint);

        Iterator<Tuple> recommendations = ps.openIterator("recommendations");
        while (recommendations.hasNext()) {
            Tuple tuple = recommendations.next();

            String userid = (String) tuple.get(0);
            Integer rank = (Integer) tuple.get(1);
            String docid = (String) tuple.get(2);
            Double relevance = (Double) tuple.get(3);
            String fieldId = (String) tuple.get(4);
            String fieldContent = (String) tuple.get(5);

            MockQueryHandler.MockQueryHit hit = queryHandler.getHit(userid, rank);
            assertEquals(docid, hit.id);
            assertEquals(relevance, hit.relevance, 1e-3);
            assertEquals(fieldId, hit.fieldId);
            assertEquals(fieldContent, hit.fieldContent);
        }

        if (server != null) {
            server.stop(0);
        }

    }

    private PigServer setup(String script, String endpoint) throws Exception {
        Configuration conf = new HdfsConfiguration();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("ENDPOINT", endpoint);

        PigServer ps = new PigServer(ExecType.LOCAL, conf);
        ps.setBatchOn();
        ps.registerScript(script, parameters);

        return ps;
    }

    private MockQueryHandler createQueryHandler(String childNode) {
        MockQueryHandler queryHandler = new MockQueryHandler(childNode);

        List<String> userIds = Arrays.asList("5", "104", "313");

        int hitsPerUser = 3;
        for (int i = 0; i < hitsPerUser * userIds.size(); ++i) {
            String id = "" + (i+1);
            String userId = userIds.get(i / hitsPerUser);
            queryHandler.newHit().
                    setId("id::::" + id).
                    setRelevance(1.0 - (i % hitsPerUser) * 0.1).
                    setFieldSddocname("doctype").
                    setFieldId("" + id).
                    setFieldDate("2016060" + id).
                    setFieldContent("Content for user " + userId + " hit " + i % hitsPerUser + "...").
                    add(userId);
        }

        return queryHandler;
    }

}
