// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * @author jobergum
 */
public class MockHttpServer {

    private String response;
    private final HttpServer server;

    /**
     * Mock http server that will return response as body
     *
     * @param response the response to return along with 200 OK
     * @param path     the file path that the server will accept requests for. E.g /state/v1/metrics
     */
    public MockHttpServer(String response, String path) throws IOException {
        this.response = response;
        this.server = HttpServer.create(new InetSocketAddress(0), 10);
        this.server.createContext(path, new MyHandler());
        this.server.setExecutor(null); // creates a default executor
        this.server.start();
        System.out.println("Started web server on port " + port());
    }

    public synchronized void setResponse(String r) {
        this.response = r;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void close() {
        this.server.stop(0);
    }

    private class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            synchronized (MockHttpServer.this) {
                t.sendResponseHeaders(200, response != null ? response.length() : 0);
                try (OutputStream os = t.getResponseBody()) {
                    if (response != null) os.write(response.getBytes());
                }
            }
        }
    }

}
