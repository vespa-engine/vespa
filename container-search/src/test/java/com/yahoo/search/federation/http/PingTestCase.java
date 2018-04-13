// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.yahoo.component.ComponentId;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.StupidSingleThreadedHttpServer;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;
import com.yahoo.text.Utf8;
import com.yahoo.yolean.Exceptions;
import org.apache.http.HttpEntity;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Check for different keep-alive scenarios. What we really want to test
 * is the server does not hang.
 *
 * @author Steinar Knutsen
 */
public class PingTestCase {

    private static final int TIMEOUT_MS = 60000;

    @Test
    public void testNiceCase() throws Exception {
        NiceStupidServer server = new NiceStupidServer();
        server.start();
        checkSearchAndPing(true, true, true, server.getServerPort());
        server.stop();
    }

    private void checkSearchAndPing(boolean firstSearch, boolean pongCheck, boolean secondSearch, int port) {
        String resultThing;
        String comment;
        TestHTTPClientSearcher searcher = new TestHTTPClientSearcher("test",
                "localhost", port);
        try {

            Query query = new Query("/?query=test");

            query.setWindow(0, 10);
            // high timeout to allow for overloaded test machine
            query.setTimeout(TIMEOUT_MS);
            Ping ping = new Ping(TIMEOUT_MS);

            long start = System.currentTimeMillis();
            Execution exe = new Execution(searcher, Execution.Context.createContextStub());
            exe.search(query);

            resultThing = firstSearch ? "ok" : null;
            comment = firstSearch ? "First search should have succeeded." : "First search should fail.";
            assertEquals(comment, resultThing, query.properties().get("gotResponse"));
            Pong pong = searcher.ping(ping, searcher.getConnection());
            if (pongCheck) {
                assertEquals("Ping should not have failed.", 0, pong.getErrorSize());
            } else {
                assertEquals("Ping should have failed.", 1, pong.getErrorSize());
            }
            exe = new Execution(searcher, Execution.Context.createContextStub());
            exe.search(query);

            resultThing = secondSearch ? "ok" : null;
            comment = secondSearch ? "Second search should have succeeded." : "Second search should fail.";

            assertEquals(resultThing, query.properties().get("gotResponse"));
            long duration = System.currentTimeMillis() - start;
            // target for duration based on the timeout values + some slack
            assertTrue("This test probably hanged.", duration < TIMEOUT_MS + 4000);
            searcher.shutdownConnectionManagers();
        } finally {
            searcher.deconstruct();
        }
    }

    @Test
    public void testUselessCase() throws Exception {
        UselessStupidServer server = new UselessStupidServer();
        server.start();
        checkSearchAndPing(false, true, false, server.getServerPort());
        server.stop();
    }

    @Test
    public void testGrumpyCase() throws Exception {
        GrumpyStupidServer server = new GrumpyStupidServer();
        server.start();
        checkSearchAndPing(false, false, false, server.getServerPort());
        server.stop();
    }

    @Test
    public void testPassiveAggressiveCase() throws Exception {
        PassiveAggressiveStupidServer server = new PassiveAggressiveStupidServer();
        server.start();
        checkSearchAndPing(true, false, true, server.getServerPort());
        server.stop();
    }

    // OK on ping and search
    private static class NiceStupidServer extends StupidSingleThreadedHttpServer {
        private NiceStupidServer() throws IOException {
            super(0, 0);
        }

        @Override
        protected byte[] getResponse(String request) {
            return Utf8.toBytes("HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/xml; charset=UTF-8\r\n" +
                                "Connection: close\r\n" +
                                "Content-Length: 6\r\n" +
                                "\r\n" +
                                "hello\n");
        }
    }

    // rejects ping and accepts search
    private static class PassiveAggressiveStupidServer extends StupidSingleThreadedHttpServer {

        private PassiveAggressiveStupidServer() throws IOException {
            super(0, 0);
        }

        @Override
        protected byte[] getResponse(String request) {
            if (request.contains("/ping")) {
                return Utf8.toBytes("HTTP/1.1 404 Not found\r\n" +
                                    "Content-Type: text/xml; charset=UTF-8\r\n" +
                                    "Connection: close\r\n" +
                                    "Content-Length: 8\r\n" +
                                    "\r\n" +
                                    "go away\n");
            } else {
                return Utf8.toBytes("HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/xml; charset=UTF-8\r\n" +
                                    "Connection: close\r\n" +
                                    "Content-Length: 6\r\n" +
                                    "\r\n" +
                                    "hello\n");
            }
        }
    }

    // accepts ping and rejects search
    private static class UselessStupidServer extends StupidSingleThreadedHttpServer {
        private UselessStupidServer() throws IOException {
            super(0, 0);
        }


        @Override
        protected byte[] getResponse(String request) {
            if (request.contains("/ping")) {
                return Utf8.toBytes("HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/xml; charset=UTF-8\r\n" +
                                    "Connection: close\r\n" +
                                    "Content-Length: 6\r\n" +
                                    "\r\n" +
                                    "hello\n");
            } else {
                return Utf8.toBytes("HTTP/1.1 404 Not found\r\n" +
                                    "Content-Type: text/xml; charset=UTF-8\r\n" +
                                    "Connection: close\r\n" +
                                    "Content-Length: 8\r\n" +
                                    "\r\n" +
                                    "go away\n");
            }
        }
    }

    // rejects ping and search
    private static class GrumpyStupidServer extends StupidSingleThreadedHttpServer {
        private GrumpyStupidServer() throws IOException {
            super(0, 0);
        }

        @Override
        protected byte[] getResponse(String request) {
            return Utf8.toBytes("HTTP/1.1 404 Not found\r\n" +
                                "Content-Type: text/xml; charset=UTF-8\r\n" +
                                "Connection: close\r\n" +
                                "Content-Length: 8\r\n" +
                                "\r\n" +
                                "go away\n");
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
        public Result search(Query query, Execution execution,
                Connection connection) {
            URI uri;
            try {
                uri = new URL("http", connection.getHost(), connection
                        .getPort(), "/search").toURI();
            } catch (MalformedURLException e) {
                query.errors().add(createMalformedUrlError(query, e));
                return execution.search(query);
            } catch (URISyntaxException e) {
                query.errors().add(createMalformedUrlError(query, e));
                return execution.search(query);
            }

            HttpEntity entity;
            try {
                entity = getEntity(uri, query);
            } catch (IOException e) {
                query.errors().add(
                        ErrorMessage.createBackendCommunicationError("Error when trying to connect to HTTP backend in "
                                + this + " using " + connection
                                + " for " + query + ": "
                                + Exceptions.toMessageString(e)));
                return execution.search(query);
            } catch (TimeoutException e) {
                query.errors().add(ErrorMessage.createTimeout("No time left for HTTP traffic in "
                        + this
                        + " for " + query + ": " + e.getMessage()));
                return execution.search(query);
            }
            if (entity == null) {
                query.errors().add(
                        ErrorMessage.createBackendCommunicationError("No result from connecting to HTTP backend in "
                                + this + " using " + connection + " for " + query));
                return execution.search(query);
            }

            try {
                query = handleResponse(entity, query);
            } catch (IOException e) {
                query.errors().add(
                        ErrorMessage.createBackendCommunicationError("Error when trying to consume input in "
                                + this + ": " + Exceptions.toMessageString(e)));
            } finally {
                cleanupHttpEntity(entity);
            }
            return execution.search(query);
        }

        @Override
        public Map<String, String> getCacheKey(Query q) {
            return null;
        }

        @Override
        protected URI getPingURI(Connection connection)
                throws MalformedURLException, URISyntaxException {
            return new URL("http", connection.getHost(), connection.getPort(), "/ping").toURI();
        }

        Connection getConnection() {
            return getHasher().getNodes().select(0, 0);
        }
    }

}
