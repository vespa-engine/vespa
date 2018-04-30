// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.compress.CompressionType;
import com.yahoo.log.event.Collection;
import com.yahoo.prelude.fastsearch.DocsumDefinition;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.prelude.fastsearch.DocsumField;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Tests using a dispatcher to fill a result
 *
 * @author bratseth
 */
public class FillTestCase {

    private MockClient client = new MockClient();

    @Test
    public void testFilling() {
        Map<Integer, Client.NodeConnection> nodes = new HashMap<>();
        nodes.put(0, client.createConnection("host0", 123));
        nodes.put(1, client.createConnection("host1", 123));
        nodes.put(2, client.createConnection("host2", 123));
        Dispatcher dispatcher = new Dispatcher(nodes, client);

        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHit(0, 0));
        result.hits().add(createHit(2, 1));
        result.hits().add(createHit(1, 2));
        result.hits().add(createHit(2, 3));
        result.hits().add(createHit(0, 4));

        client.setDocsumReponse("host0", 0, "summaryClass1", map("field1", "s.0.0", "field2", 0));
        client.setDocsumReponse("host2", 1, "summaryClass1", map("field1", "s.2.1", "field2", 1));
        client.setDocsumReponse("host1", 2, "summaryClass1", map("field1", "s.1.2", "field2", 2));
        client.setDocsumReponse("host2", 3, "summaryClass1", map("field1", "s.2.3", "field2", 3));
        client.setDocsumReponse("host0", 4, "summaryClass1", map("field1", "s.0.4", "field2", 4));

        dispatcher.fill(result, "summaryClass1", db(), CompressionType.valueOf("LZ4"));

        assertEquals("s.0.0", result.hits().get("hit:0").getField("field1").toString());
        assertEquals("s.2.1", result.hits().get("hit:1").getField("field1").toString());
        assertEquals("s.1.2", result.hits().get("hit:2").getField("field1").toString());
        assertEquals("s.2.3", result.hits().get("hit:3").getField("field1").toString());
        assertEquals("s.0.4", result.hits().get("hit:4").getField("field1").toString());
        assertEquals(0L, result.hits().get("hit:0").getField("field2"));
        assertEquals(1L, result.hits().get("hit:1").getField("field2"));
        assertEquals(2L, result.hits().get("hit:2").getField("field2"));
        assertEquals(3L, result.hits().get("hit:3").getField("field2"));
        assertEquals(4L, result.hits().get("hit:4").getField("field2"));
    }

    @Test
    public void testEmptyHits() {
        Map<Integer, Client.NodeConnection> nodes = new HashMap<>();
        nodes.put(0, client.createConnection("host0", 123));
        nodes.put(1, client.createConnection("host1", 123));
        nodes.put(2, client.createConnection("host2", 123));
        Dispatcher dispatcher = new Dispatcher(nodes, client);

        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHit(0, 0));
        result.hits().add(createHit(2, 1));
        result.hits().add(createHit(1, 2));
        result.hits().add(createHit(2, 3));
        result.hits().add(createHit(0, 4));

        client.setDocsumReponse("host0", 0, "summaryClass1", map("field1", "s.0.0", "field2", 0));
        client.setDocsumReponse("host2", 1, "summaryClass1", map("field1", "s.2.1", "field2", 1));
        client.setDocsumReponse("host1", 2, "summaryClass1", new HashMap<>());
        client.setDocsumReponse("host2", 3, "summaryClass1", map("field1", "s.2.3", "field2", 3));
        client.setDocsumReponse("host0", 4, "summaryClass1",new HashMap<>());
        dispatcher.fill(result, "summaryClass1", db(), CompressionType.valueOf("LZ4"));

        assertEquals("s.0.0", result.hits().get("hit:0").getField("field1").toString());
        assertEquals("s.2.1", result.hits().get("hit:1").getField("field1").toString());
        assertNull(result.hits().get("hit:2").getField("field1"));
        assertEquals("s.2.3", result.hits().get("hit:3").getField("field1").toString());
        assertNull(result.hits().get("hit:4").getField("field1"));

        assertEquals(0L, result.hits().get("hit:0").getField("field2"));
        assertEquals(1L, result.hits().get("hit:1").getField("field2"));
        assertNull(result.hits().get("hit:2").getField("field2"));
        assertEquals(3L, result.hits().get("hit:3").getField("field2"));
        assertNull(result.hits().get("hit:4").getField("field2"));

        assertEquals("Missing hit summary data for summary summaryClass1 for 2 hits", result.hits().getError().getDetailedMessage());
    }

    @Test
    public void testErrorHandling() {
        client.setMalfunctioning(true);

        Map<Integer, Client.NodeConnection> nodes = new HashMap<>();
        nodes.put(0, client.createConnection("host0", 123));
        Dispatcher dispatcher = new Dispatcher(nodes, client);

        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHit(0, 0));

        dispatcher.fill(result, "summaryClass1", db(), CompressionType.valueOf("LZ4"));

        assertEquals("Malfunctioning", result.hits().getError().getDetailedMessage());
    }

    private DocumentDatabase db() {
        List<DocsumField> fields = new ArrayList<>();
        fields.add(DocsumField.create("field1", "string"));
        fields.add(DocsumField.create("field2", "int64"));
        DocsumDefinitionSet docsums = new DocsumDefinitionSet(Collections.singleton(new DocsumDefinition("summaryClass1",
                                                                                                         fields)));
        return new DocumentDatabase("default", docsums, Collections.emptySet());
    }

    private FastHit createHit(int sourceNodeId, int hitId) {
        FastHit hit = new FastHit("hit:" + hitId, 1.0);
        hit.setPartId(sourceNodeId);
        hit.setDistributionKey(sourceNodeId);
        hit.setGlobalId(client.globalIdFrom(hitId));
        return hit;
    }

    private Map<String, Object> map(String stringKey, String stringValue, String intKey, int intValue) {
        Map<String, Object> map = new HashMap<>();
        map.put(stringKey, stringValue);
        map.put(intKey, intValue);
        return map;
    }

}
