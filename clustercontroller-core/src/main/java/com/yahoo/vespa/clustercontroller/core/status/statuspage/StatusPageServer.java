// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status.statuspage;

import com.yahoo.log.LogLevel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows status pages with debug information through a very simple HTTP interface.
 */
public class StatusPageServer implements Runnable, StatusPageServerInterface {

    public static Logger log = Logger.getLogger(StatusPageServer.class.getName());

    private final com.yahoo.vespa.clustercontroller.core.Timer timer;
    private final Object monitor;
    private ServerSocket ssocket;
    private final Thread runner;
    private int port = 0;
    private boolean running = true;
    private boolean shouldBeConnected = false;
    private HttpRequest currentHttpRequest = null;
    private StatusPageResponse currentResponse = null;
    private long lastConnectErrorTime = 0;
    private String lastConnectError = "";
    private PatternRequestRouter staticContentRouter = new PatternRequestRouter();
    private Date startTime = new Date();

    public StatusPageServer(com.yahoo.vespa.clustercontroller.core.Timer timer, Object monitor, int port) throws java.io.IOException, InterruptedException {
        this.timer = timer;
        this.monitor = monitor;
        this.port = port;
        connect();
        runner = new Thread(this);
        runner.start();
    }

    public boolean isConnected() {
        if (ssocket != null && ssocket.isBound() && (ssocket.getLocalPort() == port || port == 0)) {
            return true;
        } else {
            log.log(LogLevel.SPAM, "Status page server socket is no longer connected: "+ (ssocket != null) + " " + ssocket.isBound() + " " + ssocket.getLocalPort() + " " + port);
            return false;
        }
    }

    public void connect() throws java.io.IOException, InterruptedException {
        synchronized(monitor) {
            if (ssocket != null) {
                if (ssocket.isBound() && ssocket.getLocalPort() == port) {
                    return;
                }
                disconnect();
            }
            ssocket = new ServerSocket();
            if (port != 0) {
                ssocket.setReuseAddress(true);
            }
            ssocket.setSoTimeout(100);
            ssocket.bind(new InetSocketAddress(port));
            shouldBeConnected = true;
            for (int i=0; i<200; ++i) {
                if (isConnected()) break;
                Thread.sleep(10);
            }
            if (!isConnected()) {
                log.log(LogLevel.INFO, "Fleetcontroller: Server Socket not ready after connect()");
            }
            log.log(LogLevel.DEBUG, "Fleet controller status page viewer listening to " + ssocket.getLocalSocketAddress());
            monitor.notifyAll();
        }
    }

    public void disconnect() throws java.io.IOException {
        synchronized(monitor) {
            shouldBeConnected = false;
            if (ssocket != null) ssocket.close();
            ssocket = null;
            monitor.notifyAll();
        }
    }

    public void setPort(int port) throws java.io.IOException, InterruptedException {
            // Only bother to reconnect if we were connected to begin with, we care about what port it runs on, and it's not already running there
        if (port != 0 && isConnected() && port != ((InetSocketAddress) ssocket.getLocalSocketAddress()).getPort()) {
            log.log(LogLevel.INFO, "Exchanging port used by status server. Moving from port "
                    + ((InetSocketAddress) ssocket.getLocalSocketAddress()).getPort() + " to port " + port);
            disconnect();
            this.port = port;
            if (ssocket == null || !ssocket.isBound() || ssocket.getLocalPort() != port) {
                connect();
            }
        } else {
            this.port = port;
        }
    }

    public int getPort() {
        // Cannot use  this.port, because of tests using port 0 to get any address
        if (ssocket == null || !ssocket.isBound()) {
            throw new IllegalStateException("Cannot ask for port before server socket is bound");
        }
        return ((InetSocketAddress) ssocket.getLocalSocketAddress()).getPort();
    }

    public void shutdown() throws InterruptedException, java.io.IOException {
        running = false;
        runner.interrupt();
        runner.join();
        disconnect();
    }

    public void run() {
        try{
            while (running) {
                Socket connection = null;
                ServerSocket serverSocket = null;
                synchronized(monitor) {
                    if (ssocket == null || !ssocket.isBound()) {
                        monitor.wait(1000);
                        continue;
                    }
                    serverSocket = ssocket;
                }
                try{
                    connection = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    // Ignore, since timeout is set to 100 ms
                } catch (java.io.IOException e) {
                    log.log(shouldBeConnected ? LogLevel.WARNING : LogLevel.DEBUG, "Caught IO exception in ServerSocket.accept(): " + e.getMessage());
                }
                if (connection == null) continue;
                log.log(LogLevel.DEBUG, "Got a status page request.");
                String requestString = "";
                OutputStream output = null;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    while (true) {
                        String s = br.readLine();
                        if (s == null) throw new java.io.IOException("No data in HTTP request on socket " + connection.toString());
                        if (s.length() > 4 && s.substring(0,4).equals("GET ")) {
                            int nextSpace = s.indexOf(' ', 4);
                            if (nextSpace == -1) {
                                requestString = s.substring(4);
                            } else {
                                requestString = s.substring(4, nextSpace);
                            }
                        }
                        if (s == null || s.equals("")) break;
                        sb.append(s).append("\n");
                    }
                    log.log(LogLevel.DEBUG, "Got HTTP request: " + sb.toString());

                    HttpRequest httpRequest = null;
                    StatusPageResponse response = null;
                    try {
                        httpRequest = new HttpRequest(requestString);
                        // Static files are served directly by the HTTP server thread, since
                        // it makes no sense to go via the fleetcontroller logic for these.
                        RequestHandler contentHandler = staticContentRouter.resolveHandler(httpRequest);
                        if (contentHandler != null) {
                            response = contentHandler.handle(httpRequest);
                        }
                    } catch (Exception e) {
                        response = new StatusPageResponse();
                        response.setResponseCode(StatusPageResponse.ResponseCode.INTERNAL_SERVER_ERROR);
                        StringBuilder content = new StringBuilder();
                        response.writeHtmlHeader(content, "Internal Server Error");
                        try (StringWriter sw = new StringWriter();
                             PrintWriter pw = new PrintWriter(sw, true)) {
                            e.printStackTrace(pw);
                            response.writeHtmlFooter(content, sw.getBuffer().toString());
                        }
                        response.writeContent(content.toString());
                    }
                    if (response == null) {
                        synchronized(monitor) {
                            currentHttpRequest = httpRequest;
                            currentResponse = null;
                            while (running) {
                                if (currentResponse != null) {
                                    response = currentResponse;
                                    break;
                                }
                                monitor.wait(100);
                            }
                        }
                    }
                    if (response == null) {
                        response = new StatusPageResponse();
                        StringBuilder content = new StringBuilder();
                        response.setContentType("text/html");
                        response.writeHtmlHeader(content, "Failed to get response. Fleet controller probably in the process of shutting down.");
                        response.writeHtmlFooter(content, "");
                        response.writeContent(content.toString());
                    }

                    output = connection.getOutputStream();
                    StringBuilder header = new StringBuilder();
                    // TODO: per-response cache control
                    header.append("HTTP/1.1 ")
                            .append(response.getResponseCode().getCode())
                            .append(" ")
                            .append(response.getResponseCode().getMessage())
                            .append("\r\n")
                            .append("Date: ").append(new Date().toString()).append("\r\n")
                            .append("Connection: Close\r\n")
                            .append("Content-type: ").append(response.getContentType()).append("\r\n");
                    if (response.isClientCachingEnabled()) {
                        // TODO(vekterli): would be better to let HTTP handlers set header values in response
                        DateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
                        df.setTimeZone(TimeZone.getTimeZone("GMT"));
                        header.append("Last-Modified: ").append(df.format(startTime)).append("\r\n");
                    } else {
                        header.append("Expires: Fri, 01 Jan 1990 00:00:00 GMT\r\n")
                                .append("Pragma: no-cache\r\n")
                                .append("Cache-control: no-cache, must-revalidate\r\n");
                    }
                    header.append("\r\n");
                    output.write(header.toString().getBytes());
                    output.write(response.getOutputStream().toByteArray());
                } catch (java.io.IOException e) {
                   log.log(e.getMessage().indexOf("Broken pipe") >= 0 ? LogLevel.DEBUG : LogLevel.INFO,
                           "Failed to process HTTP request : " + e.getMessage());
                } catch (Exception e) {
                    log.log(LogLevel.WARNING, "Caught exception in HTTP server thread: "
                            + e.getClass().getName() + ": " + e.getMessage());
                } finally {
                    if (output != null) try {
                        output.close();
                    } catch (IOException e) {
                        log.log(e.getMessage().indexOf("Broken pipe") >= 0 ? LogLevel.DEBUG : LogLevel.INFO,
                                "Failed to close output stream on socket " + connection + ": " + e.getMessage());
                    }
                    if (connection != null) try{
                        connection.close();
                    } catch (IOException e) {
                        log.log(LogLevel.INFO, "Failed to close socket " + connection + ": " + e.getMessage());
                    }
                }
            }
        } catch (InterruptedException e) {
            log.log(LogLevel.DEBUG,  "Status processing thread shut down by interrupt exception: " + e);
        }
    }

    /**
     * Very simple HTTP request class. This should be replaced the second
     * the fleetcontroller e.g. moves into the container.
     */
    public static class HttpRequest {
        private final String request;
        private String pathPrefix = "";
        private final Map<String, String> params = new HashMap<String, String>();
        private String path;

        static Pattern pathPattern;
        static {
            // NOTE: allow [=.] in path to be backwards-compatible with legacy node
            // status pages.
            // If you stare at it for long enough, this sorta looks like one of those
            // magic eye pictures.
            pathPattern = Pattern.compile("^(/([\\w=\\./]+)?)(?:\\?((?:&?\\w+(?:=[\\w\\.]*)?)*))?$");
        }

        public HttpRequest(String request) {
            this.request = request;
            Matcher m = pathPattern.matcher(request);
            if (!m.matches()) {
                throw new IllegalArgumentException("Illegal HTTP request path: " + request);
            }
            path = m.group(1);
            if (m.group(3) != null) {
                String[] rawParams = m.group(3).split("&");
                for (String param : rawParams) {
                    // Parameter values are optional.
                    String[] queryParts = param.split("=");
                    params.put(queryParts[0], queryParts.length > 1 ? queryParts[1] : null);
                }
            }
        }

        public String getPathPrefix() { return pathPrefix; }

        public String toString() {
            return "HttpRequest(" + request + ")";
        }

        public String getRequest() {
            return request;
        }

        public String getPath() {
            return path;
        }

        public boolean hasQueryParameters() {
            return !params.isEmpty();
        }

        public String getQueryParameter(String name) {
            return params.get(name);
        }

        public boolean hasQueryParameter(String name) {
            return params.containsKey(name);
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }
    }

    public interface RequestHandler {
        StatusPageResponse handle(HttpRequest request);
    }

    public interface RequestRouter {
        /**
         * Resolve a request's handler based on its path.
         * @param request HTTP request to resolve for.
         * @return the request handler, or null if none matched.
         */
        RequestHandler resolveHandler(HttpRequest request);
    }

    /**
     * Request router inspired by the Django framework's regular expression
     * based approach. Patterns are matched in the same order as they were
     * added to the router and the first matching one is used as the handler.
     */
    public static class PatternRequestRouter implements RequestRouter {
        private static class PatternRouting {
            public Pattern pattern;
            public RequestHandler handler;

            private PatternRouting(Pattern pattern, RequestHandler handler) {
                this.pattern = pattern;
                this.handler = handler;
            }
        }

        private List<PatternRouting> patterns = new ArrayList<>();

        public void addHandler(Pattern pattern, RequestHandler handler) {
            patterns.add(new PatternRouting(pattern, handler));
        }

        public void addHandler(String pattern, RequestHandler handler) {
            addHandler(Pattern.compile(pattern), handler);
        }

        @Override
        public RequestHandler resolveHandler(HttpRequest request) {
            for (PatternRouting routing : patterns) {
                Matcher m = routing.pattern.matcher(request.getPath());
                if (m.matches()) {
                    return routing.handler;
                }
            }
            return null;
        }
    }

    public HttpRequest getCurrentHttpRequest() {
        synchronized (monitor) {
            return currentHttpRequest;
        }
    }

    public void answerCurrentStatusRequest(StatusPageResponse r) {
        if (!isConnected()) {
            long time = timer.getCurrentTimeInMillis();
            try{
                connect();
            } catch (Exception e) {
                if (!e.getMessage().equals(lastConnectError) || time - lastConnectErrorTime > 60 * 1000) {
                    lastConnectError = e.getMessage();
                    lastConnectErrorTime = time;
                    log.log(LogLevel.WARNING, "Failed to initialize HTTP status server server socket: " + e.getMessage());
                }
            }
        }
        synchronized (monitor) {
            currentResponse = r;
            currentHttpRequest = null; // Avoid fleetcontroller processing request more than once
        }
    }

}
