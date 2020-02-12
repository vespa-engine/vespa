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
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * An Rpc client to a config source
 *
 * @author hmusum
 */
class RpcConfigSourceClient implements ConfigSourceClient {

    private final static Logger log = Logger.getLogger(RpcConfigSourceClient.class.getName());
    private final Supervisor supervisor = new Supervisor(new Transport());

    private final RpcServer rpcServer;
    private final ConfigSourceSet configSourceSet;
    private final HashMap<ConfigCacheKey, Subscriber> activeSubscribers = new HashMap<>();
    private final Object activeSubscribersLock = new Object();
    private final MemoryCache memoryCache;
    private final DelayedResponses delayedResponses;
    private final TimingValues timingValues;

    private final ExecutorService exec;
    private final Map<ConfigSourceSet, JRTConfigRequester> requesterPool;


    RpcConfigSourceClient(RpcServer rpcServer,
                          ConfigSourceSet configSourceSet,
                          MemoryCache memoryCache,
                          TimingValues timingValues,
                          DelayedResponses delayedResponses) {
        this.rpcServer = rpcServer;
        this.configSourceSet = configSourceSet;
        this.memoryCache = memoryCache;
        this.delayedResponses = delayedResponses;
        this.timingValues = timingValues;
        checkConfigSources();
        exec = Executors.newCachedThreadPool(new DaemonThreadFactory("subscriber-"));
        requesterPool = createRequesterPool(configSourceSet, timingValues);
    }

    /**
     * Creates a requester (connection) pool of one entry, to be used each time this {@link RpcConfigSourceClient} is used
     * @param ccs a {@link ConfigSourceSet}
     * @param timingValues a {@link TimingValues}
     * @return requester map
     */
    private Map<ConfigSourceSet, JRTConfigRequester> createRequesterPool(ConfigSourceSet ccs, TimingValues timingValues) {
        Map<ConfigSourceSet, JRTConfigRequester> ret = new HashMap<>();
        if (ccs.getSources().isEmpty()) return ret; // unit test, just skip creating any requester
        ret.put(ccs, new JRTConfigRequester(new JRTConnectionPool(configSourceSet), timingValues));
        return ret;
    }

    /**
     * Checks if config sources are available
     */
    private void checkConfigSources() {
        if (configSourceSet == null || configSourceSet.getSources() == null || configSourceSet.getSources().size() == 0) {
            log.log(LogLevel.WARNING, "No config sources defined, could not check connection");
        } else {
            Request req = new Request("ping");
            for (String configSource : configSourceSet.getSources()) {
                Spec spec = new Spec(configSource);
                Target target = supervisor.connect(spec);
                target.invokeSync(req, 30.0);
                if (target.isValid()) {
                    log.log(LogLevel.DEBUG, () -> "Created connection to config source at " + spec.toString());
                    return;
                } else {
                    log.log(LogLevel.INFO, "Could not connect to config source at " + spec.toString());
                }
                target.close();
            }
            String extra = "";
            log.log(LogLevel.INFO, "Could not connect to any config source in set " + configSourceSet.toString() +
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
            log.log(LogLevel.DEBUG, () -> "Found config " + configCacheKey + " in cache, generation=" + cachedConfig.getGeneration() +
                    ",configmd5=" + cachedConfig.getConfigMd5());
            log.log(LogLevel.SPAM, () -> "input config=" + input + ",cached config=" + cachedConfig);
            if (ProxyServer.configOrGenerationHasChanged(cachedConfig, request)) {
                log.log(LogLevel.SPAM, () -> "Cached config is not equal to requested, will return it");
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
        synchronized (activeSubscribersLock) {
            if (activeSubscribers.containsKey(configCacheKey)) {
                log.log(LogLevel.DEBUG, () -> "Already a subscriber running for: " + configCacheKey);
            } else {
                log.log(LogLevel.DEBUG, () -> "Could not find good config in cache, creating subscriber for: " + configCacheKey);
                UpstreamConfigSubscriber subscriber = new UpstreamConfigSubscriber(input, this, configSourceSet,
                                                                                   timingValues, requesterPool, memoryCache);
                try {
                    subscriber.subscribe();
                    activeSubscribers.put(configCacheKey, subscriber);
                    exec.execute(subscriber);
                } catch (ConfigurationRuntimeException e) {
                    log.log(LogLevel.INFO, "Subscribe for '" + configCacheKey + "' failed, closing subscriber");
                    subscriber.cancel();
                }
            }
        }
    }

    @Override
    public void cancel() {
        shutdownSourceConnections();
    }

    /**
     * Takes down connection(s) to config sources and running tasks
     */
    @Override
    public void shutdownSourceConnections() {
        synchronized (activeSubscribersLock) {
            for (Subscriber subscriber : activeSubscribers.values()) {
                subscriber.cancel();
            }
            activeSubscribers.clear();
        }
        exec.shutdown();
        for (JRTConfigRequester requester : requesterPool.values()) {
            requester.close();
        }
    }

    @Override
    public String getActiveSourceConnection() {
        if (requesterPool.get(configSourceSet) != null) {
            return requesterPool.get(configSourceSet).getConnectionPool().getCurrent().getAddress();
        } else {
            return "";
        }
    }

    @Override
    public List<String> getSourceConnections() {
        ArrayList<String> ret = new ArrayList<>();
        final JRTConfigRequester jrtConfigRequester = requesterPool.get(configSourceSet);
        if (jrtConfigRequester != null) {
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
        log.log(LogLevel.DEBUG, () -> "Config updated for " + config.getKey() + "," + config.getGeneration());
        DelayQueue<DelayedResponse> responseDelayQueue = delayedResponses.responses();
        log.log(LogLevel.SPAM, () -> "Delayed response queue: " + responseDelayQueue);
        if (responseDelayQueue.size() == 0) {
            log.log(LogLevel.DEBUG, () -> "There exists no matching element on delayed response queue for " + config.getKey());
            return;
        } else {
            log.log(LogLevel.DEBUG, () -> "Delayed response queue has " + responseDelayQueue.size() + " elements");
        }
        boolean found = false;
        for (DelayedResponse response : responseDelayQueue.toArray(new DelayedResponse[0])) {
            JRTServerConfigRequest request = response.getRequest();
            if (request.getConfigKey().equals(config.getKey())
                    // Generation 0 is special, used when returning empty sentinel config
                    && (config.getGeneration() >= request.getRequestGeneration() || config.getGeneration() == 0)) {
                if (delayedResponses.remove(response)) {
                    found = true;
                    log.log(LogLevel.DEBUG, () -> "Call returnOkResponse for " + config.getKey() + "," + config.getGeneration());
                    rpcServer.returnOkResponse(request, config);
                } else {
                    log.log(LogLevel.INFO, "Could not remove " + config.getKey() + " from delayedResponses queue, already removed");
                }
            }
        }
        if (!found) {
            log.log(LogLevel.DEBUG, () -> "Found no recipient for " + config.getKey() + " in delayed response queue");
        }
        log.log(LogLevel.DEBUG, () -> "Finished updating config for " + config.getKey() + "," + config.getGeneration());
    }

}
