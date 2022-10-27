// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.JrtConfigRequesters;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An Rpc client to a config source
 *
 * @author hmusum
 */
class RpcConfigSourceClient implements ConfigSourceClient, Runnable {

    private static final Logger log = Logger.getLogger(RpcConfigSourceClient.class.getName());
    private static final TimingValues timingValues = createTimingValues();

    private final Supervisor supervisor = new Supervisor(new Transport("config-source-client"));

    private final ResponseHandler responseHandler;
    private final ConfigSourceSet configSourceSet;
    private final Object subscribersLock = new Object();
    private final Map<ConfigCacheKey, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final MemoryCache memoryCache;
    private final DelayedResponses delayedResponses;
    private final ScheduledExecutorService nextConfigScheduler =
            Executors.newScheduledThreadPool(1, new DaemonThreadFactory("next config"));
    private final ScheduledFuture<?> nextConfigFuture;
    private final JrtConfigRequesters requesters;
    // Scheduled executor that periodically checks for requests that have timed out and response should be returned to clients
    private final ScheduledExecutorService delayedResponsesScheduler =
            Executors.newScheduledThreadPool(1, new DaemonThreadFactory("delayed responses"));
    private final ScheduledFuture<?> delayedResponsesFuture;

    RpcConfigSourceClient(ResponseHandler responseHandler, ConfigSourceSet configSourceSet) {
        this.responseHandler = responseHandler;
        this.configSourceSet = configSourceSet;
        this.memoryCache = new MemoryCache();
        this.delayedResponses = new DelayedResponses();
        checkConfigSources();
        nextConfigFuture = nextConfigScheduler.scheduleAtFixedRate(this, 0, SystemTimer.adjustTimeoutByDetectedHz(Duration.ofMillis(10)).toMillis(), MILLISECONDS);
        this.requesters = new JrtConfigRequesters();
        DelayedResponseHandler command = new DelayedResponseHandler(delayedResponses, memoryCache, responseHandler);
        this.delayedResponsesFuture = delayedResponsesScheduler.scheduleAtFixedRate(command, 5, 1, SECONDS);
    }

    /**
     * Checks if config sources are available
     */
    private void checkConfigSources() {
        if (configSourceSet == null || configSourceSet.getSources() == null || configSourceSet.getSources().size() == 0)
            throw new IllegalArgumentException("No config sources defined, could not check connection");

        Request req = new Request("ping");
        for (String configSource : configSourceSet.getSources()) {
            Spec spec = new Spec(configSource);
            Target target = supervisor.connect(spec);
            target.invokeSync(req, Duration.ofSeconds(30));
            if (target.isValid())
                return;

            log.log(Level.INFO, "Could not connect to config source at " + spec.toString());
            target.close();
        }
        log.log(Level.INFO, "Could not connect to any config source in set " + configSourceSet.toString() +
                ", please make sure config server(s) are running.");
    }

    /**
     * Retrieves the requested config from the cache or the remote server.
     * <p>
     * If the requested config is different from the one in cache, the cached request is returned immediately.
     * If they are equal, this method returns null.
     * <p>
     * If the config was not in cache, this method starts a <em>Subscriber</em> in a separate thread
     * that gets the config and calls updateSubscribers().
     *
     * @param input The config to retrieve - can be empty (no payload), or have a valid payload.
     * @return A Config with a payload.
     */
    @Override
    public Optional<RawConfig> getConfig(RawConfig input, JRTServerConfigRequest request) {
        // Always add to delayed responses (we remove instead if we find config in cache)
        // This is to avoid a race where we might end up not adding to delayed responses
        // nor subscribing to config if another request for the same config
        // happens at the same time
        DelayedResponse delayedResponse = new DelayedResponse(request);
        delayedResponses.add(delayedResponse);

        ConfigCacheKey configCacheKey = new ConfigCacheKey(input.getKey(), input.getDefMd5());
        Optional<RawConfig> cachedConfig = memoryCache.get(configCacheKey);
        boolean needToGetConfig = true;

        if (cachedConfig.isPresent()) {
            RawConfig config = cachedConfig.get();
            log.log(Level.FINE, () -> "Found config " + configCacheKey + " in cache, generation=" + config.getGeneration() +
                    ",config checksums=" + config.getPayloadChecksums());
            log.log(Level.FINEST, () -> "input config=" + input + ",cached config=" + config);
            if (ProxyServer.configOrGenerationHasChanged(config, request)) {
                log.log(Level.FINEST, () -> "Cached config is not equal to requested, will return it");
                if (delayedResponses.remove(delayedResponse)) {
                    // unless another thread already did it
                    return cachedConfig;
                }
            }
            if (!config.isError() && config.getGeneration() > 0) {
                needToGetConfig = false;
            }
        }
        if (needToGetConfig) {
            subscribeToConfig(input, configCacheKey);
        }
        return Optional.empty();
    }

    private void subscribeToConfig(RawConfig input, ConfigCacheKey configCacheKey) {
        synchronized (subscribersLock) {
            if (subscribers.containsKey(configCacheKey)) return;

            log.log(Level.FINE, () -> "Could not find good config in cache, creating subscriber for: " + configCacheKey);
            var subscriber = new Subscriber(input, timingValues, requesters
                    .getRequester(configSourceSet, timingValues));
            try {
                subscriber.subscribe();
                subscribers.put(configCacheKey, subscriber);
            } catch (ConfigurationRuntimeException e) {
                log.log(Level.INFO, "Subscribe for '" + configCacheKey + "' failed, closing subscriber", e);
                subscriber.cancel();
            }
        }
    }

    @Override
    public void run() {
        Collection<Subscriber> s;
        synchronized (subscribersLock) {
            s = List.copyOf(subscribers.values());
        }
        s.forEach(subscriber -> {
            if (!subscriber.isClosed()) {
                Optional<RawConfig> config = subscriber.nextGeneration();
                config.ifPresent(this::updateWithNewConfig);
            }
        });
    }

    @Override
    public void shutdown() {
        log.log(Level.FINE, "shutdownSourceConnections");
        shutdownSourceConnections();
        log.log(Level.FINE, "delayedResponsesFuture.cancel");
        delayedResponsesFuture.cancel(true);
        log.log(Level.FINE, "delayedResponsesFuture.shutdownNow");
        delayedResponsesScheduler.shutdownNow();
        log.log(Level.FINE, "supervisor.transport().shutdown().join()");
        supervisor.transport().shutdown().join();
    }

    /**
     * Takes down connection(s) to config sources and running tasks
     */
    @Override
    public void shutdownSourceConnections() {
        log.log(Level.FINE, "Subscriber::cancel");
        synchronized (subscribers) {
            subscribers.values().forEach(Subscriber::cancel);
            subscribers.clear();
        }
        log.log(Level.FINE, "nextConfigFuture.cancel");
        nextConfigFuture.cancel(true);
        log.log(Level.FINE, "nextConfigScheduler.shutdownNow");
        nextConfigScheduler.shutdownNow();
        log.log(Level.FINE, "requester.close");
        requesters.close();
    }

    @Override
    public String getActiveSourceConnection() {
        return requesters.getRequester(configSourceSet, timingValues).getConnectionPool().getCurrent().getAddress();
    }

    @Override
    public List<String> getSourceConnections() {
        ArrayList<String> ret = new ArrayList<>();
        if (configSourceSet != null) {
            ret.addAll(configSourceSet.getSources());
        }
        return ret;
    }

    /**
     * Updates subscribers with new config. This method will be called when a response with changed config is
     * received from upstream (content or generation has changed) or the server timeout has elapsed.
     *
     * @param config new config
     */
    public void updateSubscribers(RawConfig config) {
        ConfigKey<?> key = config.getKey();
        long generation = config.getGeneration();
        log.log(Level.FINE, () -> "Config updated for " + key + "," + generation);
        DelayQueue<DelayedResponse> responseDelayQueue = delayedResponses.responses();
        if (responseDelayQueue.size() == 0) return;

        log.log(Level.FINE, () -> "Delayed response queue has " + responseDelayQueue.size() + " elements");
        log.log(Level.FINEST, () -> "Delayed response queue: " + responseDelayQueue);
        boolean found = false;
        for (DelayedResponse response : responseDelayQueue.toArray(new DelayedResponse[0])) {
            JRTServerConfigRequest request = response.getRequest();
            if (request.getConfigKey().equals(key)
                    // Generation 0 is special, used when returning empty sentinel config
                    && (generation >= request.getRequestGeneration() || generation == 0)) {
                if (delayedResponses.remove(response)) {
                    found = true;
                    log.log(Level.FINE, () -> "Call returnOkResponse for " + key + "," + generation);
                    if (config.getPayload().getData().getByteLength() == 0)
                        log.log(Level.WARNING, () -> "Call returnOkResponse for " + key + "," + generation + " with empty config");
                    responseHandler.returnOkResponse(request, config);
                } else {
                    log.log(Level.INFO, "Could not remove " + key + " from delayedResponses queue, already removed");
                }
            }
        }
        if (!found) {
            log.log(Level.FINE, () -> "Found no recipient for " + key + " in delayed response queue");
        }
        log.log(Level.FINE, () -> "Finished updating config for " + key + "," + generation);
    }

    @Override
    public DelayedResponses delayedResponses() { return delayedResponses; }

    @Override
    public MemoryCache memoryCache() { return memoryCache; }

    private void updateWithNewConfig(RawConfig newConfig) {
        log.log(Level.FINE, () -> "config to be returned for '" + newConfig.getKey() +
                                  "', generation=" + newConfig.getGeneration() +
                                  ", payload=" + newConfig.getPayload());
        memoryCache.update(newConfig);
        updateSubscribers(newConfig);
    }

    private static TimingValues createTimingValues() {
        // Proxy should time out before clients upon subscription.
        double timingValuesRatio = 0.8;

        return new TimingValues()
                .setFixedDelay((long) (new TimingValues().getFixedDelay() * timingValuesRatio))
                .setSubscribeTimeout((long) (new TimingValues().getSubscribeTimeout() * timingValuesRatio));
    }

}
