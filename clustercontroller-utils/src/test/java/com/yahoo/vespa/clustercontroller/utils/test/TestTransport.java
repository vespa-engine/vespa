// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.test;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequestHandler;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * This class is a utility for unit tests.. You can register HttpRequestHandler instances in it, and then
 * you can extract an AsyncHttpClient&lt;HttpResult&gt; instance from it, which you can use to talk to the
 * registered servers. Thus you can do end to end testing of components talking over HTTP without actually
 * going through HTTP if you are using the HTTP abstraction layer in communication.http package.
 */
public class TestTransport {

    private static final Logger log = Logger.getLogger(TestTransport.class.getName());
    private static class Handler {
        HttpRequestHandler handler;
        String pathPrefix;
        Handler(HttpRequestHandler h, String prefix) { this.handler = h; this.pathPrefix = prefix; }
    }
    private static class Socket {
        public final String hostname;
        public final int port;

        Socket(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Socket)) return false;
            Socket other = (Socket) o;
            return (hostname.equals(other.hostname) && port == other.port);
        }
        @Override
        public int hashCode() {
            return hostname.hashCode() * port;
        }
    }
    private static class Request {
        public final HttpRequest request;
        public final AsyncOperationImpl<HttpResult> result;

        Request(HttpRequest r, AsyncOperationImpl<HttpResult> rr) {
            this.request = r;
            this.result = rr;
        }
    }
    private final Map<Socket, List<Handler>> handlers = new HashMap<>();
    private final LinkedList<Request> requests = new LinkedList<>();
    private final AsyncHttpClient<HttpResult> client = new AsyncHttpClient<>() {
        @Override
        public AsyncOperation<HttpResult> execute(HttpRequest r) {
            log.fine("Queueing request " + r);
            if (r.getHttpOperation() == null) {
                r = r.clone();
                r.setHttpOperation(r.getPostContent() == null ? HttpRequest.HttpOp.GET : HttpRequest.HttpOp.POST);
            }
            r.verifyComplete();
            AsyncOperationImpl<HttpResult> op = new AsyncOperationImpl<>(r.toString());
            synchronized (requests) {
                requests.addLast(new Request(r, op));
            }
            return op;
        }

        @Override
        public void close() { TestTransport.this.close(); }
    };
    private boolean running = true;
    private final Thread workerThread = new Thread() {
        @Override
        public void run() {
            while (running) {
                synchronized (requests) {
                    if (requests.isEmpty()) {
                        try {
                            requests.wait(100);
                        } catch (InterruptedException e) { return; }
                    } else {
                        Request request = requests.removeFirst();
                        HttpRequest r = request.request;
                        log.fine("Processing request " + r);
                        HttpRequestHandler handler = getHandler(r);
                        if (handler == null) {
                            if (log.isLoggable(Level.FINE)) {
                                log.fine("Failed to find target for request " + r.toString(true));
                                log.fine("Existing handlers:");
                                for (Socket socket : handlers.keySet()) {
                                    log.fine("  Socket " + socket.hostname + ":" + socket.port);
                                    for (Handler h : handlers.get(socket)) {
                                        log.fine("    " + h.pathPrefix);
                                    }
                                }
                            }
                            request.result.setResult(new HttpResult().setHttpCode(
                                    404, "No such server socket with suitable path prefix found open"));
                        } else {
                            try{
                                request.result.setResult(handler.handleRequest(r));
                            } catch (Exception e) {
                                HttpResult result = new HttpResult().setHttpCode(500, e.getMessage());
                                StringWriter sw = new StringWriter();
                                e.printStackTrace(new PrintWriter(sw));
                                result.setContent(sw.toString());
                                request.result.setResult(result);
                            }
                        }
                        //log.fine("Request " + r.toString(true) + " created result " + request.getSecond().getResult().toString(true));
                    }
                }
            }
        }
    };

    public TestTransport() {
        workerThread.start();
    }

    public void close() {
        if (!running) return;
        running = false;
        synchronized (requests) { requests.notifyAll(); }
        try {
            workerThread.join();
        } catch (InterruptedException e) {}
    }

    /** Get an HTTP client that talks to this test transport layer. */
    public AsyncHttpClient<HttpResult> getClient() { return client; }

    private HttpRequestHandler getHandler(HttpRequest r) {
        Socket socket = new Socket(r.getHost(), r.getPort());
        synchronized (this) {
            List<Handler> handlerList = handlers.get(socket);
            if (handlerList == null) {
                log.fine("No socket match");
                return null;
            }
            log.fine("Socket found");
            for (Handler h : handlers.get(socket)) {
                if (r.getPath().length() >= h.pathPrefix.length() && r.getPath().startsWith(h.pathPrefix)) {
                    return h.handler;
                }
            }
            log.fine("No path prefix match");
        }
        return null;
    }

    public void addServer(HttpRequestHandler server, String hostname, int port, String pathPrefix) {
        Socket socket = new Socket(hostname, port);
        synchronized (this) {
            List<Handler> shandlers = handlers.get(socket);
            if (shandlers == null) {
                shandlers = new LinkedList<>();
                handlers.put(socket, shandlers);
            }
            shandlers.add(new Handler(server, pathPrefix));
        }
    }

    public void removeServer(HttpRequestHandler server, String hostname, int port, String pathPrefix) {
        Socket socket = new Socket(hostname, port);
        synchronized (this) {
            List<Handler> shandlers = handlers.get(socket);
            if (shandlers == null) return;
            for (Handler h : shandlers) {
                if (h.handler == server && h.pathPrefix.equals(pathPrefix)) {
                    shandlers.remove(h);
                }
            }
        }
    }

}
