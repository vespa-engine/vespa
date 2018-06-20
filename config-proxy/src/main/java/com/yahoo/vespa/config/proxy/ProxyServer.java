// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Spec;

import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogSetup;
import com.yahoo.log.event.Event;
import com.yahoo.system.CatchSigTerm;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.filedistribution.FileDistributionRpcServer;
import com.yahoo.vespa.filedistribution.FileDownloader;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.proxy.Mode.ModeName.DEFAULT;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A proxy server that handles RPC config requests. The proxy can run in two modes:
 * 'default' and 'memorycache', where the last one will not get config from an upstream
 * config source, but will serve config only from memory cache.
 *
 * @author hmusum
 */
public class ProxyServer implements Runnable {

    private static final int DEFAULT_RPC_PORT = 19090;
    static final String DEFAULT_PROXY_CONFIG_SOURCES = "tcp/localhost:19070";

    final static Logger log = Logger.getLogger(ProxyServer.class.getName());
    private final AtomicBoolean signalCaught = new AtomicBoolean(false);

    // Scheduled executor that periodically checks for requests that have timed out and response should be returned to clients
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new DaemonThreadFactory());
    private final Supervisor supervisor = new Supervisor(new Transport());
    private final ClientUpdater clientUpdater;
    private ScheduledFuture<?> delayedResponseScheduler;

    private final ConfigProxyRpcServer rpcServer;
    final DelayedResponses delayedResponses;
    private ConfigSourceSet configSource;

    private volatile ConfigSourceClient configClient;

    private final ConfigProxyStatistics statistics;
    private final TimingValues timingValues;
    private final MemoryCache memoryCache;
    private static final double timingValuesRatio = 0.8;
    private final static TimingValues defaultTimingValues;
    private final boolean delayedResponseHandling;
    private final FileDownloader fileDownloader;

    private volatile Mode mode = new Mode(DEFAULT);

    static {
        // Proxy should time out before clients upon subscription.
        TimingValues tv = new TimingValues();
        tv.setUnconfiguredDelay((long)(tv.getUnconfiguredDelay()* timingValuesRatio)).
                setConfiguredErrorDelay((long)(tv.getConfiguredErrorDelay()* timingValuesRatio)).
                setSubscribeTimeout((long)(tv.getSubscribeTimeout()* timingValuesRatio)).
                setConfiguredErrorTimeout(-1);  // Never cache errors
        defaultTimingValues = tv;
    }

    private ProxyServer(Spec spec, DelayedResponses delayedResponses, ConfigSourceSet source,
                        ConfigProxyStatistics statistics, TimingValues timingValues,
                        boolean delayedResponseHandling, MemoryCache memoryCache,
                        ConfigSourceClient configClient) {
        this.delayedResponses = delayedResponses;
        this.configSource = source;
        log.log(LogLevel.DEBUG, "Using config source '" + source);
        this.statistics = statistics;
        this.timingValues = timingValues;
        this.delayedResponseHandling = delayedResponseHandling;
        this.memoryCache = memoryCache;
        this.rpcServer = createRpcServer(spec);
        clientUpdater = new ClientUpdater(rpcServer, statistics, delayedResponses);
        this.configClient = createClient(clientUpdater, delayedResponses, source, timingValues, memoryCache, configClient);
        this.fileDownloader = new FileDownloader(new JRTConnectionPool(source));
        new FileDistributionRpcServer(supervisor, fileDownloader);
    }

    static ProxyServer createTestServer(ConfigSourceSet source) {
        return createTestServer(source, null, new MemoryCache(), new ConfigProxyStatistics());
    }

    static ProxyServer createTestServer(ConfigSourceSet source,
                                        ConfigSourceClient configSourceClient,
                                        MemoryCache memoryCache,
                                        ConfigProxyStatistics statistics) {
        final boolean delayedResponseHandling = false;
        return new ProxyServer(null, new DelayedResponses(statistics),
                               source, statistics, defaultTimingValues(), delayedResponseHandling,
                               memoryCache, configSourceClient);
    }

    public void run() {
        if (rpcServer != null) {
            Thread t = new Thread(rpcServer);
            t.setName("RpcServer");
            t.start();
        }
        if (delayedResponseHandling) {
            // Wait for 5 seconds initially, then run every second
            delayedResponseScheduler = scheduler.scheduleAtFixedRate(new DelayedResponseHandler(delayedResponses,
                                                                                                memoryCache,
                                                                                                rpcServer),
                                                                     5, 1, SECONDS);
        } else {
            log.log(LogLevel.INFO, "Running without delayed response handling");
        }
    }

    RawConfig resolveConfig(JRTServerConfigRequest req) {
        statistics.incProcessedRequests();
        // Calling getConfig() will either return with an answer immediately or
        // create a background thread that retrieves config from the server and
        // calls updateSubscribers when new config is returned from the config source.
        // In the last case the method below will return null.
        return configClient.getConfig(RawConfig.createFromServerRequest(req), req);
    }

    static boolean configOrGenerationHasChanged(RawConfig config, JRTServerConfigRequest request) {
        return (config != null && ( ! config.hasEqualConfig(request) || config.hasNewerGeneration(request)));
    }

    Mode getMode() {
        return mode;
    }

    void setMode(String modeName) {
        if (modeName.equals(this.mode.name())) return;

        log.log(LogLevel.INFO, "Switching from " + this.mode + " mode to " + modeName.toLowerCase() + " mode");
        this.mode = new Mode(modeName);
        switch (mode.getMode()) {
            case MEMORYCACHE:
                configClient.shutdownSourceConnections();
                configClient = new MemoryCacheConfigClient(memoryCache);
                break;
            case DEFAULT:
                flush();
                configClient = createRpcClient();
                break;
            default:
                throw new IllegalArgumentException("Not able to handle mode '" + modeName + "'");
        }
    }

    private ConfigSourceClient createClient(ClientUpdater clientUpdater, DelayedResponses delayedResponses,
                                            ConfigSourceSet source, TimingValues timingValues,
                                            MemoryCache memoryCache, ConfigSourceClient client) {
        return (client == null)
                ? new RpcConfigSourceClient(source, clientUpdater, memoryCache, timingValues, delayedResponses)
                : client;
    }

    private ConfigProxyRpcServer createRpcServer(Spec spec) {
        return  (spec == null) ? null : new ConfigProxyRpcServer(this, supervisor, spec); // TODO: Try to avoid first argument being 'this'
    }

    private RpcConfigSourceClient createRpcClient() {
        return new RpcConfigSourceClient(configSource, clientUpdater, memoryCache, timingValues, delayedResponses);
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

        ConfigSourceSet configSources = new ConfigSourceSet(properties.configSources);
        DelayedResponses delayedResponses = new DelayedResponses(statistics);
        ProxyServer proxyServer = new ProxyServer(new Spec(null, port), delayedResponses, configSources, statistics,
                                                  defaultTimingValues(), true, new MemoryCache(), null);
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
        final String[] inputConfigSources = System.getProperty("proxyconfigsources", DEFAULT_PROXY_CONFIG_SOURCES).split(",");
        return new Properties(eventInterval, inputConfigSources);
    }

    static class Properties {
        final long eventInterval;
        final String[] configSources;

        Properties(long eventInterval, String[] configSources) {
            this.eventInterval = eventInterval;
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
    private synchronized void flush() {
        memoryCache.clear();
        configClient.cancel();
    }

    void stop() {
        Event.stopping("configproxy", "shutdown");
        if (rpcServer != null) rpcServer.shutdown();
        if (delayedResponseScheduler != null) delayedResponseScheduler.cancel(true);
        flush();
        if (statistics != null) {
            statistics.stop();
        }
    }

    MemoryCache getMemoryCache() {
        return memoryCache;
    }

    String getActiveSourceConnection() {
        return configClient.getActiveSourceConnection();
    }

    List<String> getSourceConnections() {
        return configClient.getSourceConnections();
    }

    void updateSourceConnections(List<String> sources) {
        configSource = new ConfigSourceSet(sources);
        flush();
        configClient = createRpcClient();
    }

    FileDownloader fileDownloader() {
        return fileDownloader;
    }
}
