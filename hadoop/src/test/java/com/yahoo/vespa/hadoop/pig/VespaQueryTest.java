package com.yahoo.vespa.hadoop.pig;

import com.sun.net.httpserver.HttpServer;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaConfiguration;
import com.yahoo.vespa.hadoop.mapreduce.util.VespaHttpClient;
import com.yahoo.vespa.hadoop.util.MockQueryHandler;
import junit.framework.Assert;
import org.apache.avro.generic.GenericData;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecJob;
import org.apache.pig.data.Tuple;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VespaQueryTest {

    @Test
    public void requireThatQueriesAreReturnedCorrectly() throws Exception {
        MockQueryHandler queryHandler = createQueryHandler();

        final int port = 18901;
        final String endpoint = "http://localhost:" + port;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", queryHandler);
        server.start();

        PigServer ps = setup("src/test/pig/query.pig", endpoint);

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

    private MockQueryHandler createQueryHandler() {
        MockQueryHandler queryHandler = new MockQueryHandler();

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
