// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.collections.Tuple2;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.metrics.DocumentApiMetricsHelper;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpResponse.Status;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.net.HostName;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

/**
 * Accept feeds from outside of the Vespa cluster.
 *
 * @author Steinar Knutsen
 * @since 5.1
 */
public class FeedHandler extends LoggingRequestHandler {

    private final ExecutorService workers = Executors.newCachedThreadPool(ThreadFactoryFactory.getThreadFactory("feedhandler"));
    private final DocumentTypeManager docTypeManager;
    private final Map<String, ClientState> clients;
    private final ScheduledThreadPoolExecutor cron;
    private final SessionCache sessionCache;
    protected final ReplyHandler feedReplyHandler;
    private final AtomicLong sessionId;
    private final Metric metric;
    private static final List<Integer> serverSupportedVersions = Collections.unmodifiableList(Arrays.asList(2));
    private final String localHostname;
    private final FeedHandlerV3 feedHandlerV3;

    @Inject
    public FeedHandler(
            Executor executor,
            DocumentmanagerConfig documentManagerConfig,
            SessionCache sessionCache,
            Metric metric,
            AccessLog accessLog,
            ThreadpoolConfig threadpoolConfig,
            MetricReceiver metricReceiver) throws Exception {
        super(executor, accessLog);
        DocumentApiMetricsHelper metricsHelper = new DocumentApiMetricsHelper(metricReceiver, "vespa.http.server");
        feedHandlerV3 = new FeedHandlerV3(executor, documentManagerConfig, sessionCache, metric, accessLog, threadpoolConfig, metricsHelper);
        docTypeManager = createDocumentManager(documentManagerConfig);
        clients = new HashMap<>();
        this.sessionCache = sessionCache;
        sessionId = new AtomicLong(new Random(System.currentTimeMillis()).nextLong());
        feedReplyHandler = new FeedReplyReader(metric, metricsHelper);
        cron = new ScheduledThreadPoolExecutor(1, ThreadFactoryFactory.getThreadFactory("feedhandler.cron"));
        cron.scheduleWithFixedDelay(new CleanClients(), 16, 11, TimeUnit.MINUTES);
        this.metric = metric;
        this.localHostname = resolveLocalHostname();
    }

    /**
     * Exposed for creating mocks.
     */
    protected DocumentTypeManager createDocumentManager(DocumentmanagerConfig documentManagerConfig) {
        return new DocumentTypeManager(documentManagerConfig);
    }

    private class CleanClients implements Runnable {

        @Override
        public void run() {
            List<ClientState> clientsToShutdown = new ArrayList<>();
            long now = System.currentTimeMillis();

            synchronized (clients) {
                for (Iterator<Map.Entry<String, ClientState>> i = clients
                        .entrySet().iterator(); i.hasNext();) {
                    ClientState client = i.next().getValue();

                    if (now - client.creationTime > 10 * 60 * 1000) {
                        clientsToShutdown.add(client);
                        i.remove();
                    }
                }
            }
            for (ClientState client : clientsToShutdown) {
                client.sourceSession.getReference().close();
            }
        }
    }

    private Tuple2<HttpResponse, Integer> checkProtocolVersion(HttpRequest request) {
        return doCheckProtocolVersion(request.getJDiscRequest().headers().get(Headers.VERSION));
    }

    static Tuple2<HttpResponse, Integer> doCheckProtocolVersion(List<String> clientSupportedVersions) {
        List<String> washedClientVersions = splitVersions(clientSupportedVersions);

        if (washedClientVersions == null || washedClientVersions.isEmpty()) {
            return new Tuple2<>(new ErrorHttpResponse(
                    Headers.HTTP_NOT_ACCEPTABLE,
                    "Request did not contain " + Headers.VERSION
                    + "header. Server supports protocol versions "
                    + serverSupportedVersions), -1);
        }

        //select the highest version supported by both parties
        //this could be extended when we support a gazillion versions - but right now: keep it simple.
        int version;
        if (washedClientVersions.contains("3")) {
            version = 3;
        } else if (washedClientVersions.contains("2")) {
            version = 2;
        } else {
            return new Tuple2<>(new ErrorHttpResponse(
                    Headers.HTTP_NOT_ACCEPTABLE,
                    "Could not parse " + Headers.VERSION
                    + "header of request (values: " + washedClientVersions +
                    "). Server supports protocol versions "
                    + serverSupportedVersions), -1);
        }
        return new Tuple2<>(null, version);
    }

    private static List<String> splitVersions(List<String> clientSupportedVersions) {
        List<String> splittedVersions = new ArrayList<>();
        for (String v : clientSupportedVersions) {
            if (v == null || v.trim().isEmpty()) {
                continue;
            }
            if (!v.contains(",")) {
                splittedVersions.add(v.trim());
                continue;
            }
            for (String part : v.split(",")) {
                part = part.trim();
                if (!part.isEmpty()) {
                    splittedVersions.add(part);
                }
            }
        }
        return splittedVersions;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Tuple2<HttpResponse, Integer> protocolVersion = checkProtocolVersion(request);

        if (protocolVersion.first != null) {
            return protocolVersion.first;
        }
        if (3 == protocolVersion.second) {
            return feedHandlerV3.handle(request);
        }
        final BlockingQueue<OperationStatus> operations = new LinkedBlockingQueue<>();
        Tuple2<String, Boolean> clientId;
        clientId = sessionId(request);

        if (clientId.second != null && clientId.second) {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Received initial request from client with session ID " +
                                        clientId.first + ", protocol version " + protocolVersion.second);
            }
        }

        Feeder feeder;
        try {
            feeder = createFeeder(request, request.getData(), operations, clientId.first, 
                                  clientId.second, protocolVersion.second);
            // the synchronous FeedResponse blocks draining the InputStream, letting the Feeder read it
            workers.submit(feeder);
        } catch (UnknownClientException uce) {
            String msg = Exceptions.toMessageString(uce);
            log.log(LogLevel.WARNING, msg);
            return new ErrorHttpResponse(Status.BAD_REQUEST, msg);
        } catch (Exception e) {
            String msg = "Could not initialize document parsing";
            log.log(LogLevel.WARNING, "Could not initialize document parsing", e);
            return new ErrorHttpResponse(Status.INTERNAL_SERVER_ERROR, msg + ": " + Exceptions.toMessageString(e));
        }

        try {
            feeder.waitForRequestReceived();
        } catch (InterruptedException e) {
            return new ErrorHttpResponse(Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        return new FeedResponse(200, operations, protocolVersion.second, clientId.first);
    }

    // Protected for testing
    protected static InputStream unzipStreamIfNeeded(InputStream inputStream, HttpRequest httpRequest)
            throws IOException {
        String contentEncodingHeader = httpRequest.getHeader("content-encoding");
        if ("gzip".equals(contentEncodingHeader)) {
            return new GZIPInputStream(inputStream);
        } else {
            return inputStream;
        }
    }

    /**
     * Exposed for creating mocks.
     */
    protected Feeder createFeeder(
            HttpRequest request,
            InputStream requestInputStream,
            BlockingQueue<OperationStatus> operations,
            String clientId,
            boolean sessionIdWasGeneratedJustNow,
            int protocolVersion) throws Exception {
        if (protocolVersion != 2) 
            throw new IllegalStateException("Protocol version " + protocolVersion + " not supported.");

        return new Feeder(
                unzipStreamIfNeeded(requestInputStream, request),
                new FeedReaderFactory(),
                docTypeManager,
                operations,
                popClient(clientId),
                new FeederSettings(request),
                clientId,
                sessionIdWasGeneratedJustNow,
                sourceSessionParams(request),
                sessionCache,
                this,
                metric,
                feedReplyHandler,
                localHostname);
    }

    private Tuple2<String, Boolean> sessionId(HttpRequest request) {
        boolean sessionIdWasGeneratedJustNow = false;
        String sessionId = request.getHeader(Headers.SESSION_ID);
        if (sessionId == null) {
            sessionId = Long.toString(this.sessionId.incrementAndGet()) + "-" +
                        remoteHostAddressAndPort(request.getJDiscRequest()) + "#" +
                        localHostname;
            sessionIdWasGeneratedJustNow = true;
        }
        return new Tuple2<>(sessionId, sessionIdWasGeneratedJustNow);
    }

    private static String remoteHostAddressAndPort(com.yahoo.jdisc.http.HttpRequest httpRequest) {
        SocketAddress remoteAddress = httpRequest.getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) remoteAddress;
            return isa.getAddress().getHostAddress() + "-" + isa.getPort();
        }
        return "";
    }

    private static String resolveLocalHostname() {
        String hostname = HostName.getLocalhost();
        if (hostname.equals("localhost")) {
            return "";
        }
        return hostname;
    }

    /**
     * Exposed for use when creating mocks.
     */
    protected SourceSessionParams sourceSessionParams(HttpRequest request) {
        SourceSessionParams params = new SourceSessionParams();
        String timeout = request.getHeader(Headers.TIMEOUT);

        if (timeout != null) {
            try {
                params.setTimeout(Double.parseDouble(timeout));
            } catch (NumberFormatException e) {
                // NOP
            }
        }
        return params;
    }

    @Override
    protected void destroy() {
        feedHandlerV3.destroy();
        // We are forking this to avoid that accidental dereferrencing causes any random thread doing destruction.
        // This caused a deadlock when the single Messenger thread in MessageBus was the last one referring this
        // and started destructing something that required something only the messenger thread could provide.
        Thread destroyer = new Thread(() -> {
            internalDestroy();
        });
        destroyer.setDaemon(true);
        destroyer.start();
    }

    private void internalDestroy() {
        super.destroy();
        workers.shutdown();
        cron.shutdown();
        synchronized (clients) {
            for (ClientState client : clients.values()) {
                client.sourceSession.getReference().close();
            }
            clients.clear();
        }
    }

    void putClient(final String sessionId, final ClientState value) {
        synchronized (clients) {
            clients.put(sessionId, value);
        }
    }

    ClientState popClient(String sessionId) {
        synchronized (clients) {
            return clients.remove(sessionId);
        }
    }

    /**
     * Guess what, testing only.
     */
    void forceRunCleanClients() {
        new CleanClients().run();
    }

}
