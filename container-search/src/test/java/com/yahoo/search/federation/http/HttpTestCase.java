// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.StupidSingleThreadedHttpServer;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;
import com.yahoo.text.Utf8;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Rudimentary http searcher test.
 *
 * @author bratseth
 */
public class HttpTestCase {

    private StupidSingleThreadedHttpServer httpServer;
    private TestHTTPClientSearcher searcher;

    @Test
    public void testSearcher() throws JAXBException {
        Result result = searchUsingLocalhost();

        assertEquals("ok", result.getQuery().properties().get("gotResponse"));
        assertEquals(0, result.getQuery().errors().size());
    }

    private Result searchUsingLocalhost() {
        searcher = new TestHTTPClientSearcher("test","localhost",getPort());
        Query query = new Query("/?query=test");

        query.setWindow(0,10);
        return searcher.search(query, new Execution(searcher, Execution.Context.createContextStub()));
    }

    @Test
    public void test_that_ip_address_set_on_meta_hit() {
        Result result = searchUsingLocalhost();
        Hit metaHit = getFirstMetaHit(result.hits());
        String ip = (String) metaHit.getField(HTTPSearcher.LOG_IP_ADDRESS);

        assertEquals(ip, "127.0.0.1");
    }

    private Hit getFirstMetaHit(HitGroup hits) {
        for (Iterator<Hit> i = hits.unorderedDeepIterator(); i.hasNext();) {
            Hit hit = i.next();
            if (hit.isMeta())
                return hit;
        }
        return null;
    }

    @Before
    public void setUp() throws Exception {
        httpServer = new StupidSingleThreadedHttpServer(0, 0) {
            @Override
            protected byte[] getResponse(String request) {
                return Utf8.toBytes("HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/xml; charset=UTF-8\r\n" +
                                    "Connection: close\r\n" +
                                    "Content-Length: 5\r\n" +
                                    "\r\n" +
                                    "hello");
            }
        };
        httpServer.start();
    }

    private int getPort() {
        return httpServer.getServerPort();
    }

    @After
    public void tearDown() throws Exception {
        httpServer.stop();
        if (searcher != null) {
            searcher.shutdownConnectionManagers();
        }
    }

    private static class TestHTTPClientSearcher extends HTTPClientSearcher {

        public TestHTTPClientSearcher(String id, String hostName, int port) {
            super(new ComponentId(id), toConnections(hostName,port), "", Statistics.nullImplementation);
        }

        private static List<Connection> toConnections(String hostName,int port) {
            List<Connection> connections=new ArrayList<>();
            connections.add(new Connection(hostName,port));
            return connections;
        }

        @Override
        public Query handleResponse(InputStream inputStream, long contentLength, Query query) throws IOException {
            query.properties().set("gotResponse","ok");
            return query;
        }

        @Override
        public Map<String, String> getCacheKey(Query q) {
            return null;
        }

    }

}
