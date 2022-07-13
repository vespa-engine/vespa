// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.TargetWatcher;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.server.GetConfigContext;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import com.yahoo.vespa.config.server.monitoring.Metrics;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Takes care of <i>delayed responses</i> in the config server.
 * A delayed response is a response sent at request (server) timeout
 * for a config which has not changed since the request was initiated.
 *
 * @author hmusum
 */
public class DelayedConfigResponses {
    private static final Logger log = Logger.getLogger(DelayedConfigResponses.class.getName());
    private final RpcServer rpcServer;

    private final ScheduledExecutorService executorService;
    private final boolean useJrtWatcher;

    private final Map<ApplicationId, MetricUpdater> metrics = new ConcurrentHashMap<>();
    
    /* Requests that resolve to config that has not changed are put on this queue. When activating
       config, all requests on this queue are reprocessed as if they were a new request */
    private final Map<ApplicationId, BlockingQueue<DelayedConfigResponse>> delayedResponses =
            new ConcurrentHashMap<>();
            
    DelayedConfigResponses(RpcServer rpcServer, int numTimerThreads) {
        this(rpcServer, numTimerThreads, true);
    }

    // Since JRT does not allow adding watcher for "fake" requests, we must be able to disable it for unit tests :(
    DelayedConfigResponses(RpcServer rpcServer, int numTimerThreads, boolean useJrtWatcher) {
        this.rpcServer = rpcServer;
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(numTimerThreads,
                                                ThreadFactoryFactory.getDaemonThreadFactory("delayed config responses"));
        executor.setRemoveOnCancelPolicy(true);
        this.executorService = executor;
        this.useJrtWatcher = useJrtWatcher;
    }

    List<DelayedConfigResponse> allDelayedResponses() {
        List<DelayedConfigResponse> responses = new ArrayList<>();
        for (Map.Entry<ApplicationId, BlockingQueue<DelayedConfigResponse>> entry : delayedResponses.entrySet()) {
            responses.addAll(entry.getValue());
        }
        return responses;
    }

    /**
     * The run method of this class is run by a Timer when the timeout expires.
     * The timer associated with this response must be cancelled first.
     */
    class DelayedConfigResponse implements Runnable, TargetWatcher {

        final JRTServerConfigRequest request;
        private final BlockingQueue<DelayedConfigResponse> delayedResponsesQueue;
        private final ApplicationId app;
        private ScheduledFuture<?> future;

        DelayedConfigResponse(JRTServerConfigRequest req, BlockingQueue<DelayedConfigResponse> delayedResponsesQueue, ApplicationId app) {
            this.request = req;
            this.delayedResponsesQueue = delayedResponsesQueue;
            this.app = app;
        }

        @Override
        public synchronized void run() {
            removeFromQueue();
            removeWatcher();
            rpcServer.addToRequestQueue(request, true, null);
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, logPre()+"DelayedConfigResponse. putting on queue: " + request.getShortDescription());
            }
        }

        /**
         * Remove delayed response from its queue
         */
        private void removeFromQueue() {
            delayedResponsesQueue.remove(this);
        }

        JRTServerConfigRequest getRequest() {
            return request;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Delayed response for ").append(logPre()).append(request.getShortDescription());
            return sb.toString();
        }

        String logPre() {
            return TenantRepository.logPre(app);
        }

        synchronized void cancelAndRemove() {
            removeFromQueue();
            cancel();
        }

        synchronized boolean cancel() {
            removeWatcher();
            if (future == null) {
                throw new IllegalStateException("Cannot cancel a task that has not been scheduled");
            }
            return future.cancel(false);
        }

        synchronized void schedule(long delay) throws InterruptedException {
            delayedResponsesQueue.put(this);
            future = executorService.schedule(this, delay, TimeUnit.MILLISECONDS);
            addWatcher();
        }

        /**
         * Removes this delayed response if target is invalid.
         *
         * @param target a Target that has become invalid (i.e, client has closed connection)
         * @see DelayedConfigResponses
         */
        @Override
        public void notifyTargetInvalid(Target target) {
            cancelAndRemove();
        }

        private void addWatcher() {
            if (useJrtWatcher) {
                request.getRequest().target().addWatcher(this);
            }
        }

        private void removeWatcher() {
            if (useJrtWatcher) {
                request.getRequest().target().removeWatcher(this);
            }
        }
    }

    /**
     * Creates a DelayedConfigResponse object for taking care of requests that should
     * not be responded to right away.  Puts the object on the delayedResponsesQueue.
     *
     * NOTE: This method is called from multiple threads, so everything here needs to be
     * thread safe!
     *
     * @param request a JRTConfigRequest
     */
    final void delayResponse(JRTServerConfigRequest request, GetConfigContext context) {
        if (request.isDelayedResponse()) {
            log.log(Level.FINE, () -> context.logPre()+"Request already delayed");
        } else {            
            createQueueIfNotExists(context);
            BlockingQueue<DelayedConfigResponse> delayedResponsesQueue = delayedResponses.get(context.applicationId());
            DelayedConfigResponse response = new DelayedConfigResponse(request, delayedResponsesQueue, context.applicationId());
            request.setDelayedResponse(true);
            try {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, context.logPre()+"Putting on delayedRequests queue (" + delayedResponsesQueue.size() + " elements): " +
                            response.getRequest().getShortDescription());
                }
                // Config will be resolved in the run() method of DelayedConfigResponse,
                // when the timer expires or config is updated/activated.
                response.schedule(Math.max(0, request.getTimeout()));
                metricDelayedResponses(context.applicationId(), delayedResponsesQueue.size());
            } catch (InterruptedException e) {
                log.log(Level.WARNING, context.logPre()+"Interrupted when putting on delayed requests queue.");
            }
        }
    }

    private synchronized void metricDelayedResponses(ApplicationId app, int elems) {
        metrics.computeIfAbsent(app, key -> rpcServer.metricUpdaterFactory()
                                                     .getOrCreateMetricUpdater(Metrics.createDimensions(key)))
               .setDelayedResponses(elems);
    }

    private synchronized void createQueueIfNotExists(GetConfigContext context) {
        if ( ! delayedResponses.containsKey(context.applicationId())) {
            delayedResponses.put(context.applicationId(), new LinkedBlockingQueue<>());
        }
    }

    void stop() {
        executorService.shutdown();
    }

    /**
     * Drains delayed responses queue and returns responses in an array
     *
     * @return and array of DelayedConfigResponse objects
     */
    List<DelayedConfigResponse> drainQueue(ApplicationId app) {
        ArrayList<DelayedConfigResponse> ret = new ArrayList<>();
        
        if (delayedResponses.containsKey(app)) {
            BlockingQueue<DelayedConfigResponse> queue = delayedResponses.get(app);
            queue.drainTo(ret);
        }
        metrics.remove(app);
        return ret;
    }

    @Override
    public String toString() {
        return "DelayedConfigResponses. Average Size=" + size();
    }

    int size() {
        int totalQueueSize = 0;
        int numQueues = 0;
        for (Map.Entry<ApplicationId, BlockingQueue<DelayedConfigResponse>> e : delayedResponses.entrySet()) {
            numQueues++;
            totalQueueSize+=e.getValue().size();
        }
        return (numQueues > 0) ? (totalQueueSize / numQueues) : 0;
    }
}
