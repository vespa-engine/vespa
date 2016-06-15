// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Spec;

import com.yahoo.log.LogLevel;
import com.yahoo.log.LogSetup;
import com.yahoo.log.event.Event;
import com.yahoo.system.CatchSigTerm;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A proxy server that handles RPC config requests. The proxy can run in three modes:
 * 'default' and 'memorycache', where the last one will not get config from an upstream
 * config source, but will serve config only from memory cache.
 *
 * @author <a href="musum@yahoo-inc.com">Harald Musum</a>
 */
public class ProxyServer implements Runnable {

    private static final int DEFAULT_RPC_PORT = 19090;
    static final String DEFAULT_PROXY_CONFIG_SOURCES = "tcp/localhost:19070";

    final static Logger log = Logger.getLogger(ProxyServer.class.getName());
    private final AtomicBoolean signalCaught = new AtomicBoolean(false);

    // Scheduled executor that periodically checks for requests that have timed out and response should be returned to clients
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new DaemonThreadFactory());
    private final ClientUpdater clientUpdater;
    private ScheduledFuture<?> checkerHandle;

    private final ConfigProxyRpcServer rpcServer;
    final DelayedResponses delayedResponses;
    private ConfigSource configSource;

    private volatile ConfigSourceClient configSourceClient;

    private final ConfigProxyStatistics statistics;
    private final TimingValues timingValues;
    private final CacheManager cacheManager;
    private static final double timingvaluesRatio = 0.8;
    private final static TimingValues defaultTimingValues;
    private final boolean delayedResponseHandling;

    private volatile Mode mode;


    static {
        // Proxy should time out before clients upon subscription.
        TimingValues tv = new TimingValues();
        tv.setUnconfiguredDelay((long)(tv.getUnconfiguredDelay()* timingvaluesRatio)).
                setConfiguredErrorDelay((long)(tv.getConfiguredErrorDelay()* timingvaluesRatio)).
                setSubscribeTimeout((long)(tv.getSubscribeTimeout()* timingvaluesRatio)).
                setConfiguredErrorTimeout(-1);  // Never cache errors
        defaultTimingValues = tv;
    }

    private ProxyServer(Spec spec, DelayedResponses delayedResponses, ConfigSource source,
                       ConfigProxyStatistics statistics, TimingValues timingValues, Mode mode, boolean delayedResponseHandling,
                       CacheManager cacheManager) {
        this.delayedResponses = delayedResponses;
        this.configSource = source;
        log.log(LogLevel.DEBUG, "Using config sources: " + source);
        this.statistics = statistics;
        this.timingValues = timingValues;
        this.mode = mode;
        this.delayedResponseHandling = delayedResponseHandling;
        this.cacheManager = cacheManager;
        if (spec == null) {
            rpcServer = null;
        } else {
            rpcServer = new ConfigProxyRpcServer(this, spec);
        }
        clientUpdater = new ClientUpdater(cacheManager, rpcServer, statistics, delayedResponses, mode);
        this.configSourceClient = ConfigSourceClient.createClient(source, clientUpdater, cacheManager, timingValues, statistics, delayedResponses);
    }

    static ProxyServer create(int port, DelayedResponses delayedResponses, ConfigSource source,
                              ConfigProxyStatistics statistics, Mode mode) {
        return new ProxyServer(new Spec(null, port), delayedResponses, source, statistics, defaultTimingValues(), mode, true, new CacheManager(new MemoryCache()));
    }

    static ProxyServer createTestServer(ConfigSource source) {
        final Mode mode = new Mode(Mode.ModeName.DEFAULT.name());
        return ProxyServer.createTestServer(source, false, mode, CacheManager.createTestCacheManager());
    }

    static ProxyServer createTestServer(ConfigSource source, boolean delayedResponseHandling, Mode mode, CacheManager cacheManager) {
        final ConfigProxyStatistics statistics = new ConfigProxyStatistics();
        return new ProxyServer(null, new DelayedResponses(statistics), source, statistics, defaultTimingValues(), mode, delayedResponseHandling, cacheManager);
    }

    public void run() {
        if (rpcServer != null) {
            Thread t = new Thread(rpcServer);
            t.setName("RpcServer");
            t.start();
        }
        if (delayedResponseHandling) {
            // Wait for 5 seconds initially, then run every second
            checkerHandle = scheduler.scheduleAtFixedRate(new CheckDelayedResponses(delayedResponses, cacheManager.getMemoryCache(), rpcServer), 5, 1, SECONDS);
        } else {
            log.log(LogLevel.INFO, "Running without delayed response handling");
        }
    }

    public RawConfig resolveConfig(JRTServerConfigRequest req) {
        statistics.incProcessedRequests();
        // Calling getConfig() will either return with an answer immediately or
        // create a background thread that retrieves config from the server and
        // calls updateSubscribers when new config is returned from the config source.
        // In the last case the method below will return null.
        RawConfig config = configSourceClient.getConfig(RawConfig.createFromServerRequest(req), req);
        if (configOrGenerationHasChanged(config, req)) {
            cacheManager.putInCache(config);
        }
        return config;
    }

    static boolean configOrGenerationHasChanged(RawConfig config, JRTServerConfigRequest request) {
        return (config != null && (!config.hasEqualConfig(request) || config.hasNewerGeneration(request)));
    }

    Mode getMode() {
        return mode;
    }

    void setMode(String modeName) {
        if (modeName.equals(this.mode.name())) return;

        log.log(LogLevel.INFO, "Switching from " + this.mode + " mode to " + modeName.toLowerCase() + " mode");
        this.mode = new Mode(modeName);
        if (mode.isMemoryCache()) {
            configSourceClient.shutdownSourceConnections();
            configSourceClient = new MemoryCacheConfigClient(cacheManager.getMemoryCache());
        } else if (mode.isDefault()) {
            flush();
            configSourceClient = createRpcClient();
        } else {
            throw new IllegalArgumentException("Not able to handle mode '" + modeName + "'");
        }
    }

    private RpcConfigSourceClient createRpcClient() {
        return new RpcConfigSourceClient((ConfigSourceSet) configSource, clientUpdater, cacheManager, timingValues, statistics, delayedResponses);
    }

    private void setupSigTermHandler() {
        CatchSigTerm.setup(signalCaught); // catch termination signal
    }

    private void waitForShutdown() {
        synchronized (signalCaught) {
            while (!signalCaught.get()) {
                try {
                    signalCaught.wait();
                } catch (InterruptedException e) {
                    // empty
                }
            }
        }
        stop();
        System.exit(0);
    }

    public static void main(String[] args) {
        /* Initialize the log handler */
        LogSetup.clearHandlers();
        LogSetup.initVespaLogging("configproxy");

        Properties properties = getSystemProperties();

        int port = DEFAULT_RPC_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        Event.started("configproxy");
        ConfigProxyStatistics statistics = new ConfigProxyStatistics(properties.eventInterval);
        Thread t = new Thread(statistics);
        t.setName("Metrics generator");
        t.setDaemon(true);
        t.start();

        Mode startupMode = new Mode(properties.mode);
        if (!startupMode.isDefault()) log.log(LogLevel.INFO, "Starting config proxy in '"  + startupMode + "' mode");

        if (startupMode.isMemoryCache()) {
            log.log(LogLevel.ERROR, "Starting config proxy in '"  + startupMode + "' mode is not allowed");
            System.exit(1);
        }

        ConfigSourceSet configSources = new ConfigSourceSet();
        if (startupMode.requiresConfigSource()) {
            configSources = new ConfigSourceSet(properties.configSources);
        }
        DelayedResponses delayedResponses = new DelayedResponses(statistics);
        ProxyServer proxyServer = ProxyServer.create(port, delayedResponses, configSources, statistics, startupMode);
        // catch termination signal
        proxyServer.setupSigTermHandler();
        Thread proxyserverThread = new Thread(proxyServer);
        proxyserverThread.setName("configproxy");
        proxyserverThread.start();
        proxyServer.waitForShutdown();
    }

    static Properties getSystemProperties() {
        // Read system properties
        long eventInterval = Long.getLong("eventinterval", ConfigProxyStatistics.defaultEventInterval);
        String mode = System.getProperty("mode", Mode.ModeName.DEFAULT.name());
        final String[] inputConfigSources = System.getProperty("proxyconfigsources", DEFAULT_PROXY_CONFIG_SOURCES).split(",");
        return new Properties(eventInterval, mode, inputConfigSources);
    }

    static class Properties {
        final long eventInterval;
        final String mode;
        final String[] configSources;

        Properties(long eventInterval, String mode, String[] configSources) {
            this.eventInterval = eventInterval;
            this.mode = mode;
            this.configSources = configSources;
        }
    }

    static TimingValues defaultTimingValues() {
        return defaultTimingValues;
    }

    TimingValues getTimingValues() {
        return timingValues;
    }

    ConfigProxyStatistics getStatistics() {
        return statistics;
    }

    // Cancels all config instances and flushes the cache. When this method returns,
    // the cache will not be updated again before someone calls getConfig().
    synchronized void flush() {
        cacheManager.getMemoryCache().clear();
        configSourceClient.cancel();
    }

    public void stop() {
        Event.stopping("configproxy", "shutdown");
        if (rpcServer != null) rpcServer.shutdown();
        if (checkerHandle != null) checkerHandle.cancel(true);
        flush();
        if (statistics != null) {
            statistics.stop();
        }
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    String getActiveSourceConnection() {
        return configSourceClient.getActiveSourceConnection();
    }

    List<String> getSourceConnections() {
        return configSourceClient.getSourceConnections();
    }

    public void updateSourceConnections(List<String> sources) {
        configSource = new ConfigSourceSet(sources);
        flush();
        configSourceClient = createRpcClient();
    }
}
