// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An Rpc client to a config source
 *
 * @author hmusum
 */
class RpcConfigSourceClient implements ConfigSourceClient, Runnable {

    private final static Logger log = Logger.getLogger(RpcConfigSourceClient.class.getName());
    private static final double timingValuesRatio = 0.8;

    private final Supervisor supervisor = new Supervisor(new Transport("config-source-client"));

    private final RpcServer rpcServer;
    private final ConfigSourceSet configSourceSet;
    private final Map<ConfigCacheKey, Subscriber> activeSubscribers = new ConcurrentHashMap<>();
    private final MemoryCache memoryCache;
    private final DelayedResponses delayedResponses;
    private final static TimingValues timingValues;
    private final ScheduledExecutorService nextConfigScheduler =
            Executors.newScheduledThreadPool(1, new DaemonThreadFactory("next config"));
    private final ScheduledFuture<?> nextConfigFuture;
    private final JRTConfigRequester requester;
    // Scheduled executor that periodically checks for requests that have timed out and response should be returned to clients
    private final ScheduledExecutorService delayedResponsesScheduler =
            Executors.newScheduledThreadPool(1, new DaemonThreadFactory("delayed responses"));
    private final ScheduledFuture<?> delayedResponsesFuture;

    static {
        // Proxy should time out before clients upon subscription.
        TimingValues tv = new TimingValues();
        tv.setUnconfiguredDelay((long)(tv.getUnconfiguredDelay()* timingValuesRatio)).
                setConfiguredErrorDelay((long)(tv.getConfiguredErrorDelay()* timingValuesRatio)).
                setSubscribeTimeout((long)(tv.getSubscribeTimeout()* timingValuesRatio)).
                setConfiguredErrorTimeout(-1);  // Never cache errors
        timingValues = tv;
    }

    RpcConfigSourceClient(RpcServer rpcServer, ConfigSourceSet configSourceSet, MemoryCache memoryCache) {
        this.rpcServer = rpcServer;
        this.configSourceSet = configSourceSet;
        this.memoryCache = memoryCache;
        this.delayedResponses = new DelayedResponses();
        checkConfigSources();
        nextConfigFuture = nextConfigScheduler.scheduleAtFixedRate(this, 0, 10, MILLISECONDS);
        requester = JRTConfigRequester.create(configSourceSet, timingValues);
        DelayedResponseHandler command = new DelayedResponseHandler(delayedResponses, memoryCache, rpcServer);
        delayedResponsesFuture = delayedResponsesScheduler.scheduleAtFixedRate(command, 5, 1, SECONDS);
    }

    /**
     * Checks if config sources are available
     */
    private void checkConfigSources() {
        if (configSourceSet == null || configSourceSet.getSources() == null || configSourceSet.getSources().size() == 0) {
            log.log(Level.WARNING, "No config sources defined, could not check connection");
        } else {
            Request req = new Request("ping");
            for (String configSource : configSourceSet.getSources()) {
                Spec spec = new Spec(configSource);
                Target target = supervisor.connect(spec);
                target.invokeSync(req, 30.0);
                if (target.isValid()) {
                    log.log(Level.FINE, () -> "Created connection to config source at " + spec.toString());
                    return;
                } else {
                    log.log(Level.INFO, "Could not connect to config source at " + spec.toString());
                }
                target.close();
            }
            String extra = "";
            log.log(Level.INFO, "Could not connect to any config source in set " + configSourceSet.toString() +
                    ", please make sure config server(s) are running. " + extra);
        }
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
    public RawConfig getConfig(RawConfig input, JRTServerConfigRequest request) {
        // Always add to delayed responses (we remove instead if we find config in cache)
        // This is to avoid a race where we might end up not adding to delayed responses
        // nor subscribing to config if another request for the same config
        // happens at the same time
        DelayedResponse delayedResponse = new DelayedResponse(request);
        delayedResponses.add(delayedResponse);

        final ConfigCacheKey configCacheKey = new ConfigCacheKey(input.getKey(), input.getDefMd5());
        RawConfig cachedConfig = memoryCache.get(configCacheKey);
        boolean needToGetConfig = true;

        RawConfig ret = null;
        if (cachedConfig != null) {
            log.log(Level.FINE, () -> "Found config " + configCacheKey + " in cache, generation=" + cachedConfig.getGeneration() +
                    ",configmd5=" + cachedConfig.getConfigMd5());
            log.log(Level.FINEST, () -> "input config=" + input + ",cached config=" + cachedConfig);
            if (ProxyServer.configOrGenerationHasChanged(cachedConfig, request)) {
                log.log(Level.FINEST, () -> "Cached config is not equal to requested, will return it");
                if (delayedResponses.remove(delayedResponse)) {
                    // unless another thread already did it
                    ret = cachedConfig;
                }
            }
            if (!cachedConfig.isError() && cachedConfig.getGeneration() > 0) {
                needToGetConfig = false;
            }
        }
        if (needToGetConfig) {
            subscribeToConfig(input, configCacheKey);
        }
        return ret;
    }

    private void subscribeToConfig(RawConfig input, ConfigCacheKey configCacheKey) {
        if (activeSubscribers.containsKey(configCacheKey)) return;

        log.log(Level.FINE, () -> "Could not find good config in cache, creating subscriber for: " + configCacheKey);
        var subscriber = new Subscriber(input, configSourceSet, timingValues, requester);
        try {
            subscriber.subscribe();
            activeSubscribers.put(configCacheKey, subscriber);
        } catch (ConfigurationRuntimeException e) {
            log.log(Level.INFO, "Subscribe for '" + configCacheKey + "' failed, closing subscriber");
            subscriber.cancel();
        }
    }

    @Override
    public void run() {
        activeSubscribers.values().forEach(subscriber -> {
            if (!subscriber.isClosed()) {
                Optional<RawConfig> config = subscriber.nextGeneration();
                config.ifPresent(this::updateWithNewConfig);
            }
        });
    }

    @Override
    public void cancel() {
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
        activeSubscribers.values().forEach(Subscriber::cancel);
        activeSubscribers.clear();
        log.log(Level.FINE, "nextConfigFuture.cancel");
        nextConfigFuture.cancel(true);
        log.log(Level.FINE, "nextConfigScheduler.shutdownNow");
        nextConfigScheduler.shutdownNow();
        log.log(Level.FINE, "requester.close");
        requester.close();
    }

    @Override
    public String getActiveSourceConnection() {
        return requester.getConnectionPool().getCurrent().getAddress();
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
     * This method will be called when a response with changed config is received from upstream
     * (content or generation has changed) or the server timeout has elapsed.
     *
     * @param config new config
     */
    public void updateSubscribers(RawConfig config) {
        log.log(Level.FINE, () -> "Config updated for " + config.getKey() + "," + config.getGeneration());
        DelayQueue<DelayedResponse> responseDelayQueue = delayedResponses.responses();
        log.log(Level.FINEST, () -> "Delayed response queue: " + responseDelayQueue);
        if (responseDelayQueue.size() == 0) {
            log.log(Level.FINE, () -> "There exists no matching element on delayed response queue for " + config.getKey());
            return;
        } else {
            log.log(Level.FINE, () -> "Delayed response queue has " + responseDelayQueue.size() + " elements");
        }
        boolean found = false;
        for (DelayedResponse response : responseDelayQueue.toArray(new DelayedResponse[0])) {
            JRTServerConfigRequest request = response.getRequest();
            if (request.getConfigKey().equals(config.getKey())
                    // Generation 0 is special, used when returning empty sentinel config
                    && (config.getGeneration() >= request.getRequestGeneration() || config.getGeneration() == 0)) {
                if (delayedResponses.remove(response)) {
                    found = true;
                    log.log(Level.FINE, () -> "Call returnOkResponse for " + config.getKey() + "," + config.getGeneration());
                    rpcServer.returnOkResponse(request, config);
                } else {
                    log.log(Level.INFO, "Could not remove " + config.getKey() + " from delayedResponses queue, already removed");
                }
            }
        }
        if (!found) {
            log.log(Level.FINE, () -> "Found no recipient for " + config.getKey() + " in delayed response queue");
        }
        log.log(Level.FINE, () -> "Finished updating config for " + config.getKey() + "," + config.getGeneration());
    }

    @Override
    public DelayedResponses delayedResponses() {
        return delayedResponses;
    }

    private void updateWithNewConfig(RawConfig newConfig) {
        log.log(Level.FINE, () -> "config to be returned for '" + newConfig.getKey() +
                                  "', generation=" + newConfig.getGeneration() +
                                  ", payload=" + newConfig.getPayload());
        memoryCache.update(newConfig);
        updateSubscribers(newConfig);
    }

}
