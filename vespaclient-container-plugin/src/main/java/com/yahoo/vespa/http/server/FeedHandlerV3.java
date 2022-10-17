// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.yolean.Exceptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One client has one ClientFeederV3 shared between all client threads.
 * Contains logic for shutting down cleanly as the server is upgraded.
 *
 * @author dybis
 */
public class FeedHandlerV3 extends ThreadedHttpRequestHandler {

    private DocumentTypeManager docTypeManager;
    private final Map<String, ClientFeederV3> clientFeederByClientId = new HashMap<>();
    private final ScheduledThreadPoolExecutor cron;
    private final SessionCache sessionCache;
    protected final ReplyHandler feedReplyHandler;
    private final Metric metric;
    private final Object monitor = new Object();
    private static final Logger log = Logger.getLogger(FeedHandlerV3.class.getName());

    public FeedHandlerV3(Executor executor,
                         Metric metric,
                         DocumentTypeManager documentTypeManager,
                         SessionCache sessionCache,
                         DocumentApiMetrics metricsHelper) {
        super(executor, metric);
        docTypeManager = documentTypeManager;
        this.sessionCache = sessionCache;
        feedReplyHandler = new FeedReplyReader(metric, metricsHelper);
        cron = new ScheduledThreadPoolExecutor(1, ThreadFactoryFactory.getThreadFactory("feed-handler-v3-janitor"));
        cron.scheduleWithFixedDelay(this::removeOldClients, 3, 3, TimeUnit.SECONDS);
        this.metric = metric;
    }

    public void injectDocumentManangerForTests(DocumentTypeManager docTypeManager) {
        this.docTypeManager = docTypeManager;
    }

    // TODO: If this is set up to run without first invoking the old FeedHandler code, we should
    //       verify the version header first.
    @Override
    public HttpResponse handle(HttpRequest request) {
        String clientId = clientId(request);
        ClientFeederV3 clientFeederV3;
        synchronized (monitor) {
            if (! clientFeederByClientId.containsKey(clientId)) {
                SourceSessionParams sourceSessionParams = sourceSessionParams(request);
                clientFeederByClientId.put(clientId,
                                           new ClientFeederV3(retainSource(sessionCache, sourceSessionParams),
                                                              new FeedReaderFactory(true), // TODO: Make error debugging configurable
                                                              docTypeManager,
                                                              clientId,
                                                              metric,
                                                              feedReplyHandler));
            }
            clientFeederV3 = clientFeederByClientId.get(clientId);
        }
        try {
            return clientFeederV3.handleRequest(request);
        } catch (UnknownClientException uce) {
            String msg = Exceptions.toMessageString(uce);
            log.log(Level.WARNING, msg);
            return new ErrorHttpResponse(com.yahoo.jdisc.http.HttpResponse.Status.BAD_REQUEST, msg);
        } catch (Exception e) {
            String msg = "Could not initialize document parsing: " + Exceptions.toMessageString(e);
            log.log(Level.WARNING, msg);
            return new ErrorHttpResponse(com.yahoo.jdisc.http.HttpResponse.Status.INTERNAL_SERVER_ERROR, msg);
        }
    }

    // SessionCache is final and no easy way to mock it so we need this to be able to do testing.
    protected ReferencedResource<SharedSourceSession> retainSource(SessionCache sessionCache, SourceSessionParams params) {
        return sessionCache.retainSource(params);
    }

    @Override
    protected void destroy() {
        // We are forking this to avoid that accidental de-referencing causes any random thread doing destruction.
        // This caused a deadlock when the single Messenger thread in MessageBus was the last one referring this
        // and started destructing something that required something only the messenger thread could provide.
        Thread destroyer = new Thread(() -> {
            cron.shutdown();
            synchronized (monitor) {
                for (var iterator = clientFeederByClientId.values().iterator(); iterator.hasNext(); ) {
                    iterator.next().kill();
                    iterator.remove();
                }
            }
        }, "feed-handler-v3-adhoc-destroyer");
        destroyer.setDaemon(true);
        destroyer.start();
    }

    private String clientId(HttpRequest request) {
        String clientDictatedId = request.getHeader(Headers.CLIENT_ID);
        if (clientDictatedId == null ||  clientDictatedId.isEmpty()) {
            throw new IllegalArgumentException("Did not get any CLIENT_ID header (" + Headers.CLIENT_ID + ")");
        }
        return clientDictatedId;
    }

    private SourceSessionParams sourceSessionParams(HttpRequest request) {
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

    private void removeOldClients() {
        synchronized (monitor) {
            for (var iterator = clientFeederByClientId.values().iterator(); iterator.hasNext(); ) {
                ClientFeederV3 client = iterator.next();
                if (client.timedOut()) {
                    client.kill();
                    iterator.remove();
                }
            }
        }
    }

}
